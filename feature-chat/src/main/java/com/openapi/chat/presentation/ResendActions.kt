package com.openapi.chat.presentation

import com.openapi.chat.domain.model.ChatRole
import com.openapi.chat.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 重试失败的消息、重新生成回复、提交编辑。从 ChatViewModel 拆出来。
 * [startAttachments] 由 ViewModel 实现：它要把附件任务记到自己的 attachmentJob 里，停止按钮才能取消它。
 * [refresh] 编辑失败时重新拉一次当前会话。
 */
internal class ResendActions(
    private val repository: ChatRepository,
    private val state: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    private val streamer: ReplyStreamer,
    private val startAttachments: (com.openapi.chat.domain.model.ChatMessage) -> Unit,
    private val refresh: () -> Unit,
) {

    /** 重新发送一条失败的用户消息（不重新插入气泡，也不重复保存）。 */
    fun retry(messageId: String) {
        val snapshot = state.value
        val message = snapshot.messages.firstOrNull { it.id == messageId } ?: return
        if (snapshot.sending || message.role != ChatRole.USER) return
        if (message.images.isNotEmpty() || message.files.isNotEmpty()) {
            startAttachments(message)
        } else if (message.text.isNotBlank()) {
            streamer.stream(message.text, userMessageId = message.id, persistUser = false)
        }
    }

    /**
     * 重新生成：与服务端一致，去掉最后一条用户消息之后的所有助手回复，然后重新回答最后一条用户消息。
     * 只对最后一轮有意义；[assistantId] 仅用于定位，实际删除范围由最后一条用户消息决定。
     */
    fun regenerate(assistantId: String) {
        val snapshot = state.value
        if (snapshot.sending) return
        val lastUserIndex = snapshot.messages.indexOfLast { it.role == ChatRole.USER }
        if (lastUserIndex < 0) return
        val lastUser = snapshot.messages[lastUserIndex]
        val lastReply = snapshot.messages.lastOrNull()?.takeIf { it.role == ChatRole.ASSISTANT }
        if (lastUser.images.isNotEmpty() || lastUser.files.isNotEmpty()) {
            // 带附件的消息不走 regenerate（附件不在服务端历史里），按原样重新上传：这种情况仍然截断后重发
            state.update { it.copy(messages = snapshot.messages.subList(0, lastUserIndex + 1).toList()) }
            startAttachments(lastUser)
        } else if (lastReply != null) {
            // 原位替换：最新回复的气泡留在原处，新内容流进同一个气泡
            streamer.stream(text = null, userMessageId = null, persistUser = false, regenerate = true, replaceKey = lastReply.key)
        } else {
            state.update { it.copy(messages = snapshot.messages.subList(0, lastUserIndex + 1).toList()) }
            streamer.stream(text = null, userMessageId = null, persistUser = false, regenerate = true)
        }
    }

    /**
     * 提交编辑：服务端改写这条用户消息，并删除它之后的所有消息；本地同步丢弃后续气泡，
     * 然后用 regenerate 重新回答。
     */
    fun submitEdit(messageId: String, text: String) {
        val snapshot = state.value
        val index = snapshot.messages.indexOfFirst { it.id == messageId }
        if (index < 0) {
            state.update { it.copy(editingMessageId = null) }
            return
        }
        if (messageId.toLongOrNull() == null) {
            state.update { it.copy(error = "这条消息还没同步到服务端，稍后再编辑") }
            return
        }
        val edited = snapshot.messages[index].copy(text = text)
        state.update {
            it.copy(
                messages = snapshot.messages.subList(0, index).toList() + edited,
                input = "",
                editingMessageId = null,
                error = null,
            )
        }
        scope.launch {
            val ok = repository.editMessage(snapshot.sessionId, messageId, text)
            if (!ok) {
                state.update { it.copy(error = "编辑失败，请稍后重试") }
                refresh()
                return@launch
            }
            streamer.stream(text = null, userMessageId = null, persistUser = false, regenerate = true)
        }
    }
}
