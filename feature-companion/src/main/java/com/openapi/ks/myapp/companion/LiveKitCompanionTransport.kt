package com.openapi.ks.myapp.companion

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import com.openapi.ks.myapp.avatar.ProactiveKind
import com.openapi.ks.myapp.companion.protocol.CompanionProtocolLink
import com.openapi.ks.myapp.companion.protocol.ProtocolCatalog
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
import kotlinx.coroutines.withTimeout
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
    @Volatile private var firstLocalPcmLogged = false

    /** 最近一次麦克风意图。房间还没建好时先存着，连上后补偿执行。 */
    @Volatile private var micIntent: Boolean? = null
    @Volatile private var playbackAudible = true

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

    /** 房间事件到界面状态的唯一来源：重连/重连成功都必须上报，否则通话页只能永远显示「正在接通」。 */
    override val connection: StateFlow<TransportConnection> = _connection.asStateFlow()

    override suspend fun connect(session: SessionResponse) {
        require(session.livekitUrl.isNotBlank() && session.token.isNotBlank()) {
            "会话缺少房间地址或入场凭证"
        }
        activeAttemptId = UUID.randomUUID().toString().take(8)
        connectStartedAtMs = SystemClock.elapsedRealtime()
        firstRemotePcmLogged = false
        firstLocalPcmLogged = false
        logInfo("attempt=$activeAttemptId event=connect_start")
        _connection.value = TransportConnection.CONNECTING
        _lastConnectionError.value = null
        val newRoom = LiveKit.create(appContext)
        room = newRoom
        // 新连接就是新 epoch（§5.1）：沿用上一次的 epoch，对端会把我从 0 开始的 seq
        // 当成回退丢掉。identity 用来判 dst 是不是指我，现在还不知道，等连上再看。
        val protocolLink = CompanionProtocolLink(identity = null)
        link = protocolLink
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
                        logInfo("attempt=$activeAttemptId event=room_connected elapsedMs=${SystemClock.elapsedRealtime() - connectStartedAtMs}")
                        // identity 由 SDK 在入会时分配，建 Room 的时候还拿不到。不补上的话
                        // 带 `dst` 的定向消息全被当成「不是给我的」丢掉，而广播照常——
                        // 表现是「她单聊时说的话我收不到，群发都正常」，很难查。
                        protocolLink.bindIdentity(newRoom.localParticipant.identity?.value)
                    }
                    is RoomEvent.FailedToConnect -> {
                        _connection.value = TransportConnection.DISCONNECTED
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
                        logWarn("attempt=$activeAttemptId event=reconnecting")
                    }
                    is RoomEvent.Reconnected -> {
                        _connection.value = TransportConnection.CONNECTED
                        _lastConnectionError.value = null
                        logInfo("attempt=$activeAttemptId event=reconnected elapsedMs=${SystemClock.elapsedRealtime() - connectStartedAtMs}")
                        attachSelfSink(newRoom)
                        // 重连是新的一条流：清表、换 epoch、重新握手（§5.1 / F4）。
                        // 少了这一步，对端从 0 重发的消息全被当作 stale 丢掉——表现是
                        // 「重连之后她突然不理我了」，而两端日志都干净。
                        protocolLink.onReconnected()
                        sendHello(newRoom, protocolLink)
                    }
                    is RoomEvent.Disconnected -> {
                        _connection.value = TransportConnection.DISCONNECTED
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

        // 握手必须由客户端发起（§7.3）：老客户端从不发 hello，服务端只会等 3 秒然后
        // 按 v1 处理。连上了还不发，这一整条连接就永远拿不到 cmp.*。
        sendHello(newRoom, protocolLink)
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
            current.localParticipant
                .publishData(bytes, DataPublishReliability.RELIABLE, ProtocolCatalog.TOPIC_CTL)
                .onFailure {
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
            current.localParticipant.sendText(text, StreamTextOptions(topic = TEXT_IN_TOPIC))
                .onFailure { bus.emit(CompanionEvent.Error("发送失败：${it.message}")) }
        }
    }

    /**
     * 点她一下：走文本流的独立话题 `companion.touch`，payload 是含 region 的 JSON。
     *
     * 不新开数据通道类型：文本流本来就在用，agent 侧只需要再注册一个话题的 handler。
     * 失败不提示——触摸是低价值的尝试性输入，弹一条「触摸失败」只会打断沉浸感。
     */
    override fun sendTouch(region: String) {
        val current = room ?: return
        val payload = JSONObject().put("v", 1).put("type", "touch").put("region", region).toString()
        scope.launch {
            current.localParticipant.sendText(payload, StreamTextOptions(topic = TOPIC_TOUCH))
                .onFailure { /* 见上：触摸上报失败保持静默 */ }
        }
    }

    override suspend fun disconnect() {
        detachMouthSink()
        detachSelfSink()
        _mouthLevel.value = 0f
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

                callbacks++
                rawSum += rms
                levelMax = maxOf(levelMax, level)
                if (callbacks == 1L && !firstRemotePcmLogged) {
                    firstRemotePcmLogged = true
                    logInfo("attempt=$activeAttemptId event=remote_pcm_first frames=$frames channels=$numberOfChannels rate=$sampleRate rms=${"%.4f".format(rms)}")
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
            handleProtocol(accepted.envelope.t, accepted.envelope.args)
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
     * P0 只接上握手：`hello_ack` 决定后续能不能收 `cmp.*`。表现面（`presentation.*`、
     * `speech.*`）在后续阶段接，现在收到只会计数、不处理——那是**已知的空位**，
     * 不是遗漏。
     */
    private suspend fun handleProtocol(eventType: String, args: JSONObject) {
        when (eventType) {
            "control.hello_ack" -> {
                link?.onAck(args)
                val ack = link?.negotiator?.ack
                logInfo(
                    "attempt=$activeAttemptId event=hello_ack sid=${ack?.sid} compat=${ack?.compat} caps=${ack?.caps?.size ?: 0}",
                )
                if (ack == null) {
                    logWarn("attempt=$activeAttemptId event=hello_ack_unusable")
                }
            }
            // 其余事件在 P0 阶段只走收侧状态机（已计数），没有消费者。
            else -> Unit
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
