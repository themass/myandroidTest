package com.openapi.ks.myapp.companion.protocol

import org.json.JSONObject

/**
 * 端侧 `speech.*` + `control.cancel` + 表现 cue 的运行时状态。
 *
 * 纯 Kotlin、不碰协程也不碰 LiveKit：时间全部由调用方以单调毫秒传入。这样
 * "一条 cue 什么时候执行"能在单测里用假时钟完整走一遍，而不必等真实音频。
 *
 * ## 两条不可逆的规则
 *
 * 1. **`speech_id` 一旦作废，不可复活**（§10.3）。墓碑集合有界（8 条），先进先出。
 *    先 `cancel` 后 `seg` 的乱序（F5）靠它挡住：`seg` 到了发现 id 在墓碑里，
 *    整条丢，时间线不建。
 * 2. **`t0` 定了就不改**（§9.4）。第一帧音频已经播出去了，回头改只会让已经过去的
 *    段重新计算一遍。端侧没有实测校正：起点只按 `est_ms` 累加。
 */
class SpeechRegistry<P>(
    private val maxTimelines: Int = 8,
    private val maxTombstones: Int = 8,
) {

    /** 一条已解析、还没被执行的 cue。`receivedAt` 冻结 `not_before_ms` 的基准。 */
    private data class Pending<P>(
        val cue: ScheduledCue<P>,
        val receivedAt: Long,
        val speechId: String?,
    )

    // LinkedHashMap 保插入顺序：最老的在最前面，淘汰就是 remove 第一个。
    private val timelines = LinkedHashMap<String, SpeechTimeline>()
    private val tombstones = LinkedHashSet<String>()
    private val pending = ArrayList<Pending<P>>()

    /** 计数：进去多少、执行多少、丢了多少。`telemetry.health` 需要（§14.3）。 */
    var cuesApplied: Int = 0
        private set
    var cuesDropped: Int = 0
        private set
    /** 因超过 `max_late_ms` 主动丢弃的次数（§12.3 late_ratio 分子）。 */
    var cuesExpired: Int = 0
        private set
    /** `speech.seg` 被拒（坏参数、段号不连续、时间线已结束或作废）的次数。 */
    var segRejected: Int = 0
        private set
    /** `speech.seg` 的 id 在墓碑里（F5：先 cancel 后 seg）的次数。 */
    var segDroppedTombstone: Int = 0
        private set
    /** 未知 `speech_id` 且 `idx != 0` 的 seg 被丢弃的次数。 */
    var segDroppedUnknown: Int = 0
        private set

    /** 一条 cue 在等 `seg` / `t0` 时最多挂多久。§12.1 给的数是 2 s。 */
    var bufferMs: Long = 2_000L

    // -------------------------------------------------------------- 收事件

    /**
     * `speech.seg`（§9.3）：一句话的声明。
     *
     * - `idx == 0` 且 id 未知：建时间线，接收第一段。
     * - `idx != 0` 且 id 未知：丢弃并计数（`segDroppedUnknown`）。不能为了一条 seg 去建时间线。
     * - id 在墓碑里：丢弃并计数（`segDroppedTombstone`），墓碑不删（F5）。
     * - 段号不连续、时间线已结束或已作废：拒绝并计数（`segRejected`）。
     *
     * @return 是否接受这一段。
     */
    @Synchronized
    fun onSeg(args: JSONObject): Boolean {
        val (speechId, seg) = SpeechTimeline.parseSeg(args) ?: run {
            segRejected++
            return false
        }
        if (speechId in tombstones) {
            segDroppedTombstone++
            return false
        }
        val existing = timelines[speechId]
        if (existing == null) {
            if (seg.idx != 0) {
                segDroppedUnknown++
                return false
            }
            val timeline = SpeechTimeline(speechId)
            timeline.append(seg)
            putTimeline(timeline)
            return true
        }
        if (!existing.append(seg)) {
            segRejected++
            return false
        }
        return true
    }

    /** `speech.end`：服务端 TTS 生成侧排空（§9.3）。**不取消**挂在这一句上、尚未到点的 cue。 */
    @Synchronized
    fun onEnd(args: JSONObject) {
        val speechId = (args.opt("speech_id") as? String)?.takeIf { it.isNotBlank() } ?: return
        timelines[speechId]?.markDone()
    }

    /** `control.cancel`：打进墓碑，并把还没执行的 cue 一次性判死（§10.3）。 */
    @Synchronized
    fun onCancel(args: JSONObject) {
        val speechId = (args.opt("speech_id") as? String)?.takeIf { it.isNotBlank() } ?: return
        timelines[speechId]?.markCancelled()
        tombstones.remove(speechId)      // 重新插入，让它变成最新的
        tombstones.add(speechId)
        while (tombstones.size > maxTombstones) {
            tombstones.remove(tombstones.first())
        }
    }

    /**
     * 收一条带 `at` 的表现 cue。
     *
     * 解析不出锚点的 cue 直接丢（§14.1：一条坏包不影响下一条）。解析得出的进待执行
     * 队列，等 [sweep] 判定。返回是否入队。
     */
    @Synchronized
    fun onCue(
        eventType: String,
        id: String,
        payload: P,
        at: JSONObject?,
        now: Long,
    ): Boolean {
        return when (val parsed = AnchorParser.parse(at, eventType)) {
            is AnchorParser.Parsed.Bad -> {
                cuesDropped++
                false
            }
            is AnchorParser.Parsed.Ok -> {
                val speechId = (parsed.anchor as? Anchor.OnSpeech)?.speechId
                pending.add(Pending(ScheduledCue(eventType, id, parsed.anchor, payload), now, speechId))
                true
            }
        }
    }

    // -------------------------------------------------------------- 音频开始

    /**
     * 该给哪条时间线标定 `t0`：最新一条已收到 seg、还没开始播的。
     *
     * 端侧只有一条远端音频轨道，所以"音频开始播了"这件事没有 `speech_id`。用最新的
     * PENDING 时间线是唯一合理的归属：`speech.seg` 一定早于音频首帧（服务端先发
     * seg 再推流），所以它就在最上面。
     */
    @Synchronized
    fun timebasePendingId(): String? =
        timelines.values.lastOrNull { it.state == SpeechTimeline.State.PENDING && it.t0 == null }?.speechId

    /** sink 给了采集时间戳（§9.4 首选路径）。 */
    @Synchronized
    fun onAudioStarted(now: Long, nowCaptureTs: Long?, firstCaptureTs: Long?): Boolean {
        val tl = timelineForTimebase() ?: return false
        return if (nowCaptureTs != null && firstCaptureTs != null) {
            tl.startFromCapture(now, nowCaptureTs, firstCaptureTs)
        } else {
            // sink 不给时间戳：退回首帧到达时刻，误差约一个 jitter buffer（§9.4）。
            tl.startNow(now)
        }
    }

    /**
     * 每一帧音频都可以调这里：它自己记住"这句话第一帧的采集时间戳"。
     *
     * §9.4 的公式是 `t0 = now - (nowCapture - firstCapture)`，`firstCapture` 必须是
     * **这句语音**的第一帧。sink 只告诉我们这一帧的采集时刻，所以第一帧得自己记。
     * 记住的时机取"该时间线第一次出现在待标定位置"，这也正是它的首帧。
     */
    @Synchronized
    fun onAudioFrame(now: Long, captureTs: Long?): Boolean {
        val id = timebasePendingId()
        if (id == null) {
            armedId = null
            armedFirstCapture = null
            return false
        }
        if (id != armedId) {
            armedId = id
            armedFirstCapture = captureTs
        }
        return onAudioStarted(now, captureTs, armedFirstCapture)
    }

    private var armedId: String? = null
    private var armedFirstCapture: Long? = null

    private fun timelineForTimebase(): SpeechTimeline? =
        timebasePendingId()?.let { timelines[it] }

    // -------------------------------------------------------------- 判定

    /**
     * 把待执行队列过一遍：该做的做、该丢的丢、其余的留着。
     *
     * 返回两个列表而不是直接回调，是为了让调用方自己决定"做"是不是要切到主线程——
     * 形象层的更新必须回到主线程，而判定本身不该关心线程。
     */
    @Synchronized
    fun sweep(now: Long): Sweep<P> {
        if (pending.isEmpty()) return Sweep(emptyList(), emptyList())

        val apply = ArrayList<AppliedCue<P>>()
        val drop = ArrayList<Decision.Drop<P>>()
        val iterator = pending.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val tl = entry.speechId?.let { timelines[it] }

            // 墓碑先判：作废的 speech 上挂的东西一律不做，与锚点解析得出来与否无关。
            if (entry.speechId != null && entry.speechId in tombstones) {
                drop.add(Decision.Drop(entry.cue, Decision.Reason.CANCELLED))
                iterator.remove()
                continue
            }

            val decision = Scheduler.decide(entry.cue, tl, now, entry.receivedAt)
            when (decision) {
                is Decision.Now -> {
                    apply.add(AppliedCue(decision.cue, decision.lateMs))
                    iterator.remove()
                }
                is Decision.Later -> Unit       // 还没到点，留着
                is Decision.Drop -> {
                    val waiting = decision.reason == Decision.Reason.NO_TIMEBASE ||
                        decision.reason == Decision.Reason.NO_ANCHOR
                    if (waiting && now - entry.receivedAt <= bufferMs) {
                        // F3：seg / t0 还没到。先挂着，到点再判。
                        Unit
                    } else {
                        drop.add(decision)
                        iterator.remove()
                    }
                }
            }
        }

        cuesApplied += apply.size
        cuesDropped += drop.size
        cuesExpired += drop.count { it.reason == Decision.Reason.EXPIRED }
        return Sweep(apply, drop)
    }

    /**
     * 下一次需要醒来的时刻；`null` 表示没有东西在等时间。
     *
     * 解不出目标的（等 `seg`、等 `t0`）返回它的**缓冲截止时刻**，不是跳过：调用方
     * 靠这个数准时醒来把过期的那条清掉。跳过它会让 F3 的 2 秒退化成"下次有别的
     * 闹钟再说"，而那种 cue 恰恰是没人会再推动的。
     */
    @Synchronized
    fun nextWakeAt(): Long? {
        var earliest: Long? = null
        for (entry in pending) {
            // `Immediate` 靠入队信号唤醒，不需要定时——给它排个闹钟反而会睡过头。
            if (entry.cue.anchor is Anchor.Immediate) continue
            val tl = entry.speechId?.let { timelines[it] }
            val target = targetOf(entry.cue.anchor, tl, entry.receivedAt) ?: (entry.receivedAt + bufferMs)
            if (earliest == null || target < earliest) earliest = target
        }
        return earliest
    }

    /** 与 [Scheduler.decide][Scheduler] 的解法一致。算不出来（`t0` 未定、越界）返回 `null`。 */
    private fun targetOf(anchor: Anchor, tl: SpeechTimeline?, receivedAt: Long): Long? {
        return when (anchor) {
            is Anchor.Immediate -> null        // 到达即生效，sweep 一跑就做，不需要定时
            is Anchor.Wall -> anchor.atMs
            is Anchor.NotBefore -> receivedAt + anchor.ms
            is Anchor.OnSpeech -> {
                val timeline = tl ?: return null
                val start = timeline.startOf(anchor.seg) ?: return null
                when (anchor.phase) {
                    Phase.BEFORE -> start - anchor.offsetMs
                    Phase.START, Phase.DURING -> start + anchor.offsetMs
                    Phase.AFTER -> timeline.endOf(anchor.seg)?.plus(anchor.offsetMs)
                }
            }
        }
    }

    /** 到点可执行的一条 cue，带上判定时算出的迟到量（P4 late_ratio / 健康上报）。 */
    data class AppliedCue<P>(val cue: ScheduledCue<P>, val lateMs: Long)

    /** 执行结果，见 [sweep]。 */
    data class Sweep<P>(val apply: List<AppliedCue<P>>, val dropped: List<Decision.Drop<P>>)

    // -------------------------------------------------------------- 维护

    fun timeline(speechId: String): SpeechTimeline? = timelines[speechId]

    fun isCancelled(speechId: String): Boolean = speechId in tombstones

    /**
     * 当前正在播的那句（仅 PLAYING），取最新一条。
     *
     * §10.1 本地打断要用：端侧只有一条远端音频轨，"她正在说" = t0 已标定。
     * PENDING 不算——seg 到了但还没远端 PCM，打断会自掐 TTS（frames=0）。
     * DONE / CANCELLED 也不算——说完或作废了就没有可打断的对象。
     */
    @Synchronized
    fun activeSpeechId(): String? =
        timelines.values.lastOrNull {
            it.state == SpeechTimeline.State.PLAYING
        }?.speechId

    /** 已播时长（单调毫秒）。还没标定 t0 时返回 0——服务端把这个字段当提示，不依赖精确值。 */
    @Synchronized
    fun playedMs(speechId: String, now: Long): Long {
        val t0 = timelines[speechId]?.t0 ?: return 0L
        return (now - t0).coerceAtLeast(0L)
    }

    /** 已结束的、且不再有 pending cue 的时间线可以清掉，避免无限涨。 */
    private fun putTimeline(timeline: SpeechTimeline) {
        timelines.remove(timeline.speechId)
        timelines[timeline.speechId] = timeline
        while (timelines.size > maxTimelines) {
            val oldest = timelines.keys.first()
            timelines.remove(oldest)
        }
    }

    /** 重连时调用：时间线全部作废，墓碑保留（`speech_id` 不复用，墓碑无害）。 */
    fun onReconnected() {
        timelines.clear()
        pending.clear()
    }

    val pendingCount: Int get() = pending.size
    val timelineCount: Int get() = timelines.size
}
