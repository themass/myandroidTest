package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.ChatSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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

    // ---------------------------------------------------------------- 切会话先显示本地缓存

    /** 会话 2 的本地缓存：一条一眼可认的消息。 */
    private fun cachedFor2() = listOf(
        ChatMessage(id = "s2-1", role = ChatRole.ASSISTANT,
            text = "会话2的缓存", createdAtMillis = 1L),
    )

    @Test
    fun switchingShowsTheTargetSessionsCacheBeforeTheServerPage() {
        val h = Harness()
        h.repo.cachedMessages[2L] = cachedFor2()
        h.actions.select(2L)
        h.scope.advanceUntilIdle()
        // refresh 只是计数器（不推 state.sessionId），缓存应当已经顶上
        assertEquals(listOf("s2-1"), h.state.value.messages.map { it.id })
        assertEquals(1, h.refreshes)
    }

    @Test
    fun switchingWithNoCacheKeepsTheCurrentMessages() {
        // 缓存为空就不顶：留着上一个会话的内容，也比闪一下白屏强。
        val h = Harness()
        h.state.value = h.state.value.copy(
            messages = listOf(ChatMessage(id = "s1-1", role = ChatRole.ASSISTANT,
                text = "会话1的旧消息", createdAtMillis = 1L)),
        )
        h.actions.select(2L)
        h.scope.advanceUntilIdle()
        assertEquals(listOf("s1-1"), h.state.value.messages.map { it.id })
    }

    @Test
    fun lateCacheFromAnOlderSwitchIsDiscardedAfterAnotherSwitch() {
        // 切到 2（缓存读盘慢），期间用户又切到 3：迟到的 2 号缓存不能画在 3 号会话上。
        val h = Harness()
        h.repo.cachedMessages[2L] = cachedFor2()
        h.repo.cachedMessagesDelay[2L] = 1_000L
        h.repo.cachedMessages[3L] = listOf(
            ChatMessage(id = "s3-1", role = ChatRole.ASSISTANT,
                text = "会话3的缓存", createdAtMillis = 1L),
        )
        h.actions.select(2L)
        h.scope.advanceTimeBy(100L) // 2 号的缓存还在读盘
        h.actions.select(3L)
        h.scope.advanceUntilIdle()
        assertEquals(listOf("s3-1"), h.state.value.messages.map { it.id })
    }

    @Test
    fun lateCacheIsDiscardedOnceTheServerPageAlreadyDrew() {
        // refresh 完成时把 state.sessionId 推进（ChatViewModel.refresh 的真实行为）。
        // 服务端页先到、慢缓存后到：拿更旧且没有签名 URL 的缓存盖掉新鲜内容，是倒退，必须丢弃。
        val repo = FakeChatRepository()
        val scope = TestScope(StandardTestDispatcher())
        val state = MutableStateFlow(ChatUiState(sessionId = 1L, sessionsVisible = true))
        repo.cachedMessages[2L] = cachedFor2()
        repo.cachedMessagesDelay[2L] = 1_000L
        val actions = SessionActions(
            repository = repo,
            state = state,
            scope = scope,
            // 模拟 ChatViewModel.refresh：快，且结束时推进 state.sessionId
            refresh = { state.update { it.copy(sessionId = repo.currentSessionId()) } },
            io = StandardTestDispatcher(scope.testScheduler),
        )
        actions.select(2L)
        scope.advanceUntilIdle()
        assertEquals(emptyList<String>(), state.value.messages.map { it.id })
    }
}
