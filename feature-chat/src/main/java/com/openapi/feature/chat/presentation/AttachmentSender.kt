package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 附件消息的发送：逐个上传，各自的识别结果合并成一条助手回复；有配文时再走文字流。
 * 从 ChatViewModel 拆出来。发送中的消息 id 记在 inFlight 里，刷新历史时不把它们当成「中断的发送」。
 */
internal class AttachmentSender(
    private val repository: ChatRepository,
    private val state: MutableStateFlow<ChatUiState>,
    private val streamer: ReplyStreamer,
    /** 配文走文字流（用户消息已保存过，不重复保存）。 */
    private val streamText: (String) -> Unit,
) {

    private companion object {
        /** 单个附件上传+识别的超时：超时按失败处理，气泡显示重试图标，不再一直转圈。 */
        const val ATTACHMENT_TIMEOUT_MS = 120_000L
    }

    /** 正在本进程里发送的附件消息 id：刷新历史时不能把它们当成「中断的发送」。 */
    private val inFlight = HashSet<String>()

    fun isInFlight(messageId: String): Boolean = messageId in inFlight

    /** 把附件消息的发送结果写回本地记录，重启后状态一致。 */
    private suspend fun persistDelivery(message: ChatMessage, status: DeliveryStatus) {
        repository.saveMessage(repository.currentSessionId(), message.copy(delivery = status))
    }

    /** 附件发送也可以被「停止」打断：和流式回复一样显示生成中，停止后该消息标为可重试。 */
    suspend fun deliver(message: ChatMessage) {
        inFlight += message.id
        state.update { it.copy(streaming = true) }
        try {
            deliverBody(message)
        } finally {
            inFlight -= message.id
            state.update { it.copy(streaming = streamer.isReplyActive()) }
        }
    }

    private suspend fun deliverBody(message: ChatMessage) {
        streamer.setDelivery(message.id, DeliveryStatus.SENDING)
        val placeholder = streamer.newPlaceholder()
        state.update { it.copy(messages = it.messages + placeholder, sending = true, error = null) }
        val sessionId = repository.currentSessionId()
        val uploads = message.images.map { Triple(MessageKind.IMAGE, java.io.File(it.path), java.io.File(it.path).name) } +
            message.files.map { Triple(MessageKind.FILE, java.io.File(it.path), it.name) }

        val replies = mutableListOf<String>()
        var replyId: String? = null
        for ((kind, file, name) in uploads) {
            val reply = runCatching {
                kotlinx.coroutines.withTimeoutOrNull(ATTACHMENT_TIMEOUT_MS) {
                    repository.sendAttachment(sessionId, kind, file, name, placeholder.id)
                }
            }.getOrNull()
            if (reply == null) {
                val stopped = !kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]!!.isActive
                state.update {
                    it.copy(
                        messages = it.messages.filterNot { m -> m.id == placeholder.id },
                        sending = false,
                        error = if (stopped) "已停止，消息未发出，可点重试" else "发送失败，请稍后重试",
                    )
                }
                streamer.setDelivery(message.id, DeliveryStatus.FAILED)
                persistDelivery(message, DeliveryStatus.FAILED)
                return
            }
            replyId = reply.id
            if (reply.text.isNotBlank()) replies += reply.text
        }
        streamer.setDelivery(message.id, DeliveryStatus.SENT)
        persistDelivery(message, DeliveryStatus.SENT)

        val combined = replies.joinToString("\n\n")
        if (combined.isBlank()) {
            state.update { it.copy(messages = it.messages.filterNot { m -> m.id == placeholder.id }, sending = false) }
        } else {
            val assistant = ChatMessage(
                id = replyId ?: "assistant-${System.currentTimeMillis()}",
                role = ChatRole.ASSISTANT,
                text = combined,
                createdAtMillis = System.currentTimeMillis(),
            )
            repository.saveMessage(sessionId, assistant)
            state.update { current ->
                current.copy(
                    messages = current.messages.map { m -> if (m.id == placeholder.id) assistant.copy(key = placeholder.key) else m },
                    sending = false,
                )
            }
        }
        // 附件不在对话历史接口里，这里不再从服务端整页刷新，否则附件气泡会被冲掉
        if (message.text.isNotBlank()) streamText(message.text)
    }
}
