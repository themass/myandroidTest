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
        val history = HistoryActions(
            repository = repo,
            state = state,
            scope = scope,
            isInFlight = { it in inFlight },
            io = StandardTestDispatcher(scope.testScheduler),
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
}
