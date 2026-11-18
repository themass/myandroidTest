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
 * [SpeechRegistry]：`speech.seg` 注册、墓碑、待执行队列。
 *
 * 这里测的是**接线语义**，不是调度算术——§9.5 的判定在 [SpeechSchedulerTest] 里。
 * 断言集中在：段号连续（F5 之后的乱序）、墓碑有界（8）、缓冲（F3）、作废不复活。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SpeechRegistryTest {

    private val t0 = 500_000L

    private fun seg(speechId: String = "sp_1", idx: Int, est: Long = 1000): JSONObject = JSONObject()
        .put("speech_id", speechId)
        .put("idx", idx)
        .put("chars", 10)
        .put("est_ms", est)

    private fun cancel(speechId: String = "sp_1"): JSONObject = JSONObject().put("speech_id", speechId)

    private fun at(json: String): JSONObject = JSONObject(json)

    private fun registry(): SpeechRegistry<String> = SpeechRegistry()

    // ------------------------------------------------------------ 本地打断（§10.1 / F11）

    @Test
    fun `activeSpeechId 取最新一条未结束的时间线`() {
        val r = registry()
        assertNull(r.activeSpeechId())
        r.onSeg(seg("sp_1", idx = 0))
        assertEquals("sp_1", r.activeSpeechId())
        r.onSeg(seg("sp_2", idx = 0))
        assertEquals("sp_2", r.activeSpeechId())
        r.onEnd(cancel("sp_2"))
        assertEquals("sp_1", r.activeSpeechId())
        r.onCancel(cancel("sp_1"))
        assertNull(r.activeSpeechId())
    }

    @Test
    fun `本地 barge 与服务端 cancel 一样作废时间线并清掉挂着的 cue`() {
        // F11：打断后该 id 的表现不再执行。端侧自己发起时走同一条 onCancel。
        val r = registry()
        r.onSeg(seg("sp_1", idx = 0))
        r.timeline("sp_1")!!.startNow(t0)
        r.onCue("presentation.emotion", "m1", "happy", at("""{"speech":"sp_1","seg":0,"phase":"start"}"""), now = t0)

        r.onCancel(cancel("sp_1"))

        assertNull(r.activeSpeechId())
        assertTrue(r.isCancelled("sp_1"))
        val sweep = r.sweep(now = t0 + 10)
        assertTrue(sweep.apply.isEmpty())
        assertEquals(Decision.Reason.CANCELLED, sweep.dropped.single().reason)
    }

    @Test
    fun `playedMs 在 t0 标定前为 0，标定后按单调钟差值`() {
        val r = registry()
        r.onSeg(seg("sp_1", idx = 0))
        assertEquals(0L, r.playedMs("sp_1", now = t0 + 500))
        r.timeline("sp_1")!!.startNow(t0)
        assertEquals(500L, r.playedMs("sp_1", now = t0 + 500))
    }

    // ------------------------------------------------------------ 墓碑（F5）

    @Test
    fun `先 cancel 后 seg 时 seg 被丢弃并计入墓碑计数`() {
        val r = registry()
        r.onCancel(cancel("sp_1"))

        assertFalse(r.onSeg(seg("sp_1", idx = 0)))
        assertEquals(1, r.segDroppedTombstone)
        assertNull(r.timeline("sp_1"))
        assertEquals(0, r.timelineCount)
    }

    @Test
    fun `cancel 之后 seg 不会把墓碑抹掉`() {
        val r = registry()
        r.onCancel(cancel("sp_1"))
        r.onSeg(seg("sp_1", idx = 0))

        assertTrue(r.isCancelled("sp_1"))
    }

    @Test
    fun `正常顺序 cancel 会把时间线标记作废并清掉挂着的 cue`() {
        val r = registry()
        r.onSeg(seg("sp_1", idx = 0))
        r.onCue("presentation.emotion", "m1", "happy", at("""{"speech":"sp_1","seg":0,"phase":"start"}"""), now = t0)

        r.onCancel(cancel("sp_1"))

        assertEquals(SpeechTimeline.State.CANCELLED, r.timeline("sp_1")!!.state)
        val sweep = r.sweep(now = t0 + 5_000)
        assertTrue(sweep.apply.isEmpty())
        assertEquals(1, sweep.dropped.size)
        assertEquals(Decision.Reason.CANCELLED, sweep.dropped[0].reason)
        assertEquals(0, r.pendingCount)
    }

    @Test
    fun `墓碑默认上限为 8 且淘汰最老的`() {
        val r = SpeechRegistry<String>()
        repeat(9) { r.onCancel(cancel("sp_$it")) }

        assertFalse(r.isCancelled("sp_0"))
        for (i in 1..8) assertTrue("sp_$i 应仍在墓碑里", r.isCancelled("sp_$i"))
    }

    @Test
    fun `墓碑有界且淘汰最老的（自定义上限）`() {
        val r = SpeechRegistry<String>(maxTombstones = 3)
        repeat(4) { r.onCancel(cancel("sp_$it")) }

        assertFalse(r.isCancelled("sp_0"))
        for (i in 1..3) assertTrue("sp_$i 应仍在墓碑里", r.isCancelled("sp_$i"))
    }

    // ------------------------------------------------------------ speech.seg 的段号

    @Test
    fun `idx 0 建时间线，之后按序追加`() {
        val r = registry()
        assertTrue(r.onSeg(seg("sp_1", idx = 0, est = 1000)))
        assertTrue(r.onSeg(seg("sp_1", idx = 1, est = 800)))
        assertTrue(r.onSeg(seg("sp_1", idx = 2, est = 600)))

        val tl = r.timeline("sp_1")!!
        assertEquals(3, tl.size)
        r.timeline("sp_1")!!.startNow(t0)
        assertEquals(t0 + 1000, tl.startOf(1))
        assertEquals(t0 + 1800, tl.startOf(2))
    }

    @Test
    fun `未知 speech_id 且 idx 不为 0 的 seg 被丢弃并计数`() {
        val r = registry()
        assertFalse(r.onSeg(seg("sp_gone", idx = 3)))
        assertEquals(1, r.segDroppedUnknown)
        assertEquals(0, r.timelineCount)
    }

    @Test
    fun `段号跳号时拒绝且时间线不变`() {
        val r = registry()
        r.onSeg(seg("sp_1", idx = 0))

        assertFalse(r.onSeg(seg("sp_1", idx = 2)))
        assertEquals(1, r.timeline("sp_1")!!.size)
        assertEquals(1, r.segRejected)
    }

    @Test
    fun `重复的段号被拒绝`() {
        val r = registry()
        r.onSeg(seg("sp_1", idx = 0))

        assertFalse(r.onSeg(seg("sp_1", idx = 0)))
        assertEquals(1, r.timeline("sp_1")!!.size)
        assertEquals(1, r.segRejected)
    }

    @Test
    fun `坏参数的 seg 计为拒绝`() {
        val r = registry()
        assertFalse(r.onSeg(JSONObject().put("speech_id", "sp_1").put("idx", 0)))   // 缺 est_ms
        assertEquals(1, r.segRejected)
        assertEquals(0, r.timelineCount)
    }

    @Test
    fun `已 cancel 的时间线不接受 seg`() {
        // 时间线还在表里（墓碑优先判定），但 seg 不能再追加。
        val r = registry()
        r.onSeg(seg("sp_1", idx = 0))
        r.onCancel(cancel("sp_1"))

        assertFalse(r.onSeg(seg("sp_1", idx = 1)))
        assertEquals(1, r.timeline("sp_1")!!.size)
    }

    @Test
    fun `speech 结束之后的 seg 被拒绝`() {
        val r = registry()
        r.onSeg(seg("sp_1", idx = 0))
        r.onEnd(cancel("sp_1"))

        assertFalse(r.onSeg(seg("sp_1", idx = 1)))
        assertEquals(1, r.timeline("sp_1")!!.size)
        assertEquals(1, r.segRejected)
    }

    // ------------------------------------------------------------ F3 缓冲

    @Test
    fun `seg 还没到的 cue 先挂着而不是丢掉`() {
        val r = registry()
        r.onCue("presentation.emotion", "m1", "happy", at("""{"speech":"sp_9","seg":0,"phase":"start"}"""), now = t0)

        assertTrue(r.sweep(now = t0 + 100).let { it.apply.isEmpty() && it.dropped.isEmpty() })
        assertEquals(1, r.pendingCount)
    }

    @Test
    fun `等待超过缓冲窗口就丢掉`() {
        val r = registry()
        r.onCue("presentation.emotion", "m1", "happy", at("""{"speech":"sp_9","seg":0,"phase":"start"}"""), now = t0)

        r.sweep(now = t0 + 1_900)          // 还在窗口内
        assertEquals(1, r.pendingCount)

        val sweep = r.sweep(now = t0 + 2_100)   // 超窗：seg 一直没来
        assertEquals(1, sweep.dropped.size)
        assertEquals(Decision.Reason.NO_ANCHOR, sweep.dropped[0].reason)
        assertEquals(0, r.pendingCount)
    }

    @Test
    fun `t0 未定也走同一条缓冲逻辑`() {
        // seg 到了但音频还没开始播：此时不能丢，Scheduler 只是还解不出起点。
        val r = registry()
        r.onSeg(seg("sp_1", idx = 0))
        r.onCue("presentation.emotion", "m1", "happy", at("""{"speech":"sp_1","seg":0,"phase":"start"}"""), now = t0)

        val sweep = r.sweep(now = t0 + 500)
        assertTrue(sweep.dropped.isEmpty())
        assertEquals(1, r.pendingCount)
    }

    @Test
    fun `音频首帧之后缓冲中的 cue 立刻可以判定`() {
        val r = registry()
        r.onSeg(seg("sp_1", idx = 0))
        r.onCue("presentation.emotion", "m1", "happy", at("""{"speech":"sp_1","seg":0,"phase":"start"}"""), now = t0)

        assertEquals("sp_1", r.timebasePendingId())
        assertTrue(r.onAudioStarted(now = t0, nowCaptureTs = 7_000, firstCaptureTs = 7_000))

        val sweep = r.sweep(now = t0 + 10)
        assertEquals(1, sweep.apply.size)
        assertEquals(0, r.pendingCount)
    }

    @Test
    fun `没有音频时 onAudioStarted 不做事也不崩`() {
        val r = registry()
        assertFalse(r.onAudioStarted(now = t0, nowCaptureTs = null, firstCaptureTs = null))
        assertNull(r.timebasePendingId())
    }

    @Test
    fun `作废的时间线不会被迟到的音频帧标定`() {
        // cancel 之后音频可能还在播。这条帧不能把 state 改回 PLAYING。
        val r = registry()
        r.onSeg(seg("sp_1", idx = 0))
        r.onCancel(cancel("sp_1"))

        assertNull(r.timebasePendingId())
        assertNull(r.timeline("sp_1")!!.t0)
        assertEquals(SpeechTimeline.State.CANCELLED, r.timeline("sp_1")!!.state)
    }

    // ------------------------------------------------------------ speech.end

    @Test
    fun `end 先于首帧音频到达时，迟到的音频不会把时间线拉回播放中`() {
        // speech.end = 服务端 TTS 生成侧排空，不是扬声器播完：短回复常态是 end 先到。
        // 之后第一帧音频进来时，t0 仍然要定（句尾估算 / 挂在这句上的 cue 靠它），但状态不能倒退。
        val r = registry()
        r.onSeg(seg("sp_1", idx = 0, est = 800))
        r.onEnd(cancel("sp_1"))
        assertEquals(SpeechTimeline.State.DONE, r.timeline("sp_1")!!.state)

        assertTrue(r.timeline("sp_1")!!.startNow(t0))

        assertEquals(SpeechTimeline.State.DONE, r.timeline("sp_1")!!.state)
        assertEquals(t0, r.timeline("sp_1")!!.t0)
        // DONE 之后也不再接受新段：这一句已经说完了。
        assertFalse(r.onSeg(seg("sp_1", idx = 1)))
    }

    @Test
    fun `end 之后挂最后一段的 after 仍然按时执行`() {
        // §17 P1 验收：speech.end 之后的 after 动作要准。
        val r = registry()
        r.onSeg(seg("sp_1", idx = 0, est = 800))
        r.onSeg(seg("sp_1", idx = 1, est = 900))
        r.timeline("sp_1")!!.startNow(t0)
        r.onEnd(cancel("sp_1"))

        r.onCue(
            "presentation.emotion", "m1", "relief",
            at("""{"speech":"sp_1","seg":1,"phase":"after","offset_ms":100}"""),
            now = t0 + 1_600,
        )

        // t_end(1) = t0 + 1700，+100 = t0 + 1800。
        assertEquals(t0 + 1_800, r.nextWakeAt())
        assertTrue(r.sweep(now = t0 + 1_700).apply.isEmpty())
        assertEquals(1, r.sweep(now = t0 + 1_800).apply.size)
    }

    // ------------------------------------------------------------ 队列维护

    @Test
    fun `坏 at 直接丢弃并计数`() {
        val r = registry()
        assertFalse(r.onCue("presentation.emotion", "m1", "x", at("""{"speech":"sp","seg":0,"phase":"?"}"""), t0))
        assertEquals(1, r.cuesDropped)
        assertEquals(0, r.pendingCount)
    }

    @Test
    fun `不带 at 的 cue 立即执行`() {
        val r = registry()
        r.onCue("presentation.state", "m1", "idle", null, t0)
        assertEquals(1, r.sweep(now = t0).apply.size)
        assertEquals(1, r.cuesApplied)
    }

    @Test
    fun `nextWakeAt 取最早的目标时刻`() {
        val r = registry()
        r.onCue("presentation.emotion", "m1", "a", at("""{"not_before_ms":900}"""), t0)
        r.onCue("presentation.emotion", "m2", "b", at("""{"not_before_ms":300}"""), t0)
        assertEquals(t0 + 300, r.nextWakeAt())
    }

    @Test
    fun `重连清空时间线与队列但保留墓碑`() {
        val r = registry()
        r.onSeg(seg("sp_1", idx = 0))
        r.onCancel(cancel("sp_2"))
        r.onCue("presentation.emotion", "m1", "a", at("""{"not_before_ms":900}"""), t0)

        r.onReconnected()

        assertEquals(0, r.timelineCount)
        assertEquals(0, r.pendingCount)
        assertTrue(r.isCancelled("sp_2"))
    }

    @Test
    fun `时间线数量有界`() {
        val r = SpeechRegistry<String>(maxTimelines = 2)
        repeat(3) { r.onSeg(seg("sp_$it", idx = 0)) }
        assertEquals(2, r.timelineCount)
        assertNull(r.timeline("sp_0"))       // 最老的被淘汰
        assertNotNull(r.timeline("sp_2"))
    }

    @Test
    fun `锚在未到段上的 cue 等 seg 到达后执行`() {
        // 分批声明的核心理由：服务端对第 0 句发完 seg 就能开始推 cue；引用段 1 的 cue
        // 必须等 seg(1) 进来，而不是被当成 NO_ANCHOR 丢掉。
        val r = registry()
        r.onSeg(seg("sp_1", idx = 0, est = 1000))
        r.timeline("sp_1")!!.startNow(t0)
        r.onCue("presentation.emotion", "m1", "happy", at("""{"speech":"sp_1","seg":1,"phase":"start"}"""), now = t0 + 1000)

        assertTrue(r.sweep(now = t0 + 1000).apply.isEmpty())
        assertEquals(1, r.pendingCount)

        r.onSeg(seg("sp_1", idx = 1, est = 1000))
        val swept = r.sweep(now = t0 + 1000)
        assertEquals(listOf("m1"), swept.apply.map { it.id })
        assertEquals(0, r.pendingCount)
    }
}
