package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.domain.model.OutgoingAttachment
import com.openapi.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 附件消息的发送。
 *
 * **一条消息 = 一个回合。** 以前这里对每个附件单独发一次、再对文字单独发一次，
 * 于是「这张图是什么」得到两条回复，界面上也被拆成两条。现在：
 * - 图片先静默上传拿到 `upload_id`（这一步不产生回复）；
 * - 文字和所有图片引用**一起**提交给同一个回合，模型只回一次话。
 *
 * 文件（非图片）仍走 legacy 的逐条 multipart 接口——`/v1/uploads` 没有文件类型，
 * 那是能力缺口。文件和图片混发时，图片跟随文字进 `/v1` 回合，文件仍各自走旧链路。
 */
internal class AttachmentSender(
    private val repository: ChatRepository,
    private val state: MutableStateFlow<ChatUiState>,
    private val streamer: ReplyStreamer,
    /** 图文消息的图片已上传完，交由流式层用一个回合发出（避免这里再起第二条回复）。 */
    private val streamWithAttachments: (String, String?, Boolean, List<OutgoingAttachment>) -> Unit,
) {

    private companion object {
        /** 单个附件上传的超时：超时按失败处理，气泡显示重试图标，不再一直转圈。 */
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
        val sessionId = repository.currentSessionId()

        // 1) 图片先传完，拿到 upload_id。上传本身不产生回复。
        val sent: List<OutgoingAttachment> = message.images.map { image ->
            val file = java.io.File(image.path)
            val uploadId = kotlinx.coroutines.withTimeoutOrNull(ATTACHMENT_TIMEOUT_MS) {
                repository.uploadImage(file, file.name)
            }
            if (uploadId == null) {
                fail(message, sessionId)
                return
            }
            OutgoingAttachment(uploadId = uploadId, kind = MessageKind.IMAGE, name = file.name)
        }

        // 2) 文字 + 图片引用一起进同一个回合：模型只回一次话，界面也只出一条回复。
        if (sent.isNotEmpty() || message.text.isNotBlank()) {
            streamWithAttachments(message.text, message.id, false, sent)
        }

        // 3) 文件仍走 legacy 的逐条接口（`/v1` 没有文件类型）。
        for (file in message.files) {
            val reply = runCatching {
                kotlinx.coroutines.withTimeoutOrNull(ATTACHMENT_TIMEOUT_MS) {
                    repository.sendAttachment(
                        sessionId, MessageKind.FILE, java.io.File(file.path), file.name, message.id,
                    )
                }
            }.getOrNull()
            if (reply == null) {
                fail(message, sessionId)
                return
            }
        }
        streamer.setDelivery(message.id, DeliveryStatus.SENT)
        persistDelivery(message, DeliveryStatus.SENT)
    }

    /** 失败收尾：撤掉占位、标红、写回本地，行为与以前一致。 */
    private suspend fun fail(message: ChatMessage, sessionId: Long) {
        val stopped = !kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]!!.isActive
        state.update {
            it.copy(sending = false, error = if (stopped) "已停止，消息未发出，可点重试" else "发送失败，请稍后重试")
        }
        streamer.setDelivery(message.id, DeliveryStatus.FAILED)
        persistDelivery(message, DeliveryStatus.FAILED)
    }
}
