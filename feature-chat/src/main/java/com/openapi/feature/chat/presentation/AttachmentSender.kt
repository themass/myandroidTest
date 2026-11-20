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
    suspend fun deliver(message: ChatMessage, restoreDraftsOnFailure: Boolean = false) {
        inFlight += message.id
        state.update { it.copy(streaming = true) }
        try {
            deliverBody(message, restoreDraftsOnFailure)
        } catch (c: kotlinx.coroutines.CancellationException) {
            // 用户按了停止（attachmentJob 只有 onStopGenerating 会取消它）。收尾必须做，
            // 且在 NonCancellable 里做：否则收尾自己的第一个挂起点会再次抛出取消，
            // 消息永远停在「发送中」、界面无反馈。收尾之后把取消原样抛出——吞协程
            // 取消是对整个协程系统撒谎（整改清单 P3-20）。
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                fail(message, restoreDraftsOnFailure, stopped = true)
            }
            throw c
        } finally {
            inFlight -= message.id
            state.update { it.copy(streaming = streamer.isReplyActive()) }
        }
    }

    private suspend fun deliverBody(message: ChatMessage, restoreDraftsOnFailure: Boolean) {
        streamer.setDelivery(message.id, DeliveryStatus.SENDING)
        val sessionId = repository.currentSessionId()

        // 1) 图片先备好 upload_id。上传本身不产生回复。
        //    本机文件在 → 一律重新上传：服务端只接受 10 分钟内签发的直传 id（ADR-011），
        //    隔一夜再点重发，旧的必然 410；有字节在手就别赌它还能用。
        //    本机文件没了（缓存清理/换设备）但这条传过 → 复用已存的 upload_id：
        //    没有字节可传，这是唯一能把图带上的路（远程来源在缓存里就带着这个 id）。
        val prepared: List<Pair<com.openapi.feature.chat.domain.model.ImageAttachment, OutgoingAttachment>> =
            message.images.map { image ->
                val file = java.io.File(image.path)
                val reuse = !file.exists() && image.uploadId != null
                val uploadId = if (reuse) image.uploadId else {
                    kotlinx.coroutines.withTimeoutOrNull(ATTACHMENT_TIMEOUT_MS) {
                        repository.uploadImage(file, file.name)
                    }
                }
                if (uploadId == null) {
                    fail(message, restoreDraftsOnFailure, stopped = false)
                    return
                }
                image to OutgoingAttachment(uploadId = uploadId, kind = MessageKind.IMAGE, name = file.name)
            }

        // 新拿到的 upload_id 记回这条消息并持久化：下次只剩远程来源时它就是复用凭据。
        val updated = message.copy(images = prepared.map { (image, outgoing) ->
            image.copy(uploadId = outgoing.uploadId)
        })

        // 2) 文字 + 图片引用一起进同一个回合：模型只回一次话，界面也只出一条回复。
        val sent = prepared.map { it.second }
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
                fail(message, restoreDraftsOnFailure, stopped = false)
                return
            }
        }
        streamer.setDelivery(message.id, DeliveryStatus.SENT)
        persistDelivery(updated, DeliveryStatus.SENT)
    }

    /**
     * 失败收尾：撤掉占位、标红、写回本地。
     * 「是不是被停止」是调用方的事实，不是这里靠 `coroutineContext[Job]` 猜的
     * （整改清单 P3-20：那种写法只在仓库吞掉取消异常时才碰巧执行到）。
     */
    private suspend fun fail(message: ChatMessage, restoreDraftsOnFailure: Boolean, stopped: Boolean) {
        state.update {
            it.copy(sending = false, error = if (stopped) "已停止，消息未发出，可点重试" else "发送失败，请稍后重试")
        }
        streamer.setDelivery(message.id, DeliveryStatus.FAILED)
        persistDelivery(message, DeliveryStatus.FAILED)
        if (restoreDraftsOnFailure) restoreDrafts(message)
    }

    /**
     * 从输入栏发起的发送失败后，把附件和文字放回输入栏（整改清单 P1-11）：
     * 气泡上的重试只能原样再发一次，用户要「改一下再发」就得靠这里。
     * 只恢复本机还在的文件（没有字节什么都恢复不了）；输入框已有内容
     * （用户已经在重打）时不盖掉它，只把附件放回托盘。
     */
    private fun restoreDrafts(message: ChatMessage) {
        val staged = ArrayList<DraftAttachment>()
        fun addOne(path: String, name: String, kind: MessageKind) {
            val file = java.io.File(path)
            if (!file.exists()) return
            staged += DraftAttachment(
                id = "draft-${System.nanoTime()}-${staged.size}",
                kind = kind,
                staged = StagedAttachment(file = file, displayName = name, sizeBytes = file.length()),
            )
        }
        message.images.forEach { addOne(it.path, java.io.File(it.path).name, MessageKind.IMAGE) }
        message.files.forEach { addOne(it.path, it.name, MessageKind.FILE) }
        if (staged.isEmpty()) return
        state.update { current ->
            current.copy(
                drafts = current.drafts + staged,
                input = if (current.input.isEmpty()) message.text else current.input,
            )
        }
    }
}
