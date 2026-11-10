package com.openapi.chat.presentation

import com.openapi.chat.domain.model.ChatSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionActionsTest {

    private class Harness(initialSession: Long = 1L) {
        val repo = FakeChatRepository()
        val state = MutableStateFlow(ChatUiState(sessionId = initialSession, sessionsVisible = true))
        val scope = TestScope(StandardTestDispatcher())
        var refreshes = 0
        val actions = SessionActions(
            repository = repo,
            state = state,
            scope = scope,
            refresh = { refreshes++ },
            io = StandardTestDispatcher(scope.testScheduler),
        )
    }

    @Test
    fun selectingTheSameSessionOnlyClosesTheList() {
        val h = Harness(initialSession = 5L)
        h.actions.select(5L)
        assertFalse(h.state.value.sessionsVisible)
        assertTrue(h.repo.setCurrentCalls.isEmpty())
        assertEquals(0, h.refreshes)
    }

    @Test
    fun selectingAnotherSessionSwitchesAndRefreshes() {
        val h = Harness(initialSession = 1L)
        h.actions.select(7L)
        assertEquals(listOf(7L), h.repo.setCurrentCalls)
        assertFalse(h.state.value.sessionsVisible)
        assertEquals(1, h.refreshes)
    }

    @Test
    fun createFailureShowsAnError() {
        val h = Harness()
        h.repo.nextCreated = null
        h.actions.create("新会话")
        h.scope.advanceUntilIdle()
        assertNotNull(h.state.value.error)
        assertEquals(0, h.refreshes)
    }

    @Test
    fun createSuccessSwitchesToTheNewSession() {
        val h = Harness()
        h.repo.nextCreated = ChatSession(9L, "新会话", isDefault = false)
        h.actions.create("新会话")
        h.scope.advanceUntilIdle()
        assertEquals(listOf(9L), h.repo.setCurrentCalls)
        assertFalse(h.state.value.sessionsVisible)
        assertEquals(1, h.refreshes)
    }

    @Test
    fun deleteFailureShowsAnErrorAndDoesNotRefresh() {
        val h = Harness()
        h.repo.deleteOk = false
        h.actions.delete(3L)
        h.scope.advanceUntilIdle()
        assertEquals(listOf(3L), h.repo.deleted)
        assertNotNull(h.state.value.error)
        assertEquals(0, h.refreshes)
    }

    @Test
    fun renameFailureShowsAnErrorAndDoesNotReloadTheList() {
        val h = Harness()
        h.repo.renameResult = null
        h.actions.rename(2L, "改名")
        h.scope.advanceUntilIdle()
        assertEquals(listOf(2L to "改名"), h.repo.renamed)
        assertNotNull(h.state.value.error)
    }

    @Test
    fun renameSuccessReloadsTheSessionList() {
        val h = Harness()
        h.repo.renameResult = ChatSession(2L, "改名", isDefault = false)
        h.repo.sessionList = listOf(ChatSession(2L, "改名", isDefault = false))
        h.actions.rename(2L, "改名")
        h.scope.advanceUntilIdle()
        assertEquals("改名", h.state.value.sessions.single().name)
        assertNull(h.state.value.error)
    }
}
