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
     * [declaredAt] 是收包时刻（单调毫秒），写进新建时间线的 `declaredAt`，供端侧
     * 首响计时（`turn_first_audio`）。默认 `null`：调用方不喂就没有这个口径。
     *
     * @return 是否接受这一段。
     */
    @Synchronized
    fun onSeg(args: JSONObject, declaredAt: Long? = null): Boolean {
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
            timeline.declaredAt = declaredAt
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
     * 该给哪条时间线标定 `t0`：**最早声明**、还没开始播的那条。
     *
     * 服务端并行合成按序交付（延迟刀 C3）后，一条远端音轨上会同时在飞多条已声明、
     * 都还没定 `t0` 的句子——下一句的 `speech.seg` 先于上一句的音频播完就到（data
     * channel 比 jitter buffer 快）。服务端按序交付 ⇒ 音频序 == seg 声明序 ⇒ FIFO。
     * 不带 `now` 时不做「上一句是否还在出声」判定（没有钟就没法判），只按声明序取。
     */
    @Synchronized
    fun timebasePendingId(): String? =
        timelines.values.firstOrNull {
            (it.state == SpeechTimeline.State.PENDING || it.state == SpeechTimeline.State.DONE) &&
                it.t0 == null
        }?.speechId

    /**
     * 带钟的归属判定（[onAudioFrame] 用）：在 FIFO 之上加「上一句还在出声就不许换」。
     *
     * 候选 = [timebasePendingId()]。若它前面（声明更早）还有任何一条**已定 t0、未作废**
     * 的时间线在 `now` 时刻仍然「在出声」，这一帧属于那句，返回 `null`：
     *
     * - 「在出声」= `now <` 估算终点（[SpeechTimeline.estimatedEnd]），或静音间隔还没出现；
     * - 出现连续 ≥ [silenceGapMs] 的亚阈值帧后，该句标记为静音收尾——`est_ms` 偏长时
     *   这是唯一的收尾信号。副作用：句内真出现 ≥ 阈值的自然停顿、且下一句 seg 已声明时，
     *   会把停顿后的音频错归给下一句。彻底的解法是服务端按句打流标记（方案文档 §4），
     *   端侧启发式只求 P50 不错位。
     *
     * DONE（end 早到）也算在出声：end 只是服务端生成侧排空，jitter buffer 里还有声音。
     */
    @Synchronized
    fun timebasePendingId(now: Long): String? {
        val candidate = timebasePendingId() ?: return null
        for (tl in timelines.values) {
            if (tl.speechId == candidate) break
            if (tl.t0 == null || tl.state == SpeechTimeline.State.CANCELLED) continue
            if (isSounding(tl, now)) return null
        }
        return candidate
    }

    /**
     * 有「已声明、还没定 `t0`」的时间线吗？transport 每帧调一次的便宜门。
     *
     * 与 [timebasePendingId]`(now)` 的区别：这个不带归属判定——上一句还在出声时
     * 门也必须开着，静音观察要连续（收尾判据靠它）。
     */
    @Synchronized
    fun hasUntimedPending(): Boolean =
        timelines.values.any {
            (it.state == SpeechTimeline.State.PENDING || it.state == SpeechTimeline.State.DONE) &&
                it.t0 == null
        }

    /** 上一条被成功标定 `t0` 的时间线 id（transport 打 `turn_first_audio` 日志要用）。 */
    var lastTimebaseId: String? = null
        private set

    /** 静音收尾阈值：连续亚阈值帧超过这个时长，「在出声」判定提前结束（见 [timebasePendingId]）。 */
    var silenceGapMs: Long = 200L

    private var silentRunStartedAt: Long? = null

    private fun isSounding(tl: SpeechTimeline, now: Long): Boolean {
        if (tl.streamClosed) return false
        val end = tl.estimatedEnd() ?: return false
        return now < end
    }

    /** 每帧先记账：连续静音达到阈值就把最近一条「在出声」的句子判收尾（幂等）。 */
    private fun observeFrame(loud: Boolean, now: Long) {
        if (loud) {
            silentRunStartedAt = null
            return
        }
        val start = silentRunStartedAt
        if (start == null) {
            silentRunStartedAt = now
            return
        }
        if (now - start >= silenceGapMs) {
            // 最近一条已定 t0、未作废、未收尾的时间线 = 刚才在出声的那句。
            val sounding = timelines.values.lastOrNull {
                it.t0 != null && it.state != SpeechTimeline.State.CANCELLED && !it.streamClosed
            }
            sounding?.streamClosed = true
        }
    }

    /** sink 给了采集时间戳（§9.4 首选路径）。 */
    @Synchronized
    fun onAudioStarted(now: Long, nowCaptureTs: Long?, firstCaptureTs: Long?): Boolean {
        val tl = timelineForTimebase() ?: return false
        val ok = if (nowCaptureTs != null && firstCaptureTs != null) {
            tl.startFromCapture(now, nowCaptureTs, firstCaptureTs)
        } else {
            // sink 不给时间戳：退回首帧到达时刻，误差约一个 jitter buffer（§9.4）。
            tl.startNow(now)
        }
        if (ok && tl.t0 != null) lastTimebaseId = tl.speechId
        return ok
    }

    /**
     * 每一帧音频都可以调这里：它自己记住"这句话第一帧的采集时间戳"。
     *
     * §9.4 的公式是 `t0 = now - (nowCapture - firstCapture)`，`firstCapture` 必须是
     * **这句语音**的第一帧。sink 只告诉我们这一帧的采集时刻，所以第一帧得自己记。
     * 记住的时机取"该时间线第一次出现在待标定位置"，这也正是它的首帧。
     *
     * [minRms] 与 [FirstAudioDetector] 同阈：LiveKit 会先推引导静音，把首帧当 t0 会进入假
     * PLAYING，用户一开口就 barge_in → 服务端 TTS `frames=0`（190d782a / 5313185b）。
     * 静音帧同时喂给 [observeFrame] 做「还在出声」的收尾记账，但**不标定** `t0`。
     */
    @Synchronized
    fun onAudioFrame(
        now: Long,
        captureTs: Long?,
        rms: Double = Double.MAX_VALUE,
        minRms: Double = 0.0,
    ): Boolean {
        val loud = rms > minRms
        observeFrame(loud, now)
        if (!loud) return false
        val id = timebasePendingId(now)
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

    /**
     * 举手打断（C7）该作废的那句，取最新一条活的（PENDING ∪ PLAYING）。
     *
     * 与 [activeSpeechId] 的口径差在 PENDING：VAD 自动打断只认 PLAYING——seg 到了但
     * 远端 PCM 还没起时发 barge_in 会自掐 TTS（frames=0，回归 8a7c909e）。用户**明确按键**
     * 是另一回事：这一轮照样该停，「按了没反应」不可接受。DONE / CANCELLED 两边都不算。
     */
    @Synchronized
    fun handInterruptibleSpeechId(): String? =
        timelines.values.lastOrNull {
            it.state == SpeechTimeline.State.PLAYING || it.state == SpeechTimeline.State.PENDING
        }?.speechId

    /**
     * 此刻是否有句 speech 已声明、还没 end/cancel（PENDING ∪ PLAYING）。
     *
     * 降级阶梯的静默传感器要用（§12.3）：cmp.* 只在回合边界前后有包是协议形状本身，
     * 「有没有活动 speech」决定了 cmp 静默是「数据该来没来」（真降级）还是
     * 「她没说、也没人该发」（安静对话）。与 activeSpeechId 不同，PENDING 要算——
     * seg 已声明但 PCM 还没起时，cue 与后续 seg 正是该到的时候。
     */
    @Synchronized
    fun expectsCueData(): Boolean =
        timelines.values.any {
            it.state == SpeechTimeline.State.PENDING || it.state == SpeechTimeline.State.PLAYING
        }

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
        silentRunStartedAt = null
        lastTimebaseId = null
    }

    val pendingCount: Int get() = pending.size
    val timelineCount: Int get() = timelines.size
}
