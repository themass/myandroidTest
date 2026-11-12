package com.openapi.ks.myapp.agent

import com.openapi.ks.myapp.agent.ui.DefaultAgentRoles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 角色选择页的兜底介绍必须和服务端角色卡的 tagline 一致。
 * 服务端那边有对应测试（companion-agent/tests/test_character_taglines.py）把同一份文案写死，
 * 两边任何一边改了都会失败，逼着另一边一起改。
 */
class AgentRoleCopyTest {

    private val expected = mapOf(
        "linche" to "直接务实的成长教练，帮你把想法落成行动",
        "sudai" to "懂团队心理的组织心理学家，帮你看清说不清的事",
    )

    @Test
    fun `fallback taglines match the server cards`() {
        expected.forEach { (id, tagline) ->
            val role = DefaultAgentRoles.single { it.id == id }
            assertEquals("$id 的介绍与服务端角色卡不一致", tagline, role.tagline)
        }
    }

    @Test
    fun `every fallback role is a known server card`() {
        assertTrue(DefaultAgentRoles.all { it.id in expected })
    }
}
