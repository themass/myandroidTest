package com.openapi.chatkit.model

import androidx.compose.runtime.Immutable

/** 消息由谁发出。 */
enum class Sender { User, Assistant }

/** 用户消息的发送状态。 */
enum class SendStatus { Sent, Sending, Failed }

/** 用户对助手消息的评价。 */
enum class Reaction { None, Like, Dislike }

/**
 * 一条消息里的一个部分。一条消息可以由多个部分组合而成，渲染顺序固定为
 * 图片 → 文件 → 语音 → 文字（附件在上、文字在下），与列表顺序无关。
 */
@Immutable
sealed interface MessagePart {
    /** 文字。助手文字可由调用方用 Markdown 渲染（见 [com.openapi.chatkit.message.ChatMessageList] 的 text 参数）。 */
    data class Text(val text: String) : MessagePart

    /** 一组图片：本地文件路径或 http(s) 地址。 */
    data class Images(val sources: List<String>) : MessagePart

    /** 一个文件。 */
    data class File(val path: String, val name: String, val sizeBytes: Long) : MessagePart

    /** 语音。[playing] 由调用方根据播放器状态填入，组件据此显示声波动画。 */
    /** 语音消息。[transcript] 为 null 或空白表示没有识别出文字；[transcribing] 为 true 表示还在识别中。 */
    data class Voice(
        val durationSeconds: Int,
        val transcript: String?,
        val playing: Boolean,
        val transcribing: Boolean = false,
    ) : MessagePart
}

/** 列表里的一条消息（UI 模型）。由调用方从自己的领域模型映射而来。 */
@Immutable
data class ChatItem(
    val id: String,
    /** 列表项的稳定标识（不随服务端 id 的替换而变化），LazyColumn 用它做 key。 */
    val key: String = id,
    val sender: Sender,
    val parts: List<MessagePart>,
    val status: SendStatus = SendStatus.Sent,
    /** 助手正在思考、还没有任何内容时为 true，显示跳动的点。 */
    val thinking: Boolean = false,
    val reaction: Reaction = Reaction.None,
    /** 正在朗读这条消息。 */
    val speaking: Boolean = false,
    /** 助手正在流式输出这条消息（已有部分文字但还没结束）。 */
    val streaming: Boolean = false,
) {
    /** 用于复制、分享、编辑的纯文本：文字部分，没有文字时退回语音转写。 */
    val plainText: String
        get() {
            val text = parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
            if (text.isNotBlank()) return text
            return parts.filterIsInstance<MessagePart.Voice>().mapNotNull { it.transcript }.joinToString("\n")
        }
}

/** 用户在消息上触发的操作。组件只负责把操作交给调用方，不关心怎么实现。 */
sealed interface MessageAction {
    data object Copy : MessageAction
    data object Delete : MessageAction
    data object Retry : MessageAction
    data object Edit : MessageAction
    data object Speak : MessageAction
    data object Like : MessageAction
    data object Dislike : MessageAction
    data object Share : MessageAction
    data object Regenerate : MessageAction
    data object PlayVoice : MessageAction
    data class OpenFile(val file: MessagePart.File) : MessageAction
}
