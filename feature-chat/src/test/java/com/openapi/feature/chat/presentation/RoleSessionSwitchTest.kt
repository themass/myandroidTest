package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatSession
import com.openapi.feature.chat.roles.domain.SelectedRole
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

/**
 * 换角色 = 换会话。每个用户与每个角色是一段独立的对话历史：
 * 增删改查、清空都落在这一个会话上，换到别的角色看不到它，换回来又回到它。
 *
 * 这是线上功能：之前 selectRole 只改了“选中状态”，会话没换，
 * 所以新角色下还显示、还在沿用上一个角色的整段历史。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoleSessionSwitchTest {

    private class Harness(sessions: List<ChatSession> = emptyList(), current: Long = 0L) {
        val repo = FakeChatRepository().apply {
            sessionList = sessions
            this.current = current
        }
        val roles = FakeRolesRepository()
        val state = MutableStateFlow(ChatUiState(sessionId = current))
        val scope = TestScope(StandardTestDispatcher())
        var changed = 0
        val actions = CharacterActions(repo, roles, state, scope, onSessionChanged = { changed++ })
    }

    private val detective = SelectedRole("r-detective", "侦探", "🕵", 0xFF00FF00L)
    private val chef = SelectedRole("r-chef", "厨师", "🍳", 0xFFFF0000L)

    private fun session(id: Long, roleId: String? = null, characterId: Int? = null, name: String = "对话$id") =
        ChatSession(id, name, isDefault = false, roleId = roleId, characterId = characterId)

    @Test
    fun switchingToARoleWithoutASessionCreatesItsOwnAndMakesItCurrent() {
        val h = Harness(listOf(session(1)), current = 1)
        h.actions.selectRole(detective)
        h.scope.advanceUntilIdle()

        assertEquals(listOf(Triple("侦探", "r-detective", null)), h.repo.createdFor)
        assertEquals("新角色必须有自己的会话，不能继续用默认助手的", 100L, h.repo.currentSessionId())
        assertEquals(detective, h.state.value.selectedRole)
        assertEquals(1, h.changed)
    }

    @Test
    fun switchingBackToAnEarlierRoleReturnsToItsExistingSessionAndCreatesNothing() {
        val h = Harness(listOf(session(5, roleId = "r-detective"), session(1)), current = 1)
        h.actions.selectRole(detective)
        h.scope.advanceUntilIdle()

        assertTrue("已有该角色的会话就不能再建", h.repo.createdFor.isEmpty())
        assertEquals(5L, h.repo.currentSessionId())
        assertEquals(1, h.changed)
    }

    @Test
    fun twoRolesNeverShareASession() {
        val h = Harness(listOf(session(1)), current = 1)
        h.actions.selectRole(detective)
        h.scope.advanceUntilIdle()
        val detectiveSession = h.repo.currentSessionId()
        h.actions.selectRole(chef)
        h.scope.advanceUntilIdle()
        val chefSession = h.repo.currentSessionId()
        assertTrue(detectiveSession != chefSession)

        h.actions.selectRole(detective)
        h.scope.advanceUntilIdle()
        assertEquals("切回侦探要回到侦探自己的会话", detectiveSession, h.repo.currentSessionId())
        assertEquals("来回切换不应该重复建会话", 2, h.repo.createdFor.size)
    }

    @Test
    fun whenSeveralSessionsBelongToTheRoleTheMostRecentOneIsUsed() {
        // 会话列表按更新时间倒序，第一个匹配的就是最近用过的
        val h = Harness(listOf(session(9, roleId = "r-detective"), session(4, roleId = "r-detective"), session(1)), current = 1)
        h.actions.selectRole(detective)
        h.scope.advanceUntilIdle()
        assertEquals(9L, h.repo.currentSessionId())
    }

    @Test
    fun theDefaultAssistantHasItsOwnSessionToo() {
        val h = Harness(listOf(session(5, roleId = "r-detective"), session(1)), current = 5)
        h.actions.selectCharacter(null)
        h.scope.advanceUntilIdle()

        assertEquals("选回默认助手要回到没有角色的那个会话", 1L, h.repo.currentSessionId())
        assertNull(h.state.value.selectedRole)
        assertNull(h.state.value.selectedCharacterId)
        assertTrue(h.repo.createdFor.isEmpty())
    }

    @Test
    fun theDefaultAssistantGetsASessionWhenNoneExists() {
        val h = Harness(listOf(session(5, roleId = "r-detective")), current = 5)
        h.actions.selectCharacter(null)
        h.scope.advanceUntilIdle()
        assertEquals(listOf(Triple("新对话", null, null)), h.repo.createdFor)
        assertTrue(h.repo.currentSessionId() != 5L)
    }

    @Test
    fun aCustomCharacterAlsoMapsToItsOwnSession() {
        val h = Harness(listOf(session(7, characterId = 4), session(1)), current = 1)
        h.actions.selectCharacter(4)
        h.scope.advanceUntilIdle()
        assertEquals(7L, h.repo.currentSessionId())
        assertEquals(4, h.state.value.selectedCharacterId)
    }

    @Test
    fun aRoleSessionIsNotMistakenForACharacterSessionWithTheSameNumber() {
        val h = Harness(listOf(session(3, roleId = "4"), session(1)), current = 1)
        h.actions.selectCharacter(4)
        h.scope.advanceUntilIdle()
        assertEquals("roleId=\"4\" 不是 characterId=4", 1, h.repo.createdFor.size)
    }

    @Test
    fun selectingTheRoleAlreadyInUseDoesNotSwitchOrCreateAnything() {
        val h = Harness(listOf(session(5, roleId = "r-detective")), current = 5)
        h.state.value = h.state.value.copy(selectedRole = detective, sessionId = 5)
        h.actions.selectRole(detective)
        h.scope.advanceUntilIdle()
        assertTrue(h.repo.createdFor.isEmpty())
        assertEquals(0, h.repo.setCurrentCalls.size)
        assertEquals(0, h.changed)
    }

    @Test
    fun ifCreatingTheSessionFailsTheOldRoleAndSessionStayAndTheUserIsTold() {
        val h = Harness(listOf(session(1)), current = 1)
        h.repo.createForFails = true
        h.actions.selectRole(detective)
        h.scope.advanceUntilIdle()

        assertEquals("会话没建成就不能显示成已经换了角色", 1L, h.repo.currentSessionId())
        assertNull(h.state.value.selectedRole)
        assertNotNull(h.state.value.error)
        assertEquals(0, h.changed)
        assertFalse(h.state.value.rolePickerVisible)
    }

    @Test
    fun theSessionsOwnRoleIsWrittenBackSoRestartsStayConsistent() {
        val h = Harness(listOf(session(1)), current = 1)
        h.actions.selectRole(detective)
        h.scope.advanceUntilIdle()
        assertEquals(detective, h.roles.selectedFor(100L))
    }
}
