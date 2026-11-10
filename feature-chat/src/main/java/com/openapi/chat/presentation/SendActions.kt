package com.openapi.chat.presentation

import com.openapi.asr.tts.DeviceSpeech
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatRole
import com.openapi.chat.domain.model.DeliveryStatus
import com.openapi.chat.domain.model.FileAttachment
import com.openapi.chat.domain.model.ImageAttachment
import com.openapi.chat.domain.model.MessageKind
import com.openapi.chat.domain.repository.ChatRepository
import com.openapi.core.media.AudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 发送：文字和草稿附件的发送、选择附件、朗读助手回复。从 ChatViewModel 拆出来。
 * 编辑态的发送交给 [submitEdit]；带附件的消息由 [startAttachmentSend] 保存并发送。
 */
internal class SendActions(
    private val repository: ChatRepository,
    private val state: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    private val composer: ComposerActions,
    private val streamer: ReplyStreamer,
    private val submitEdit: (String, String) -> Unit,
    private val startAttachmentSend: (ChatMessage) -> Unit,
) {

    fun onSend() {
        val snapshot = state.value
        if (!snapshot.canSend) return
        val text = snapshot.input.trim()
        val drafts = snapshot.drafts
        // 正在编辑一条用户消息：提交的是编辑，而不是新消息（附件草稿不参与编辑）
        val editingId = snapshot.editingMessageId
        if (editingId != null && drafts.isEmpty()) {
            submitEdit(editingId, text)
            return
        }
        state.update { it.copy(input = "", drafts = emptyList()) }
        if (drafts.isEmpty()) {
            sendText(text)
            return
        }
        // 附件和文字组成一条消息：界面上一个气泡，保存一条记录
        val now = System.currentTimeMillis()
        val images = drafts.filter { it.kind == MessageKind.IMAGE }
            .map { ImageAttachment(path = it.staged.file.absolutePath) }
        val files = drafts.filter { it.kind == MessageKind.FILE }
            .map { FileAttachment(path = it.staged.file.absolutePath, name = it.staged.displayName, sizeBytes = it.staged.sizeBytes) }
        val message = ChatMessage(
            id = "local-user-$now",
            role = ChatRole.USER,
            text = text,
            createdAtMillis = now,
            kind = if (images.isNotEmpty()) MessageKind.IMAGE else MessageKind.FILE,
            images = images,
            files = files,
            delivery = DeliveryStatus.SENDING,
        )
        state.update { it.copy(messages = it.messages + message) }
        startAttachmentSend(message)
    }

    fun sendText(text: String) {
        if (text.isBlank() || state.value.sending) return
        val now = System.currentTimeMillis()
        val user = ChatMessage(
            id = "local-user-$now",
            role = ChatRole.USER,
            text = text,
            createdAtMillis = now,
            delivery = DeliveryStatus.SENDING,
        )
        state.update { it.copy(messages = it.messages + user) }
        streamer.stream(text, userMessageId = user.id, persistUser = true)
    }

    fun attachmentPicked(context: android.content.Context, kind: MessageKind, uri: android.net.Uri) {
        if (state.value.sending) return
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { AttachmentStager.stage(context, kind, uri) }
            when (outcome) {
                is StageOutcome.Staged -> composer.addDraft(kind, outcome.attachment)
                is StageOutcome.Rejected -> state.update { it.copy(error = outcome.reason) }
                StageOutcome.Failed -> state.update { it.copy(error = "读取附件失败，请重试") }
            }
        }
    }

    fun imagesPicked(context: android.content.Context, uris: List<android.net.Uri>) {
        if (uris.isEmpty() || state.value.sending) return
        scope.launch {
            for (uri in uris) {
                val outcome = withContext(Dispatchers.IO) { AttachmentStager.stage(context, MessageKind.IMAGE, uri) }
                when (outcome) {
                    is StageOutcome.Staged -> composer.addDraft(MessageKind.IMAGE, outcome.attachment)
                    is StageOutcome.Rejected -> state.update { it.copy(error = outcome.reason) }
                    StageOutcome.Failed -> state.update { it.copy(error = "读取图片失败，请重试") }
                }
            }
        }
    }

    fun speak(context: android.content.Context, message: ChatMessage) {
        if (message.text.isBlank() || state.value.speakingMessageId != null) return
        scope.launch {
            state.update { it.copy(speakingMessageId = message.id) }
            val spokenOnDevice = runCatching { DeviceSpeech.speakChinese(context, message.text) }.getOrDefault(false)
            if (spokenOnDevice) {
                state.update { it.copy(speakingMessageId = null) }
                return@launch
            }
            val uri = runCatching { repository.synthesizeSpeech(message.id, message.text) }.getOrNull()
            state.update { it.copy(speakingMessageId = null) }
            if (uri == null) {
                state.update { it.copy(error = "朗读失败，请稍后重试") }
            } else {
                AudioPlayer.play(message.id, uri)
            }
        }
    }
}
