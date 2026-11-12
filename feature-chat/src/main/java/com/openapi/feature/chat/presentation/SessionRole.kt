package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatSession
import com.openapi.feature.chat.roles.data.RolesRepository
import com.openapi.feature.chat.roles.domain.SelectedRole

/**
 * 从会话本身还原“这段对话是哪个角色”。
 *
 * 会话的 roleId 是权威来源（服务端存的）。界面还需要名字、图标和颜色，按下面的顺序找，
 * 找不到才用占位，保证至少角色 id 是对的——发出去的请求带的是 id，显示可以稍后补全。
 * 绝不退回到“最近一次全局选的角色”：那会让别的角色的会话显示成当前角色。
 */
internal object SessionRole {
    private const val FALLBACK_EMOJI = "🎭"
    private const val FALLBACK_COLOR = 0xFF9E9E9EL

    fun resolve(session: ChatSession?, roles: RolesRepository, sessionId: Long): SelectedRole? {
        val roleId = session?.roleId?.takeIf { it.isNotBlank() } ?: return null
        roles.selectedFor(sessionId)?.takeIf { it.id == roleId }?.let { return it }
        roles.selected()?.takeIf { it.id == roleId }?.let { return it }
        (roles.favorites() + roles.recents()).firstOrNull { it.id == roleId }?.let {
            return SelectedRole(it.id, it.name, it.emoji, it.colorArgb)
        }
        return SelectedRole(roleId, session.name.ifBlank { roleId }, FALLBACK_EMOJI, FALLBACK_COLOR)
    }
}
