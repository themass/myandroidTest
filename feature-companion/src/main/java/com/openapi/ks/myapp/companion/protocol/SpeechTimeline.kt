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
