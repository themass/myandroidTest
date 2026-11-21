package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 重试失败的消息、重新生成回复、提交编辑。从 ChatViewModel 拆出来。
 * [startAttachments] 由 ViewModel 实现：它要把附件任务记到自己的 attachmentJob 里，停止按钮才能取消它。
 */
internal class ResendActions(
    private val repository: ChatRepository,
    private val state: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    private val streamer: ReplyStreamer,
    private val startAttachments: (com.openapi.feature.chat.domain.model.ChatMessage) -> Unit,
) {

    /** 重新发送一条失败的用户消息（不重新插入气泡，也不重复保存）。 */
    fun retry(messageId: String) {
        val snapshot = state.value
        val message = snapshot.messages.firstOrNull { it.id == messageId } ?: return
        if (snapshot.sending || message.role != ChatRole.USER) return
        // 视频走同一条附件通道；位置没有附件但同样是「一条消息一个回合」的富内容，
        // 由 AttachmentSender 直接把这个回合发出（它认 message.location）。
        if (message.images.isNotEmpty() || message.files.isNotEmpty() ||
            message.videos.isNotEmpty() || message.location != null
        ) {
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
        // 带图片的消息也走服务端 regenerate：图片引用跟着用户消息存进服务端 meta，
        // 重跑时由服务端重新读图给模型。以前这里「重新上传后整条重发」，
        // 会在服务端多出一条一模一样的用户消息（上一条还留在历史里）。
        // 文件仍走 legacy 的逐条接口，服务端历史里没有文件引用，只能整条重发。
        if (lastUser.files.isNotEmpty()) {
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
     *
     * 顺序很重要：**先问服务端，成功了才动界面**。以前是先乐观改写气泡、清空输入框，
     * 服务端失败后再 refresh 拉回来——拉回来的是服务端上的**旧文字**，
     * 用户辛苦改的那段字就这么没了（离线时更糟：整屏历史被刷成加载失败，编辑内容无处可寻）。
     * 失败时一切保持原样：编辑横幅还在、输入框里还是改过的文字，直接再点一次发送就是重试。
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
        scope.launch {
            val ok = repository.editMessage(snapshot.sessionId, messageId, text)
            if (!ok) {
                state.update { it.copy(error = "编辑失败，内容已保留，可直接重发") }
                return@launch
            }
            // 服务端已经改写完，才做本地的截断 + 落编辑文字（refresh 会拉到同样的结果，这里先把界面到位）
            state.update { current ->
                val i = current.messages.indexOfFirst { it.id == messageId }.coerceAtLeast(0)
                current.copy(
                    messages = current.messages.subList(0, i).toList() + current.messages[i].copy(text = text),
                    input = "",
                    editingMessageId = null,
                    error = null,
                )
            }
            streamer.stream(text = null, userMessageId = null, persistUser = false, regenerate = true)
        }
    }
}
