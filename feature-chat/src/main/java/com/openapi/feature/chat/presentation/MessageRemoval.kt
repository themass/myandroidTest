package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 删除消息：单条、多选、清空会话。从 ChatViewModel 拆出来。
 *
 * **乐观删除**（本次改动）：点击之后消息**立刻**从界面和本地缓存里消失，服务端删除在后台补。
 * 旧顺序是「服务端全删完才动界面」——批量删除要串行等 N 个 HTTP 往返，界面全程像没反应，
 * 用户接着再点一次（这是「多选删不掉、删了又回来」投诉的成因之一）。
 * 现在的代价说清楚：服务端删除失败时，消息会**放回原来的位置**并报错条——不是永久删错，
 * 界面上会出现一次「回去又回来」，比点一下没反应轻。
 *
 * [pending] 是墓碑集合（与 ViewModel 的历史合并路径共享）：挂起期间任何一次
 * 「用服务端历史重建列表」都不能把删掉的条目带回来，见 [PendingDeletions]。
 * 乐观时序下**不再需要删完对齐一次**——移除是本地先做的，失败回滚用的是原位表，
 * 旧的 `reconcile` 回调因此删掉了（它只会在服务端删除成功后又拉一整页回来，多一次往返还
 * 制造复活窗口）。
 *
 * 没有服务端 id 的本地消息（`local-` 前缀）：本来就只存在本机，直接移除并挂墓碑，
 * 服务端历史里的同一条（若已经落库）在窗口内也被藏掉。
 */
internal class MessageRemoval(
    private val repository: ChatRepository,
    private val state: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    private val strings: ChatStrings,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** 墓碑：与 ViewModel 的历史合并路径共享同一个实例。 */
    val pending: PendingDeletions = PendingDeletions(),
) {

    private suspend fun deleteOnServer(messageId: String): Boolean =
        repository.deleteMessage(repository.currentSessionId(), messageId)

    /**
     * 乐观移除：记下原位（index + 整条消息），界面/缓存立即删掉。
     * 返回原位表供失败回滚。
     */
    private fun removeOptimistically(ids: List<String>): List<Pair<Int, com.openapi.feature.chat.domain.model.ChatMessage>> {
        val snapshot = state.value.messages
        val placed = ids.mapNotNull { id ->
            val index = snapshot.indexOfFirst { it.id == id }
            if (index < 0) null else index to snapshot[index]
        }
        val doomed = placed.map { it.second.id }.toSet()
        placed.forEach { (_, m) -> pending.mark(m) }
        state.update { current -> current.copy(messages = current.messages.filterNot { it.id in doomed }) }
        scope.launch {
            placed.forEach { (_, m) -> repository.deleteLocalMessage(m.id) }
        }
        return placed
    }

    /** 服务端删除失败：把消息放回原位（按记录的 index 逐个插回），并撤销墓碑。 */
    private fun restore(placed: List<Pair<Int, com.openapi.feature.chat.domain.model.ChatMessage>>) {
        placed.forEach { (index, m) -> pending.clear(m.id) }
        state.update { current ->
            val list = current.messages.toMutableList()
            // 按 index 升序插回：先插靠前的，后面的下标才不会因插入而错位
            placed.sortedBy { it.first }.forEach { (index, m) ->
                if (list.none { it.id == m.id }) list.add(index.coerceAtMost(list.size), m)
            }
            current.copy(messages = list)
        }
    }

    fun deleteOne(messageId: String) {
        scope.launch {
            if (messageId.toLongOrNull() == null) {
                // 本地-only：没有服务端可删，墓碑挡住兜底历史把它带回来即可
                removeOptimistically(listOf(messageId))
                return@launch
            }
            val placed = removeOptimistically(listOf(messageId))
            if (!deleteOnServer(messageId)) {
                restore(placed)
                state.update { it.copy(error = strings.deleteFailed()) }
            } else {
                pending.clear(messageId)
            }
        }
    }

    fun deleteMany(messageIds: List<String>) {
        scope.launch {
            // 一次性整批移除：多选要立刻消失，不能等第一条的服务端往返
            val placed = removeOptimistically(messageIds)
            val failed = mutableListOf<Pair<Int, com.openapi.feature.chat.domain.model.ChatMessage>>()
            for (entry in placed) {
                val m = entry.second
                // 本地-only（`local-`）没有服务端可删：删了就是删了，不参与失败回滚
                if (m.id.toLongOrNull() == null) continue
                if (deleteOnServer(m.id)) pending.clear(m.id) else failed += entry
            }
            if (failed.isNotEmpty()) {
                restore(failed)
                state.update { it.copy(error = strings.deleteManyPartFailed(failed.size)) }
            }
        }
    }

    /**
     * 清空当前会话的消息，保留会话本身。优先走服务端的一键清空接口
     * （`DELETE /sessions/{id}/messages`，整改清单 P1-9）；接口不可用（旧服务端）时
     * 回退到旧路径：先分页拉出全部消息 id，再逐条删除，任一条失败会保留它并提示数量。
     */
    fun clearConversation() {
        scope.launch {
            val sessionId = repository.currentSessionId()
            if (repository.clearHistory(sessionId)) {
                // 服务端已清空：本地缓存里的助手回复也要清掉，否则刷新时会被读回来
                repository.clearLocalMessages(sessionId)
                state.update { it.copy(messages = emptyList(), hasMoreHistory = false) }
                return@launch
            }
            val ids = mutableListOf<String>()
            try {
                // 按最旧的 id 一直往前翻，直到一页都没有新消息为止（不依赖 hasMore，服务端可能不可靠）
                var beforeId: Long? = null
                val seen = HashSet<String>()
                while (true) {
                    val page = withContext(io) { repository.loadHistoryPage(sessionId, beforeId, 50) }
                    val fresh = page.messages.filter { seen.add(it.id) }
                    if (fresh.isEmpty()) break
                    ids += fresh.map { it.id }
                    val oldest = fresh.mapNotNull { it.id.toLongOrNull() }.minOrNull() ?: break
                    beforeId = oldest
                }
            } catch (e: Exception) {
                state.update { it.copy(error = strings.readMessagesFailed()) }
                return@launch
            }
            // 清空走服务端逐条（一键接口不可用的回退路径）：这里**不**乐观移除整个列表——
            // id 清单本身要先分页拉（服务端权威），删失败的那几条必须留在界面上（clearFailed 的
            // 语义就是「还留着 N 条」），与单条/多选的乐观时序不同。
            val removed = mutableListOf<String>()
            var failed = 0
            for (id in ids) {
                if (id.toLongOrNull() == null) {
                    removed += id
                } else if (deleteOnServer(id)) {
                    removed += id
                } else {
                    failed++
                }
            }
            if (failed == 0) {
                // 服务端全部删除成功：本地缓存里的助手回复也要清掉，否则刷新时会被读回来
                repository.clearLocalMessages(sessionId)
                state.update { it.copy(messages = emptyList(), hasMoreHistory = false) }
            } else {
                state.update { it.copy(messages = it.messages.filterNot { m -> m.id in removed }) }
                state.update { it.copy(error = strings.clearFailed(failed)) }
            }
        }
    }
}
