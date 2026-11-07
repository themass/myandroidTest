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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
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
    private var room: Room? = null

    override val events: Flow<CompanionEvent> = bus.asSharedFlow()

    override suspend fun connect(session: SessionResponse) {
        require(session.livekitUrl.isNotBlank() && session.token.isNotBlank()) {
            "会话缺少房间地址或入场凭证"
        }
        val newRoom = LiveKit.create(appContext)
        room = newRoom
        newRoom.connect(session.livekitUrl, session.token)

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
                    is RoomEvent.Disconnected -> bus.emit(CompanionEvent.Error("连接已断开"))
                    else -> Unit
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
