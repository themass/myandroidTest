package com.openapi.ks.myapp.companion

import android.content.Context
import com.openapi.ks.myapp.avatar.ProactiveKind
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.datastream.StreamTextOptions
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
import org.json.JSONObject

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

    override suspend fun disconnect() {
        room?.disconnect()
        room = null
        scope.cancel()
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
        const val TOPIC_AVATAR = "companion.avatar"
        const val TOPIC_PROACTIVE = "companion.proactive"
        const val TOPIC_ACTION = "companion.action"
        const val EVENT_BUFFER = 256
    }
}
