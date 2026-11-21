package com.openapi.ks.myapp.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长按她的动作轮盘内容（纯函数，JVM 测试钉住）。
 *
 * 两类动作：聊天通道动作（messageText 发出去，她回话）现在就能用；
 * 真动作（转身/走动…）要等 Duix 模型带 motionRegions——Oliver 素材实测是空表，
 * 所以全部置灰并带原因，不假装能点。
 */
class AvatarActionWheelTest {

    @Test
    fun `轮盘至少三个聊天动作和三个等待中的真动作`() {
        val actions = avatarActions()
        val chat = actions.filter { it.enabled }
        val pending = actions.filter { !it.enabled }
        assertTrue("聊天动作至少 3 个", chat.size >= 3)
        assertTrue("置灰动作至少 3 个", pending.size >= 3)
    }

    @Test
    fun `聊天动作都带发送文案，置灰动作都带原因`() {
        avatarActions().forEach { a ->
            if (a.enabled) {
                assertTrue("聊天动作 ${a.id} 要有发送文案", a.messageText!!.isNotBlank())
            } else {
                assertTrue("置灰动作 ${a.id} 要有原因", a.disabledReason.isNotBlank())
                assertEquals("置灰动作不许带发送文案", null, a.messageText)
            }
        }
    }

    @Test
    fun `聊天动作的文案是陪伴口吻，第一人称动作描述`() {
        val texts = avatarActions().filter { it.enabled }.map { it.messageText!! }
        // 与已验证过的聊天通道文案同源（「摸了摸她的头」→ 她回话）。
        assertTrue("要有摸头", texts.any { it.contains("摸了摸") })
        texts.forEach { assertTrue("文案别太长", it.length in 3..12) }
    }

    @Test
    fun `id 不重复，聊天动作排在前面`() {
        val actions = avatarActions()
        assertEquals("id 必须唯一", actions.size, actions.map { it.id }.toSet().size)
        val firstDisabled = actions.indexOfFirst { !it.enabled }
        assertTrue("聊天动作全部排在置灰动作前", actions.subList(firstDisabled, actions.size).none { it.enabled })
    }
}
