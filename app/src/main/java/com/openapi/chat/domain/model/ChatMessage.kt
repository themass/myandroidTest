package com.openapi.chat.domain.model

/** 消息方向。 */
enum class ChatRole {
    USER,
    ASSISTANT,
}

/** 消息内容类型，对应历史数据里的 [chat.ui.data.model.MessageType]。 */
enum class MessageKind {
    TEXT,
    VOICE,
    IMAGE,
    FILE,
}

/** 语音附件：本地文件或远端 URL。TTS 产物以 `file://` 形式给出。 */
data class VoiceAttachment(
    val uri: String,
    val durationSeconds: Int,
)

/** 图片附件：已压缩并落在应用私有目录下的本地文件路径。 */
data class ImageAttachment(
    val path: String,
)

/** 用户消息的投递状态。 */
enum class DeliveryStatus { SENT, SENDING, FAILED }

/** 文件附件：应用私有目录下的本地路径，连同原始文件名与大小。 */
data class FileAttachment(
    val path: String,
    val name: String,
    val sizeBytes: Long,
)

/**
 * 对话中的一条消息。纯 Kotlin，不依赖 Android 与任何 UI 框架。
 *
 * [id] 是服务端给的消息 id（对应老数据 ChatLog.chatId）；本地占位消息在拿到回复前
 * 使用临时 id，回复到达后整体替换。[pending] 表示「等待大模型返回」的占位气泡。
 */
data class ChatMessage(
    val id: String,
    /**
     * 列表里这条气泡的稳定标识。创建时确定，之后不再变化。
     * [id] 会在回复完成时从本地临时 id 换成服务端 id，如果列表用 [id] 当 key，
     * 完成的一瞬间整条气泡会被当成另一条消息删除再重建（表现为跳变、闪烁、流式效果丢失）。
     */
    val key: String = id,
    val role: ChatRole,
    val text: String,
    val createdAtMillis: Long,
    val kind: MessageKind = MessageKind.TEXT,
    val voice: VoiceAttachment? = null,
    /** 一条消息可以带多张图片（多选或「图片 + 文字」组合）。 */
    val images: List<ImageAttachment> = emptyList(),
    /** 一条消息可以带多个文件。 */
    val files: List<FileAttachment> = emptyList(),
    val pending: Boolean = false,
    val delivery: DeliveryStatus = DeliveryStatus.SENT,
)

/**
 * 可选人设（服务端 setting_character 表，`/user/setting/character.json`）。
 *
 * [id] 就是新对话契约里的 characterId；[preview] 是人设正文的前几十字，用于选择界面展示，
 * 不是完整 prompt——完整内容只有服务端需要，端上选完只传 id。
 */
data class ChatCharacterOption(
    val id: Int,
    val title: String,
    val avatarUrl: String?,
    /** 人设正文的前几十字，仅用于选择列表展示。 */
    val preview: String,
    /** 是不是当前用户自己上传的角色；官方角色为 false。决定要不要显示编辑/删除入口。 */
    val mine: Boolean = false,
    /**
     * 完整人设正文。
     *
     * 编辑角色时必须用这个而不是 [preview]——preview 是截断过的，拿它回填编辑框
     * 再把用户点保存，会把人设静默截短到几十个字。
     */
    val content: String = "",
)

/** 附件上传后的一次性回复（非流式接口返回整段文字）。 */
data class ChatReply(
    val id: String,
    val text: String,
)

/**
 * 一页历史消息（按时间正序）。[hasMore] 为 true 表示还有更早的消息，
 * 用 [messages] 里最早一条的服务端 id 作为 beforeId 继续拉。
 */
data class HistoryPage(
    val messages: List<ChatMessage>,
    val hasMore: Boolean,
)
