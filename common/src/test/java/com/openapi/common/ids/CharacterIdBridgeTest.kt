package com.openapi.common.ids

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * chat 线 Int 角色 id ↔ 服务端 role_id 字符串的显式桥（persona/her 契约文档
 * 「聊天与陪伴角色 ID 映射核账」项，先行未闭环）。
 *
 * 唯一**可证**的映射是用户自建人设的 `u_<id>`（`AgentBackedChatRepository` 既有隐式
 * 约定，这里提公成单一事实源）。系统角色 id（`linche`、`specialized-chief-of-staff`）
 * 是另一套不透明命名，服务端没有对账表——桥不猜，回 null 由调用方走各自原路径。
 */
class CharacterIdBridgeTest {

    @Test
    fun `chat character id becomes the u_ role id the agent line already uses`() {
        assertEquals("u_4", CharacterIds.toRoleId(4))
    }

    @Test
    fun `u_ role id parses back to the chat character id`() {
        assertEquals(4, CharacterIds.fromRoleId("u_4"))
    }

    @Test
    fun `system role ids are not reversed into the chat id space`() {
        assertNull(CharacterIds.fromRoleId("linche"))
        assertNull(CharacterIds.fromRoleId("specialized-chief-of-staff"))
        assertNull(CharacterIds.fromRoleId(null))
    }

    @Test
    fun `u_ prefix with a non-numeric tail is not a chat character id`() {
        assertNull(CharacterIds.fromRoleId("u_"))
        assertNull(CharacterIds.fromRoleId("u_abc"))
    }
}
