package com.openapi.ks.myapp.companion.protocol

import org.json.JSONObject

/**
 * 一段话的端侧时间线（§9.4）。
 *
 * 端侧只回答一个问题：**"第 k 段的起点，在单调钟上是什么时刻"**。所有 `{speech, seg}`
 * 锚点最终都落到这个坐标上。服务端不知道扬声器里播到了哪（§9.1），所以起点只能由
 * 端侧按每段有效时长累加推出来：
 *
 * ```
 * t_start(k) = t0 + Σ_{j<k} effectiveMs[j]
 * effectiveMs = dur_ms ?: est_ms
 * ```
 *
 * `dur_ms` 可选（P3）：有则优先，没有就退回 `est_ms`。声明时通常仍只有 `est_ms`；
 * 未知键继续忽略。误差随段数累积的上界见 §9.7。
 *
 * `t0` 只定一次（§9.4），之后不改——第一帧音频已经播出去了。
 */
class SpeechTimeline(val speechId: String) {

    /** `speech.seg` 里的一句。`est_ms` 必需；`dur_ms` 可选（§9.3 / P3）。 */
    data class Segment(
        val idx: Int,
        val chars: Int,
        val estMs: Long,
        val durMs: Long? = null,
    ) {
        /** 时间线累加用的有效时长：有 `dur_ms` 用它，否则 `est_ms`。 */
        val effectiveMs: Long get() = durMs ?: estMs
    }

    enum class State { PENDING, PLAYING, DONE, CANCELLED }

    private val segmentList: MutableList<Segment> = ArrayList()

    /** 每段相对 `t0` 的起点偏移。与 [segmentList] 等长，`rel[k] = Σ_{j<k} effective[j]`。 */
    private val rel: MutableList<Long> = ArrayList()

    /** 到目前为止所有段的有效总时长（= 最后一段的终点偏移）。 */
    private var totalEstMs: Long = 0L

    val segments: List<Segment> get() = segmentList

    val size: Int get() = segmentList.size

    fun contains(idx: Int): Boolean = idx >= 0 && idx < size

    var state: State = State.PENDING
        private set

    /**
     * `speech.seg(idx==0)` 到达的单调时刻（由 [SpeechRegistry.onSeg] 的 `declaredAt` 写入）。
     *
     * 端侧首响计时的起点：`首帧音频归属时刻 − declaredAt` 就是「这句话从声明到出声」
     * 的端上可见段（延迟刀 C3 的验收口径 `turn_first_audio`）。没写入过为 `null`。
     */
    var declaredAt: Long? = null
        internal set

    /**
     * 端侧观测到的「这句的音频流已结束」：连续静音 ≥ 阈值（[SpeechRegistry.silenceGapMs]）
     * 后由归属判定写入。`est_ms` 偏长时这是唯一能提前收尾「还在出声」的信号，
     * 让流水线下句的首帧能提前标定。与 [state] 无关——`state` 只由协议事件驱动。
     */
    var streamClosed: Boolean = false
        internal set

    /**
     * 该 speech 的第一帧音频进入播放的单调钟时刻（§9.4）。`null` 表示还没开始播：
     * 此时段起点算不出来，必须缓存 cue 而不是猜一个值。
     */
    var t0: Long? = null
        private set

    /**
     * 追加一段（`speech.seg`，§9.3）。
     *
     * 段号必须是 `size`（从 0 开始、连续）。跳号或重复说明端侧与服务端对"第 k 段是谁"
     * 的理解已经错位，整段拒绝。`DONE` / `CANCELLED` 后不接受新段。
     *
     * @return 是否接受。
     */
    fun append(seg: Segment): Boolean {
        if (state == State.CANCELLED || state == State.DONE) return false
        if (seg.idx != size) return false
        rel.add(totalEstMs)
        segmentList.add(seg)
        totalEstMs += seg.effectiveMs
        return true
    }

    // -------------------------------------------------------------- 起点求解

    /**
     * `t0` 的第一种来源：audio sink 的采集时间戳（§9.4）。
     *
     * ```
     * t0 = now_mono - (now_capture - first_capture_ts)
     * ```
     *
     * `firstCaptureTs` 是**这一句话第一帧**的采集时间戳。
     *
     * @return 是否接受。已作废的不接受。
     */
    fun startFromCapture(nowMono: Long, nowCaptureTs: Long, firstCaptureTs: Long): Boolean =
        startAt(nowMono - (nowCaptureTs - firstCaptureTs))

    /** `t0` 的第二种来源：sink 不给时间戳时退回首帧到达时刻（§9.4）。 */
    fun startNow(nowMono: Long): Boolean = startAt(nowMono)

    private fun startAt(t0Mono: Long): Boolean {
        // 作废的时间线不接受 t0：否则迟到的音频帧会把 CANCELLED 改回 PLAYING，墓碑就复活了。
        if (state == State.CANCELLED) return false
        if (t0 != null) return true
        t0 = t0Mono
        // `DONE` 之后才到第一帧音频：`speech.end` 排干得比音频起播快（短回复就是常态）。
        // t0 仍要记下——挂在这句上的 `after` cue 靠它算时刻，而 §9.4 的 `speech.end`
        // 明确不取消尚未到点的 `after`。但状态不能倒退：这一句已经说完了，把 DONE 拉回
        // PLAYING 会让「说完了」这件事看起来像没发生，也会让后续的 end/cancel 记账错位。
        if (state != State.DONE) state = State.PLAYING
        return true
    }

