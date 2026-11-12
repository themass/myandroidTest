package com.openapi.ks.myapp.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SseFramesTest 之外的事件映射：每种服务端事件都要有一次「字段对得上」和一次「缺字段被丢弃」。
 * 服务端新增字段不该让客户端崩，缺关键字段的帧必须被丢弃而不是造出半截事件。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SseEventMappingTest {

    private fun map(event: String, json: String, id: String? = "1-0") = SseFrame(id, event, json).toSessionEvent()

    @Test
    fun `message created keeps the client id and defaults kind to chat`() {
        val e = map("message.created", """{"message_id":"m1","client_message_id":"c1","role":"user","content":"hi"}""")
            as SessionEvent.MessageCreated
        assertEquals("c1", e.clientMessageId)
        assertEquals("chat", e.kind)
        assertNull(map("message.created", """{"role":"user","content":"hi"}"""))
    }

    @Test
    fun `proactive kind is preserved so the ui can hide the internal instruction`() {
        val e = map("message.created", """{"message_id":"m2","role":"system","content":"x","kind":"proactive"}""")
            as SessionEvent.MessageCreated
        assertEquals("proactive", e.kind)
        assertNull(e.clientMessageId)
    }

    @Test
    fun `turn snapshot carries the partial text and the covered user messages`() {
        val e = map("turn.snapshot", """{"turn_id":"T","user_message_ids":["a","b"],"text":"半截"}""")
            as SessionEvent.TurnSnapshot
        assertEquals(listOf("a", "b"), e.userMessageIds)
        assertEquals("半截", e.text)
        assertNull(map("turn.snapshot", """{"text":"x"}"""))
    }

    @Test
    fun `turn started and restarted are distinct events`() {
        assertTrue(map("turn.started", """{"turn_id":"T","user_message_ids":["a"]}""") is SessionEvent.TurnStarted)
        assertTrue(map("turn.restarted", """{"turn_id":"T","user_message_ids":["a"]}""") is SessionEvent.TurnRestarted)
    }

    @Test
    fun `step defaults missing text fields to empty strings`() {
        val e = map("step", """{"turn_id":"T","kind":"tool"}""") as SessionEvent.Step
        assertEquals("tool", e.step.kind)
        assertEquals("", e.step.title)
        assertEquals("", e.step.detail)
        assertNull(map("step", """{"kind":"tool"}"""))
    }

    @Test
    fun `input lifecycle events map and reject missing ids`() {
        assertTrue(map("input.merged", """{"message_id":"m","turn_id":"T"}""") is SessionEvent.InputMerged)
        assertTrue(map("input.steered", """{"message_id":"m","turn_id":"T"}""") is SessionEvent.InputSteered)
        assertTrue(map("input.withdrawn", """{"message_id":"m"}""") is SessionEvent.InputWithdrawn)
        assertNull(map("input.merged", """{"message_id":"m"}"""))
        assertNull(map("input.withdrawn", """{}"""))
    }

    @Test
    fun `input queued without a position gives null instead of zero`() {
        val e = map("input.queued", """{"message_id":"9"}""") as SessionEvent.InputQueued
        assertNull(e.position)
    }

    @Test
    fun `queue drained lists the messages that start the turn`() {
        val e = map("queue.drained", """{"turn_id":"T","message_ids":["a","b"]}""") as SessionEvent.QueueDrained
        assertEquals(listOf("a", "b"), e.messageIds)
    }

    @Test
    fun `done keeps the assistant id and alt group, and tolerates null alt group`() {
        val withAlt = map("message.done", """{"turn_id":"T","assistant_message_id":"A","alt_group":7}""")
            as SessionEvent.Done
        assertEquals("A", withAlt.assistantMessageId)
        assertEquals("7", withAlt.altGroup)
        val noAlt = map("message.done", """{"turn_id":"T","assistant_message_id":null,"alt_group":null}""")
            as SessionEvent.Done
        assertNull(noAlt.assistantMessageId)
        assertNull(noAlt.altGroup)
        assertEquals(emptyList<String>(), noAlt.steeredMessageIds)
    }

    @Test
    fun `cancelled turn maps with an optional assistant id`() {
        val e = map("turn.cancelled", """{"turn_id":"T"}""") as SessionEvent.TurnCancelled
        assertNull(e.assistantMessageId)
    }

    @Test
    fun `error defaults to non retryable and unknown code`() {
        val e = map("error", """{}""") as SessionEvent.Failed
        assertEquals("unknown", e.code)
        assertFalse(e.retryable)
        assertNull(e.turnId)
        val retry = map("error", """{"turn_id":"T","code":"upstream_timeout","retryable":true}""") as SessionEvent.Failed
        assertTrue(retry.retryable)
    }

    @Test
    fun `confirmation events carry the id the server needs back`() {
        val ask = map("action.confirm_required", """{"confirmation_id":"c9","title":"建立提醒","summary":"明天 8 点"}""")
            as SessionEvent.ConfirmRequired
        assertEquals("c9", ask.confirmationId)
        assertEquals("建立提醒", ask.title)
        assertNull(map("action.confirm_required", """{"title":"x"}"""))
        assertEquals("accepted",
            (map("action.done", """{"confirmation_id":"c9","status":"accepted"}""") as SessionEvent.ActionDone).status)
    }

    @Test
    fun `reconnect without a delay falls back to one second even for an empty body`() {
        assertEquals(1000L, (map("reconnect", "") as SessionEvent.Reconnect).afterMs)
        assertEquals(1000L, (map("reconnect", "not json") as SessionEvent.Reconnect).afterMs)
    }

    @Test
    fun `malformed json is dropped for every data carrying event`() {
        listOf("message.delta", "turn.started", "message.done", "error", "step").forEach {
            assertNull("$it with bad json should be dropped", map(it, "{oops"))
        }
    }

    @Test
    fun `unknown extra fields are ignored`() {
        val e = map("message.delta", """{"turn_id":"T","delta":"好","future_field":{"a":1}}""")
        assertEquals("好", (e as SessionEvent.Delta).text)
    }

    @Test
    fun `parser handles crlf-free colon edge cases`() {
        val p = SseFrameParser()
        p.feed("data:no-space")          // 冒号后没有空格也合法
        p.feed("data")                   // 没有冒号的字段名，值为空串
        p.feed("retry: 3000")            // 未使用的字段不能影响分帧
        val frame = p.feed("")!!
        assertEquals("no-space\n", frame.data)
        assertEquals("message", frame.event)
        assertNull(frame.id)
    }

    @Test
    fun `an id or event without data does not leak into the next frame`() {
        val p = SseFrameParser()
        p.feed("id: stale")
        p.feed("event: stale.event")
        assertNull(p.feed(""))
        p.feed("data: {}")
        val frame = p.feed("")!!
        assertNull(frame.id)
        assertEquals("message", frame.event)
    }
}
