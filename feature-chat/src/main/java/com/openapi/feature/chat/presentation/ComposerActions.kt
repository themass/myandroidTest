package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.model.MessageKind
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
        if (message.role != com.openapi.feature.chat.domain.model.ChatRole.USER || message.images.isNotEmpty() || message.files.isNotEmpty()) return
        // 草稿托盘一起清掉：编辑提交不带附件（onSend 只在 drafts 为空时走 submitEdit），
        // 留着旧草稿会让用户以为图文一起编辑，实际发出的是新消息。
        state.update { it.copy(input = message.text, editingMessageId = messageId, drafts = emptyList()) }
    }

    /** 取消编辑：清空输入框并退出编辑状态。 */
    fun cancelEdit() {
        state.update { it.copy(input = "", editingMessageId = null) }
    }

    fun dismissError() {
        state.update { it.copy(error = null) }
    }

    /**
     * 放进草稿托盘。
     *
     * 图片在这里有第二道数量闸：选择器的 `maxItems` 只是**建议**（厂商 ROM 的选择器会不认），
     * 而这条路上没有别的地方挡得住——服务端确实按 9 裁（`SendBody.attachments`），但那发生在
     * 附件**已经传完**之后，用户先把流量花完再吃一个 422。上限与服务端同源，
     * 见 [com.openapi.common.media.MAX_MESSAGE_IMAGES]。
     *
     * 返回 false 表示「因为到顶了所以没放进去」：这里不写 [ChatUiState.error]，
     * 因为文案归 [ChatStrings] 端口，而端口在调用方（[SendActions]）手里——
     * 与 `attachmentPicked` 把 `StageOutcome.Rejected` 翻成错误提示是同一个分工。
     */
    fun addDraft(kind: MessageKind, staged: StagedAttachment): Boolean {
        if (kind == MessageKind.IMAGE &&
            state.value.drafts.count { it.kind == MessageKind.IMAGE } >= com.openapi.common.media.MAX_MESSAGE_IMAGES
        ) {
            return false
        }
        val draft = DraftAttachment(id = "draft-${System.nanoTime()}", kind = kind, staged = staged)
        state.update { it.copy(drafts = it.drafts + draft) }
        return true
    }

    fun removeDraft(id: String) {
        state.update { it.copy(drafts = it.drafts.filterNot { d -> d.id == id }) }
    }

    /**
     * 放进待发送位置（P2-13）。单值而不是列表：一条消息只带一个坐标，
     * 再点一次「位置」就是换掉上一个（微信同款行为），不堆积。
     */
    fun setLocationDraft(location: com.openapi.feature.chat.domain.model.GeoLocation) {
        state.update { it.copy(locationDraft = location) }
    }

    fun removeLocationDraft() {
        state.update { it.copy(locationDraft = null) }
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
