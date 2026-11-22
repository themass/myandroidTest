package com.openapi.ks.myapp.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import com.openapi.ks.myapp.avatar.ProactiveKind
import com.openapi.ks.myapp.companion.protocol.BargeInGate
import com.openapi.ks.myapp.companion.protocol.CompanionProtocolLink
import com.openapi.ks.myapp.companion.protocol.CueGapBaseline
import com.openapi.ks.myapp.companion.protocol.Decision
import com.openapi.ks.myapp.companion.protocol.FirstAudioDetector
import com.openapi.ks.myapp.companion.protocol.DegradeController
import com.openapi.ks.myapp.companion.protocol.PresentationCue
import com.openapi.ks.myapp.companion.protocol.ProtocolCatalog
import com.openapi.ks.myapp.companion.protocol.ProtocolCodec
import com.openapi.ks.myapp.companion.protocol.ProtocolHandshake
import com.openapi.ks.myapp.companion.protocol.ScheduledCue
import com.openapi.ks.myapp.companion.protocol.SpeechRegistry
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.datastream.StreamTextOptions
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.AudioTrack
import io.livekit.android.room.track.DataPublishReliability
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import livekit.org.webrtc.AudioTrackSink
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteOrder
import java.nio.ByteBuffer
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 与服务端 companion agent 通过 LiveKit 房间通信。话题名与服务端
 * src/companion/agent/entrypoint.py 保持一致：
 *
 * - 用户文字：`lk.chat` 文本流（agent 的 TEXT_TOPIC）
 * - agent 文字回复：`lk.transcription` 文本流，只取 agent 发出的那一路（按发送者区分，
 *   用户自己的语音转写不当作她说的话）
 * - 形象事件 `companion.avatar`、主动消息 `companion.proactive`、待确认动作 `companion.action`
 *   都是数据通道 JSON
 *
 * 待确认的回应走 REST（服务端执行器是权威），所以这里不处理 confirm。
 */
