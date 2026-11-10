package com.openapi.chat.presentation

import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessageRemovalTest {

    private class Harness(messages: List<ChatMessage>) {
        val repo = FakeChatRepository()
        val state = MutableStateFlow(ChatUiState(messages = messages))
        val scope = TestScope(StandardTestDispatcher())
        var reconciles = 0
        val removal = MessageRemoval(
            repository = repo,
            state = state,
            scope = scope,
            reconcile = { reconciles++ },
            io = StandardTestDispatcher(scope.testScheduler),
        )
    }

    private fun msg(id: String, text: String = "消息$id") =
        ChatMessage(id = id, role = ChatRole.USER, text = text, createdAtMillis = id.hashCode().toLong())

    @Test
    fun serverDeleteSuccessRemovesTheMessage() {
        val h = Harness(listOf(msg("10"), msg("11")))
        h.removal.deleteOne("10")
        h.scope.advanceUntilIdle()
        assertEquals(listOf("11"), h.state.value.messages.map { it.id })
        assertEquals(listOf("10"), h.repo.deletedMessages)
    }

    @Test
    fun serverDeleteFailureKeepsTheMessageAndShowsAnError() {
        val h = Harness(listOf(msg("10")))
        h.repo.deleteMessageOk = false
        h.removal.deleteOne("10")
        h.scope.advanceUntilIdle()
        assertEquals(listOf("10"), h.state.value.messages.map { it.id })
        assertNotNull(h.state.value.error)
    }

    @Test
    fun localOnlyMessageIsRemovedWithoutAServerCall() {
        val h = Harness(listOf(msg("local-1")))
        h.removal.deleteOne("local-1")
        h.scope.advanceUntilIdle()
        assertTrue(h.state.value.messages.isEmpty())
        assertTrue(h.repo.deletedMessages.isEmpty())
    }

    @Test
    fun deleteManyReportsUnsyncedMessagesAndReconciles() {
        val h = Harness(listOf(msg("10"), msg("local-2"), msg("12")))
        h.removal.deleteMany(listOf("10", "local-2", "12"))
        h.scope.advanceUntilIdle()
        // 本地临时消息不删服务端，留在列表里并提示
        assertEquals(listOf("local-2"), h.state.value.messages.map { it.id })
        assertEquals(listOf("10", "12"), h.repo.deletedMessages)
        assertEquals("1 条尚未同步，请稍后再删", h.state.value.error)
        assertEquals(1, h.reconciles)
    }

    @Test
    fun clearConversationRemovesEverythingAndClearsTheLocalCache() {
        val h = Harness(listOf(msg("10"), msg("11")))
        h.repo.history = listOf(msg("10"), msg("11"))
        h.removal.clearConversation()
        h.scope.advanceUntilIdle()
        assertTrue(h.state.value.messages.isEmpty())
        assertNull(h.state.value.error)
        assertEquals(1, h.repo.clearedLocal)
    }

    @Test
    fun clearConversationWithFailuresKeepsTheFailedOnesAndCountsThem() {
        val h = Harness(listOf(msg("10"), msg("11")))
        h.repo.history = listOf(msg("10"), msg("11"))
        h.repo.deleteMessageOk = false
        h.removal.clearConversation()
        h.scope.advanceUntilIdle()
        assertEquals(setOf("10", "11"), h.state.value.messages.map { it.id }.toSet())
        assertEquals("有 2 条消息清空失败，请稍后重试", h.state.value.error)
        assertEquals(0, h.repo.clearedLocal)
    }
}
