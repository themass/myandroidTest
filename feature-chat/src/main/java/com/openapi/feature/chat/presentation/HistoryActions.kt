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
    private val strings: ChatStrings,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /**
     * 乐观删除的墓碑集合（与 [MessageRemoval] 共享同一实例）：历史里「正在等服务端确认删除」
     * 的条目不能被重新画出来（[loadOlder] 与 [mergePending] 两个入口都过一遍）。
     */
    private val pendingDeletions: PendingDeletions = PendingDeletions(),
) {

    private val loadHistory = LoadHistoryUseCase(repository)

    /**
     * 加载更早的一页（按最早一条的服务端 id 作为 beforeId）。
     *
     * 闸只认「没有更多」和「已经在加载」：**不再看 `sending`**——读历史是只读操作，
     * 与当前回合互不相干；旧代码在生成中静默吞掉这次请求，用户翻到顶只会看到
     * 「没有反应」（任务 4 移除了手动按钮之后，自动加载更容易被这条误伤）。
     * [ChatTurnGate] 的 `allowLoadOlder` 只挡 loadingOlder/hasMore 两项，与此一致。
     */
    fun loadOlder() {
        val current = state.value
        if (!current.hasMoreHistory || current.loadingOlder) return
        val oldestId = current.messages.mapNotNull { it.id.toLongOrNull() }.minOrNull() ?: return
        state.update { it.copy(loadingOlder = true) }
        scope.launch {
            val page = runCatching { loadHistory(beforeId = oldestId) }.getOrNull()
            state.update { cur ->
                if (page == null) {
                    cur.copy(loadingOlder = false, error = strings.historyLoadOlderFailed())
                } else {
                    val fresh = pendingDeletions.filter(page.messages)
                    val known = fresh.map { it.id }.toSet()
                    cur.copy(
                        messages = fresh + cur.messages.filterNot { it.id in known },
                        hasMoreHistory = page.hasMore,
                        loadingOlder = false,
                    )
                }
            }
        }
    }

    /**
     * 服务端历史和本地未送达的消息合并：本地「发送中」但不在途中的，说明上次没发完，标成失败（可重试）。
     *
     * 乐观删除挂起的条目（[pending]）在这里被剔掉：兜底对齐不能把刚删掉的消息带回来。
     */
    suspend fun mergePending(page: List<ChatMessage>, sessionId: Long): List<ChatMessage> {
        val server = pendingDeletions.filter(page)
        val serverIds = server.map { it.id }.toSet()
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
        return (server + recovered).sortedBy { it.createdAtMillis }
    }
}
