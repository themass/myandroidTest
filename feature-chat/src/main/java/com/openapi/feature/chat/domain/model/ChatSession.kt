package com.openapi.feature.chat.domain.model

/**
 * 一个会话 = 用户与某一个角色的一段独立对话历史。
 *
 * [roleId] 是角色目录里的角色，[characterId] 是旧的自定义人设；两者都为 null 表示默认助手。
 * 会话一旦建好，它的角色就固定了（服务端也是这样存的），换角色 = 换到另一个会话，
 * 而不是在同一段历史上改提示词。
 */
data class ChatSession(
    val id: Long,
    val name: String,
    val isDefault: Boolean,
    val roleId: String? = null,
    val characterId: Int? = null,
) {
    /** 这个会话属于默认助手（既没有目录角色也没有自定义人设）。 */
    val isDefaultAssistant: Boolean get() = roleId.isNullOrBlank() && (characterId == null || characterId <= 0)
}
