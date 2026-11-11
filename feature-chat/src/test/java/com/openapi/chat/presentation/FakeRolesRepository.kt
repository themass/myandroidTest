package com.openapi.chat.presentation

import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.feature.chat.roles.data.RolesRepository
import com.openapi.feature.chat.roles.domain.RoleDivision
import com.openapi.feature.chat.roles.domain.RolePage
import com.openapi.feature.chat.roles.domain.RoleSummary
import com.openapi.feature.chat.roles.domain.SelectedRole

/** 测试用的假角色仓库：只实现选择相关的方法。 */
internal class FakeRolesRepository : RolesRepository {
    var selectedRole: SelectedRole? = null
    val selectedForSession = mutableMapOf<Long, SelectedRole?>()

    override suspend fun divisions(): List<RoleDivision>? = null
    override suspend fun roles(division: String?, query: String, featuredOnly: Boolean, limit: Int, offset: Int): RolePage? = null
    override fun selected(): SelectedRole? = selectedRole
    override fun setSelected(role: SelectedRole?) {
        selectedRole = role
    }
    override fun selectedFor(sessionId: Long): SelectedRole? = selectedForSession[sessionId]
    override fun setSelectedFor(sessionId: Long, role: SelectedRole?) {
        selectedForSession[sessionId] = role
    }
    override fun favorites(): List<RoleSummary> = emptyList()
    override fun isFavorite(roleId: String) = false
    override fun toggleFavorite(role: RoleSummary) = false
    override fun recents(): List<RoleSummary> = emptyList()
    override fun recordUse(role: RoleSummary) {}
}

internal fun character(id: Int, title: String = "角色$id", content: String = "正文$id") =
    ChatCharacterOption(id = id, title = title, avatarUrl = null, preview = content.take(40), mine = true, content = content)
