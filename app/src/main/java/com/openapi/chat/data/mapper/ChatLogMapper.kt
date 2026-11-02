package com.openapi.chat.data.mapper

import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatRole
import com.openapi.chat.domain.model.MessageKind
import com.openapi.chat.domain.model.VoiceAttachment
import org.json.JSONObject

/**
 * 老数据与新领域模型之间的转换。
 *
 * 历史数据的形状（greenDAO `ChatLog`）：
 * - `content` 字段存的是 `MsgContent` 的 JSON：{content, org, time, msgId, holdId}
 *   - `content`：文本内容；语音消息时是音频文件路径
 *   - `org`：语音消息的转写文本
 *   - `time`：语音时长（秒）
 * - `who`：TYPE_SEND / TYPE_RECEIVED / TYPE_SYSTEM
 * - `msgType`：TXT_TYPE / AUDIO_TYPE / IMAGE_TYPE
 * - `chatId`：服务端消息 id
 * - `createTime`：时间戳
 *
 * **兼容性是硬要求**：升级后老会话必须能正常打开，所以这份映射要保持双向、可往返。
 */
object ChatLogMapper {

    const val WHO_SYSTEM = 0
    const val WHO_SENT = 1
    const val WHO_RECEIVED = 2

    const val TYPE_TXT = 0
    const val TYPE_AUDIO = 1
    const val TYPE_IMAGE = 2

    /** MsgContent 的字段名，与老数据保持一致。 */
    private const val FIELD_CONTENT = "content"
    private const val FIELD_ORG = "org"
    private const val FIELD_TIME = "time"

    /** 把一条老 ChatLog 的字段映射成领域模型。 */
    fun toDomain(
        chatId: String?,
        who: Int,
        msgType: Int,
        rawContent: String?,
        createdAtMillis: Long,
    ): ChatMessage? {
        // 系统消息不展示（与老实现 loadMessages 里的过滤一致）
        if (who == WHO_SYSTEM) return null

        val json = parseOrNull(rawContent)
        val text: String
        val voice: VoiceAttachment?

        if (msgType == TYPE_AUDIO) {
            val audioUri = json?.optString(FIELD_CONTENT).orEmpty()
            val transcript = json?.optString(FIELD_ORG).orEmpty()
            val duration = json?.optInt(FIELD_TIME, 0) ?: 0
            // 语音气泡上显示的是转写文本；拿不到就退回文件路径的展示名
            text = transcript
            voice = VoiceAttachment(uri = audioUri, durationSeconds = duration)
        } else {
            text = json?.optString(FIELD_CONTENT).orEmpty().ifEmpty { rawContent.orEmpty() }
            voice = null
        }

        return ChatMessage(
            id = chatId ?: "",
            role = if (who == WHO_SENT) ChatRole.USER else ChatRole.ASSISTANT,
            text = text,
            createdAtMillis = createdAtMillis,
            kind = if (msgType == TYPE_AUDIO) MessageKind.VOICE else MessageKind.TEXT,
            voice = voice,
        )
    }

    /** 领域模型 → 要写进 ChatLog.content 的 JSON 字符串（与老格式一致）。 */
    fun toRawContent(message: ChatMessage): String {
        val json = JSONObject()
        when (message.kind) {
            MessageKind.VOICE -> {
                json.put(FIELD_CONTENT, message.voice?.uri.orEmpty())
                json.put(FIELD_ORG, message.text)
                json.put(FIELD_TIME, message.voice?.durationSeconds ?: 0)
            }

            MessageKind.TEXT -> {
                json.put(FIELD_CONTENT, message.text)
            }
        }
        return json.toString()
    }

    fun toWho(role: ChatRole): Int =
        if (role == ChatRole.USER) WHO_SENT else WHO_RECEIVED

    fun toMsgType(kind: MessageKind): Int =
        if (kind == MessageKind.VOICE) TYPE_AUDIO else TYPE_TXT

    private fun parseOrNull(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }
}