class LiveKitCompanionTransport(
    private val appContext: Context,
) : CompanionTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bus = MutableSharedFlow<CompanionEvent>(extraBufferCapacity = EVENT_BUFFER)
    private val _connection = MutableStateFlow(TransportConnection.DISCONNECTED)
    private val _lastConnectionError = MutableStateFlow<String?>(null)
    val lastConnectionError: StateFlow<String?> = _lastConnectionError.asStateFlow()
    private var room: Room? = null

    /** 正在跑健康上报的那个 `Room`。同一房间重复 `Connected` 时不另起一个循环。 */
    @Volatile private var healthLoopRoom: Room? = null

    /**
     * 协议 v2 的连接状态（epoch / 序号 / 协商结果 / 收侧计数）。
     *
     * 每次 `connect()` 重新建一个：epoch 必须换，旧连接的簿记对新连接没有意义。
     * 用 `@Volatile` 是因为房间事件在 SDK 线程、发送在建连协程，两边都会碰它。
     */
    @Volatile private var link: CompanionProtocolLink? = null
    @Volatile private var activeAttemptId: String = "none"
    @Volatile private var connectStartedAtMs: Long = 0L
    @Volatile private var firstRemotePcmLogged = false
    private val firstAudio = FirstAudioDetector(REMOTE_FIRST_AUDIO_RMS)
    @Volatile private var firstLocalPcmLogged = false

    /** 最近一次麦克风意图。房间还没建好时先存着，连上后补偿执行。 */
    @Volatile private var micIntent: Boolean? = null
    @Volatile private var playbackAudible = true

    /**
     * §9 的语音时间线与表现 cue 队列。
     *
     * 单独一个对象而不是散在 transport 里：调度判定是纯逻辑，能用假时钟单测
     * （[com.openapi.ks.myapp.companion.protocol.SpeechRegistryTest]）。这里只负责把
     * "现在几毫秒"和"音频开始播了"喂进去。
     *
     * payload 是**已翻译好的 v1 事件串**：锚点解析发生在收包线程，执行发生在调度协程，
     * 中间隔着一段时间——所以进队列时就翻译完，不能在执行时再解析 args。
     */
    private val speech = SpeechRegistry<String>()

    /** §12.3：本地降级阶梯。假时钟可测；这里只喂传感器并在 apply 闸门用。 */
    private val degrade = DegradeController()

    /**
     * P4 验收注入口（§12.3「每一档都能手动触发」）：
     * `adb shell am broadcast -a com.openapi.ks.chat.action.DEBUG_DEGRADE_LEVEL --ei level 2`
     * （level = -1 解除强制，回自动判定）。debug 构建才注册——release 里留这个广播
     * 等于把「形象冻结」开关交给任何能发广播的应用。见 DegradeDebugInjectTest。
     */
    private var debugDegradeReceiver: BroadcastReceiver? = null

    private fun registerDegradeDebugInject() {
        if (!isDebugBuild) return
        val filter = IntentFilter("com.openapi.ks.chat.action.DEBUG_DEGRADE_LEVEL")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra("level", -2) ?: return
                degrade.forceLevel = if (level < 0) null else level
                logWarn("attempt=$activeAttemptId event=degrade_force level=${degrade.forceLevel}")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
        debugDegradeReceiver = receiver
    }

    /** L1 表情抽样序号：每条到闸的 emotion +1，`seq % 2 == 0` 才放行。 */
    private var emotionEmitSeq = 0

    /** 上一次 speechLoop 看到的降级档位，只为找「升入 L2」那条边（B3）。 */
    private var lastDegradeLevel = 0

    /** lifetime gaps → 本连接 streak 增量；重连必须 baseline，不能清零。 */
    private val cueGaps = CueGapBaseline()

    /** §10.1：本地开口打断闸。只在本房间生命周期内有意义。 */
    private val bargeGate = BargeInGate()

    /** 有新 cue 入库 / 有新音频时把自己踹醒。CONFLATED：连来十条只醒一次，醒来全扫。 */
    private val speechWake = Channel<Unit>(Channel.CONFLATED)

    /** 正在跑调度循环的房间。同一房间重复 `Connected` 时不另起一个。 */
    @Volatile private var speechLoopRoom: Room? = null

    /** 麦克风发布串行化：连点两下开/关，异步调用乱序会让按钮状态和实际发布不一致。 */
    private val micMutex = Mutex()

    /**
     * 远端（她）说话的瞬时振幅 0..1，取自 LiveKit 音频轨的 PCM 回调。
     *
     * 只留一个 StateFlow 而不是走 [bus]：振幅是每秒几十次的高频采样，
     * 走事件流会让「每条消息一个事件」的语义被冲垮，而且没人需要它的历史。
     */
    private val _mouthLevel = MutableStateFlow(0f)
    override val mouthLevel: StateFlow<Float> = _mouthLevel.asStateFlow()

    private val _remotePcm = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    override val remotePcm: Flow<ByteArray> = _remotePcm.asSharedFlow()

    /**
     * 用户自己的音量 0..1。本地麦克风采集轨的 PCM。
     *
     * 以前通话页底部波形接的是 [mouthLevel]（她的声音），所以「你说话波形不动」——
     * 那本来就画错了对象。这里补上本地采集的振幅。
     */
    private val _selfLevel = MutableStateFlow(0f)
    override val selfLevel: StateFlow<Float> = _selfLevel.asStateFlow()
    private val _selfBands = MutableStateFlow(FloatArray(0))
    override val selfBands: StateFlow<FloatArray> = _selfBands.asStateFlow()

    /** 已挂上的远端音频 sink；断开时按这个引用摘掉，避免泄漏。 */
    private var mouthSink: AudioTrackSink? = null
    private var mouthTrack: AudioTrack? = null

    /** 已挂上的本地采集 sink（驱动 [selfLevel]）。 */
    private var selfSink: AudioTrackSink? = null
    private var selfTrack: AudioTrack? = null

    /** 远端音频轨，用于退出通话页时静音播放（不是断开房间）。 */
    private var remoteAudio: io.livekit.android.room.track.RemoteAudioTrack? = null

    override val events: Flow<CompanionEvent> = bus.asSharedFlow()

    /**
     * `media.image` 谈成了没有——决定输入区的「+」出不出现在用户面前。
     *
     * 只有这一处写它的值，写它的四个时刻都是「现在还不知道 / 知道了」这两件事之一：
     * 收到 `hello_ack`（知道），以及 [connect] 重建 link、`RoomEvent.Reconnected` 清掉
     * 协商表、[disconnect] 断开（三条都是不知道——留着上一条连接的答案，等于让用户
     * 对着一个按下去什么都不会发生的「+」等）。
     */
    private val _imageSupported = MutableStateFlow(false)
    override val imageSupported: StateFlow<Boolean> = _imageSupported.asStateFlow()

    /** 房间事件到界面状态的唯一来源：重连/重连成功都必须上报，否则通话页只能永远显示「正在接通」。 */
    override val connection: StateFlow<TransportConnection> = _connection.asStateFlow()

    override suspend fun connect(session: SessionResponse) {
        require(session.livekitUrl.isNotBlank() && session.token.isNotBlank()) {
            "会话缺少房间地址或入场凭证"
        }
        activeAttemptId = UUID.randomUUID().toString().take(8)
        connectStartedAtMs = SystemClock.elapsedRealtime()
        firstRemotePcmLogged = false
        firstAudio.reset()
        firstLocalPcmLogged = false
        logInfo("attempt=$activeAttemptId event=connect_start")
        _connection.value = TransportConnection.CONNECTING
        _lastConnectionError.value = null
        val newRoom = LiveKit.create(appContext)
        room = newRoom
        registerDegradeDebugInject()
        // 新连接就是新 epoch（§5.1）：沿用上一次的 epoch，对端会把我从 0 开始的 seq
        // 当成回退丢掉。identity 用来判 dst 是不是指我，现在还不知道，等连上再看。
        val protocolLink = CompanionProtocolLink(identity = null)
        link = protocolLink
        // 新连接上的协商结果还不知道：先收回「不能发图」，等 hello_ack 到了再说。
        // 留着上一条连接的答案，会让用户在一个不支持图片的服务端面前按一个按不动的「+」。
        _imageSupported.value = false
        logInfo("attempt=$activeAttemptId event=room_created elapsedMs=${SystemClock.elapsedRealtime() - connectStartedAtMs}")

        // SDK events are not replayed: subscribe before starting the signal handshake.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            newRoom.events.events.collect { event ->
                when (event) {
                    is RoomEvent.DataReceived -> handleData(
                        topic = event.topic,
                        data = event.data,
                        publisher = event.participant,
                    )
                    is RoomEvent.Connected -> {
                        _lastConnectionError.value = null
                        _connection.value = TransportConnection.CONNECTED
                        degrade.onConnection(DegradeController.Conn.CONNECTED)
                        logInfo("attempt=$activeAttemptId event=room_connected elapsedMs=${SystemClock.elapsedRealtime() - connectStartedAtMs}")
                        // identity 由 SDK 在入会时分配，建 Room 的时候还拿不到。不补上的话
                        // 带 `dst` 的定向消息全被当成「不是给我的」丢掉，而广播照常——
                        // 表现是「她单聊时说的话我收不到，群发都正常」，很难查。
                        protocolLink.bindIdentity(newRoom.localParticipant.identity?.value)
                        // 握手在事件回调里发，而不是等 connect() 协程恢复。实测
                        // 协程恢复比本回调晚 2–4 秒（首帧建连时更明显），而服务端
                        // §7.3 只等 3 秒——hello 再可靠也救不回迟到的发送。
                        sendHello(newRoom, protocolLink)
                        scheduleHelloRetry(newRoom, protocolLink)
                        startHealthLoop(newRoom)
                        startSpeechLoop(newRoom)
                    }
                    is RoomEvent.FailedToConnect -> {
                        _connection.value = TransportConnection.DISCONNECTED
                        degrade.onConnection(DegradeController.Conn.DISCONNECTED)
                        _lastConnectionError.value = safeError(event.error.message)
                        logWarn("attempt=$activeAttemptId event=room_failed_to_connect type=${event.error.javaClass.simpleName} message=${safeError(event.error.message)}")
                    }
                    is RoomEvent.TrackSubscribed -> {
                        logInfo("attempt=$activeAttemptId event=track_subscribed kind=${event.track.kind} sid=${event.track.sid}")
                        if (event.participant.identity != newRoom.localParticipant.identity) attachMouthSink(event.track)
                    }
                    is RoomEvent.TrackUnsubscribed -> {
                        logInfo("attempt=$activeAttemptId event=track_unsubscribed sid=${event.track.sid}")
                        detachMouthSink()
                    }
                    is RoomEvent.Reconnecting -> {
                        _connection.value = TransportConnection.RECONNECTING
                        degrade.onConnection(DegradeController.Conn.RECONNECTING)
                        logWarn("attempt=$activeAttemptId event=reconnecting")
                    }
                    is RoomEvent.Reconnected -> {
                        _connection.value = TransportConnection.CONNECTED
                        degrade.onConnection(DegradeController.Conn.CONNECTED)
                        _lastConnectionError.value = null
                        logInfo("attempt=$activeAttemptId event=reconnected elapsedMs=${SystemClock.elapsedRealtime() - connectStartedAtMs}")
                        attachSelfSink(newRoom)
                        // 重连是新的一条流：清表、换 epoch、重新握手（§5.1 / F4）。
                        // 少了这一步，对端从 0 重发的消息全被当作 stale 丢掉——表现是
                        // 「重连之后她突然不理我了」，而两端日志都干净。
                        protocolLink.onReconnected()
                        // 协商结果随 onReconnected 一起作废了：在新一轮 hello_ack 到之前，
                        // 「能不能发图」是不知道的，先把入口收回去，别让用户对着一个
                        // 按下去什么都不会发生的「+」等。
                        _imageSupported.value = false
                        bargeGate.reset()
                        // 时间线属于旧连接上的那几段音频：全部作废，墓碑保留
                        // （`speech_id` 不复用，留着无害，还能挡住迟到的 begin）。
                        speech.onReconnected()
                        // gaps 是 lifetime：baseline 到当前值，避免把历史缺口重放进 streak。
                        cueGaps.baseline(protocolLink.counters().gaps)
                        emotionEmitSeq = 0
                        sendHello(newRoom, protocolLink)
                        scheduleHelloRetry(newRoom, protocolLink)
                    }
                    is RoomEvent.Disconnected -> {
                        _connection.value = TransportConnection.DISCONNECTED
                        degrade.onConnection(DegradeController.Conn.DISCONNECTED)
                        _lastConnectionError.value = safeError(event.error?.message).ifBlank { "连接已断开" }
                        logWarn("attempt=$activeAttemptId event=disconnected reason=${event.reason} error=${safeError(event.error?.message)}")
                        bus.emit(CompanionEvent.Error("连接已断开"))
                    }
                    else -> Unit
                }
            }
        }

        // Participant events are a separate stream from Room.events.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            newRoom.localParticipant.events.events.collect { event ->
                when (event) {
                    is io.livekit.android.events.ParticipantEvent.LocalTrackPublished -> {
                        logInfo("attempt=$activeAttemptId event=local_track_published kind=${event.publication.kind} sid=${event.publication.sid}")
                        if (event.publication.kind == Track.Kind.AUDIO) {
                            val published = event.publication.track
                            if (published is AudioTrack) attachSelfSink(published) else attachSelfSink(newRoom)
                        }
                    }
                    is io.livekit.android.events.ParticipantEvent.LocalTrackUnpublished -> {
                        logInfo("attempt=$activeAttemptId event=local_track_unpublished kind=${event.publication.kind}")
                        if (event.publication.kind == Track.Kind.AUDIO) detachSelfSink()
                    }
                    else -> Unit
                }
            }
        }

        logInfo("attempt=$activeAttemptId event=room_connect_begin")
        try {
            withTimeout(CONNECT_TIMEOUT_MS) {
                newRoom.connect(session.livekitUrl, session.token)
                // 2.29.0 的 connect() 只等待 join job；媒体连接完成由 onEngineConnected 异步通知。
                // collector 已在握手前订阅，用 StateFlow 等待最终事件，不能把 CONNECTING 当错误。
                val outcome = _connection.first {
                    it == TransportConnection.CONNECTED || it == TransportConnection.DISCONNECTED
                }
                check(outcome == TransportConnection.CONNECTED) {
                    _lastConnectionError.value ?: "连接已断开，请重试"
                }
            }
            logInfo("attempt=$activeAttemptId event=room_connect_success elapsedMs=${SystemClock.elapsedRealtime() - connectStartedAtMs}")
        } catch (e: TimeoutCancellationException) {
            _lastConnectionError.value = "连接超时，请重试"
            _connection.value = TransportConnection.DISCONNECTED
            newRoom.disconnect()
            logError("attempt=$activeAttemptId event=room_connect_timeout")
            throw IllegalStateException("连接超时，请重试", e)
        } catch (e: CancellationException) {
            _connection.value = TransportConnection.DISCONNECTED
            newRoom.disconnect()
            throw e
        } catch (e: Exception) {
            _lastConnectionError.value = safeError(e.message)
            _connection.value = TransportConnection.DISCONNECTED
            newRoom.disconnect()
            logError("attempt=$activeAttemptId event=room_connect_failure type=${e.javaClass.simpleName}")
            throw IllegalStateException("连接失败，请重试", e)
        }
        // 建连前用户就点进了通话（建连通常要一两秒）：那时麦克风意图先记着，现在补偿执行。
        micIntent?.let { applyMic(newRoom, it) }
        attachSelfSink(newRoom)

        newRoom.registerTextStreamHandler(TEXT_OUT_TOPIC) { reader, identity ->
            scope.launch {
                if (identity == newRoom.localParticipant.identity) return@launch
                reader.flow.collect { chunk -> bus.emit(CompanionEvent.TextDelta(chunk)) }
                bus.emit(CompanionEvent.TurnDone)
            }
        }

        // 握手必须由客户端发起（§7.3）。首发在 Connected 事件里（赶在服务端 3 秒
        // 窗口之前）；这里是第二枪：§7.3 明确「服务端必须容忍客户端重发 hello」，
        // 序号状态机也只认更大 seq 的新 id——补发一条不会打挂任何一边。
        sendHello(newRoom, protocolLink)
    }

    /**
     * 没收到 hello_ack 就一直按节拍补发，直到 ack 到达或预算用完（§6 A 类：
     * 「发送方重试直到 ack 或超时」）。
     *
     * 为什么必须重试而不是补一枪：agent 进程冷启动（import、语音栈装配）实测
     * 15–20 秒，而它的 3 秒 hello 窗口是**装配完成之后**才开始等的——客户端连接
     * 时房间里根本还没有接收者。首连的所有 hello 都发给了一个还不存在的耳朵，
     * 这不是竞态窗口能调掉的，是到达性问题，只有重试能解。
     *
     * 预算 60 秒：agent 最慢的冷启动也在这个量级内完成；用完仍无 ack 就接受
     * v1 回落（功能不退化），不给每个失败会话留一个无限循环。
     */
    private fun scheduleHelloRetry(current: Room, protocolLink: CompanionProtocolLink) {
        scope.launch {
            var sent = 0
            while (isActive && sent < HELLO_RETRY_MAX) {
                delay(HELLO_RETRY_INTERVAL_MS)
                // 房间可能已经换了一次（用户挂断又拨通）：旧 attempt 的定时器不许
                // 往新房间补发。
                if (room !== current) return@launch
                if (protocolLink.negotiator.ack != null) return@launch
                sent++
                logInfo("attempt=$activeAttemptId event=hello_retry n=$sent")
                sendHello(current, protocolLink)
            }
            // 走到这里说明预算用完还没谈成：这条连接按 v1 走，是设计内的降级。
            if (room === current && protocolLink.negotiator.ack == null) {
                logWarn("attempt=$activeAttemptId event=hello_gave_up after=$sent")
            }
        }
    }

    /**
     * 发 `control.hello`。
     *
     * 发不出去只记日志：协商失败的后果是「这条连接退回 v1」，而 v1 的旧 topic 仍然
     * 可用，所以不该把整次建连判为失败。
     */
    private fun sendHello(current: Room, protocolLink: CompanionProtocolLink) {
        val bytes = protocolLink.helloBytes() ?: return
        scope.launch {
            sendGuard {
                current.localParticipant
                    .publishData(bytes, DataPublishReliability.RELIABLE, ProtocolCatalog.TOPIC_CTL)
            }.onFailure {
                logWarn("attempt=$activeAttemptId event=hello_publish_failed type=${it.javaClass.simpleName}")
            }
                .onSuccess {
                    logInfo("attempt=$activeAttemptId event=hello_sent ep=${protocolLink.epoch}")
                }
        }
    }

    /**
     * 开/关本地麦克风。
     *
     * SDK 没有暴露「当前是否已发布」，这里直接按调用方意图执行；重复开/关是幂等的。
     * 失败不静默：麦克风被占用时用户会只看到按钮没反应，必须给一条可读的错误。
     */
    override fun setMicEnabled(enabled: Boolean) {
        micIntent = enabled
        val current = room ?: return
        if (current.state == Room.State.CONNECTED) applyMic(current, enabled)
    }

    private fun applyMic(current: Room, enabled: Boolean) {
        scope.launch {
            micMutex.withLock {
                // 同一次建连可能先后收到数次意图，只执行最新值。
                if (room !== current || micIntent != enabled) return@withLock
                runCatching {
                    check(current.localParticipant.setMicrophoneEnabled(enabled)) {
                        "麦克风采集或发布失败，请检查录音权限和麦克风占用"
                    }
                    if (enabled) {
                        attachSelfSink(current)
                    } else {
                        _selfLevel.value = 0f
                        _selfBands.value = FloatArray(0)
                    }
                    logInfo("attempt=$activeAttemptId event=mic_applied enabled=$enabled")
                }.onFailure {
                    logError("attempt=$activeAttemptId event=mic_apply_failed type=${it.javaClass.simpleName}")
                    bus.emit(CompanionEvent.Error("麦克风未开启，请检查录音权限和麦克风占用后重试"))
                }
            }
        }
    }

    override fun sendText(text: String) {
        val current = room ?: return
        scope.launch {
            // sendGuard：sendText 在 data channel 未建立时抛 RoomException 而非返回
            // failure，裸链 .onFailure 会漏（真机通话页曾因此整 app 重启，见 RoomSendGuard）。
            sendGuard {
                current.localParticipant.sendText(text, StreamTextOptions(topic = TEXT_IN_TOPIC))
            }.onFailure { bus.emit(CompanionEvent.Error("发送失败：${it.message}")) }
        }
    }

    /**
     * 每 30 秒把 §14.3 的计数与降级档位回报一次（`telemetry.health`，C 类、DROPPABLE）。
     *
     * 现在报的是收侧桶与 gap 数：它们原本只活在内存里，进程一退就没了，线上排查
     * 「是她没发还是我没收」时无从下手。降级阶梯要到 P4 才实现，在那之前固定报 L0
     * ——这是**当前事实**，不是占位：端侧确实还没有会降档的逻辑。
     *
     * 发不出去不重试、不提示：C 类消息的价值窗口极短，补发一条过期的健康快照
     * 不如等下一个 30 秒。
     */
    private fun startHealthLoop(current: Room) {
        // `Connected` 在部分 SDK 路径上会重复抛出。放第二个循环会让同一份快照被发两遍、
        // `cmp.tel` 的序号走得比 30 秒一格快一倍——对端看不出错，只是计数的增量对不上。
        if (healthLoopRoom === current) return
        healthLoopRoom = current
        scope.launch {
            while (true) {
                delay(HEALTH_INTERVAL_MS)
                // 房间换了（重新 `connect()` 建了一条新连接）：这个循环已经属于旧房间，
                // 停掉。留着继续跑只会每 30 秒空转一次，而且 `link` 已经是新连接的了。
                if (room !== current) break
                val protocolLink = link ?: continue
                // 还没谈成 v2：不发，但继续等——握手可能刚好晚于连上几秒。
                if (!protocolLink.isV2) continue
                publishHealth(current, protocolLink)
            }
        }
    }

    private fun publishHealth(current: Room, protocolLink: CompanionProtocolLink) {
        val counters = protocolLink.counters()
        val now = SystemClock.elapsedRealtime()
        val level = degrade.level(now)
        val args = JSONObject()
            .put("received", counters.received)
            .put("applied", counters.applied)
            .put("dropped_dup", counters.droppedDup)
            .put("dropped_stale", counters.droppedStale)
            .put("dropped_expired", counters.droppedExpired)
            .put("dropped_unknown", counters.droppedUnknown)
            .put("dropped_unauthorized", counters.droppedUnauthorized)
            .put("dropped_bad", counters.droppedBad)
            .put("gap_count", counters.gaps)
            .put("cues_applied", speech.cuesApplied)
            .put("cues_expired", speech.cuesExpired)
            .put("degrade_level", "L$level")
        val bytes = protocolLink.outgoing(
            topic = ProtocolCatalog.TOPIC_TEL,
            eventType = "telemetry.health",
            args = args,
        ) ?: return
        scope.launch {
            runCatching {
                current.localParticipant
                    .publishData(bytes, DataPublishReliability.LOSSY, ProtocolCatalog.TOPIC_TEL)
            }.onFailure {
                logWarn("attempt=$activeAttemptId event=health_publish_failed type=${it.javaClass.simpleName}")
            }
        }
    }

    /**
     * 点她一下：走 v2 的 `cmp.in` `control.input`（§15 迁移表）。
     *
     * 旧路径是 `companion.touch` 文本流，服务端从来没有注册过这个话题的 handler——
     * 每一下触摸都发进黑洞。数据包比文本流省，也省掉建流的往返。
     *
     * v1 连接（未握手）发不出去：`outgoing` 会在协商未完成时返回 null。此时退回旧的
     * 文本流，至少老服务端还能收到。
     *
     * 失败不提示——触摸是低价值的尝试性输入，弹一条「触摸失败」只会打断沉浸感。
     */
    override fun sendTouch(region: String) {
        val current = room ?: return
        val protocolLink = link
        val bytes = if (protocolLink == null) {
            null
        } else {
            protocolLink.outgoing(
                topic = ProtocolCatalog.TOPIC_IN,
                eventType = "control.input",
                args = JSONObject().put("kind", "touch").put("region", region),
            )
        }
        if (bytes == null) {
            // 未协商出 v2：老服务端只认文本流。
            val payload = JSONObject().put("v", 1).put("type", "touch").put("region", region).toString()
            scope.launch {
                // sendGuard 不是摆设：channel 没建立时 sendText 直接抛，
                // .onFailure 接不到——真机上每点一次形象 app 就自杀重启一次。
                sendGuard {
                    current.localParticipant.sendText(payload, StreamTextOptions(topic = TOPIC_TOUCH))
                }.onFailure { /* 见上：触摸上报失败保持静默 */ }
            }
            return
        }
        scope.launch {
            runCatching {
                current.localParticipant
                    .publishData(bytes, DataPublishReliability.RELIABLE, ProtocolCatalog.TOPIC_IN)
            }.onFailure {
                logWarn("attempt=$activeAttemptId event=touch_publish_failed type=${it.javaClass.simpleName}")
            }
        }
    }

    /**
     * 一组刚上传好的图片进房间：`cmp.in` 的 `control.input`，`kind="attachments"`。
     *
     * 与 [sendTouch] 同一个事件类型、不同 `kind`——服务端本来就按 `args.kind` 分派，
     * 没必要为图片再开一个 `t`（协议目录两端各写一份，多一项就多一处可能不同步的地方）。
     *
     * **没有旧版回退通道**：v1 的服务端压根不认 `upload_ids`，退化成文本流只会发出去一串
     * 谁也不读的 id。没握手到 v2、或 `media.image` 没谈成，就记一条 warn 然后什么都不做
     * ——界面上的「+」在这种连接下本来就不出现，走到这里说明状态在通话中间变了，
     * 让它留在日志里可查，而不是假装成功。
     *
     * 这里**直接 `publishData` 而不是 `scope.launch`**：返回即「已落进房间」，调用方才能
     * 等它返回再发文本（见 [CompanionTransport.sendAttachments]）。`scope.launch` 的写法
     * 与 [sendText] 各自一条协程，谁先到达由调度器决定，文本抢跑一次，图就落到下一条消息上。
     */
    override suspend fun sendAttachments(uploadIds: List<String>, clientMsgId: String) {
        if (uploadIds.isEmpty()) return
        val current = room ?: return
        val protocolLink = link
        val negotiatedImage = protocolLink?.negotiator?.canReceive(ProtocolHandshake.CAP_MEDIA_IMAGE) == true
        if (protocolLink == null || !protocolLink.isV2 || !negotiatedImage) {
            logWarn(
                "attempt=$activeAttemptId event=attachments_skipped ids=${uploadIds.size} " +
                    "v2=${protocolLink?.isV2} negotiated=$negotiatedImage",
            )
            return
        }
        // JSONArray 手动装：Android 的 org.json 没有 `JSONArray(Collection)` 这个构造器
        // （那是桌面版 org.json 的），`putJSONArray` 同样不存在。逐个 put 出来的字节
        // 与它们完全一样。
        val idsJson = JSONArray()
        uploadIds.forEach { idsJson.put(it) }
        val args = JSONObject()
            .put("kind", "attachments")
            .put("upload_ids", idsJson)
            .put("client_msg_id", clientMsgId)
        val bytes = protocolLink.outgoing(
            topic = ProtocolCatalog.TOPIC_IN,
            eventType = "control.input",
            args = args,
        )
        if (bytes == null) {
            logWarn("attempt=$activeAttemptId event=attachments_encode_failed ids=${uploadIds.size}")
            return
        }
        runCatching {
            current.localParticipant
                .publishData(bytes, DataPublishReliability.RELIABLE, ProtocolCatalog.TOPIC_IN)
        }.onSuccess {
            logInfo("attempt=$activeAttemptId event=attachments_sent ids=${uploadIds.size} clientMsgId=$clientMsgId")
        }.onFailure {
            logWarn("attempt=$activeAttemptId event=attachments_publish_failed type=${it.javaClass.simpleName}")
        }
    }

    /**
     * §10.1：用户开口打断。先本地作废时间线，再发 ``control.barge_in``。
     *
     * 不等服务端确认——音频截断由服务端 ``interrupt`` 负责，端侧只需同步视觉。
     * 未协商出 v2 时只做本地 cancel（老服务端没有这条协议路径）。
     * 失败静默：打断是低延迟路径，弹错误只会更吵。
     */
    private fun sendBargeIn(speechId: String, playedMs: Long) {
        // 本地立刻作废（§10.1）：哪怕发包失败，视觉也要停。
        speech.onCancel(JSONObject().put("speech_id", speechId))
        speechWake.trySend(Unit)

        val current = room ?: return
        val protocolLink = link ?: return
        val args = JSONObject()
            .put("speech_id", speechId)
            .put("played_ms", playedMs)
            .put("reason", "vad")
        val bytes = protocolLink.outgoing(
            topic = ProtocolCatalog.TOPIC_CTL,
            eventType = "control.barge_in",
            args = args,
        ) ?: return
        scope.launch {
            runCatching {
                current.localParticipant
                    .publishData(bytes, DataPublishReliability.RELIABLE, ProtocolCatalog.TOPIC_CTL)
            }.onFailure {
                logWarn("attempt=$activeAttemptId event=barge_publish_failed type=${it.javaClass.simpleName}")
            }.onSuccess {
                logInfo("attempt=$activeAttemptId event=barge_in id=$speechId playedMs=$playedMs")
            }
        }
    }

    override suspend fun disconnect() {
        debugDegradeReceiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        debugDegradeReceiver = null
        detachMouthSink()
        detachSelfSink()
        _mouthLevel.value = 0f
        bargeGate.reset()
        speech.onReconnected()      // 断开了：时间线与队列都没有意义了
        // 房间没了，协商结果也就没了：不收回，界面会拿着上一条连接的「能发图」
        // 继续显示「+」，下一次发送只会失败。
        _imageSupported.value = false
        room?.disconnect()
        room = null
        scope.cancel()
    }

    /**
     * 把她那一路音频接进嘴型。
     *
     * 只认远端音频轨：本地麦克风轨是用户自己的声音，拿它驱动她的嘴会把「你说话时她也在动嘴」
     * 变成常态。`onData` 给的是 16-bit PCM，按 RMS 换算成 0..1：
     * 人声通常在 0.02~0.2 之间，所以乘一个增益再截断——直接归一化会一直贴在闭嘴阈值以下。
     */
    private fun attachMouthSink(track: Track) {
        val audio = track as? AudioTrack
        if (audio == null) {
            android.util.Log.w(TAG, "attachMouthSink: 订阅到的不是音频轨 kind=${track.kind}")
            return
        }
        detachMouthSink()
        android.util.Log.i(TAG, "attachMouthSink: 已挂上远端音频轨 sid=${track.sid}")

        // Reduce diagnostics to one first-frame event and a ten-second aggregate.
        var callbacks = 0L
        var windowStartedAt = SystemClock.elapsedRealtime()
        var rawSum = 0.0
        var levelMax = 0f

        val sink = object : AudioTrackSink {
            override fun onData(
                data: ByteBuffer,
                bitsPerSample: Int,
                sampleRate: Int,
                numberOfChannels: Int,
                frames: Int,
                absoluteCaptureTimestampMs: Long,
            ) {
                if (bitsPerSample != 16) {
                    if (callbacks == 0L) {
                        android.util.Log.w(TAG, "onData: bitsPerSample=$bitsPerSample 非 16-bit，已跳过")
                    }
                    return
                }
                // 只取前若干帧算 RMS：整块算没必要，够判断「这一刻响不响」即可。
                val limit = minOf(frames, RMS_WINDOW_FRAMES) * numberOfChannels
                if (limit <= 0) return
                val buf = data.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                var sum = 0.0
                var n = 0
                for (i in 0 until limit) {
                    if (buf.remaining() < 2) break
                    val sample = buf.short.toDouble()
                    sum += sample * sample
                    n++
                }
                if (n == 0) return
                val rms = sqrt(sum / n) / Short.MAX_VALUE
                val level = (rms / FULL_SCALE_RMS).coerceIn(0.0, 1.0).toFloat() * LEVEL_CEILING
                _mouthLevel.value = level
                val nowMono = SystemClock.elapsedRealtime()
                // §12.3：PCM 存活传感器。不分配；与口型同路径。
                degrade.onPcm(nowMono)
                if (_remotePcm.subscriptionCount.value > 0) {
                    com.openapi.ks.myapp.dh.PcmConvert.toMono16k(data, bitsPerSample, sampleRate, numberOfChannels, frames)
                        ?.let { _remotePcm.tryEmit(it) }
                }

                // §9.4：远端首帧就是"这句话的 t0"。没有这一步，所有 `before` /
                // `after` 锚点都解不出来，表现 cue 会挂在队列里直到 2 秒后过期。
                // 只在有待标定的时间线时才做，省掉稳态下每帧一次的无用计算。
                if (speech.timebasePendingId() != null) {
                    speech.onAudioFrame(
                        now = nowMono,
                        captureTs = absoluteCaptureTimestampMs,
                    )
                    speechWake.trySend(Unit)
                }

                callbacks++
                rawSum += rms
                levelMax = maxOf(levelMax, level)
                if (callbacks == 1L && !firstRemotePcmLogged) {
                    firstRemotePcmLogged = true
                    logInfo("attempt=$activeAttemptId event=remote_pcm_first frames=$frames channels=$numberOfChannels rate=$sampleRate rms=${"%.4f".format(rms)}")
                }
                if (firstAudio.onFrame(rms)) {
                    logInfo("attempt=$activeAttemptId event=remote_pcm_first_audio rms=${"%.4f".format(rms)}")
                }
                val now = SystemClock.elapsedRealtime()
                if (now - windowStartedAt >= PCM_LOG_INTERVAL_MS) {
                    logInfo("attempt=$activeAttemptId event=remote_pcm_summary samples=$callbacks meanRms=${"%.4f".format(rawSum / callbacks)} maxLevel=${"%.3f".format(levelMax)} windowMs=${now - windowStartedAt}")
                    callbacks = 0L
                    rawSum = 0.0
                    levelMax = 0f
                    windowStartedAt = now
                }
            }
        }
        val result = runCatching { audio.addSink(sink) }
        result.onFailure { logError("attempt=$activeAttemptId event=remote_sink_attach_failed sid=${audio.sid} type=${it.javaClass.simpleName}") }
        if (result.isSuccess) {
            mouthSink = sink
            mouthTrack = audio
            remoteAudio = audio as? io.livekit.android.room.track.RemoteAudioTrack
            remoteAudio?.setVolume(if (playbackAudible) 1.0 else 0.0)
            logInfo("attempt=$activeAttemptId event=remote_sink_attached sid=${audio.sid}")
        }
    }

    /** 摘掉音频 sink。SDK 不会替我们摘：留在 track 上就是一个永不释放的回调。 */
    private fun detachMouthSink() {
        val sink = mouthSink ?: return
        runCatching { mouthTrack?.removeSink(sink) }
        mouthSink = null
        mouthTrack = null
        remoteAudio = null
        _mouthLevel.value = 0f
    }

    /**
     * 把她那一路播放静音 / 恢复。
     *
     * 退出通话页**不断开房间**（通话只是形态，不是另一条连接），但用户的心智是
     * 「我挂断了，她不该还在说话」。以前这里什么也没做：退出去之后她的 TTS 还在放，
     * 用户只能在文字页里找到那个「语音回答」开关才能关掉——两个开关表达同一件事，
     * 而且都不直观。
     *
     * 用 `setVolume(0)` 而不是取消订阅：音频轨还在，恢复是一瞬间的事，不用重新协商。
     */
    override fun setPlaybackAudible(audible: Boolean) {
        playbackAudible = audible
        runCatching { remoteAudio?.setVolume(if (audible) 1.0 else 0.0) }
        if (!audible) _mouthLevel.value = 0f
    }

    /**
     * 用户自己的音量：本地麦克风采集轨的 PCM。
     *
     * 与 [attachMouthSink] 共用同一套 RMS 计算，但写进 [selfLevel]。
     * SDK 在麦克风发布之后才创建采集轨，所以这里从 `localParticipant.audioTrackPublications`
     * 里找已经发布的那一条，而不是自己造轨。
     */
    /** 建连后/重连后的补偿路径：从 participant 的 publication map 里找本地音频轨。 */
    private fun attachSelfSink(current: Room) {
        val publications = current.localParticipant.audioTrackPublications
        android.util.Log.i(TAG, "attachSelfSink: 当前本地音频 publication 数=${publications.size}")
        val publication = publications
            .firstOrNull { it.second != null }
            ?: run {
                // 这不是错误：麦克风可能还在发布中，LocalTrackPublished 事件到达时会走轨直挂路径。
                android.util.Log.i(TAG, "attachSelfSink: 本地还没有已发布的音频轨，等 LocalTrackPublished 事件")
                return
            }
        val audio = publication.second as? AudioTrack
            ?: run {
                android.util.Log.w(TAG, "attachSelfSink: publication 不是 AudioTrack，实际=${publication.second?.javaClass?.name}")
                return
            }
        attachSelfSink(audio)
    }

    /**
     * 把 PCM sink 挂到给定的本地音频轨上。LocalTrackPublished 事件直接带着轨对象走这里，
     * 绕开「事件已到、participant map 还没更新」的时序窗口。先摘后挂，重复调用幂等。
     */
    private fun attachSelfSink(audio: AudioTrack) {
        if (selfTrack === audio && selfSink != null) return
        detachSelfSink()
        var callbacks = 0L
        var windowStartedAt = SystemClock.elapsedRealtime()
        var rawSum = 0.0
        var levelMax = 0f
        // 每条采集轨一个分析器：它复用缓冲区，只在这一个音频回调线程里用。
        val spectrum = VoiceSpectrum()
        val silentBands = FloatArray(VoiceSpectrum.BANDS)
        val sink = object : AudioTrackSink {
            override fun onData(
                data: ByteBuffer,
                bitsPerSample: Int,
                sampleRate: Int,
                numberOfChannels: Int,
                frames: Int,
                absoluteCaptureTimestampMs: Long,
            ) {
                val rms = rmsOf(data, bitsPerSample, frames, numberOfChannels) ?: return
                // 噪声门以下不做频谱：底噪的频谱形状没有意义，画出来就是「没说话也在动」。
                _selfBands.value = if (rms <= SELF_NOISE_GATE) {
                    silentBands
                } else {
                    spectrum.analyze(data, frames, numberOfChannels, sampleRate)
                }
                // 本地采集与远端是两套量纲：近端麦克风的归一化 RMS 普遍只有 0.01~0.6，
                // 直接除以 0.6 会让波形几乎看不见。先过一道噪声门（门限是实测底噪的 ~10 倍），
                // 再按本地参考点 0.35 做平方根映射——平方根让轻声也有可见起伏，大声又不会轻易顶死。
                val gated = (rms - SELF_NOISE_GATE).coerceAtLeast(0.0)
                val level = sqrt(gated / SELF_FULL_SCALE_RMS).coerceIn(0.0, 1.0).toFloat()
                _selfLevel.value = level

                // §10.1：她在说、你开始出声 → 本地 cancel + control.barge_in。
                // 用原始 rms 过噪声门（与波形用的 gated level 分开）：门限是实测底噪的
                // ~10 倍，比映射后的 level 更稳，也避免轻声误触。
                val nowMono = SystemClock.elapsedRealtime()
                val bargeId = bargeGate.onSelfLevel(
                    selfLevel = rms.toFloat(),
                    noiseGate = SELF_NOISE_GATE.toFloat(),
                    activeSpeechId = speech.activeSpeechId(),
                    now = nowMono,
                )
                if (bargeId != null) {
                    sendBargeIn(bargeId, speech.playedMs(bargeId, nowMono))
                }

                callbacks++
                rawSum += rms
                levelMax = maxOf(levelMax, level)
                if (callbacks == 1L && !firstLocalPcmLogged) {
                    firstLocalPcmLogged = true
                    logInfo("attempt=$activeAttemptId event=local_pcm_first frames=$frames channels=$numberOfChannels rate=$sampleRate rms=${"%.4f".format(rms)}")
                }
                val now = SystemClock.elapsedRealtime()
                if (now - windowStartedAt >= PCM_LOG_INTERVAL_MS) {
                    logInfo("attempt=$activeAttemptId event=local_pcm_summary samples=$callbacks meanRms=${"%.4f".format(rawSum / callbacks)} maxLevel=${"%.3f".format(levelMax)} windowMs=${now - windowStartedAt}")
                    callbacks = 0L
                    rawSum = 0.0
                    levelMax = 0f
                    windowStartedAt = now
                }
            }
        }
        // LocalAudioTrack.addSink 走的是 AudioRecordSamplesDispatcher（SDK 源码确认），
        // 默认 JavaAudioDeviceModule 下麦克风一启动就有 PCM；失败要可见，不能静默。
        val result = runCatching { audio.addSink(sink) }
        result.onFailure { logError("attempt=$activeAttemptId event=local_sink_attach_failed sid=${audio.sid} type=${it.javaClass.simpleName}") }
        if (result.isSuccess) {
            selfSink = sink
            selfTrack = audio
            logInfo("attempt=$activeAttemptId event=local_sink_attached sid=${audio.sid}")
        }
    }

    private fun detachSelfSink() {
        val sink = selfSink ?: return
        runCatching { selfTrack?.removeSink(sink) }
        selfSink = null
        selfTrack = null
        _selfLevel.value = 0f
        _selfBands.value = FloatArray(0)
    }

    /**
     * 从 16-bit PCM 里算 RMS（0..1）。只取前 [RMS_WINDOW_FRAMES] 帧：
     * 够判断「这一刻响不响」，不必整块扫。非 16-bit 或空数据返回 null。
     */
    private fun rmsOf(
        data: ByteBuffer,
        bitsPerSample: Int,
        frames: Int,
        numberOfChannels: Int,
    ): Double? {
        if (bitsPerSample != 16) return null
        val limit = minOf(frames, RMS_WINDOW_FRAMES) * numberOfChannels
        if (limit <= 0) return null
        val buf = data.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0.0
        var n = 0
        for (i in 0 until limit) {
            if (buf.remaining() < 2) break
            val sample = buf.short.toDouble()
            sum += sample * sample
            n++
        }
        if (n == 0) return null
        return sqrt(sum / n) / Short.MAX_VALUE
    }

    /**
     * 一条数据包进来了：先过协议 v2 的校验，再过旧的 v1 topic 分派。
     *
     * **两条路互不影响**：`cmp.*` 上的包只走协议栈；旧的 `companion.*` 包完全不知道
     * 协议存在，照旧分派。这样 v1 客户端的能力一条不少（P0 的验收条件），而 v2 的
     * 校验也不会误伤它们。
     *
     * `publisher` 是 SDK 交出来的发布者。以前这里把它丢掉了，于是「谁发来的」根本
     * 无从判断——房间里任何参与者都能伪造一条 `control.cancel` 把我这句话掐断。
     */
    private suspend fun handleData(
        topic: String?,
        data: ByteArray,
        publisher: Participant?,
    ) {
        val protocolLink = link
        // topic 是判断走哪条路的唯一依据：v2 的四个 topic 名字不会与旧的对上。
        if (topic != null && topic in ProtocolCatalog.TOPICS && protocolLink != null) {
            val publisherIdentity = publisher?.identity?.value
            val accepted = protocolLink.onIncoming(
                topic = topic,
                data = data,
                publisher = publisherIdentity,
                publisherIsAgent = publisher?.kind == Participant.Kind.AGENT,
            ) ?: return
            val now = SystemClock.elapsedRealtime()
            degrade.onDataAccepted(now)
            // cmp.cue 的 gap streak：跳号记连续缺口；只有无 gap 的干净到达才清零。
            // （gap 是在 ACCEPT 时发现的——同包既记 gap 又清零会让 streak 永远到不了 3。）
            if (topic == ProtocolCatalog.TOPIC_CUE) {
                val newGaps = cueGaps.observe(protocolLink.counters().gaps)
                if (newGaps > 0) {
                    repeat(newGaps) { degrade.onCueGap() }
                }
                degrade.onCueAccepted(now, clearGapStreak = newGaps == 0)
            } else {
                // 非 cue 的 v2 包也推进 baseline，避免下次 cue 把中间的非 cue gap 一次灌进 streak。
                cueGaps.observe(protocolLink.counters().gaps)
            }
            handleProtocol(accepted.envelope)
            return
        }

        val raw = data.toString(Charsets.UTF_8)
        when (topic) {
            TOPIC_AVATAR -> bus.emit(CompanionEvent.AvatarJson(raw))
            TOPIC_PROACTIVE -> {
                val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
                bus.emit(
                    CompanionEvent.Proactive(
                        text = json.optString("text"),
                        kind = proactiveKindOf(json.optString("kind")),
                    ),
                )
            }
            TOPIC_ACTION -> {
                val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
                bus.emit(
                    CompanionEvent.NeedsConfirmation(
                        confirmationId = json.optString("confirmation_id"),
                        prompt = json.optString("prompt"),
                    ),
                )
            }
        }
    }

    /**
     * 协议 v2 的事件分派。
     *
     * `hello_ack` 决定后续能不能收 `cmp.*`；`presentation.*` 是表现面（§11.1）。
     * 表现指令在入口处翻译成 v1 的事件串，界面层与形象层不用知道协议升过版——
     * 这也是 P0.5 冒烟闭环的落点：一条 `cmp.cue` 能一路走到脸。
     *
     * P1 起 `presentation.*` 不再当场执行，而是**连同信封上的 `at` 一起进调度队列**：
     * "现在做"只是四种 `at` 形态里的缺省一种，无条件立刻执行会让 `before` / `after`
     * 永远落空。表现 cue 多走一次判定，代价是一次队列扫描。
     */
    private suspend fun handleProtocol(envelope: ProtocolCodec.Envelope) {
        val eventType = envelope.t
        val args = envelope.args
        when (eventType) {
            "control.hello_ack" -> {
                link?.onAck(args)
                // 协商结果一次读到底：`link` 是 @Volatile，房间事件与发送协程都会碰它，
                // 同一个分支里读三次可能读到三条不同的连接上。
                val negotiator = link?.negotiator
                val ack = negotiator?.ack
                logInfo(
                    "attempt=$activeAttemptId event=hello_ack sid=${ack?.sid} compat=${ack?.compat} caps=${ack?.caps?.size ?: 0}",
                )
                if (ack == null) {
                    logWarn("attempt=$activeAttemptId event=hello_ack_unusable")
                }
                // 协商结果决定输入区的「+」出不出现在用户面前，所以要打日志：线上判断
                // 「为什么这人的图片入口不见了」只有这一条线索。
                val image = negotiator?.canReceive(ProtocolHandshake.CAP_MEDIA_IMAGE) == true
                if (image != _imageSupported.value) {
                    logInfo("attempt=$activeAttemptId event=media_image_supported value=$image")
                }
                _imageSupported.value = image
            }
            "presentation.emotion" -> {
                val emotion = PresentationCue.parseEmotion(args) ?: return
                if (!emotion.known) {
                    // 未知情绪映射成 neutral 并计数（§11.1）：静默抛掉会让「她表情卡住」
                    // 变成一个查不出来的问题。
                    logWarn("attempt=$activeAttemptId event=emotion_unknown name=${args.optString("name")}")
                }
                scheduleCue(eventType, envelope, at = envelope.at) {
                    PresentationCue.emotionEventJson(emotion.name)
                }
            }
            "presentation.state" -> {
                val state = PresentationCue.parseState(args) ?: return
                scheduleCue(eventType, envelope, at = envelope.at) {
                    PresentationCue.stateEventJson(state)
                }
            }
            "presentation.gesture" -> {
                val id = PresentationCue.parseGesture(args) ?: return
                scheduleCue(eventType, envelope, at = envelope.at) {
                    PresentationCue.gestureEventJson(id)
                }
            }
            "presentation.gaze" -> {
                val gaze = PresentationCue.parseGaze(args) ?: return
                scheduleCue(eventType, envelope, at = envelope.at) {
                    PresentationCue.gazeEventJson(gaze.name, gaze.x, gaze.y)
                }
            }
            // ---- 语音时间线（§9）----
            "speech.seg" -> {
                // 句级声明（§9.3）：先于引用它的 cue 与 TTS 到达。
                val accepted = speech.onSeg(args)
                if (!accepted) {
                    logWarn("attempt=$activeAttemptId event=speech_seg_rejected id=${args.optString("speech_id")} idx=${args.optString("idx")}")
                }
                speechWake.trySend(Unit)
            }
            "speech.end" -> {
                speech.onEnd(args)
                logInfo("attempt=$activeAttemptId event=speech_end id=${args.optString("speech_id")}")
                speechWake.trySend(Unit)
            }
            "control.cancel" -> {
                // 打断是 A 类：到达即生效，不排队、不判锚点（§10.2）。
                speech.onCancel(args)
                logInfo(
                    "attempt=$activeAttemptId event=speech_cancel id=${args.optString("speech_id")} reason=${args.optString("reason")}",
                )
                speechWake.trySend(Unit)
            }
            // 其余事件仍然只过收侧状态机（已计数）而没有消费者。
            else -> Unit
        }
    }

    /**
     * 把一条表现 cue 送进调度队列。
     *
     * [build] 在**这里**就执行：它把 args 翻译成 v1 事件串。放到执行时刻做的话，
     * 那段代码就得在两个线程之间共享 `args`，而 `JSONObject` 不是线程安全的。
     */
    private fun scheduleCue(
        eventType: String,
        envelope: ProtocolCodec.Envelope,
        at: JSONObject?,
        build: () -> String,
    ) {
        val queued = speech.onCue(
            eventType = eventType,
            id = envelope.id,
            payload = build(),
            at = at,
            now = SystemClock.elapsedRealtime(),
        )
        if (!queued) {
            logWarn("attempt=$activeAttemptId event=cue_rejected type=$eventType id=${envelope.id}")
        }
        // 进入队列不一定会立刻执行（可能排期），但"排了个更早的"这次唤醒必须发生，
        // 否则调度循环还睡在之前那个更晚的时刻上。
        speechWake.trySend(Unit)
    }

    /**
     * 调度循环：睡到下一次该醒的时刻，醒来把队列扫一遍。
     *
     * 单一循环而不是每条 cue 一个 `delay`：cue 的判定依据（`t0`、段起点校正）会
     * **在执行之前被后来的消息改写**，逐条 `delay` 的写法锁不住这一点——一条已经排好
     * 3200 ms 的 cue，在收到 `start_boundary` 之后应该按新时刻执行，而 `delay` 里做不到。
     */
    private fun startSpeechLoop(current: Room) {
        if (speechLoopRoom === current) return
        speechLoopRoom = current
        scope.launch {
            while (true) {
                val now = SystemClock.elapsedRealtime()
                // 每拍先记一次档位：循环最坏每 5s 醒一回（SPEECH_LOOP_MAX_SLEEP_MS），
                // L2 判定本身要 cue 静默 5s，这个延迟上界够用且不加消息。
                // 进 L2 的上升沿清一次表情：此后 presentation 全被闸掉，
                // 不主动收，卡在脸上的表情会一直挂到恢复。
                val level = degrade.level(now)
                if (DegradeController.shouldClearEmotion(lastDegradeLevel, level)) {
                    logInfo(
                        "attempt=$activeAttemptId event=emotion_cleared from=L$lastDegradeLevel to=L$level",
                    )
                    bus.tryEmit(CompanionEvent.ClearAvatarEmotion)
                }
                lastDegradeLevel = level
                val sweep = speech.sweep(now)
                for (applied in sweep.apply) {
                    degrade.onCueApplied(applied.lateMs)
                    val type = applied.cue.eventType
                    val seq = if (type == "presentation.emotion") emotionEmitSeq++ else 0
                    // 每条单独问一次档：同一次 sweep 里 onCueApplied 会改写 late_ratio，
                    // 后面的 cue 要按新窗口判定（B4 之前的既有语义，不动）。
                    val cueLevel = degrade.level(now)
                    if (!DegradeController.shouldEmit(type, cueLevel, seq)) {
                        logInfo(
                            "attempt=$activeAttemptId event=cue_gated type=$type id=${applied.cue.id} level=L$cueLevel",
                        )
                        continue
                    }
                    // 形象层要主线程：bus 是 SharedFlow，collect 在主线程，所以这里
                    // 只需要非挂起地 emit（tryEmit），真正落地由收侧决定。
                    bus.tryEmit(CompanionEvent.AvatarJson(applied.cue.payload))
                }
                for (dropped in sweep.dropped) {
                    if (dropped.reason == Decision.Reason.EXPIRED) {
                        degrade.onCueExpired()
                    }
                    logInfo(
                        "attempt=$activeAttemptId event=cue_dropped type=${dropped.cue.eventType} id=${dropped.cue.id} reason=${dropped.reason}",
                    )
                }

                val wakeAt = speech.nextWakeAt()
                val waitMs = when {
                    wakeAt == null -> Long.MAX_VALUE
                    else -> (wakeAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                }
                if (waitMs > SPEECH_LOOP_MAX_SLEEP_MS) {
                    // 上限不是为了精度，是为了让循环有机会发现"房间换了"。
                    withTimeoutOrNull(SPEECH_LOOP_MAX_SLEEP_MS) { speechWake.receive() }
                } else {
                    withTimeoutOrNull(waitMs.coerceAtLeast(1)) { speechWake.receive() }
                }
                // 房间换了（重新 connect 建了新连接）：这个循环属于旧房间，退出。
                if (room !== current) break
            }
        }
    }

    private fun proactiveKindOf(kind: String): ProactiveKind = when (kind) {
        "event" -> ProactiveKind.EVENT
        "greeting" -> ProactiveKind.GREETING
        "call" -> ProactiveKind.CALL
        else -> ProactiveKind.REPLY
    }

    private companion object {
        const val TEXT_IN_TOPIC = "lk.chat"
        const val TEXT_OUT_TOPIC = "lk.transcription"
        /** 用户点她一下：`{"v":1,"type":"touch","region":"head"}`。 */
        const val TOPIC_TOUCH = "companion.touch"
        const val TOPIC_AVATAR = "companion.avatar"
        const val TOPIC_PROACTIVE = "companion.proactive"
        const val TOPIC_ACTION = "companion.action"
        const val EVENT_BUFFER = 256

        /** RMS 只算前 480 帧（10ms @48kHz）：够判断「这一刻响不响」，不必整块扫。 */
        const val RMS_WINDOW_FRAMES = 480

        /**
         * 满幅参考点的原始 RMS。**这不是猜的，是实测的**：5043 条真实采样里，
         * 静音底噪 P50=0.0003，有声音那 2% 的采样中位 0.5755、最高 0.586。
         * 所以 0.6 就是「她说得最大声」的位置，除以它比乘一个凭空的增益可靠得多。
         *
         * 之前用 ×4：0.5755×4=2.30 → 一出声就顶到 1.0 卡住，静音时又骤降到 0，
         * 视觉上是抽搐而不是说话。
         */
        const val FULL_SCALE_RMS = 0.6
        /** 引导静音 rms≈0.0025、真声 >0.1（生产实测），首能量帧判据卡在两者之间。 */
        const val REMOTE_FIRST_AUDIO_RMS = 0.02

        /**
         * 电平上限。留 0.9 而不是 1.0：满幅时把嘴型顶死会失去「还在说」的余地，
         * 而且呼吸环贴到最大尺寸会顶出圆形边界。
         */
        const val LEVEL_CEILING = 0.9f

        /**
         * 与 [com.openapi.ks.myapp.avatar.SimpleAvatarRenderer] 的开合阈值保持同一个数。
         * 日志里打出来是为了判断「这个阈值配当前增益是否合适」，两处改了要一起改。
         */
        const val MOUTH_THRESHOLD_LOG = 0.35f

        /**
         * 本地采集的噪声门限（归一化 RMS）。室内底噪大约在 0.0003 量级，
         * 门限定在 0.003——底噪的约 10 倍：环境声不会把波形顶起来，正常说话（0.01 以上）
         * 又远高于门限，不会被吞掉。真机日志确认分布后再调。
         */
        const val SELF_NOISE_GATE = 0.003

        /**
         * 本地采集的满幅参考点（归一化 RMS，门限之外的部分）。
         *
         * 0.12 是早期没真机数据时的猜测，实测说话 RMS 常态就在 0.07~0.12、峰值 0.3~0.6，
         * 除以 0.12 后正常说话全程 sqrt 后顶在 1.0——动态范围被压没，波形看着又快又乱、
         * 和声音对不上。按实测分布把参考点抬到 0.35，平方根映射后的曲线：
         * rms=0.013 → 0.17（轻声可见），0.07 → 0.44（正常说话在中段），
         * 0.3 → 0.92（大声接近满幅），0.6 → 1.0（真峰值才顶死）。
         */
        const val SELF_FULL_SCALE_RMS = 0.35

        const val TAG = "MouthLevel"
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val PCM_LOG_INTERVAL_MS = 10_000L
        /** 没收到 hello_ack 的补发节拍与预算（§6 A 类），见 scheduleHelloRetry。 */
        const val HELLO_RETRY_INTERVAL_MS = 3_000L
        const val HELLO_RETRY_MAX = 20

        /** `telemetry.health` 的上报周期（§8 表格：30 s）。 */
        const val HEALTH_INTERVAL_MS = 30_000L

        /**
         * 调度循环的最长单次睡眠。没有 cue 在等时间时也要定期醒来检查"房间是否换了"，
         * 否则一次空闲就会让旧房间的循环一直挂到 scope 被取消。
         */
        const val SPEECH_LOOP_MAX_SLEEP_MS = 5_000L
    }

    private fun logInfo(message: String) {
        if (isDebugBuild) android.util.Log.i(TAG, message)
    }

    private fun logWarn(message: String) {
        if (isDebugBuild) android.util.Log.w(TAG, message)
    }

    private fun logError(message: String) {
        if (isDebugBuild) android.util.Log.e(TAG, message)
    }

    private val isDebugBuild: Boolean
        get() = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun safeError(message: String?): String =
        message.orEmpty()
            .replace(Regex("(?i)(token|authorization|bearer)\\s*[:=]\\s*[^ ,;]+"), "$1=<redacted>")
            .take(180)
}
