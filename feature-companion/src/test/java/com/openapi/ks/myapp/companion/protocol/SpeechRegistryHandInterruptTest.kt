package com.openapi.ks.myapp.companion.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 举手打断（C7）找「该作废的那句」的口径，与 VAD 打断（§10.1）不同：
 * VAD 只认 PLAYING（PENDING 时打断会自掐 TTS frames=0）；**用户明确按键**时
 * PENDING 也要作废——seg 已声明、音频还没起，这一轮照样该停，不然「按了没反应」。
 * DONE / CANCELLED 不算：说完或已作废，没有可打断的对象。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SpeechRegistryHandInterruptTest {

    private fun seg(speechId: String, idx: Int): JSONObject = JSONObject()
        .put("speech_id", speechId)
        .put("idx", idx)
        .put("chars", 10)
        .put("est_ms", 1_000)

    @Test
    fun `nothing in flight means nothing to interrupt`() {
        val r = SpeechRegistry<String>()
        assertNull(r.handInterruptibleSpeechId())
    }

    @Test
    fun `PENDING counts for the hand key`() {
        val r = SpeechRegistry<String>()
        r.onSeg(seg("sp_1", 0))
        // VAD 口径此时为 null（回归 8a7c909e），举手口径必须不是。
        assertNull(r.activeSpeechId())
        assertEquals("sp_1", r.handInterruptibleSpeechId())
    }

    @Test
    fun `PLAYING counts and takes precedence as the newest`() {
        val r = SpeechRegistry<String>()
        r.onSeg(seg("sp_1", 0))
        r.timeline("sp_1")!!.startNow(500_000)
        assertEquals("sp_1", r.handInterruptibleSpeechId())
    }

    @Test
    fun `DONE and cancelled do not count`() {
        val r = SpeechRegistry<String>()
        r.onSeg(seg("sp_1", 0))
        r.timeline("sp_1")!!.startNow(500_000)
        r.onEnd(JSONObject().put("speech_id", "sp_1"))
        assertNull(r.handInterruptibleSpeechId())
        r.onSeg(seg("sp_2", 0))
        r.onCancel(JSONObject().put("speech_id", "sp_2"))
        assertNull(r.handInterruptibleSpeechId())
    }

    @Test
    fun `the newest live timeline wins`() {
        val r = SpeechRegistry<String>()
        r.onSeg(seg("sp_1", 0))
        r.onSeg(seg("sp_2", 0))
        assertEquals("sp_2", r.handInterruptibleSpeechId())
    }
}
