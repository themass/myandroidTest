package com.openapi.ks.myapp.companion

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 服务端 JSON 到客户端模型的解析。用 Robolectric 跑，保证 org.json 是真实实现。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CompanionModelsTest {

    @Test
    fun `preferences parse the server shape and treat an empty pause as not paused`() {
        val prefs = CompanionPreferences.from(
            JSONObject(
                """{"events_enabled":true,"greetings_enabled":false,"call_enabled":false,
                   "quiet_start":"23:00","quiet_end":"08:00","max_per_day":2,"max_per_week":6,
                   "paused_until":null}""",
            ),
        )
        assertTrue(prefs.eventsEnabled)
        assertFalse(prefs.greetingsEnabled)
        assertEquals("23:00", prefs.quietStart)
        assertNull(prefs.pausedUntil)
    }

    @Test
    fun `a paused user keeps the pause timestamp`() {
        val prefs = CompanionPreferences.from(
            JSONObject("""{"paused_until":"2026-11-07T12:00:00Z"}"""),
        )
        assertEquals("2026-11-07T12:00:00Z", prefs.pausedUntil)
    }

    @Test
    fun `inbox message keeps the trigger that decides how she is labelled`() {
        val message = InboxMessage.from(
            JSONObject(
                """{"id":"inapp-1","character_id":"linche","text":"面试加油","channel":"in_app",
                   "at":"2026-11-07T10:00:00Z","trigger":"event_due"}""",
            ),
        )
        assertEquals("event_due", message.trigger)
        assertEquals("linche", message.characterId)
    }

    @Test
    fun `pending action keeps the confirmation id the server needs back`() {
        val action = PendingAction.from(
            JSONObject(
                """{"confirmation_id":"cfm-abc","user_id":"u1","character_id":"linche",
                   "action":"create_reminder","description":"建提醒","arguments":{"title":"吃药"}}""",
            ),
        )
        assertEquals("cfm-abc", action.confirmationId)
        assertEquals("create_reminder", action.action)
    }

    @Test
    fun `character detail reads the opening line and alternate greetings`() {
        val detail = CharacterDetail.from(
            JSONObject(
                """{"id":"linche","name":"林澈","tagline":"成长教练","tags":["成长","直接"],
                   "voice_available":false,"description":"d","personality":"p","scenario":"s",
                   "first_mes":"你好","alternate_greetings":["嗨"],"skill":"growth-coaching"}""",
            ),
        )
        assertEquals("你好", detail.firstMessage)
        assertEquals(listOf("嗨"), detail.alternateGreetings)
        assertFalse(detail.summary.voiceAvailable)
        assertEquals(listOf("成长", "直接"), detail.summary.tags)
    }
}

class CompanionTimeTest {

    @Test
    fun `a local date time becomes a UTC ISO string the server accepts`() {
        val iso = localToUtcIso("2026-12-01 10:00")
        assertNotNull(iso)
        assertTrue(iso!!.matches(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""")))
    }

    @Test
    fun `malformed input is rejected rather than guessed`() {
        assertNull(localToUtcIso("明天下午"))
        assertNull(localToUtcIso("2026-13-40 99:99"))
    }

    @Test
    fun `pause for an hour produces a future UTC timestamp`() {
        val iso = utcIsoAfterHours(1)
        assertTrue(iso.matches(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""")))
    }

    @Test
    fun `server timestamps are shown in short local form`() {
        val shown = formatShortDate("2026-11-07T10:00:00Z")
        assertTrue(shown.matches(Regex("""\d{2}-\d{2} \d{2}:\d{2}""")))
    }
}
