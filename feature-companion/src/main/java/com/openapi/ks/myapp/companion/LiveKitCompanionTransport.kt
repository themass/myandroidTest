package com.openapi.ks.myapp.companion

import android.content.Context
import com.openapi.ks.myapp.avatar.ProactiveKind
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.datastream.StreamTextOptions
import io.livekit.android.room.track.AudioTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import livekit.org.webrtc.AudioTrackSink
import org.json.JSONObject
import java.nio.ByteBuffer
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
    private var room: Room? = null

    /** 最近一次麦克风意图。房间还没建好时先存着，连上后补偿执行。 */
    private var micIntent: Boolean? = null

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

    /** 已挂上的远端音频 sink；断开时按这个引用摘掉，避免泄漏。 */
    private var mouthSink: AudioTrackSink? = null
    private var mouthTrack: AudioTrack? = null

    override val events: Flow<CompanionEvent> = bus.asSharedFlow()

    /** 房间事件到界面状态的唯一来源：重连/重连成功都必须上报，否则通话页只能永远显示「正在接通」。 */
    override val connection: StateFlow<TransportConnection> = _connection.asStateFlow()

    override suspend fun connect(session: SessionResponse) {
        require(session.livekitUrl.isNotBlank() && session.token.isNotBlank()) {
            "会话缺少房间地址或入场凭证"
        }
        _connection.value = TransportConnection.CONNECTING
        val newRoom = LiveKit.create(appContext)
        room = newRoom
        newRoom.connect(session.livekitUrl, session.token)
        _connection.value = TransportConnection.CONNECTED
        // 建连前用户就点进了通话（建连通常要一两秒）：那时麦克风意图先记着，现在补上。
        micIntent?.let { applyMic(newRoom, it) }

        newRoom.registerTextStreamHandler(TEXT_OUT_TOPIC) { reader, identity ->
            scope.launch {
                // 只取 agent 那一路：用户自己的语音转写不应被当作她的回复。
                if (identity == newRoom.localParticipant.identity) return@launch
                reader.flow.collect { chunk -> bus.emit(CompanionEvent.TextDelta(chunk)) }
                bus.emit(CompanionEvent.TurnDone)
            }
        }

        scope.launch {
            newRoom.events.events.collect { event ->
                when (event) {
                    is RoomEvent.DataReceived -> handleData(event.topic, event.data)
                    // 她那一路上麦：只有远端音频轨能算振幅，本地轨是用户自己的声音。
                    is RoomEvent.TrackSubscribed -> {
                        if (event.participant.identity != newRoom.localParticipant.identity) {
                            attachMouthSink(event.track)
                        }
                    }
                    is RoomEvent.TrackUnsubscribed -> detachMouthSink()
                    is RoomEvent.Reconnecting -> _connection.value = TransportConnection.RECONNECTING
                    is RoomEvent.Reconnected -> _connection.value = TransportConnection.CONNECTED
                    is RoomEvent.Disconnected -> {
                        _connection.value = TransportConnection.DISCONNECTED
                        bus.emit(CompanionEvent.Error("连接已断开"))
                    }
                    else -> Unit
                }
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
        applyMic(current, enabled)
    }

    private fun applyMic(current: Room, enabled: Boolean) {
        scope.launch {
            micMutex.withLock {
                runCatching { current.localParticipant.setMicrophoneEnabled(enabled) }
                    .onFailure { bus.emit(CompanionEvent.Error("麦克风切换失败：${it.message}")) }
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
        val audio = track as? AudioTrack ?: return
        detachMouthSink()
        val sink = object : AudioTrackSink {
            override fun onData(
                data: ByteBuffer,
                bitsPerSample: Int,
                sampleRate: Int,
                numberOfChannels: Int,
                frames: Int,
                absoluteCaptureTimestampMs: Long,
            ) {
                if (bitsPerSample != 16) return
                // 只取前若干帧算 RMS：整块算没必要，够判断「这一刻响不响」即可。
                val limit = minOf(frames, RMS_WINDOW_FRAMES) * numberOfChannels
                if (limit <= 0) return
                val buf = data.duplicate()
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
                _mouthLevel.value = (rms * MOUTH_GAIN).coerceIn(0.0, 1.0).toFloat()
            }
        }
        audio.addSink(sink)
        mouthSink = sink
        mouthTrack = audio
    }

    /** 摘掉音频 sink。SDK 不会替我们摘：留在 track 上就是一个永不释放的回调。 */
    private fun detachMouthSink() {
        val sink = mouthSink ?: return
        runCatching { mouthTrack?.removeSink(sink) }
        mouthSink = null
        mouthTrack = null
        _mouthLevel.value = 0f
    }

    private suspend fun handleData(topic: String?, data: ByteArray) {
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
         * 振幅增益。归一化后的 RMS 人声常在 0.02~0.2，乘以 4 才够跨过嘴型的 0.35 阈值；
         * 与 `:speech` 的 VoiceRecorder 用同一个系数，两处说「多大算说话」的口径才一致。
         */
        const val MOUTH_GAIN = 4.0
    }
}