    /**
     * 第 `idx` 段的起点；`idx == size` 合法，表示整句话的终点（`after` 挂在最后一段时要的数）。
     * `t0` 未定或越界返回 `null`。
     */
    fun startOf(idx: Int): Long? {
        val base = t0 ?: return null
        return when {
            idx in 0 until size -> base + rel[idx]
            idx == size -> base + totalEstMs
            else -> null
        }
    }

    /** 第 `idx` 段的结束时刻（= 下一段起点）。越界或 `t0` 未定返回 `null`。 */
    fun endOf(idx: Int): Long? {
        if (!contains(idx)) return null
        return startOf(idx)?.plus(segmentList[idx].effectiveMs)
    }

    /**
     * 按估算时长累加出的**播放终点**（`t0 + Σ effective_ms`）。
     *
     * 流式交付下用来回答「这句话理论上还在不在出声」：并行合成的下一句 seg 会先于
     * 上一句的音频播完到达，上一句没被作废、也没过这个点之前，新到的音频帧属于它。
     * `t0` 未定返回 `null`。
     */
    fun estimatedEnd(): Long? = t0?.plus(totalEstMs)

    /**
     * 打断瞬间「实际说出口了多少个字」（P2 契约
     * docs/design/2026-11-24-state-cancel-contract-v2.md §2.1-2）。
     *
     * 字幕截断吃这个数（服务端历史入库用同一口径按 `est_ms` 算）。完整段照收
     * `chars`，正在播的段按 `est_ms` 线性比例取字符，播过头封顶。`est_ms` 是估计值——
     * 这里要的不是精确，而是**不把没说的话留在气泡里**，宁可少留一个字。
     *
     * [playMs] 是相对 `t0` 的已播毫秒（端侧经 `SpeechRegistry.playedMs` 取，`t0`
     * 未定时它给 0）。纯算术，不查 `t0`：还没出声就被打断时传 0，前缀自然是空。
     */
    fun spokenChars(playMs: Long): Int {
        if (playMs <= 0) return 0
        var chars = 0
        for (i in segmentList.indices) {
            val seg = segmentList[i]
            val start = rel[i]
            val dur = seg.effectiveMs
            when {
                playMs >= start + dur -> chars += seg.chars          // 这一段说完了
                playMs <= start -> return chars                       // 还没轮到这一段
                dur == 0L -> return chars + seg.chars                 // 0 时长段瞬间说完
                else -> return chars + (seg.chars * (playMs - start) / dur).toInt()
            }
        }
        return chars
    }

    // -------------------------------------------------------------- 状态迁移

    /**
     * `speech.end`：服务端本轮 TTS **生成侧**已排空（§9.3）。
     *
     * 不是扬声器播完——那条消息常会早于首帧音频到达。标 DONE 后仍可定 `t0`，
     * **不取消**尚未到点的 cue（含挂最后一段的收尾动作）。
     */
    fun markDone() {
        if (state != State.CANCELLED) state = State.DONE
    }

    /** `control.cancel` / 本地打断：该 id 永久作废（§10.3）。 */
    fun markCancelled() {
        state = State.CANCELLED
    }

    companion object {
        /**
         * 解析 `speech.seg` 的 args（§9.3）：`{speech_id, idx, chars, est_ms, dur_ms?}`。
         *
         * 返回 `(speechId, segment)`；缺字段或类型不对返回 `null`。`est_ms` 必需：
         * 没有它就算不出起点，与其猜一个时长，不如整条丢弃。`dur_ms` 可选；
         * 负值或缺类型时忽略（仍用 `est_ms`），不因此丢整段。
         */
        fun parseSeg(args: JSONObject): Pair<String, Segment>? {
            val speechId = (args.opt("speech_id") as? String)?.takeIf { it.isNotBlank() } ?: return null
            val idx = intOrNull(args.opt("idx")) ?: return null
            if (idx < 0) return null
            val chars = intOrNull(args.opt("chars")) ?: 0
            val estMs = longOrNull(args.opt("est_ms")) ?: return null
            if (estMs < 0) return null
            val durMs = longOrNull(args.opt("dur_ms"))?.takeIf { it >= 0 }
            return speechId to Segment(
                idx = idx,
                chars = chars.coerceAtLeast(0),
                estMs = estMs,
                durMs = durMs,
            )
        }

        private fun intOrNull(v: Any?): Int? = when (v) {
            is Int -> v
            is Long -> if (v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else null
            is Double -> if (v == Math.floor(v) && v in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) v.toInt() else null
            else -> null
        }

        private fun longOrNull(v: Any?): Long? = when (v) {
            is Int -> v.toLong()
            is Long -> v
            is Double -> if (v == Math.floor(v) && !v.isInfinite()) v.toLong() else null
            else -> null
        }
    }
}
