package com.openapi.chat.presentation

import com.openapi.roles.domain.SelectedRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
class CharacterActionsTest {

    private class Harness(selectedCharacter: Int? = null) {
        val repo = FakeChatRepository().apply { this.selectedCharacter = selectedCharacter }
        val roles = FakeRolesRepository()
        val state = MutableStateFlow(ChatUiState(selectedCharacterId = selectedCharacter))
        val scope = TestScope(StandardTestDispatcher())
        val actions = CharacterActions(repo, roles, state, scope)
    }

    private val role = SelectedRole(id = "r1", name = "侦探", emoji = "🕵", colorArgb = 0xFF00FF00L)

    @Test
    fun selectingACharacterClearsTheDirectoryRoleAndClosesThePicker() {
        val h = Harness()
        h.roles.selectedRole = role
        h.state.update { it.copy(rolePickerVisible = true, selectedRole = role) }
        h.actions.selectCharacter(4)
        assertEquals(4, h.state.value.selectedCharacterId)
        assertNull(h.state.value.selectedRole)
        assertFalse(h.state.value.rolePickerVisible)
        assertNull(h.roles.selectedRole)
        assertEquals(4, h.repo.selectedCharacter)
    }

    @Test
    fun selectingARoleClearsTheOldCharacter() {
        val h = Harness(selectedCharacter = 4)
        h.actions.selectRole(role)
        assertEquals(role, h.state.value.selectedRole)
        assertNull(h.state.value.selectedCharacterId)
        assertEquals(role, h.roles.selectedRole)
        assertNull(h.repo.selectedCharacter)
    }

    @Test
    fun createFailureShowsAnErrorAndAddsNothing() {
        val h = Harness()
        h.repo.createdCharacter = null
        h.actions.create("新角色", "正文")
        h.scope.advanceUntilIdle()
        assertNotNull(h.state.value.error)
        assertTrue(h.state.value.characters.isEmpty())
    }

    @Test
    fun createSuccessAddsTheCharacterAndSelectsIt() {
        val h = Harness()
        h.repo.createdCharacter = character(7, title = "新角色")
        h.actions.create("新角色", "正文")
        h.scope.advanceUntilIdle()
        assertEquals(listOf(7), h.state.value.characters.map { it.id })
        assertEquals(7, h.state.value.selectedCharacterId)
        assertEquals(7, h.repo.selectedCharacter)
    }

    @Test
    fun updateFailureShowsAnError() {
        val h = Harness()
        h.state.update { it.copy(characters = listOf(character(3))) }
        h.repo.updateCharacterOk = false
        h.actions.update(3, "改名", "新正文")
        h.scope.advanceUntilIdle()
        assertNotNull(h.state.value.error)
        assertEquals("角色3", h.state.value.characters.single().title)
    }

    @Test
    fun updateKeepsTheFullContentNotOnlyThePreview() {
        val h = Harness()
        h.state.update { it.copy(characters = listOf(character(3))) }
        val longText = "很长的人设正文".repeat(20)
        h.actions.update(3, "改名", longText)
        h.scope.advanceUntilIdle()
        val updated = h.state.value.characters.single()
        assertEquals("改名", updated.title)
        assertEquals(longText, updated.content) // 编辑框回填要用完整正文，不能用截断的 preview
        assertEquals(40, updated.preview.length)
    }

    @Test
    fun deletingTheSelectedCharacterFallsBackToTheDefault() {
        val h = Harness(selectedCharacter = 5)
        h.state.update { it.copy(characters = listOf(character(5), character(6)), selectedCharacterId = 5) }
        h.actions.delete(5)
        h.scope.advanceUntilIdle()
        assertEquals(listOf(6), h.state.value.characters.map { it.id })
        assertNull(h.state.value.selectedCharacterId)
        assertNull(h.repo.selectedCharacter)
    }

    @Test
    fun deletingAnotherCharacterKeepsTheSelection() {
        val h = Harness(selectedCharacter = 5)
        h.state.update { it.copy(characters = listOf(character(5), character(6)), selectedCharacterId = 5) }
        h.actions.delete(6)
        h.scope.advanceUntilIdle()
        assertEquals(listOf(5), h.state.value.characters.map { it.id })
        assertEquals(5, h.state.value.selectedCharacterId)
        assertEquals(5, h.repo.selectedCharacter)
    }

    @Test
    fun deleteFailureShowsAnError() {
        val h = Harness()
        h.state.update { it.copy(characters = listOf(character(5))) }
        h.repo.deleteCharacterOk = false
        h.actions.delete(5)
        h.scope.advanceUntilIdle()
        assertNotNull(h.state.value.error)
        assertEquals(1, h.state.value.characters.size)
    }
}
