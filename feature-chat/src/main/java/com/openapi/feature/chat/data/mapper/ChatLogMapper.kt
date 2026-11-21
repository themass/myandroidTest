package com.openapi.feature.chat.data.mapper

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.FileAttachment
import com.openapi.feature.chat.domain.model.GeoLocation
import com.openapi.feature.chat.domain.model.ImageAttachment
import com.openapi.feature.chat.domain.model.ImageSource
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.domain.model.VideoAttachment
import com.openapi.feature.chat.domain.model.VoiceAttachment
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

    /** 视频沿用 core MessageType.VIDEO_TYPE = 3；位置是本地新增类型（服务端用 kind 字符串区分）。 */
    const val TYPE_VIDEO = 3
    const val TYPE_LOCATION = 5

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

    /** P2-13 新增：视频列表、坐标、视频首帧缩略图与时长（只有本地行有，服务端不回传缩略图）。 */
    private const val FIELD_VIDEOS = "videos"
    private const val FIELD_LOCATION = "location"
    private const val FIELD_THUMB = "thumb"
    private const val FIELD_DURATION = "duration"

    /** 发送状态：只有非「已发送」才写入（旧数据没有这个字段，读取时视为已发送）。 */
    private const val FIELD_DELIVERY = "delivery"

    /**
     * 图片 content 的取值标记：这条图只存在服务端对象存储（无本机文件），
     * 渲染时要拿 uploadId 现换签名 URL（URL 只活 5 分钟，不进缓存）。
     */
    private const val REMOTE_MARK = "remote"

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
        var videos: List<VideoAttachment> = emptyList()
        var location: GeoLocation? = null
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

            TYPE_VIDEO -> {
                videos = readVideos(json)
                text = json?.optString(FIELD_CAPTION).orEmpty()
                kind = MessageKind.VIDEO
            }

            TYPE_LOCATION -> {
                location = readLocation(json)
                // 老行没有 caption 时退回 content：位置消息的配文两种落法都读得到
                text = json?.optString(FIELD_CAPTION).orEmpty().ifBlank {
                    json?.optString(FIELD_CONTENT).orEmpty()
                }
                kind = MessageKind.LOCATION
            }

            else -> {
                // 只有 rawContent 不是 JSON 时才原样显示；是 JSON 但 content 为空，就是空文本
                text = if (json != null) json.optString(FIELD_CONTENT).orEmpty() else rawContent.orEmpty()
                kind = MessageKind.TEXT
            }
        }

        // 助手的空文本回复（老数据里存下来的）不显示
        if (who != WHO_SENT && kind == MessageKind.TEXT && text.isBlank()) return null

        val delivery = runCatching { DeliveryStatus.valueOf(json?.optString(FIELD_DELIVERY).orEmpty()) }
            .getOrDefault(DeliveryStatus.SENT)
        return ChatMessage(
            id = chatId ?: "",
            role = if (who == WHO_SENT) ChatRole.USER else ChatRole.ASSISTANT,
            text = text,
            createdAtMillis = createdAtMillis,
            kind = kind,
            voice = voice,
            images = images,
            files = files,
            videos = videos,
            location = location,
            delivery = delivery,
        )
    }

    private fun readImages(json: JSONObject?): List<ImageAttachment> {
        val array = json?.optJSONArray(FIELD_IMAGES)
        if (array != null && array.length() > 0) {
            return (0 until array.length()).mapNotNull { index ->
                // 新格式是对象 {content, uploadId}；旧数据是纯路径字符串。
                // content=="remote" 表示这条图没有本机文件，只有服务端对象（uploadId），
                // 路径要现问服务器换签名 URL——URL 只活 5 分钟，不进缓存。
                val item = array.optJSONObject(index)
                if (item != null) {
                    val uploadId = item.optString("uploadId").takeIf { it.isNotEmpty() }
                    val path = item.optString(FIELD_CONTENT)
                    when {
                        path == REMOTE_MARK -> ImageAttachment(path = "", source = ImageSource.REMOTE, uploadId = uploadId)
                        path.isNotEmpty() -> ImageAttachment(path = path, uploadId = uploadId)
                        else -> null
                    }
                } else {
                    val path = array.optString(index)
                    if (path.isEmpty()) null else ImageAttachment(path = path)
                }
            }
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

    /**
     * 视频列表：对象数组 {content, name, size, duration, uploadId, thumb}。
     * content=="remote" 与图片同一约定（本机文件已丢，只有服务端对象）；thumb 是首帧缩略图的
     * 本机路径——它只对本机有效，缩略图文件已不在就丢掉（显示会退化成文件名卡片）。
     */
    private fun readVideos(json: JSONObject?): List<VideoAttachment> {
        val array = json?.optJSONArray(FIELD_VIDEOS) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val uploadId = item.optString("uploadId").takeIf { it.isNotEmpty() }
            val path = item.optString(FIELD_CONTENT)
            if (path.isEmpty() && uploadId == null) return@mapNotNull null
            val thumb = item.optString(FIELD_THUMB)
            VideoAttachment(
                path = if (path == REMOTE_MARK) "" else path,
                name = item.optString(FIELD_FILE_NAME),
                sizeBytes = item.optString(FIELD_FILE_SIZE).toLongOrNull() ?: 0L,
                durationSeconds = item.optInt(FIELD_DURATION, 0),
                uploadId = uploadId,
                thumbnailPath = thumb.takeIf { it.isNotEmpty() && java.io.File(thumb).exists() },
            )
        }
    }

    /** 坐标对象 {lat, lng, accuracy}；缺任一经纬度就当没有（宁可不显示也不画一个 (0,0) 气泡）。 */
    private fun readLocation(json: JSONObject?): GeoLocation? {
        val obj = json?.optJSONObject(FIELD_LOCATION) ?: return null
        val lat = obj.optDouble("lat", Double.NaN)
        val lng = obj.optDouble("lng", Double.NaN)
        if (lat.isNaN() || lng.isNaN()) return null
        val accuracy = obj.optInt("accuracy", -1).takeIf { it >= 0 }
        return GeoLocation(latitude = lat, longitude = lng, accuracyMeters = accuracy)
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
                    json.put(FIELD_IMAGES, JSONArray(message.images.map { image ->
                        // 旧数据这里是字符串数组；升级后写成对象，多出来的字段旧版本读不到但不崩。
                        // 远程来源没有本机路径，content 写 REMOTE_MARK，渲染时拿 uploadId 换 URL。
                        JSONObject()
                            .put(FIELD_CONTENT,
                                if (image.source == ImageSource.REMOTE) REMOTE_MARK else image.path)
                            .apply { image.uploadId?.let { put("uploadId", it) } }
                    }))
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

            MessageKind.VIDEO -> {
                // content / fileName / fileSize 照图片/文件的旧例写第一个视频，旧版本读得出来
                val first = message.videos.firstOrNull()
                json.put(FIELD_CONTENT, first?.path?.takeIf { it.isNotEmpty() } ?: REMOTE_MARK)
                if (first != null) {
                    json.put(FIELD_FILE_NAME, first.name)
                    json.put(FIELD_FILE_SIZE, first.sizeBytes.toString())
                }
                if (message.videos.isNotEmpty()) {
                    json.put(FIELD_VIDEOS, JSONArray(message.videos.map { video ->
                        JSONObject()
                            .put(FIELD_CONTENT, video.path.takeIf { it.isNotEmpty() } ?: REMOTE_MARK)
                            .put(FIELD_FILE_NAME, video.name)
                            .put(FIELD_FILE_SIZE, video.sizeBytes.toString())
                            .put(FIELD_DURATION, video.durationSeconds)
                            .apply {
                                video.uploadId?.let { put("uploadId", it) }
                                // 首帧缩略图只对本机有效；文件已经不在了就不写（读时也会丢弃）
                                video.thumbnailPath
                                    ?.takeIf { java.io.File(it).exists() }
                                    ?.let { put(FIELD_THUMB, it) }
                            }
                    }))
                }
                if (message.text.isNotBlank()) json.put(FIELD_CAPTION, message.text)
            }

            MessageKind.LOCATION -> {
                json.put(FIELD_CONTENT, message.text)
                message.location?.let { loc ->
                    json.put(
                        FIELD_LOCATION,
                        JSONObject()
                            .put("lat", loc.latitude)
                            .put("lng", loc.longitude)
                            .apply { loc.accuracyMeters?.let { put("accuracy", it) } },
                    )
                }
            }
        }
        if (message.delivery != DeliveryStatus.SENT) json.put(FIELD_DELIVERY, message.delivery.name)
        return json.toString()
    }

    fun toWho(role: ChatRole): Int =
        if (role == ChatRole.USER) WHO_SENT else WHO_RECEIVED

    fun toMsgType(kind: MessageKind): Int = when (kind) {
        MessageKind.TEXT -> TYPE_TXT
        MessageKind.VOICE -> TYPE_AUDIO
        MessageKind.IMAGE -> TYPE_IMAGE
        MessageKind.FILE -> TYPE_FILE
        MessageKind.VIDEO -> TYPE_VIDEO
        MessageKind.LOCATION -> TYPE_LOCATION
    }

    private fun parseOrNull(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }
}
