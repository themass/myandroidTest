package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.repository.ChatRepository
import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.usecase.LoadHistoryUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 历史消息：加载更早的一页，以及把本地还没送达的消息合回列表。从 ChatViewModel 拆出来。
 *
 * [isInFlight] 告诉这里哪些消息还在发送途中（这些不能被当成「发送失败」改掉）。
 */
internal class HistoryActions(
    private val repository: ChatRepository,
    private val state: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    private val isInFlight: (String) -> Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private val loadHistory = LoadHistoryUseCase(repository)

    /** 加载更早的一页（按最早一条的服务端 id 作为 beforeId）。 */
    fun loadOlder() {
        val current = state.value
        if (!current.hasMoreHistory || current.loadingOlder || current.sending) return
        val oldestId = current.messages.mapNotNull { it.id.toLongOrNull() }.minOrNull() ?: return
        state.update { it.copy(loadingOlder = true) }
        scope.launch {
            val page = runCatching { loadHistory(beforeId = oldestId) }.getOrNull()
            state.update { cur ->
                if (page == null) {
                    cur.copy(loadingOlder = false, error = "加载更早的消息失败，请稍后重试")
                } else {
                    val known = page.messages.map { it.id }.toSet()
                    cur.copy(
                        messages = page.messages + cur.messages.filterNot { it.id in known },
                        hasMoreHistory = page.hasMore,
                        loadingOlder = false,
                    )
                }
            }
        }
    }

    /**
     * 服务端历史和本地未送达的消息合并：本地「发送中」但不在途中的，说明上次没发完，标成失败（可重试）。
     */
    suspend fun mergePending(page: List<ChatMessage>, sessionId: Long): List<ChatMessage> {
        val serverIds = page.map { it.id }.toSet()
        val recovered = repository.localPendingMessages(sessionId)
            .filter { it.id !in serverIds }
            .map { m ->
                if (m.delivery == DeliveryStatus.SENDING && !isInFlight(m.id)) {
                    val failed = m.copy(delivery = DeliveryStatus.FAILED)
                    repository.saveMessage(sessionId, failed)
                    failed
                } else {
                    m
                }
            }
        return (page + recovered).sortedBy { it.createdAtMillis }
    }
}
