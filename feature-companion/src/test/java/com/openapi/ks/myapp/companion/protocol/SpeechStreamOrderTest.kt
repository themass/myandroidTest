package com.openapi.ks.myapp.companion.protocol

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

/**
 * 流式交付下的**音频帧归属**（延迟刀 C3 的端侧前提）。
 *
 * 服务端并行合成按序交付后，一条远端音轨上会同时存在多条已声明、都还没定 `t0` 的
 * 句子（下一句的 `speech.seg` 先于上一句的音频播完就到——data channel 比 jitter
 * buffer 快）。这时"第一帧归谁"不再是显然的：
 *
 * - 归属按**声明序**（FIFO）：服务端按序交付，音频序 == seg 序。
 * - 上一句还在出声（估算未播完、也没见过静音间隔）时，这一帧属于它，**不许**给新句定 `t0`。
 * - 上一句被作废 / 已估算播完 / 出现静音间隔 → 才允许给下一句定 `t0`。
 *
 * 全部用假时钟。钉的是「cue 锚点与 heard 前缀在流水线下不错位」这件事。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SpeechStreamOrderTest {

    private val t0 = 500_000L
    private val minRms = 0.02

    private fun seg(speechId: String, idx: Int = 0, est: Long = 1000): JSONObject = JSONObject()
        .put("speech_id", speechId)
        .put("idx", idx)
        .put("chars", 10)
        .put("est_ms", est)

    private fun cancel(speechId: String): JSONObject = JSONObject().put("speech_id", speechId)

    // ------------------------------------------------------------ FIFO 归属

    @Test
    fun `两句同时在飞 首帧归最早声明的那句`() {
        val r = SpeechRegistry<String>()
        r.onSeg(seg("sp_1", est = 900))
        r.onSeg(seg("sp_2", est = 900))   // 并行合成：下一句的 seg 先于上一句的音频到

        assertEquals("sp_1", r.timebasePendingId(t0))
        assertTrue(r.onAudioFrame(now = t0, captureTs = 1_000L, rms = 0.3, minRms = minRms))

        assertNotNull(r.timeline("sp_1")!!.t0)
        assertNull(r.timeline("sp_2")!!.t0)
    }

    @Test
    fun `上一句估算未播完时 这一帧属于它 不许给新句定 t0`() {
        val r = SpeechRegistry<String>()
        r.onSeg(seg("sp_1", est = 1_000))
        assertTrue(r.onAudioFrame(now = t0, captureTs = 1_000L, rms = 0.3, minRms = minRms))

        r.onSeg(seg("sp_2", est = 1_000))
        // 还在 sp_1 的估算窗口内（t0+1000）：这帧是 sp_1 的尾巴。
        for (off in longArrayOf(100L, 500L, 900L)) {
            assertNull(r.timebasePendingId(t0 + off))
            assertFalse(r.onAudioFrame(now = t0 + off, captureTs = 1_100L + off, rms = 0.3, minRms = minRms))
        }
        assertNull(r.timeline("sp_2")!!.t0)
    }

    @Test
    fun `估算播完后 下一句的首帧才标定`() {
        val r = SpeechRegistry<String>()
        r.onSeg(seg("sp_1", est = 1_000))
        assertTrue(r.onAudioFrame(now = t0, captureTs = 1_000L, rms = 0.3, minRms = minRms))
        r.onSeg(seg("sp_2", est = 1_000))

        // 估算终点过后第一帧：归 sp_2。
        assertTrue(r.onAudioFrame(now = t0 + 1_000, captureTs = 2_000L, rms = 0.3, minRms = minRms))
        assertEquals(t0 + 1_000, r.timeline("sp_2")!!.t0)
        assertEquals(SpeechTimeline.State.PLAYING, r.timeline("sp_2")!!.state)
    }

    @Test
    fun `静音间隔比估算终点早 就提前放行（est 偏长的兜底）`() {
        val r = SpeechRegistry<String>()
        r.onSeg(seg("sp_1", est = 2_000))
        assertTrue(r.onAudioFrame(now = t0, captureTs = 1_000L, rms = 0.3, minRms = minRms))
        r.onSeg(seg("sp_2", est = 1_000))

        // sp_1 实际只说了 600ms：后面是静音。est 说还有 1400ms，静音间隔先替我们收尾。
        r.onAudioFrame(now = t0 + 600, captureTs = 1_600L, rms = 0.3, minRms = minRms)
        r.onAudioFrame(now = t0 + 700, captureTs = 1_700L, rms = 0.0, minRms = minRms)
        r.onAudioFrame(now = t0 + 800, captureTs = 1_800L, rms = 0.0, minRms = minRms)
        r.onAudioFrame(now = t0 + 900, captureTs = 1_900L, rms = 0.0, minRms = minRms)
        // 连续静音 ≥ 200ms（t0+700 起算，t0+900 满 200ms）后，静音帧本身不标定，但门已开。
        assertNull(r.timeline("sp_2")!!.t0)

        assertTrue(r.onAudioFrame(now = t0 + 950, captureTs = 2_500L, rms = 0.25, minRms = minRms))
        assertEquals(t0 + 950, r.timeline("sp_2")!!.t0)
    }

    // ------------------------------------------------------------ 截断与放行

    @Test
    fun `上一句被打断后 下一句的首帧立刻可标定`() {
        // 即刻截断的下游后果：墓碑那一句不再"在出声"，新回合的 t0 不该被它的估算拖住。
        val r = SpeechRegistry<String>()
        r.onSeg(seg("sp_1", est = 4_000))
        assertTrue(r.onAudioFrame(now = t0, captureTs = 1_000L, rms = 0.3, minRms = minRms))
        r.onCancel(cancel("sp_1"))
        r.onSeg(seg("sp_2", est = 1_000))

        assertEquals("sp_2", r.timebasePendingId(t0 + 120))
        assertTrue(r.onAudioFrame(now = t0 + 120, captureTs = 1_120L, rms = 0.3, minRms = minRms))
        assertEquals(t0 + 120, r.timeline("sp_2")!!.t0)
    }

    @Test
    fun `首句没有前句时第一帧立标定（保持现行为）`() {
        val r = SpeechRegistry<String>()
        r.onSeg(seg("sp_1", est = 1_000))
        assertTrue(r.onAudioFrame(now = t0, captureTs = 1_000L, rms = 0.3, minRms = minRms))
        assertEquals(t0, r.timeline("sp_1")!!.t0)
    }

    @Test
    fun `speech_end 早到的句子仍在出声时同样阻塞新句`() {
        // end 只表示服务端生成侧排空；扬声器里还在播的那句仍然占住这一帧。
        val r = SpeechRegistry<String>()
        r.onSeg(seg("sp_1", est = 1_000))
        assertTrue(r.onAudioFrame(now = t0, captureTs = 1_000L, rms = 0.3, minRms = minRms))
        r.onEnd(cancel("sp_1"))
        r.onSeg(seg("sp_2", est = 1_000))

        assertNull(r.timebasePendingId(t0 + 400))
        assertTrue(r.onAudioFrame(now = t0 + 1_000, captureTs = 2_000L, rms = 0.3, minRms = minRms))
        assertEquals(t0 + 1_000, r.timeline("sp_2")!!.t0)
    }

    // ------------------------------------------------------------ 便宜门 + 首响计时

    @Test
    fun `hasUntimedPending 是 transport 每帧调用的便宜门`() {
        val r = SpeechRegistry<String>()
        assertFalse(r.hasUntimedPending())
        r.onSeg(seg("sp_1", est = 1_000))
        assertTrue(r.hasUntimedPending())
        r.onAudioFrame(now = t0, captureTs = 1_000L, rms = 0.3, minRms = minRms)
        assertFalse(r.hasUntimedPending())
        r.onSeg(seg("sp_2", est = 1_000))
        assertTrue(r.hasUntimedPending())      // 还在 sp_1 的出声窗口里：门要开着，静音观察才连续
    }

    @Test
    fun `seg 声明时刻记进时间线 供端侧首响计时`() {
        val r = SpeechRegistry<String>()
        r.onSeg(seg("sp_1", est = 1_000), declaredAt = t0)
        assertEquals(t0, r.timeline("sp_1")!!.declaredAt)
    }
}
