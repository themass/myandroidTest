package com.openapi.ks.myapp.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// 用普通 Application：应用自身的 Application 会在每个用例里启动 Koin，第二次就会冲突。
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AgentChatStateTest {

    private fun event(id: String, e: SessionEvent) = StreamUpdate.Event(e)

    @Test
    fun `local send becomes queued with its receipt and drained when the turn starts`() {
        var s = AgentChatState().withLocalSending("local-1", "吃药提醒")
        assertEquals(BubbleState.SENDING, s.bubbles.single().state)
        s = s.withReceipt("local-1", SubmitReceipt("55", "T1", Disposition.QUEUED, 1))
        assertEquals(BubbleState.QUEUED, s.bubbles.single().state)
        assertEquals("55", s.queued.single().messageId)
        s = reduce(s, event("10-0", SessionEvent.TurnStarted("10-0", "T2", listOf("55"))))
        assertTrue(s.queued.isEmpty())
        assertEquals(BubbleState.ANSWERED, s.bubbles.single().state)
    }

    @Test
    fun `streamed deltas build the active turn and done appends the assistant reply`() {
        var s = AgentChatState()
        s = reduce(s, event("1-0", SessionEvent.TurnStarted("1-0", "T", listOf("7"))))
        s = reduce(s, event("2-0", SessionEvent.Delta("2-0", "T", "你")))
        s = reduce(s, event("3-0", SessionEvent.Delta("3-0", "T", "好")))
        assertEquals("你好", s.active?.text)
        s = reduce(s, event("4-0", SessionEvent.Done("4-0", "T", "88", emptyList())))
        assertNull(s.active)
        val reply = s.bubbles.last()
        assertEquals("assistant", reply.role)
        assertEquals("你好", reply.content)
    }

    @Test
    fun `a restart clears the partial text but keeps the merged message ids`() {
        var s = reduce(AgentChatState(), event("1-0", SessionEvent.Delta("1-0", "T", "半截")))
        s = reduce(s, event("2-0", SessionEvent.TurnRestarted("2-0", "T", listOf("7", "8"))))
        assertEquals("", s.active?.text)
        assertEquals(listOf("7", "8"), s.active?.userMessageIds)
    }

    @Test
    fun `duplicate events with an id not newer than the last applied are ignored`() {
        var s = reduce(AgentChatState(), event("5-0", SessionEvent.Delta("5-0", "T", "a")))
        s = reduce(s, event("5-0", SessionEvent.Delta("5-0", "T", "a")))
        s = reduce(s, event("4-9", SessionEvent.Delta("4-9", "T", "b")))
        assertEquals("a", s.active?.text)
        assertEquals("5-0", s.lastAppliedEventId)
    }

    @Test
    fun `stream ids compare numerically, not as text`() {
        assertTrue(compareStreamIds("10-0", "9-0") > 0)
        assertTrue(compareStreamIds("9-10", "9-2") > 0)
    }

    @Test
    fun `cancel finishes the turn without clearing the queue`() {
        var s = AgentChatState(queued = listOf(QueuedInput("9", "等着", 1)))
        s = reduce(s, event("1-0", SessionEvent.Delta("1-0", "T", "说到一半")))
        s = reduce(s, event("2-0", SessionEvent.TurnCancelled("2-0", "T", "66")))
        assertNull(s.active)
        assertEquals(1, s.queued.size)
        assertEquals("说到一半", s.bubbles.last().content)
    }

    @Test
    fun `gone asks for a history reload and withHistory replaces the list`() {
        var s = reduce(AgentChatState(), StreamUpdate.Event(SessionEvent.Gone("9-0")))
        assertTrue(s.needsHistoryReload)
        val history = listOf(AgentBubble("1", "1", "user", "旧的", BubbleState.SYNCED))
        s = s.withHistory(history)
        assertFalse(s.needsHistoryReload)
        assertEquals(history, s.bubbles)
    }

    @Test
    fun `failed sends can be retried with the same client id`() {
        var s = AgentChatState().withLocalSending("local-2", "再试")
        s = s.withSendFailed("local-2")
        assertEquals(BubbleState.FAILED, s.bubbles.single().state)
        s = s.withRetrying("local-2")
        assertEquals(BubbleState.SENDING, s.bubbles.single().state)
        assertEquals("local-2", s.bubbles.single().key)
    }

    @Test
    fun `withdraw removes the bubble and the queued entry`() {
        var s = AgentChatState(
            bubbles = listOf(AgentBubble("55", "55", "user", "撤回我", BubbleState.QUEUED)),
            queued = listOf(QueuedInput("55", "撤回我", 1)),
        )
        s = s.withoutQueued("55")
        assertTrue(s.bubbles.isEmpty())
        assertTrue(s.queued.isEmpty())
    }
}
