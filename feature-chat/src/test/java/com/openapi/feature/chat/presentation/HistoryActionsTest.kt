package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.DeliveryStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryActionsTest {

    private class Harness(messages: List<ChatMessage> = emptyList(), hasMore: Boolean = true, inFlight: Set<String> = emptySet()) {
        val repo = FakeChatRepository()
        val state = MutableStateFlow(ChatUiState(messages = messages, hasMoreHistory = hasMore))
        val scope = TestScope(StandardTestDispatcher())
        val pendingDeletions = PendingDeletions()
        val history = HistoryActions(
            repository = repo,
            state = state,
            scope = scope,
            isInFlight = { it in inFlight },
            strings = FakeChatStrings(),
            io = StandardTestDispatcher(scope.testScheduler),
            pendingDeletions = pendingDeletions,
        )
    }

    private fun msg(id: String, at: Long, role: ChatRole = ChatRole.USER, delivery: DeliveryStatus = DeliveryStatus.SENT) =
        ChatMessage(id = id, role = role, text = "t$id", createdAtMillis = at, delivery = delivery)

    @Test
    fun loadOlderPrependsThePageAndUpdatesHasMore() {
        val h = Harness(messages = listOf(msg("20", 20L)))
        h.repo.olderMessages = listOf(msg("10", 10L), msg("15", 15L))
        h.repo.olderHasMore = false
        h.history.loadOlder()
        h.scope.advanceUntilIdle()
        assertEquals(listOf("10", "15", "20"), h.state.value.messages.map { it.id })
        assertFalse(h.state.value.hasMoreHistory)
        assertFalse(h.state.value.loadingOlder)
    }

    @Test
    fun loadOlderDoesNothingWhenThereIsNoMoreHistory() {
        val h = Harness(messages = listOf(msg("20", 20L)), hasMore = false)
        h.history.loadOlder()
        h.scope.advanceUntilIdle()
        assertFalse(h.state.value.loadingOlder)
        assertEquals(listOf("20"), h.state.value.messages.map { it.id })
    }

    @Test
    fun loadOlderFailureShowsAnErrorAndStopsTheSpinner() {
        val h = Harness(messages = listOf(msg("20", 20L)))
        h.repo.historyFails = true
        h.history.loadOlder()
        h.scope.advanceUntilIdle()
        assertFalse(h.state.value.loadingOlder)
        assertNotNull(h.state.value.error)
        assertEquals(listOf("20"), h.state.value.messages.map { it.id })
    }

    @Test
    fun pendingMessageThatIsNotInFlightBecomesFailedAndIsSaved() {
        val h = Harness()
        h.repo.pending = listOf(msg("local-1", 5L, delivery = DeliveryStatus.SENDING))
        val merged = runBlocking { h.history.mergePending(emptyList(), sessionId = 1L) }
        assertEquals(DeliveryStatus.FAILED, merged.single().delivery)
        assertEquals(DeliveryStatus.FAILED, h.repo.saved.single().delivery)
    }

    @Test
    fun pendingMessageInFlightIsKeptAsSending() {
        val h = Harness(inFlight = setOf("local-1"))
        h.repo.pending = listOf(msg("local-1", 5L, delivery = DeliveryStatus.SENDING))
        val merged = runBlocking { h.history.mergePending(emptyList(), sessionId = 1L) }
        assertEquals(DeliveryStatus.SENDING, merged.single().delivery)
        assertTrue(h.repo.saved.isEmpty())
    }

    @Test
    fun pendingMessagesAlreadyOnTheServerAreNotDuplicatedAndResultIsSorted() {
        val h = Harness()
        h.repo.pending = listOf(msg("local-2", 30L, delivery = DeliveryStatus.SENT), msg("srv-1", 10L))
        val server = listOf(msg("srv-1", 10L), msg("srv-3", 20L))
        val merged = runBlocking { h.history.mergePending(server, sessionId = 1L) }
        assertEquals(listOf("srv-1", "srv-3", "local-2"), merged.map { it.id })
    }

    // ---------------------------------------------------- 乐观删除不能被历史复活（任务 3）

    /** 墓碑挡 id：兜底合并（refresh 的路径）不能把「正在等确认删除」的条目带回来。 */
    @Test
    fun mergePendingDropsMessagesPendingDeletion() {
        val h = Harness()
        h.pendingDeletions.mark(msg("20", 20L))
        val merged = runBlocking { h.history.mergePending(listOf(msg("20", 20L), msg("21", 21L)), sessionId = 1L) }
        assertEquals(listOf("21"), merged.map { it.id })
    }

    /** loadOlder 那一页同理：刚删过的条目不能在翻页时从服务端历史里回来。 */
    @Test
    fun loadOlderDoesNotRevivePendingDeletions() {
        val h = Harness(messages = listOf(msg("20", 20L)))
        h.repo.olderMessages = listOf(msg("10", 10L), msg("15", 15L))
        h.pendingDeletions.mark(msg("15", 15L))
        h.history.loadOlder()
        h.scope.advanceUntilIdle()
        assertEquals(listOf("10", "20"), h.state.value.messages.map { it.id })
    }

    // ---------------------------------------------------- 生成中允许读历史（任务 4）

    /**
     * 旧闸把 `sending` 也算进 loadOlder：生成中翻到顶，这次请求被**静默吞掉**。
     * 读历史是只读操作，与当前回合无关——现在必须照常加载。
     */
    @Test
    fun loadOlderWorksWhileASendIsInFlight() {
        val h = Harness(messages = listOf(msg("20", 20L)))
        h.state.value = h.state.value.copy(sending = true)
        h.repo.olderMessages = listOf(msg("10", 10L))
        h.history.loadOlder()
        h.scope.advanceUntilIdle()
        assertEquals(listOf("10", "20"), h.state.value.messages.map { it.id })
    }
}
