package com.openapi.feature.chat.data.remote

import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.ImageSource
import com.openapi.feature.chat.domain.model.MessageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/**
 * P3-17 拆分钉桩：VO → 领域模型的映射规则在拆分前后必须一字不差。
 * 这些断言全部来自拆分前 AgentBackedChatRepository 里的既有行为。
 */
class AgentVoMappersTest {

    private fun message(
        id: Long = 7,
        role: String = "assistant",
        content: String = "你好",
        createdAt: String = "2026-11-20T08:30:00Z",
        clientMessageId: String? = null,
        interrupted: Boolean = false,
        attachments: List<AgentAttachmentVo> = emptyList(),
    ) = AgentMessageVo(
        id = id, role = role, content = content, kind = "text",
        createdAt = createdAt, interrupted = interrupted,
        clientMessageId = clientMessageId, attachments = attachments,
    )

    @Test
    fun userRoleMapsToUserAndEverythingElseToAssistant() {
        assertEquals(ChatRole.USER, message(role = "user").toChatMessage().role)
        assertEquals(ChatRole.ASSISTANT, message(role = "assistant").toChatMessage().role)
        // 服务端将来加新角色名时不至于崩：兜底成 assistant，而不是抛异常
        assertEquals(ChatRole.ASSISTANT, message(role = "tool").toChatMessage().role)
    }

    @Test
    fun imageAttachmentsBecomeRemoteImageMessage() {
        val msg = message(attachments = listOf(AgentAttachmentVo("up_1", "image", "a.jpg")))

        val mapped = msg.toChatMessage()
        assertEquals(MessageKind.IMAGE, mapped.kind)
        assertEquals(1, mapped.images.size)
        assertEquals("up_1", mapped.images.single().uploadId)
        assertEquals(ImageSource.REMOTE, mapped.images.single().source)
        assertTrue(mapped.images.single().path.isEmpty())
    }

    @Test
    fun nonImageAttachmentsDoNotChangeKind() {
        val msg = message(attachments = listOf(AgentAttachmentVo("up_2", "file", "a.pdf")))

        val mapped = msg.toChatMessage()
        assertEquals(MessageKind.TEXT, mapped.kind)
        assertTrue(mapped.images.isEmpty())
    }

    @Test
    fun clientMessageIdPassesThrough() {
        assertEquals("local-user-1", message(clientMessageId = "local-user-1").toChatMessage().clientMessageId)
        assertNull(message().toChatMessage().clientMessageId)
    }

    /** 任务 2：服务端标记的「这一轮被中断过」以前解析了但映射时丢掉，半截回复与完整回复无法区分。 */
    @Test
    fun interruptedFlagSurvivesTheMapping() {
        assertTrue(message(interrupted = true).toChatMessage().interrupted)
        assertFalse(message().toChatMessage().interrupted)
    }

    @Test
    fun isoCreatedAtParsesToEpochMillis() {
        val expected = OffsetDateTime.parse("2026-11-20T08:30:00Z").toInstant().toEpochMilli()
        assertEquals(expected, message().toChatMessage().createdAtMillis)
    }

    @Test
    fun brokenCreatedAtFallsBackToNowInsteadOfThrowing() {
        val before = System.currentTimeMillis()
        val mapped = message(createdAt = "not-a-date").toChatMessage()
        // 历史里一条坏时间戳不能把整页拖崩；回落到「现在」并保证仍可排序
        assertTrue(mapped.createdAtMillis in before..System.currentTimeMillis())
    }

    @Test
    fun blankSessionNameGetsDefaultTitle() {
        val session = AgentSessionVo(id = 3, roleId = "r1", name = "   ", updatedAt = "")

        val mapped = session.toChatSession()
        assertEquals("新会话", mapped.name)
        assertEquals(3L, mapped.id)
        assertEquals("r1", mapped.roleId)
    }
}
