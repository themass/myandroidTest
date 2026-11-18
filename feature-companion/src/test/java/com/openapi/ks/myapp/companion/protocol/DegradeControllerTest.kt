package com.openapi.ks.myapp.companion.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DegradeController]：§12.3 L0–L4，假时钟。
 *
 * 不碰 LiveKit / 形象层——只断言本地传感器 → 档位 → shouldEmit。
 */
class DegradeControllerTest {

    private fun controller() = DegradeController(
        windowSize = 10,
        cueSilenceMs = 5_000L,
        dataDeadMs = 6_000L,
        pcmAliveMs = 2_000L,
        pcmDeadMs = 6_000L,
        hysteresisMs = 2_500L,
    )

    // ------------------------------------------------------------ L1 via late_ratio

    @Test
    fun `late_ratio 超过 30 百分比升到 L1`() {
        val d = controller()
        val t = 1_000L
        // 7 applied + 4 expired → ratio = 4/11 ≈ 0.36 > 0.3
        repeat(7) { d.onCueApplied(lateMs = 0) }
        repeat(4) { d.onCueExpired() }
        assertEquals(1, d.level(now = t))
    }

    @Test
    fun `late_ratio 低于阈值保持 L0`() {
        val d = controller()
        repeat(9) { d.onCueApplied(lateMs = 100) }
        repeat(1) { d.onCueExpired() } // 1/10 = 0.1
        assertEquals(0, d.level(now = 1_000L))
    }

    // ------------------------------------------------------------ L1 via gap streak

    @Test
    fun `cmp_cue gap 连续 3 次升到 L1`() {
        val d = controller()
        d.onCueGap()
        d.onCueGap()
        assertEquals(0, d.level(now = 1_000L))
        d.onCueGap()
        assertEquals(1, d.level(now = 1_000L))
    }

    @Test
    fun `cue 成功 accept 清零 gap streak`() {
        val d = controller()
        d.onCueGap()
        d.onCueGap()
        d.onCueGap()
        assertEquals(1, d.level(now = 1_000L))
        d.onCueAccepted(now = 1_000L)
        // late_ratio 还是空窗，gap 清零 → 回落需要 hysteresis；先 force 清再测
        d.forceLevel = null
        // 立刻仍可能 sticky 在 L1；喂足够好窗口
        assertEquals(1, d.level(now = 1_000L)) // sticky
        assertEquals(0, d.level(now = 1_000L + 2_500L))
    }

    // ------------------------------------------------------------ shouldEmit

    @Test
    fun `L1 state 全放 emotion 隔一条 其它丢`() {
        assertTrue(DegradeController.shouldEmit("presentation.state", level = 1, emotionSeq = 0))
        assertTrue(DegradeController.shouldEmit("presentation.emotion", level = 1, emotionSeq = 0))
        assertFalse(DegradeController.shouldEmit("presentation.emotion", level = 1, emotionSeq = 1))
        assertTrue(DegradeController.shouldEmit("presentation.emotion", level = 1, emotionSeq = 2))
        assertFalse(DegradeController.shouldEmit("presentation.gesture", level = 1, emotionSeq = 0))
    }

    @Test
    fun `L0 全放 L2 及以上 presentation 全丢`() {
        assertTrue(DegradeController.shouldEmit("presentation.emotion", level = 0, emotionSeq = 1))
        assertFalse(DegradeController.shouldEmit("presentation.state", level = 2, emotionSeq = 0))
        assertFalse(DegradeController.shouldEmit("presentation.emotion", level = 3, emotionSeq = 0))
        assertFalse(DegradeController.shouldEmit("presentation.emotion", level = 4, emotionSeq = 0))
    }

    // ------------------------------------------------------------ L2 cue silence

    @Test
    fun `cue 静默超过 5s 升到 L2`() {
        val d = controller()
        d.onCueAccepted(now = 1_000L)
        assertEquals(0, d.level(now = 1_000L + 4_999L))
        assertEquals(2, d.level(now = 1_000L + 5_001L))
    }

    @Test
    fun `cue 恢复后经 hysteresis 从 L2 回落`() {
        val d = controller()
        d.onCueAccepted(now = 1_000L)
        assertEquals(2, d.level(now = 1_000L + 5_001L))
        d.onCueAccepted(now = 1_000L + 5_100L)
        // 立刻仍 sticky L2
        assertEquals(2, d.level(now = 1_000L + 5_100L))
        assertEquals(0, d.level(now = 1_000L + 5_100L + 2_500L))
    }

    // ------------------------------------------------------------ L3 / L4

    @Test
    fun `data 死 pcm 活升到 L3`() {
        val d = controller()
        d.onDataAccepted(now = 1_000L)
        d.onPcm(now = 1_000L)
        // data 静默 6s+，pcm 1s 内还活
        d.onPcm(now = 1_000L + 6_500L)
        assertEquals(3, d.level(now = 1_000L + 6_500L))
    }

    @Test
    fun `pcm 死升到 L4`() {
        val d = controller()
        d.onPcm(now = 1_000L)
        d.onDataAccepted(now = 1_000L)
        assertEquals(4, d.level(now = 1_000L + 6_001L))
    }

    @Test
    fun `RECONNECTING 直接 L4`() {
        val d = controller()
        d.onConnection(DegradeController.Conn.RECONNECTING)
        assertEquals(4, d.level(now = 1_000L))
    }

    @Test
    fun `DISCONNECTED 直接 L4`() {
        val d = controller()
        d.onConnection(DegradeController.Conn.DISCONNECTED)
        assertEquals(4, d.level(now = 1_000L))
    }

    @Test
    fun `CONNECTED 且传感器正常回 L0`() {
        val d = controller()
        d.onConnection(DegradeController.Conn.RECONNECTING)
        assertEquals(4, d.level(now = 1_000L))
        d.onConnection(DegradeController.Conn.CONNECTED)
        d.onCueAccepted(now = 1_000L)
        d.onDataAccepted(now = 1_000L)
        d.onPcm(now = 1_000L)
        // 第一次 level 启动 hysteresis 计时；满窗口后才回落
        assertEquals(4, d.level(now = 1_000L))
        assertEquals(0, d.level(now = 1_000L + 2_500L))
    }

    // ------------------------------------------------------------ forceLevel

    @Test
    fun `forceLevel 压过自动判定`() {
        val d = controller()
        assertEquals(0, d.level(now = 1_000L))
        d.forceLevel = 3
        assertEquals(3, d.level(now = 1_000L))
        d.forceLevel = 0
        assertEquals(0, d.level(now = 1_000L))
        d.forceLevel = null
        assertEquals(0, d.level(now = 1_000L))
    }

    @Test
    fun `更高档位优先于 L1`() {
        val d = controller()
        d.onCueGap(); d.onCueGap(); d.onCueGap() // 会触发 L1
        d.onCueAccepted(now = 1_000L)
        assertEquals(2, d.level(now = 1_000L + 5_001L)) // silence → L2 盖过 L1
    }
}
