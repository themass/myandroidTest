package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatCharacterOption
import com.openapi.feature.chat.domain.model.ChatSession
import com.openapi.feature.chat.domain.repository.ChatRepository
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
/** 默认助手新建会话时的名字，与服务端的默认会话名一致。 */
private const val DEFAULT_SESSION_NAME = "新对话"

internal class CharacterActions(
    private val repository: ChatRepository,
    private val roles: RolesRepository,
    private val state: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    /** 当前会话换了：调用方据此重新加载这个会话的历史。 */
    private val onSessionChanged: () -> Unit = {},
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

    /**
     * 选了旧的人设（或默认助手，[characterId] 为 null）：换到这个人设自己的会话。
     * 每个角色一段独立历史，所以换角色不是改提示词，而是换会话。
     */
    fun selectCharacter(characterId: Int?) {
        val cid = characterId?.takeIf { it > 0 }
        val title = cid?.let { id -> state.value.characters.firstOrNull { it.id == id }?.title }
        switchTo(roleId = null, characterId = cid, sessionName = title ?: DEFAULT_SESSION_NAME) {
            repository.setSelectedCharacterId(cid)
            roles.setSelected(null)
            state.update { it.copy(selectedCharacterId = cid, selectedRole = null) }
        }
    }

    /** 选了目录角色：换到这个角色自己的会话（没有就新建一个）。 */
    fun selectRole(role: SelectedRole) {
        switchTo(roleId = role.id, characterId = null, sessionName = role.name) {
            roles.setSelected(role)
            repository.setSelectedCharacterId(null)
            state.update { it.copy(selectedRole = role, selectedCharacterId = null) }
        }
    }

    /**
     * 找到（或新建）属于 [roleId]/[characterId] 的会话并切过去。
     *
     * - 会话列表按更新时间倒序，第一个匹配的就是这个角色最近用过的会话；
     * - 已经在这个会话里就什么都不做；
     * - 新建失败时**不改任何选中状态**，只提示：否则界面显示新角色、实际还停在旧会话，
     *   就是这次要修的那个问题；
     * - [applySelection] 只在会话确定之后才执行。
     */
    private fun switchTo(roleId: String?, characterId: Int?, sessionName: String, applySelection: () -> Unit) {
        val current = repository.currentSessionId()
        val existing = repository.sessions().firstOrNull { it.matches(roleId, characterId) }
        if (existing != null && existing.id == current) {
            applySelection()
            state.update { it.copy(rolePickerVisible = false) }
            return
        }
        if (existing != null) {
            enter(existing.id, roleId, characterId, applySelection)
            return
        }
        scope.launch {
            val created = runCatching { repository.createSessionFor(sessionName, roleId, characterId) }.getOrNull()
            if (created == null) {
                state.update { it.copy(error = "切换角色失败，请稍后重试", rolePickerVisible = false) }
                return@launch
            }
            enter(created.id, roleId, characterId, applySelection)
        }
    }

    private fun enter(sessionId: Long, roleId: String?, characterId: Int?, applySelection: () -> Unit) {
        repository.setCurrentSessionId(sessionId)
        applySelection()
        // 会话自己记住角色，重启或手动切会话时以它为准
        roles.setSelectedFor(sessionId, state.value.selectedRole.takeIf { roleId != null })
        state.update { it.copy(rolePickerVisible = false, sessionId = sessionId) }
        onSessionChanged()
    }

    private fun ChatSession.matches(roleId: String?, characterId: Int?): Boolean = when {
        roleId != null -> this.roleId == roleId
        characterId != null -> this.characterId == characterId && this.roleId.isNullOrBlank()
        else -> isDefaultAssistant
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
