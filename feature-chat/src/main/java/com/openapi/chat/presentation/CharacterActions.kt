package com.openapi.chat.presentation

import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.chat.domain.repository.ChatRepository
import com.openapi.feature.chat.roles.data.RolesRepository
import com.openapi.feature.chat.roles.domain.RolePage
import com.openapi.feature.chat.roles.domain.RoleDivision
import com.openapi.feature.chat.roles.domain.RoleSummary
import com.openapi.feature.chat.roles.domain.SelectedRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 角色（人设）的选择、创建、编辑、删除。从 ChatViewModel 拆出来。
 *
 * 两套选择互斥：选了旧人设就清掉目录角色，选了目录角色就清掉旧人设，界面显示的和实际发出去的保持一致。
 */
internal class CharacterActions(
    private val repository: ChatRepository,
    private val roles: RolesRepository,
    private val state: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
) {

    fun load() {
        scope.launch {
            val characters = runCatching { repository.loadCharacters() }.getOrElse { emptyList() }
            state.update {
                it.copy(
                    characters = characters,
                    selectedCharacterId = repository.selectedCharacterId(),
                )
            }
        }
    }

    /** 选了旧的人设（或默认助手）就把目录角色清掉。 */
    fun selectCharacter(characterId: Int?) {
        val sessionId = repository.currentSessionId()
        repository.setSelectedCharacterId(characterId)
        roles.setSelected(null)
        roles.setSelectedFor(sessionId, null)
        state.update { it.copy(selectedCharacterId = characterId, selectedRole = null, rolePickerVisible = false) }
    }

    /** 选了目录角色就把旧人设清掉。 */
    fun selectRole(role: SelectedRole) {
        val sessionId = repository.currentSessionId()
        roles.setSelected(role)
        roles.setSelectedFor(sessionId, role)
        repository.setSelectedCharacterId(null)
        state.update { it.copy(selectedRole = role, selectedCharacterId = null, rolePickerVisible = false) }
    }

    fun setRolePickerVisible(visible: Boolean) {
        state.update { it.copy(rolePickerVisible = visible) }
    }

    /** 创建自定义角色并立即选中它：用户上传角色的目的就是马上用它聊天。 */
    fun create(title: String, content: String) {
        scope.launch {
            val created = runCatching { repository.createCharacter(title, content, null) }.getOrNull()
            if (created == null) {
                state.update { it.copy(error = "创建角色失败，请稍后重试") }
                return@launch
            }
            repository.setSelectedCharacterId(created.id)
            roles.setSelected(null)
            roles.setSelectedFor(repository.currentSessionId(), null)
            state.update {
                it.copy(
                    characters = it.characters + created,
                    selectedCharacterId = created.id,
                    selectedRole = null,
                )
            }
        }
    }

    fun update(characterId: Int, title: String, content: String) {
        scope.launch {
            val ok = runCatching { repository.updateCharacter(characterId, title, content, null) }.getOrDefault(false)
            if (!ok) {
                state.update { it.copy(error = "更新角色失败，请稍后重试") }
                return@launch
            }
            state.update { s ->
                s.copy(
                    characters = s.characters.map {
                        if (it.id == characterId) it.copy(title = title, preview = content.take(40), content = content) else it
                    },
                )
            }
        }
    }

    fun delete(characterId: Int) {
        scope.launch {
            val ok = runCatching { repository.deleteCharacter(characterId) }.getOrDefault(false)
            if (!ok) {
                state.update { it.copy(error = "删除角色失败，请稍后重试") }
                return@launch
            }
            val wasSelected = state.value.selectedCharacterId == characterId
            state.update { s ->
                s.copy(
                    characters = s.characters.filterNot { it.id == characterId },
                    // 删的正好是当前选中的角色，回退到默认助手，不留一个指向空角色的选择
                    selectedCharacterId = if (wasSelected) null else s.selectedCharacterId,
                )
            }
            if (wasSelected) repository.setSelectedCharacterId(null)
        }
    }
}
