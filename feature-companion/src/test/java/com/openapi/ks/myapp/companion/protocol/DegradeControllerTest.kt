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

    // ------------------------------------------------------------ shouldClearEmotion（B3）

    @Test
    fun `进入 L2 的那条边清一次表情，停留期间不重复清`() {
        // 表情在 L0/L1 还能被新 cue 改掉；进 L2 后 cue 全丢，卡住的表情必须收回立绘。
        assertTrue(DegradeController.shouldClearEmotion(prevLevel = 1, level = 2))
        assertTrue(DegradeController.shouldClearEmotion(prevLevel = 0, level = 2))
        assertTrue(DegradeController.shouldClearEmotion(prevLevel = 0, level = 4))
        // 已经在 L2+：早清过了，再清只会打断恢复期的画面。
        assertFalse(DegradeController.shouldClearEmotion(prevLevel = 2, level = 3))
        assertFalse(DegradeController.shouldClearEmotion(prevLevel = 3, level = 2))
        // 回落不是清除时机。
        assertFalse(DegradeController.shouldClearEmotion(prevLevel = 2, level = 0))
        assertFalse(DegradeController.shouldClearEmotion(prevLevel = 1, level = 1))
    }

    // ------------------------------------------------------------ 静默传感器只在「期望数据」时生效
    //
    // 2026-11-23 陪伴通话三通 job 全程自报 L3（104 voice.log `client health … degrade=L3`），
    // 服务端按自报档位闸掉全部 cue。误报机理：陪伴通话里 cmp.* 只在回合边界前后有包，
    // 静默 >6s 是**常态**（长句里 TTS 播 15 秒，cmp 一条不发明也是正常的），而远端轨
    // 保活帧让 PCM 始终「活」——安静被读成 data 死。还有自我强化：L3 → 服务端不发
    // cue → cmp 更静 → L3 锁死。
    // 修复：静默类升档（L2 cue 静默 / L3 data 静默）只在「此刻确实期望 cmp 数据」时才
    // 评估——speech.seg 已声明、还没 end/cancel（onSpeechExpected），且远端音频不在
    // 播（可听帧刷新 onAudio；她在说话时 cmp 静默是解释得通的）。空闲静默最高 L1。

    @Test
    fun `空闲静默不升档——安静对话不是降级`() {
        val d = controller()
        d.onCueAccepted(now = 1_000L)
        d.onDataAccepted(now = 1_000L)
        d.onPcm(now = 1_000L)
        // 之后 30 秒只有 PCM 保活帧：没有 cmp 包，也没有活动 speech。
        d.onPcm(now = 31_000L)
        assertEquals(0, d.level(now = 31_000L))
    }

    @Test
    fun `她在说话时 cmp 静默不升档——长句不是降级`() {
        val d = controller()
        d.onSpeechExpected(true)
        d.onDataAccepted(now = 1_000L)
        d.onCueAccepted(now = 1_000L)
        // TTS 播了 15 秒：可听帧一直来，cmp 一条没有。
        repeat(15) { d.onAudio(now = 1_000L + it * 1_000L); d.onPcm(now = 1_000L + it * 1_000L) }
        assertEquals(0, d.level(now = 15_000L))
    }

    @Test
    fun `期望数据且音频没在播时 data 死仍升 L3`() {
        val d = controller()
        d.onSpeechExpected(true)
        d.onDataAccepted(now = 1_000L)
        d.onCueAccepted(now = 1_000L)
        d.onPcm(now = 1_000L + 6_500L)
        assertEquals(3, d.level(now = 1_000L + 6_500L))
    }

    @Test
    fun `回合结束后解除期望，L3 靠 hysteresis 回落不需要 cmp 包先来`() {
        val d = controller()
        d.onSpeechExpected(true)
        d.onDataAccepted(now = 1_000L)
        d.onCueAccepted(now = 1_000L)
        d.onPcm(now = 1_000L + 6_500L)
        assertEquals(3, d.level(now = 1_000L + 6_500L))
        d.onSpeechExpected(false)
        assertEquals(3, d.level(now = 1_000L + 6_600L)) // sticky 一次
        assertEquals(0, d.level(now = 1_000L + 6_600L + 2_500L))
    }

    // ------------------------------------------------------------ L2 cue silence

    @Test
    fun `cue 静默超过 5s 升到 L2`() {
        val d = controller()
        d.onSpeechExpected(true)
        d.onCueAccepted(now = 1_000L)
        assertEquals(0, d.level(now = 1_000L + 4_999L))
        assertEquals(2, d.level(now = 1_000L + 5_001L))
    }

    @Test
    fun `cue 恢复后经 hysteresis 从 L2 回落`() {
        val d = controller()
        d.onSpeechExpected(true)
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
        d.onSpeechExpected(true)
        d.onDataAccepted(now = 1_000L)
        d.onPcm(now = 1_000L)
        // data 静默 6s+，pcm 1s 内还活
        d.onPcm(now = 1_000L + 6_500L)
        assertEquals(3, d.level(now = 1_000L + 6_500L))
    }

    @Test
    fun `data 死且 pcm 未死但已过 alive 窗口仍是 L3`() {
        // 关闭 (pcmAliveMs, pcmDeadMs] 死区：以前会掉到 L2
        // data@1000，pcm@5000，now@8000 → data 死 7s，pcm 龄 3s（>2s alive，≤6s dead）
        val d = controller()
        d.onSpeechExpected(true)
        d.onDataAccepted(now = 1_000L)
        d.onCueAccepted(now = 1_000L)
        d.onPcm(now = 5_000L)
        assertEquals(3, d.level(now = 8_000L))
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
        d.onSpeechExpected(true)
        d.onCueGap(); d.onCueGap(); d.onCueGap() // 会触发 L1
        d.onCueAccepted(now = 1_000L)
        assertEquals(2, d.level(now = 1_000L + 5_001L)) // silence → L2 盖过 L1
    }
}
