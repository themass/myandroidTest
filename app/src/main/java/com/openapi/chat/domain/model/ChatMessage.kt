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
}

/** 语音附件：本地文件或远端 URL。TTS 产物以 `file://` 形式给出。 */
data class VoiceAttachment(
    val uri: String,
    val durationSeconds: Int,
)

/**
 * 对话中的一条消息。纯 Kotlin，不依赖 Android 与任何 UI 框架。
 *
 * [id] 是服务端给的消息 id（对应老数据 ChatLog.chatId）；本地占位消息在拿到回复前
 * 使用临时 id，回复到达后整体替换。[pending] 表示「等待大模型返回」的占位气泡。
 */
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val createdAtMillis: Long,
    val kind: MessageKind = MessageKind.TEXT,
    val voice: VoiceAttachment? = null,
    val pending: Boolean = false,
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
