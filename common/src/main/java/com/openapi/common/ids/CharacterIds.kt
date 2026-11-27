package com.openapi.common.ids

/**
 * chat 线（Int 角色 id，`/user/setting/character.json` 的旧人设）与服务端 role_id
 * 字符串之间的**显式**桥（persona/her 契约文档「聊天与陪伴角色 ID 映射核账」项）。
 *
 * 唯一可证的映射是用户自建人设：服务端 role_id = `u_<characterId>`
 * （`AgentBackedChatRepository` 建会话的既有约定，此前散在调用点里；提公成单一事实源）。
 * 系统角色 id（`linche`、`specialized-chief-of-staff`…）是另一套不透明命名，
 * 服务端没有对账表——[fromRoleId] 不猜，只逆向 `u_`，其余回 null 由调用方走各自原路径。
 */
object CharacterIds {
    private const val USER_PREFIX = "u_"

    /** chat 线的 Int 角色 id → agent/companion 线的 role_id 字符串。 */
    fun toRoleId(chatCharacterId: Int): String = USER_PREFIX + chatCharacterId

    /** role_id → chat 线 Int 角色 id；不是 `u_<数字>` 形状就不是 chat 角色，回 null。 */
    fun fromRoleId(roleId: String?): Int? {
        if (roleId == null || !roleId.startsWith(USER_PREFIX)) return null
        return roleId.substring(USER_PREFIX.length).toIntOrNull()?.takeIf { it > 0 }
    }
}
