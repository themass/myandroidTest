package com.openapi.ks.myapp.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// 用普通 Application：应用自身的 Application 会在每个用例里启动 Koin，第二次就会冲突。
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SseFramesTest {

    @Test
    fun `comments and blank lines do not emit frames until data arrives`() {
        val parser = SseFrameParser()
        assertNull(parser.feed(": keepalive"))
        assertNull(parser.feed(""))
        assertNull(parser.feed("id: 1-0"))
        assertNull(parser.feed("event: message.delta"))
        assertNull(parser.feed("data: {\"turn_id\":\"T\",\"delta\":\"你\"}"))
        val frame = parser.feed("")!!
        assertEquals("1-0", frame.id)
        assertEquals("message.delta", frame.event)
        assertEquals("{\"turn_id\":\"T\",\"delta\":\"你\"}", frame.data)
    }

    @Test
    fun `multi line data is joined with newlines`() {
        val parser = SseFrameParser()
        parser.feed("id: 2-0")
        parser.feed("event: step")
        parser.feed("data: first")
        parser.feed("data: second")
        val frame = parser.feed("")!!
        assertEquals("first\nsecond", frame.data)
    }

    @Test
    fun `known event maps to a session event and unknown events are dropped`() {
        val delta = SseFrame("3-0", "message.delta", "{\"turn_id\":\"T1\",\"delta\":\"好\"}").toSessionEvent()
        assertTrue(delta is SessionEvent.Delta)
        assertEquals("好", (delta as SessionEvent.Delta).text)
        assertNull(SseFrame("4-0", "something.new", "{}").toSessionEvent())
        assertNull(SseFrame(null, "message.delta", "{}").toSessionEvent())
    }

    @Test
    fun `input queued carries its position and missing fields are rejected`() {
        val queued = SseFrame("5-0", "input.queued", "{\"message_id\":\"9\",\"queue_position\":2}").toSessionEvent()
        assertEquals(2, (queued as SessionEvent.InputQueued).position)
        assertNull(SseFrame("6-0", "turn.started", "{}").toSessionEvent())
    }

    @Test
    fun `reconnect and gone map to transport events`() {
        assertTrue(SseFrame("7-0", "reconnect", "{\"after_ms\":1000}").toSessionEvent() is SessionEvent.Reconnect)
        assertTrue(SseFrame("8-0", "gone", "{}").toSessionEvent() is SessionEvent.Gone)
    }
}
