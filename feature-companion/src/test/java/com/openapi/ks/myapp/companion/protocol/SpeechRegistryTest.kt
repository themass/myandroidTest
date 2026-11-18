package com.openapi.ks.myapp.companion.protocol

import org.json.JSONArray
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
 * [SpeechRegistry]：时间线注册、墓碑、待执行队列。
 *
 * 这里测的是**接线语义**，不是调度算术——§9.5 的七条判定已经在
 * [SpeechSchedulerTest] 里锁死了，重复一遍只会让两处慢慢走散。所以这里的断言集中在：
 * 乱序（F5）、缓冲（F3）、以及"作废的东西不能被后面的消息复活"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SpeechRegistryTest {

    private val t0 = 500_000L

    private fun begin(speechId: String = "sp_1", vararg est: Long): JSONObject = JSONObject()
        .put("speech_id", speechId)
        .put("turn_id", "tn_1")
        .put(
            "segments",
            JSONArray().apply {
                est.forEachIndexed { i, ms -> put(JSONObject().put("idx", i).put("chars", 10).put("est_ms", ms)) }
            },
        )
        .put("total_est_ms", est.sum())

    private fun at(json: String): JSONObject = JSONObject(json)

    private fun registry(): SpeechRegistry<String> = SpeechRegistry()

    // ------------------------------------------------------------ 墓碑

    @Test
    fun `先 cancel 后 begin 时 begin 被丢弃`() {
        // F5：control.cancel 比 speech.begin 先到（乱序）。begin 到了必须整条丢，
        // 而不是建一条立刻又被判死的时间线——后者会让 after 类动作仍然挂着。
        val r = registry()
        r.onCancel(JSONObject().put("speech_id", "sp_1"))

        assertNull(r.onBegin(begin("sp_1", 1000)))
        assertEquals(1, r.beginDroppedTombstone)
        assertNull(r.timeline("sp_1"))
        assertEquals(0, r.timelineCount)
    }

    @Test
    fun `cancel 之后 begin 不会把墓碑抹掉`() {
        // 复活一次就会有第二次：begin 不能从墓碑里删。
        val r = registry()
        r.onCancel(JSONObject().put("speech_id", "sp_1"))
        r.onBegin(begin("sp_1", 1000))

        assertTrue(r.isCancelled("sp_1"))
    }

    @Test
    fun `正常顺序 cancel 会把时间线标记作废并清掉挂着的 cue`() {
        val r = registry()
        r.onBegin(begin("sp_1", 1000))
        r.onCue("presentation.emotion", "m1", "happy", at("""{"speech":"sp_1","seg":0,"phase":"start"}"""), now = t0)

        r.onCancel(JSONObject().put("speech_id", "sp_1"))

        assertEquals(SpeechTimeline.State.CANCELLED, r.timeline("sp_1")!!.state)
        val sweep = r.sweep(now = t0 + 5_000)
        assertTrue(sweep.apply.isEmpty())
        assertEquals(1, sweep.dropped.size)
        assertEquals(Decision.Reason.CANCELLED, sweep.dropped[0].reason)
        assertEquals(0, r.pendingCount)
    }

    @Test
    fun `墓碑有界且淘汰最老的`() {
        val r = SpeechRegistry<String>(maxTombstones = 3)
        repeat(4) { r.onCancel(JSONObject().put("speech_id", "sp_$it")) }

        assertFalse(r.isCancelled("sp_0"))
        for (i in 1..3) assertTrue("sp_$i 应仍在墓碑里", r.isCancelled("sp_$i"))
    }

    // ------------------------------------------------------------ F3 缓冲

    @Test
    fun `begin 还没到的 cue 先挂着而不是丢掉`() {
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

        val sweep = r.sweep(now = t0 + 2_100)   // 超窗：begin 一直没来
        assertEquals(1, sweep.dropped.size)
        assertEquals(Decision.Reason.NO_ANCHOR, sweep.dropped[0].reason)
        assertEquals(0, r.pendingCount)
    }

    @Test
    fun `t0 未定也走同一条缓冲逻辑`() {
        // begin 到了但音频还没开始播：此时不能丢，Scheduler 只是还解不出起点。
        val r = registry()
        r.onBegin(begin("sp_1", 1000))
        r.onCue("presentation.emotion", "m1", "happy", at("""{"speech":"sp_1","seg":0,"phase":"start"}"""), now = t0)

        val sweep = r.sweep(now = t0 + 500)
        assertTrue(sweep.dropped.isEmpty())
        assertEquals(1, r.pendingCount)
    }

    @Test
    fun `音频首帧之后缓冲中的 cue 立刻可以判定`() {
        val r = registry()
        r.onBegin(begin("sp_1", 1000))
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
        // cancel 之后音频可能还在播（端侧静音有延迟）。这条帧不能把 state 改回 PLAYING。
        val r = registry()
        r.onBegin(begin("sp_1", 1000))
        r.onCancel(JSONObject().put("speech_id", "sp_1"))

        // 作废后 timebasePendingId 不再返回它（state 不是 PENDING），t0 保持为空。
        assertNull(r.timebasePendingId())
        assertTrue(r.timeline("sp_1")!!.t0 == null)
        assertEquals(SpeechTimeline.State.CANCELLED, r.timeline("sp_1")!!.state)
    }

    // ------------------------------------------------------------ speech.segment

    @Test
    fun `segment 的 dur_ms 会改掉后面段的起点`() {
        val r = registry()
        r.onBegin(begin("sp_1", 1000, 1000))
        r.timeline("sp_1")!!.startNow(t0)

        r.onSegment(JSONObject().put("speech_id", "sp_1").put("idx", 0).put("dur_ms", 1_400), now = t0)
        assertEquals(t0 + 1_400, r.timeline("sp_1")!!.startOf(1))
    }

    @Test
    fun `start_boundary 用到达时刻作段起点观测`() {
        // F14：这是丢掉累积误差的唯一入口。
        val r = registry()
        r.onBegin(begin("sp_1", 1000, 1000, 1000))
        r.timeline("sp_1")!!.startNow(t0)

        // 实际第 1 段到 t0+1300 才开始（估的 1000 偏短）。
        r.onSegment(
            JSONObject().put("speech_id", "sp_1").put("idx", 1).put("start_boundary", true),
            now = t0 + 1_300,
        )
        assertEquals(t0 + 1_300, r.timeline("sp_1")!!.startOf(1))
        assertEquals(t0 + 2_300, r.timeline("sp_1")!!.startOf(2))
    }

    @Test
    fun `未知 speech 或越界段的 segment 被静默忽略`() {
        val r = registry()
        r.onBegin(begin("sp_1", 1000))
        r.onSegment(JSONObject().put("speech_id", "sp_gone").put("idx", 0).put("dur_ms", 500), now = t0)
        r.onSegment(JSONObject().put("speech_id", "sp_1").put("idx", 9).put("dur_ms", 500), now = t0)

        r.timeline("sp_1")!!.startNow(t0)
        assertEquals(t0, r.timeline("sp_1")!!.startOf(0))
    }

    // ------------------------------------------------------------ speech.end

    @Test
    fun `end 之后挂最后一段的 after 仍然按时执行`() {
        // §17 P1 验收：speech.end 之后的 after 动作要准。
        val r = registry()
        r.onBegin(begin("sp_1", 800, 900))
        r.timeline("sp_1")!!.startNow(t0)
        r.onEnd(JSONObject().put("speech_id", "sp_1"))

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
        r.onBegin(begin("sp_1", 1000))
        r.onCancel(JSONObject().put("speech_id", "sp_2"))
        r.onCue("presentation.emotion", "m1", "a", at("""{"not_before_ms":900}"""), t0)

        r.onReconnected()

        assertEquals(0, r.timelineCount)
        assertEquals(0, r.pendingCount)
        assertTrue(r.isCancelled("sp_2"))
    }

    @Test
    fun `时间线数量有界`() {
        val r = SpeechRegistry<String>(maxTimelines = 2)
        repeat(3) { r.onBegin(begin("sp_$it", 1000)) }
        assertEquals(2, r.timelineCount)
        assertNull(r.timeline("sp_0"))       // 最老的被淘汰
        assertNotNull(r.timeline("sp_2"))
    }

    @Test
    fun `同一 speech_id 重复 begin 时用新的时间线`() {
        val r = registry()
        r.onBegin(begin("sp_1", 1000, 1000))
        r.onBegin(begin("sp_1", 500))
        assertEquals(1, r.timelineCount)
        assertEquals(1, r.timeline("sp_1")!!.size)
    }

    // ------------------------------------------------------------ speech.extend（§9.3.1）

    private fun extendArgs(speechId: String, fromIdx: Int, vararg est: Long): JSONObject = JSONObject()
        .put("speech_id", speechId)
        .put(
            "segments",
            JSONArray().apply {
                est.forEachIndexed { i, ms ->
                    put(JSONObject().put("idx", fromIdx + i).put("chars", 10).put("est_ms", ms))
                }
            },
        )

    @Test
    fun `extend 接着编号追加段并把总时长加上去`() {
        val r = registry()
        r.onBegin(begin("sp_1", 1000))
        val tl = r.timeline("sp_1")!!
        tl.startNow(t0)

        assertTrue(r.onExtend(extendArgs("sp_1", 1, 800, 600)))
        assertEquals(3, tl.size)
        assertEquals(2400L, tl.totalEstMs)
        assertEquals(t0 + 1000, tl.startOf(1))
        assertEquals(t0 + 1800, tl.startOf(2))
    }

    @Test
    fun `extend 段号跳号时整批拒绝且时间线不变`() {
        val r = registry()
        r.onBegin(begin("sp_1", 1000))
        val tl = r.timeline("sp_1")!!

        assertFalse(r.onExtend(extendArgs("sp_1", 2, 500)))
        assertEquals(1, tl.size)
        assertEquals(1, r.extendRejected)
    }

    @Test
    fun `未知或已作废的 speech 上的 extend 被忽略`() {
        val r = registry()
        assertFalse(r.onExtend(extendArgs("sp_nope", 0, 500)))

        r.onBegin(begin("sp_1", 1000))
        r.onCancel(JSONObject().put("speech_id", "sp_1"))
        assertFalse(r.onExtend(extendArgs("sp_1", 1, 500)))
        assertEquals(1, r.timeline("sp_1")!!.size)
    }

    @Test
    fun `speech 结束之后的 extend 被拒绝`() {
        val r = registry()
        r.onBegin(begin("sp_1", 1000))
        r.onEnd(JSONObject().put("speech_id", "sp_1"))

        assertFalse(r.onExtend(extendArgs("sp_1", 1, 500)))
        assertEquals(1, r.timeline("sp_1")!!.size)
    }

    @Test
    fun `锚在未声明段上的 cue 等 extend 到达后执行`() {
        // 分批声明的核心理由：流式 LLM 还没产出第 1 句，服务端就已经能对第 0 句发 cue。
        // 但如果某条 cue 引用了段 1，它必须等 extend 进来，而不是被当成 NO_ANCHOR 丢掉。
        val r = registry()
        r.onBegin(begin("sp_1", 1000))
        r.timeline("sp_1")!!.startNow(t0)
        r.onCue("presentation.emotion", "m1", "happy", at("""{"speech":"sp_1","seg":1,"phase":"start"}"""), now = t0 + 1000)

        assertTrue(r.sweep(now = t0 + 1000).apply.isEmpty())
        assertEquals(1, r.pendingCount)

        r.onExtend(extendArgs("sp_1", 1, 1000))
        val swept = r.sweep(now = t0 + 1000)
        assertEquals(listOf("m1"), swept.apply.map { it.id })
        assertEquals(0, r.pendingCount)
    }
}
