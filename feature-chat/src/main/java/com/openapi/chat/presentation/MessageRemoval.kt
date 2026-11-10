package com.openapi.chat.presentation

import com.openapi.chat.domain.repository.ChatRepository
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
 * 规则：只有服务端确认删除成功才从界面移除；没有服务端 id 的本地消息只能在没有服务端对应时直接移除。
 * [reconcile] 是删完之后用服务端历史对齐一次的回调（ChatViewModel 的 fallbackReloadMessages）。
 */
internal class MessageRemoval(
    private val repository: ChatRepository,
    private val state: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    private val reconcile: suspend () -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private suspend fun deleteOnServer(messageId: String): Boolean =
        repository.deleteMessage(repository.currentSessionId(), messageId)

    fun deleteOne(messageId: String) {
        scope.launch {
            if (messageId.toLongOrNull() == null) {
                state.update { it.copy(messages = it.messages.filterNot { m -> m.id == messageId }) }
                return@launch
            }
            if (deleteOnServer(messageId)) {
                state.update { it.copy(messages = it.messages.filterNot { m -> m.id == messageId }) }
            } else {
                state.update { it.copy(error = "删除失败，请稍后重试") }
            }
        }
    }

    fun deleteMany(messageIds: List<String>) {
        scope.launch {
            val removed = mutableListOf<String>()
            var failed = 0
            var unsynced = 0
            for (id in messageIds) {
                // 本地临时 id 还没有服务端 id：删不了服务端，也不能只删本地，留在列表里
                if (id.toLongOrNull() == null) {
                    unsynced++
                    continue
                }
                if (deleteOnServer(id)) removed += id else failed++
            }
            state.update { it.copy(messages = it.messages.filterNot { m -> m.id in removed }) }
            // 最后以服务端为准对齐一次：删失败的、没删掉的不会在刷新后「复活」
            reconcile()
            if (failed + unsynced > 0) {
                val parts = buildList {
                    if (failed > 0) add("$failed 条删除失败")
                    if (unsynced > 0) add("$unsynced 条尚未同步，请稍后再删")
                }
                state.update { it.copy(error = parts.joinToString("，")) }
            }
        }
    }

    /**
     * 清空当前会话的消息，保留会话本身。服务端没有「清空消息」接口：先分页拉出全部消息 id，再逐条删除。
     * 任一条失败会保留它并提示数量。
     */
    fun clearConversation() {
        scope.launch {
            val sessionId = repository.currentSessionId()
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
                state.update { it.copy(error = "读取消息失败，请稍后重试") }
                return@launch
            }
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
                state.update { it.copy(error = "有 $failed 条消息清空失败，请稍后重试") }
            }
        }
    }
}
