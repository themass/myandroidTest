package com.openapi.chat.data.mapper

import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatRole
import com.openapi.chat.domain.model.FileAttachment
import com.openapi.chat.domain.model.ImageAttachment
import com.openapi.chat.domain.model.MessageKind
import com.openapi.chat.domain.model.VoiceAttachment
import org.json.JSONArray
import org.json.JSONObject

/**
 * 老数据与新领域模型之间的转换。
 *
 * 历史数据的形状（greenDAO `ChatLog`）：
 * - `content` 字段存的是 `MsgContent` 的 JSON：{content, org, time, msgId, holdId, fileName, fileSize}
 *   - `content`：文本内容；语音消息时是音频文件路径；图片/文件消息时是本地文件路径
 *   - `org`：语音消息的转写文本
 *   - `time`：语音时长（秒）
 *   - `fileName` / `fileSize`：文件消息的原始文件名与大小（大小以字符串存放）
 * - `who`：TYPE_SEND / TYPE_RECEIVED / TYPE_SYSTEM
 * - `msgType`：TXT_TYPE / AUDIO_TYPE / IMAGE_TYPE / FILE_TYPE
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
    const val TYPE_FILE = 4

    /** MsgContent 的字段名，与老数据保持一致。 */
    private const val FIELD_CONTENT = "content"
    private const val FIELD_ORG = "org"
    private const val FIELD_TIME = "time"
    private const val FIELD_FILE_NAME = "fileName"
    private const val FIELD_FILE_SIZE = "fileSize"

    /** 新增字段（旧数据没有，读取时回退到 content）：多图路径、多文件、组合消息的配文。 */
    private const val FIELD_IMAGES = "images"
    private const val FIELD_FILES = "files"
    private const val FIELD_CAPTION = "caption"

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
        var text = ""
        var voice: VoiceAttachment? = null
        var images: List<ImageAttachment> = emptyList()
        var files: List<FileAttachment> = emptyList()
        val kind: MessageKind

        when (msgType) {
            TYPE_AUDIO -> {
                val audioUri = json?.optString(FIELD_CONTENT).orEmpty()
                val transcript = json?.optString(FIELD_ORG).orEmpty()
                val duration = json?.optInt(FIELD_TIME, 0) ?: 0
                // 语音气泡上显示的是转写文本
                text = transcript
                voice = VoiceAttachment(uri = audioUri, durationSeconds = duration)
                kind = MessageKind.VOICE
            }

            TYPE_IMAGE -> {
                images = readImages(json)
                files = readFiles(json, fallbackToContent = false)
                text = json?.optString(FIELD_CAPTION).orEmpty()
                kind = MessageKind.IMAGE
            }

            TYPE_FILE -> {
                files = readFiles(json, fallbackToContent = true)
                text = json?.optString(FIELD_CAPTION).orEmpty()
                kind = MessageKind.FILE
            }

            else -> {
                text = json?.optString(FIELD_CONTENT).orEmpty().ifEmpty { rawContent.orEmpty() }
                kind = MessageKind.TEXT
            }
        }

        return ChatMessage(
            id = chatId ?: "",
            role = if (who == WHO_SENT) ChatRole.USER else ChatRole.ASSISTANT,
            text = text,
            createdAtMillis = createdAtMillis,
            kind = kind,
            voice = voice,
            images = images,
            files = files,
        )
    }

    private fun readImages(json: JSONObject?): List<ImageAttachment> {
        val array = json?.optJSONArray(FIELD_IMAGES)
        if (array != null && array.length() > 0) {
            return (0 until array.length()).map { ImageAttachment(path = array.optString(it)) }
        }
        val single = json?.optString(FIELD_CONTENT).orEmpty()
        return if (single.isEmpty()) emptyList() else listOf(ImageAttachment(path = single))
    }

    private fun readFiles(json: JSONObject?, fallbackToContent: Boolean): List<FileAttachment> {
        val array = json?.optJSONArray(FIELD_FILES)
        if (array != null && array.length() > 0) {
            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                FileAttachment(
                    path = item.optString(FIELD_CONTENT),
                    name = item.optString(FIELD_FILE_NAME),
                    sizeBytes = item.optString(FIELD_FILE_SIZE).toLongOrNull() ?: 0L,
                )
            }
        }
        if (!fallbackToContent) return emptyList()
        return listOf(
            FileAttachment(
                path = json?.optString(FIELD_CONTENT).orEmpty(),
                name = json?.optString(FIELD_FILE_NAME).orEmpty(),
                sizeBytes = json?.optString(FIELD_FILE_SIZE)?.toLongOrNull() ?: 0L,
            ),
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

            MessageKind.IMAGE, MessageKind.FILE -> {
                // content / fileName / fileSize 仍写第一个附件，旧版本读得出来；完整内容放在新增字段里
                val firstImage = message.images.firstOrNull()
                val firstFile = message.files.firstOrNull()
                json.put(FIELD_CONTENT, firstImage?.path ?: firstFile?.path.orEmpty())
                if (firstImage == null && firstFile != null) {
                    json.put(FIELD_FILE_NAME, firstFile.name)
                    json.put(FIELD_FILE_SIZE, firstFile.sizeBytes.toString())
                }
                if (message.images.isNotEmpty()) {
                    json.put(FIELD_IMAGES, JSONArray(message.images.map { it.path }))
                }
                if (message.files.isNotEmpty()) {
                    json.put(FIELD_FILES, JSONArray(message.files.map { file ->
                        JSONObject()
                            .put(FIELD_CONTENT, file.path)
                            .put(FIELD_FILE_NAME, file.name)
                            .put(FIELD_FILE_SIZE, file.sizeBytes.toString())
                    }))
                }
                if (message.text.isNotBlank()) json.put(FIELD_CAPTION, message.text)
            }
        }
        return json.toString()
    }

    fun toWho(role: ChatRole): Int =
        if (role == ChatRole.USER) WHO_SENT else WHO_RECEIVED

    fun toMsgType(kind: MessageKind): Int = when (kind) {
        MessageKind.TEXT -> TYPE_TXT
        MessageKind.VOICE -> TYPE_AUDIO
        MessageKind.IMAGE -> TYPE_IMAGE
        MessageKind.FILE -> TYPE_FILE
    }

    private fun parseOrNull(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }
}
