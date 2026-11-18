package com.openapi.ks.myapp.companion.protocol

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `at` 的四种形态 + §9.5 的调度判定 + §9.7 的误差不跨段（§16 F2/F3/F5/F14）。
 *
 * 这一组测试用的是**假单调钟**，不是 `System.nanoTime()`。原因不是洁癖：§9.5 的
 * 分支全部分布在毫秒级窗口的两侧，真时钟会让"晚到但在窗口内"和"晚到超窗"这两条
 * 测试在慢机器上随机互换，而它们恰恰是最需要被锁住的两条。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SpeechSchedulerTest {

    // ------------------------------------------------------------ 构造 helpers

    private fun beginArgs(vararg est: Long): JSONObject = JSONObject()
        .put("speech_id", "sp_7f3a")
        .put("turn_id", "tn_55")
        .put(
            "segments",
            JSONArray().apply {
                est.forEachIndexed { i, ms ->
                    put(JSONObject().put("idx", i).put("chars", 12).put("est_ms", ms).put("dur_ms", JSONObject.NULL))
                }
            },
        )
        .put("total_est_ms", est.sum())
        .put("voice", "zh_female_1")

    private fun timeline(vararg est: Long): SpeechTimeline =
        SpeechTimeline.fromBegin(beginArgs(*est))!!

    private fun at(json: String): Anchor {
        val parsed = AnchorParser.parse(JSONObject(json), "presentation.emotion")
        assertTrue("parse failed: $json", parsed is AnchorParser.Parsed.Ok)
        return (parsed as AnchorParser.Parsed.Ok).anchor
    }

    private fun cue(anchor: Anchor, type: String = "presentation.emotion"): ScheduledCue<String> =
        ScheduledCue(type, "m-1", anchor, "payload")

    private val t0 = 100_000L   // 单调钟上的任意一点，只要不溢出

    // ------------------------------------------------------------ at 四种形态

    @Test
    fun `缺省 at 是到达即生效`() {
        val anchor = AnchorParser.parse(null, "presentation.state")
        assertEquals(Anchor.Immediate(300L), (anchor as AnchorParser.Parsed.Ok).anchor)
    }

    @Test
    fun `wall 是绝对时刻`() {
        assertEquals(Anchor.Wall(1731900000123, 1_500), at("""{"wall":1731900000123}"""))
    }

    @Test
    fun `not_before_ms 从接收到起算`() {
        // 不是从"上一次判定"起算：接线层必须把收包时刻冻结下来，否则这条 cue
        // 会永远差 800 ms 才到点。
        val recv = 1_000L
        val anchor = at("""{"not_before_ms":800}""")
        val decision = Scheduler.decide(cue(anchor), null, nowMono = recv + 300, receivedAtMono = recv)
        assertEquals(recv + 800, (decision as Decision.Later).targetMono)
    }

    @Test
    fun `phase 决定方向而 offset 永远非负`() {
        val tl = timeline(1000, 1000)
        tl.startNow(t0)

        // before: t_start(1) - 120 = t0 + 1000 - 120
        assertEquals(t0 + 880, targetOf(at("""{"speech":"sp_7f3a","seg":1,"phase":"before","offset_ms":120}"""), tl))
        // start / during: t_start(1) + 120
        assertEquals(t0 + 1120, targetOf(at("""{"speech":"sp_7f3a","seg":1,"phase":"start","offset_ms":120}"""), tl))
        assertEquals(t0 + 1120, targetOf(at("""{"speech":"sp_7f3a","seg":1,"phase":"during","offset_ms":120}"""), tl))
        // after: t_end(1) + 120 = t0 + 2000 + 120
        assertEquals(t0 + 2120, targetOf(at("""{"speech":"sp_7f3a","seg":1,"phase":"after","offset_ms":120}"""), tl))
    }

    @Test
    fun `负数 offset 被钳到零而不是丢掉整条`() {
        val tl = timeline(1000)
        tl.startNow(t0)
        val anchor = at("""{"speech":"sp_7f3a","seg":0,"phase":"after","offset_ms":-500}""")
        assertEquals(0L, (anchor as Anchor.OnSpeech).offsetMs)
        assertEquals(t0 + 1000, targetOf(anchor, tl))
    }

    // ------------------------------------------------------------ at 的坏值

    @Test
    fun `一个 at 里出现两个锚是坏值`() {
        val parsed = AnchorParser.parse(
            JSONObject("""{"wall":1,"speech":"sp","seg":0,"phase":"after"}"""),
            "presentation.emotion",
        )
        assertTrue(parsed is AnchorParser.Parsed.Bad)
    }

    @Test
    fun `不认识的 phase 整条作废`() {
        val parsed = AnchorParser.parse(
            JSONObject("""{"speech":"sp","seg":0,"phase":"sometimes"}"""),
            "presentation.emotion",
        )
        assertTrue(parsed is AnchorParser.Parsed.Bad)
    }

    @Test
    fun `负的 seg 是坏值`() {
        assertTrue(
            AnchorParser.parse(
                JSONObject("""{"speech":"sp","seg":-1,"phase":"after"}"""),
                "presentation.emotion",
            ) is AnchorParser.Parsed.Bad
        )
    }

    @Test
    fun `max_late_ms 有上限且不接受负数`() {
        assertEquals(1_500L, at("""{"wall":1}""").maxLateMs)                       // 缺省取 §10.4
        assertEquals(2_000L, at("""{"wall":1,"max_late_ms":2000}""").maxLateMs)    // 服务端覆盖
        assertEquals(1_500L, at("""{"wall":1,"max_late_ms":-1}""").maxLateMs)      // 负数退回缺省
        assertEquals(
            AnchorParser.MAX_LATE_CEILING,
            at("""{"wall":1,"max_late_ms":1000000000}""").maxLateMs,
        )
    }

    @Test
    fun `各事件的缺省截止期不同`() {
        // 同是 B 类，状态 300ms、情绪 1500ms——用一个全局默认就丢掉这个差别。
        assertEquals(300L, AnchorParser.defaultMaxLate("presentation.state"))
        assertEquals(1_500L, AnchorParser.defaultMaxLate("presentation.emotion"))
        assertEquals(800L, AnchorParser.defaultMaxLate("presentation.gesture"))
        assertEquals(400L, AnchorParser.defaultMaxLate("presentation.gaze"))
    }

    private fun targetOf(anchor: Anchor, tl: SpeechTimeline): Long {
        val decision = Scheduler.decide(cue(anchor), tl, nowMono = 0, receivedAtMono = 0)
        return (decision as Decision.Later).targetMono
    }

    // ------------------------------------------------------------ 时间线

    @Test
    fun `段起点由 t0 加累计时长推出`() {
        val tl = timeline(780, 2100, 400)
        tl.startNow(t0)

        assertEquals(t0, tl.startOf(0))
        assertEquals(t0 + 780, tl.startOf(1))
        assertEquals(t0 + 2880, tl.startOf(2))
        assertEquals(t0 + 3280, tl.startOf(3)!!)   // == size：整句话的终点
        assertNull(tl.startOf(4).takeIf { tl.contains(4) })
    }

    @Test
    fun `t0 未确定时不做任何猜测`() {
        val tl = timeline(780, 2100)
        assertNull(tl.startOf(0))

        val decision = Scheduler.decide(
            cue(at("""{"speech":"sp_7f3a","seg":0,"phase":"before","offset_ms":200}""")),
            tl,
            nowMono = t0,
            receivedAtMono = t0,
        )
        // 不是 drop：音频只是还没开始，等 begin 的音频到了再算。
        assertEquals(Decision.Reason.NO_TIMEBASE, (decision as Decision.Drop).reason)
    }

    @Test
    fun `t0 由采集时间戳标定且重复调用不改值`() {
        val tl = timeline(780, 2100)
        // 第一帧在采集侧 ts=50_000，我这边读到它时单调钟是 100_000。
        assertTrue(tl.startFromCapture(nowMono = 100_000, nowCaptureTs = 50_000, firstCaptureTs = 50_000))
        assertEquals(100_000L, tl.t0)

        // 之后的帧带来更大的 capture ts；t0 必须还是同一个数，而不是被推后。
        tl.startFromCapture(nowMono = 100_020, nowCaptureTs = 50_020, firstCaptureTs = 50_000)
        assertEquals(100_000L, tl.t0)
    }

    @Test
    fun `采集时间戳与本地钟有偏差时 t0 被拉回真实起点`() {
        val tl = timeline(780, 2100)
        // 第一帧实际是 40 ms 之前采到的：它已经在播放位置上了。
        tl.startFromCapture(nowMono = 100_000, nowCaptureTs = 50_040, firstCaptureTs = 50_000)
        assertEquals(99_960L, tl.t0)
    }

    @Test
    fun `dur_ms 优先于 est_ms`() {
        val args = beginArgs(780, 2100).apply {
            getJSONArray("segments").getJSONObject(0).put("dur_ms", 500)
        }
        val tl = SpeechTimeline.fromBegin(args)!!
        tl.startNow(t0)
        assertEquals(t0 + 500, tl.startOf(1))
    }

    @Test
    fun `实测时长优先于 dur_ms`() {
        val args = beginArgs(780, 2100).apply {
            getJSONArray("segments").getJSONObject(0).put("dur_ms", 500)
        }
        val tl = SpeechTimeline.fromBegin(args)!!
        tl.startNow(t0)
        tl.setMeasuredDuration(0, 610)
        assertEquals(t0 + 610, tl.startOf(1))
    }

    // ------------------------------------------------------------ F14：误差不跨段

    @Test
    fun `校正后段的起点会把累积误差丢掉`() {
        // 60 段、每段估 1000 ms，实际每段 1200 ms。不校正的话第 40 段会偏 8 秒。
        val tl = SpeechTimeline.fromBegin(
            JSONObject()
                .put("speech_id", "sp_long")
                .put("segments", JSONArray().apply {
                    repeat(60) { put(JSONObject().put("idx", it).put("chars", 10).put("est_ms", 1000)) }
                })
                .put("total_est_ms", 60_000),
        )!!

        tl.startNow(t0)
        assertEquals(t0 + 40_000, tl.startOf(40))

        // 第 39 段的真实起点被观测到了：前面 39 段一共花了 39_000 ms 估，
        // 实际到这里已经是 46_800 ms。
        tl.correctStart(39, t0 + 46_800)

        // 第 39 段本身的起点被修正，且误差**不往回传播**。
        assertEquals(t0 + 46_800, tl.startOf(39))
        assertEquals(t0 + 0, tl.startOf(0))
        // 第 40 段从修正点重新累计：46_800 + 1000。
        assertEquals(t0 + 47_800, tl.startOf(40))
        // 第 45 段 46_800 + 6 * 1000 —— 前 39 段的 7.8 秒误差没有继续压给后面。
        assertEquals(t0 + 52_800, tl.startOf(45))
    }

    @Test
    fun `校正不改变已经过去的段的起点`() {
        val tl = timeline(1000, 1000, 1000)
        tl.startNow(t0)
        tl.correctStart(2, t0 + 2_400)

        assertEquals(t0, tl.startOf(0))
        assertEquals(t0 + 1000, tl.startOf(1))
        assertEquals(t0 + 2_400, tl.startOf(2))
    }

    @Test
    fun `越界的段号校正被忽略`() {
        val tl = timeline(1000)
        tl.startNow(t0)
        tl.correctStart(9, t0 + 5_000)
        assertEquals(t0, tl.startOf(0))
    }

    // ------------------------------------------------------------ §9.5 的七条判定

    @Test
    fun `早到就排期`() {
        val tl = timeline(1000)
        tl.startNow(t0)
        val c = cue(at("""{"speech":"sp_7f3a","seg":0,"phase":"start","offset_ms":0}"""))

        val d = Scheduler.decide(c, tl, nowMono = t0 - 200, receivedAtMono = t0 - 300)
        assertEquals(t0, (d as Decision.Later).targetMono)
    }

    @Test
    fun `正点就立刻做`() {
        val tl = timeline(1000)
        tl.startNow(t0)
        val c = cue(at("""{"speech":"sp_7f3a","seg":0,"phase":"start"}"""))

        val d = Scheduler.decide(c, tl, nowMono = t0, receivedAtMono = t0)
        assertEquals(0L, (d as Decision.Now).lateMs)
    }

    @Test
    fun `晚到但在窗口内就补做`() {
        val tl = timeline(1000)
        tl.startNow(t0)
        val c = cue(at("""{"speech":"sp_7f3a","seg":0,"phase":"start"}"""))   // max_late 1500

        val d = Scheduler.decide(c, tl, nowMono = t0 + 900, receivedAtMono = t0)
        assertEquals(900L, (d as Decision.Now).lateMs)
    }

    @Test
    fun `晚到超窗就丢掉`() {
        // F2：3 秒后才到的 cue 不执行——宁可没有表情，也不要「上一句的表情出现在
        // 这一句上」。
        val tl = timeline(1000)
        tl.startNow(t0)
        val c = cue(at("""{"speech":"sp_7f3a","seg":0,"phase":"start"}"""))

        val d = Scheduler.decide(c, tl, nowMono = t0 + 3_000, receivedAtMono = t0)
        assertEquals(Decision.Reason.EXPIRED, (d as Decision.Drop).reason)
    }

    @Test
    fun `窗口边界上仍然是补做`() {
        val tl = timeline(1000)
        tl.startNow(t0)
        val c = cue(at("""{"speech":"sp_7f3a","seg":0,"phase":"start","max_late_ms":500}"""))

        assertEquals(Decision.Now::class, Scheduler.decide(c, tl, t0 + 500, t0).let { it::class })
        assertEquals(Decision.Drop::class, Scheduler.decide(c, tl, t0 + 501, t0).let { it::class })
    }

    @Test
    fun `已作废的 speech 上任何 cue 都丢掉`() {
        // F5：control.cancel 先到，之后的 cue（哪怕锚点算得出来）一律不做。
        val tl = timeline(1000, 1000)
        tl.startNow(t0)
        tl.markCancelled()

        val anchored = Scheduler.decide(
            cue(at("""{"speech":"sp_7f3a","seg":1,"phase":"after"}""")), tl, t0, t0,
        )
        assertEquals(Decision.Reason.CANCELLED, (anchored as Decision.Drop).reason)

        // 即便不锚在这句话上、且目标时刻还在未来，也不做：墓碑优先于一切。
        val wall = Scheduler.decide(cue(at("""{"wall":${t0 + 5_000}}""")), tl, t0, t0)
        assertEquals(Decision.Reason.CANCELLED, (wall as Decision.Drop).reason)
    }

    @Test
    fun `seg 越界的 cue 被丢掉而不是崩`() {
        val tl = timeline(1000)
        tl.startNow(t0)
        val d = Scheduler.decide(
            cue(at("""{"speech":"sp_7f3a","seg":7,"phase":"after"}""")), tl, t0, t0,
        )
        assertEquals(Decision.Reason.NO_ANCHOR, (d as Decision.Drop).reason)
    }

    @Test
    fun `begin 还没到的不锚 cue 记为没有锚点`() {
        val d = Scheduler.decide(
            cue(at("""{"speech":"sp_unknown","seg":0,"phase":"during"}""")), null, t0, t0,
        )
        assertEquals(Decision.Reason.NO_ANCHOR, (d as Decision.Drop).reason)
    }

    @Test
    fun `不锚语音的 cue 不需要时间线`() {
        val d = Scheduler.decide(cue(at("""{"not_before_ms":100}""")), null, t0 + 200, t0)
        // 目标 t0+100，现在是 t0+200：晚了 100 ms，仍在 1500 ms 窗口内。
        assertEquals(100L, (d as Decision.Now).lateMs)
    }

    // ------------------------------------------------------------ speech.end 之后

    @Test
    fun `speech_end 之后 after 类动作仍然算得准`() {
        // §17 P1 的验收项之一。end 只是「音频全排完了」，不该把时间线作废——
        // 否则挂最后一段的 after（收尾动作）永远落空。
        val tl = timeline(800, 900)
        tl.startNow(t0)
        tl.markDone()
        assertEquals(SpeechTimeline.State.DONE, tl.state)

        val c = cue(at("""{"speech":"sp_7f3a","seg":1,"phase":"after","offset_ms":100}"""))
        // t_end(1) = t0 + 1700，+100 = t0 + 1800。
        assertEquals(t0 + 1_800, (Scheduler.decide(c, tl, t0 + 1_600, t0) as Decision.Later).targetMono)
        assertEquals(Decision.Now::class, Scheduler.decide(c, tl, t0 + 1_800, t0).let { it::class })
    }

    @Test
    fun `end 之后仍可校正段起点`() {
        val tl = timeline(1000, 1000)
        tl.startNow(t0)
        tl.markDone()
        tl.correctStart(1, t0 + 2_000)
        assertEquals(t0 + 3_000, tl.startOf(2)!!)
    }

    @Test
    fun `作废之后不接受起点校正`() {
        // 墓碑语义：这个 id 不会复活。校正一条已作废的时间线等于让它复活。
        val tl = timeline(1000, 1000)
        tl.startNow(t0)
        tl.markCancelled()
        tl.correctStart(1, t0 + 5_000)
        assertEquals(t0 + 1_000, tl.startOf(1))
    }

    // ------------------------------------------------------------ begin 的解析

    @Test
    fun `缺 est_ms 的 begin 整条作废`() {
        val args = JSONObject()
            .put("speech_id", "sp_1")
            .put("segments", JSONArray().put(JSONObject().put("idx", 0).put("chars", 10)))
        assertNull(SpeechTimeline.fromBegin(args))
    }

    @Test
    fun `segments 为空或缺失的 begin 作废`() {
        assertNull(SpeechTimeline.fromBegin(JSONObject().put("speech_id", "sp_1")))
        assertNull(
            SpeechTimeline.fromBegin(
                JSONObject().put("speech_id", "sp_1").put("segments", JSONArray()),
            )
        )
    }

    @Test
    fun `段序乱序到达时按 idx 排好`() {
        val tl = SpeechTimeline.fromBegin(
            JSONObject()
                .put("speech_id", "sp_1")
                .put("segments", JSONArray().apply {
                    put(JSONObject().put("idx", 2).put("est_ms", 300))
                    put(JSONObject().put("idx", 0).put("est_ms", 100))
                    put(JSONObject().put("idx", 1).put("est_ms", 200))
                })
                .put("total_est_ms", 600),
        )!!
        tl.startNow(t0)
        assertEquals(t0 + 300, tl.startOf(2))
    }

    @Test
    fun `total_est_ms 缺失时按段累加`() {
        val tl = SpeechTimeline.fromBegin(
            JSONObject()
                .put("speech_id", "sp_1")
                .put("segments", JSONArray().apply {
                    put(JSONObject().put("idx", 0).put("est_ms", 100))
                    put(JSONObject().put("idx", 1).put("est_ms", 250))
                }),
        )!!
        assertEquals(350L, tl.totalEstMs)
    }
}
