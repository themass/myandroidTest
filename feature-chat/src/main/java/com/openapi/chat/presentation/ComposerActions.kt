package com.openapi.chat.presentation

import com.openapi.chat.domain.model.DeliveryStatus
import com.openapi.chat.domain.model.MessageKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 输入框、编辑状态、草稿托盘、错误提示。从 ChatViewModel 拆出来：纯状态操作，不依赖仓库。
 */
internal class ComposerActions(private val state: MutableStateFlow<ChatUiState>) {

    fun setInput(text: String) {
        state.update { it.copy(input = text) }
    }

    /** 进入编辑：只允许编辑纯文字的用户消息（带图片或文件的不能改）。 */
    fun startEdit(messageId: String) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        if (message.role != com.openapi.chat.domain.model.ChatRole.USER || message.images.isNotEmpty() || message.files.isNotEmpty()) return
        state.update { it.copy(input = message.text, editingMessageId = messageId) }
    }

    /** 取消编辑：清空输入框并退出编辑状态。 */
    fun cancelEdit() {
        state.update { it.copy(input = "", editingMessageId = null) }
    }

    fun dismissError() {
        state.update { it.copy(error = null) }
    }

    fun addDraft(kind: MessageKind, staged: StagedAttachment) {
        val draft = DraftAttachment(id = "draft-${System.nanoTime()}", kind = kind, staged = staged)
        state.update { it.copy(drafts = it.drafts + draft) }
    }

    fun removeDraft(id: String) {
        state.update { it.copy(drafts = it.drafts.filterNot { d -> d.id == id }) }
    }
}

/** 点赞、点踩：两者互斥。 */
internal class FeedbackActions(private val state: MutableStateFlow<ChatUiState>) {

    /** 返回切换后是否为「已赞」。点赞会取消点踩。 */
    fun toggleLike(messageId: String): Boolean {
        val liked = messageId !in state.value.likedIds
        state.update {
            it.copy(
                likedIds = if (liked) it.likedIds + messageId else it.likedIds - messageId,
                dislikedIds = it.dislikedIds - messageId,
            )
        }
        return liked
    }

    /** 点踩并提交反馈后调用。点踩会取消点赞。 */
    fun disliked(messageId: String) {
        state.update { it.copy(dislikedIds = it.dislikedIds + messageId, likedIds = it.likedIds - messageId) }
    }
}
