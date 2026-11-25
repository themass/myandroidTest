package com.openapi.chatkit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A36：发送键常驻 + 队列徽标的判定层。
 *
 * 用户反馈是「发一个消息发送键就变成取消键，不能继续发」。A36① 把发送键从
 * 「生成中被替换」改成「常驻」，A36② 把停止入口移到生成中的气泡上。
 * 这里钉住的是 `ComposerState` 上的两条判定——它们决定键位画不画：
 * - `sendVisible`：纯文字生成中也要画（走排队）；纯草稿生成中不画
 *   （feature-chat 的发送闸会把纯附件消息挡到回合结束，画了点了没结果更像坏了）；
 * - `queuedCount` 来自 `queued` 列表（徽标从条数改成列表，撤回要按 index 定位）。
 */
class ComposerSendVisibilityTest {

    private fun imageDraft(id: String) = DraftItem(
        id = id, type = DraftType.Image, source = "/tmp/$id.jpg", name = "$id.jpg",
    )
    @Test
    fun plainTextKeepsSendVisibleWhileGenerating() {
        val s = ComposerState(text = "再问一句", generating = true)
        assertTrue("生成中纯文字必须还能按发送（入队续发）", s.sendVisible)
        assertTrue(s.canSend)
    }

    @Test
    fun sendHiddenWhileGeneratingWithDraftsOnly() {
        val s = ComposerState(drafts = listOf(imageDraft("d1")), generating = true)
        assertFalse("生成中纯草稿走不了发送闸，不画发送键", s.sendVisible)
    }

    @Test
    fun textPlusDraftsStaysSendableWhileGenerating() {
        // 带文字的图文消息：SendActions 的 Queue 路径只收纯文字，
        // 但键位仍显示（文字撑起的 canSend），与旧行为一致。
        val s = ComposerState(text = "看图", drafts = listOf(imageDraft("d1")), generating = true)
        assertTrue(s.sendVisible)
    }

    @Test
    fun idleVisibilityMatchesOldRule() {
        assertFalse(ComposerState().sendVisible)
        assertTrue(ComposerState(text = "x").sendVisible)
        assertTrue(ComposerState(drafts = listOf(imageDraft("d1"))).sendVisible)
    }

    @Test
    fun queuedCountDerivesFromList() {
        assertEquals(0, ComposerState().queuedCount)
        assertEquals(2, ComposerState(queued = listOf("第一条", "第二条")).queuedCount)
    }
}
