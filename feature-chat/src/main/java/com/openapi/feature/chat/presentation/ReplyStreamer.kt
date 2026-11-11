package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.ChatStreamEvent
import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.repository.ChatRepository
import com.openapi.feature.chat.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 把一条消息发给大模型并流式接收回复，负责助手气泡的生命周期、打字机节奏、停止和失败处理。
 * 从 ChatViewModel 拆出来。依赖通过构造参数传入，ViewModel 只做转发。
 */
internal class ReplyStreamer(
    private val repository: ChatRepository,
    private val state: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    private val sendMessage: SendMessageUseCase,
    /** 附件发送是否还在进行中（结束时决定 streaming 标记）。 */
    private val attachmentActive: () -> Boolean,
    /** 兜底：用服务端历史对齐一次。 */
    private val reconcile: suspend () -> Unit,
    /** 新会话出现时同步会话列表。 */
    private val syncSessions: suspend () -> Unit,
) {

    private companion object {
        /** 流式文字的释放节拍：约 25 次/秒。 */
        const val REVEAL_TICK_MS = 40L
    }

    /** 文字回复的流式任务。停止按钮通过 [cancel] 取消它。 */
    private var replyJob: Job? = null

    fun isReplyActive(): Boolean = replyJob?.isActive == true

    /** 停止当前的文字回复。 */
    fun cancel() {
        replyJob?.cancel()
    }

    /**
     * 把 [text] 发给大模型并流式接收回复。
     * [userMessageId] 是对应的用户气泡，失败时标成发送失败（可重试）；重新生成时为 null。
     * [persistUser] 为 false 时仓库不再保存用户消息（已经保存过的场景）。
     *
     * 助手气泡的生命周期（只有一条气泡，按 [ChatMessage.key] 定位，id 变化不影响它）：
     * 思考中（pending、无文字）→ 流式（pending、有文字，逐字长出来）→ 完成（pending=false）。
     * 网络收到的文字先放进缓冲区，由一个固定节拍（[REVEAL_TICK_MS]）匀速释放到气泡里，
     * 积压越多释放越快，所以网络一次来一大块也不会整段突然出现。
     */
    fun stream(
        text: String?,
        userMessageId: String?,
        persistUser: Boolean,
        regenerate: Boolean = false,
        syncAfter: Boolean = true,
        /** 重新生成时：复用这条助手消息的气泡（同一个 key、同一个位置），而不是新增一条。 */
        replaceKey: String? = null,
    ) {
        val snapshot = state.value
        // 本轮是否会新建会话（请求时还没有当前会话）：结束后要同步会话列表
        val startsNewSession = repository.currentSessionId() <= 0L
        val existing = replaceKey?.let { key -> snapshot.messages.firstOrNull { it.key == key } }
        val placeholder = existing?.copy(text = "", pending = true) ?: newPlaceholder()
        state.update { current ->
            val messages = if (existing != null) {
                current.messages.map { if (it.key == existing.key) placeholder else it }
            } else {
                current.messages + placeholder
            }
            current.copy(messages = messages, sending = true, streaming = true, error = null)
        }
        userMessageId?.let { setDelivery(it, DeliveryStatus.SENDING) }

        replyJob = scope.launch {
            val buffer = StringBuilder()
            var ended = false
            var failed = false
            var serverId: String? = null
            var serverUserId: String? = null
            var errorUserId: String? = null
            var fullText: String? = null

            val reveal = launch {
                while (true) {
                    kotlinx.coroutines.delay(REVEAL_TICK_MS)
                    if (buffer.isNotEmpty()) {
                        val cut = RevealPacer.safeCut(buffer, RevealPacer.revealCount(buffer.length, ended))
                        val piece = buffer.substring(0, cut)
                        buffer.delete(0, cut)
                        updateByKey(placeholder.key) { it.copy(text = it.text + piece) }
                    } else if (ended) {
                        break
                    }
                }
            }

            try {
                val events = if (regenerate) {
                    repository.regenerateStream(repository.currentSessionId(), snapshot.selectedCharacterId, snapshot.selectedRole?.id)
                } else {
                    sendMessage(
                        text.orEmpty(),
                        snapshot.selectedCharacterId,
                        snapshot.selectedRole?.id,
                        persistUser,
                        clientMessageId = userMessageId,
                    )
                }
                events.collect { event ->
                        when (event) {
                            is ChatStreamEvent.Delta -> buffer.append(event.text)
                            is ChatStreamEvent.Done -> {
                                serverId = event.assistantId
                                serverUserId = event.userId
                                fullText = event.fullText
                            }
                            is ChatStreamEvent.Error -> {
                                failed = true
                                errorUserId = event.userMessageId
                                state.update { it.copy(error = event.message) }
                            }
                        }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户点了停止：保留已经生成的内容，不当作失败
                reveal.cancel()
                val replied = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    finishStopped(placeholder.key, buffer.toString())
                }
                // 停止时一个字都没收到：用户消息标成未发出（可点重试），不当作已发送
                state.update {
                    it.copy(
                        sending = false,
                        streaming = attachmentActive(),
                        error = if (replied) it.error else "已停止，消息未发出，可点重试",
                    )
                }
                userMessageId?.let { setDelivery(it, if (replied) DeliveryStatus.SENT else DeliveryStatus.FAILED) }
                // 服务端会保存已生成的部分，但这里拿不到正式 id：兜底拉一页历史
                if (syncAfter) scope.launch { reconcile() }
                if (startsNewSession) scope.launch { syncSessions() }
                throw e
            } catch (e: Exception) {
                // 记日志：文字发送偶发失败时，能从 logcat 看到真实原因（之前无法复现原因）
                android.util.Log.w("ChatViewModel", "stream reply failed", e)
                failed = true
                state.update { it.copy(error = e.message) }
            }

            ended = true
            reveal.join()

            // 流正常结束但一个字都没有：撤掉占位气泡，给提示，用户消息标成可重试
            val emptyReply = !failed && fullText.isNullOrEmpty() && buffer.isEmpty()
            state.update { current ->
                current.copy(
                    messages = current.messages.mapNotNull { m ->
                        when {
                            // 用户气泡：拿到正式 id 就直接替换（编辑、删除用它）
                            m.id == userMessageId && (serverUserId ?: errorUserId) != null -> m.copy(id = (serverUserId ?: errorUserId)!!)
                            m.key != placeholder.key -> m
                            // 出错或空回复且一个字都没收到：撤掉占位气泡；已经有内容就保留
                            // 重新生成失败：恢复原来的回复，不能把用户已经看到的内容删掉
                            (failed || emptyReply) && m.text.isEmpty() -> existing?.copy(pending = false)
                            else -> m.copy(id = serverId ?: m.id, text = fullText ?: m.text, pending = false)
                        }
                    },
                    sending = false,
                    streaming = attachmentActive(),
                    error = when {
                        emptyReply -> "没有收到回复，请重试"
                        failed -> current.error ?: "回复失败，请重试"
                        else -> current.error
                    },
                )
            }
            // 流结束但没有 done 帧（服务端中途断开、只给了部分文字）：不能当成已完整收到，用户消息标成失败可重试
            val truncated = !failed && !emptyReply && serverId == null && serverUserId == null && userMessageId != null
            if (truncated) {
                state.update { it.copy(error = "回复没有完整收到，请重试") }
            }
            userMessageId?.let { setDelivery(serverUserId ?: errorUserId ?: it, if (failed || emptyReply || truncated) DeliveryStatus.FAILED else DeliveryStatus.SENT) }
            // 正常路径靠 done 帧里的正式 id，不再每轮拉历史；只有缺 id（失败、没有 done 帧、空回复）时兜底拉一页
            if (syncAfter && (failed || serverId == null)) reconcile()
            if (startsNewSession) syncSessions()
        }
    }

    /** 返回是否保留了助手内容（false = 一个字都没收到，占位气泡已撤掉）。 */
    private suspend fun finishStopped(key: String, bufferedText: String): Boolean {
        var kept: ChatMessage? = null
        state.update { current ->
            current.copy(
                messages = current.messages.mapNotNull { m ->
                    if (m.key != key) {
                        m
                    } else {
                        val merged = m.text + bufferedText
                        if (merged.isEmpty()) null else m.copy(text = merged, pending = false).also { kept = it }
                    }
                },
            )
        }
        kept?.let { repository.saveMessage(repository.currentSessionId(), it) }
        return kept != null
    }

    fun updateByKey(key: String, transform: (ChatMessage) -> ChatMessage) {
        state.update { current ->
            current.copy(messages = current.messages.map { if (it.key == key) transform(it) else it })
        }
    }

    fun newPlaceholder(): ChatMessage {
        val now = System.currentTimeMillis()
        return ChatMessage(
            id = "local-pending-$now",
            role = ChatRole.ASSISTANT,
            text = "",
            createdAtMillis = now,
            pending = true,
        )
    }

    fun setDelivery(messageId: String, status: DeliveryStatus) {
        state.update { current ->
            current.copy(messages = current.messages.map { if (it.id == messageId) it.copy(delivery = status) else it })
        }
    }
}
