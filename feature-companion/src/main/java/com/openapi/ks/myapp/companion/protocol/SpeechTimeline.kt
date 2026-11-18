package com.openapi.ks.myapp.companion.protocol

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一段话的端侧时间线（§9.4）。
 *
 * 端侧只回答一个问题：**"第 i 段的第 X 毫秒，在单调钟上是什么时刻"**。所有
 * `at` 锚点最终都要落到这个坐标上，而服务端算不出来——它只知道 TTS 队列里排了
 * 多少，不知道扬声器里播到了哪（§9.1）。
 *
 * ## 误差不跨段累积，这是本类存在的理由
 *
 * `est_ms` 按字符数估，中文语速 4.5–6 字/秒，误差可达 ±20%，段越多偏得越远
 * （§9.7）。所以这里**不保存"每段的起点"**，而保存"一个锚 + 从锚往后的累计"：
 *
 * ```
 * starts[k+1] = starts[k] + dur[k]
 * ```
 *
 * 一旦第 k 段起点被实测校正（[correctStart]），从 k 往后的全部起点都从新锚重算，
 * 前面累积的误差就此丢弃。这就是 §9.7 的缓解手段 1，也是 F14 的通过条件。
 *
 * `dur[j]` 的取值优先级：实测 > `dur_ms` > `est_ms`。
 */
class SpeechTimeline(
    val speechId: String,
    val turnId: String?,
    segments: List<Segment>,
    val totalEstMs: Long,
    val voice: String? = null,
) {

    /** `speech.begin` 里的一段。`estMs` 必需，`durMs` 可选（§9.3）。 */
    data class Segment(
        val idx: Int,
        val chars: Int,
        val estMs: Long,
        val durMs: Long? = null,
    )

    enum class State { PENDING, PLAYING, DONE, CANCELLED }

    val segments: List<Segment> = segments.sortedBy { it.idx }

    var state: State = State.PENDING
        private set

    /**
     * 该 speech 的第一帧音频进入播放的单调钟时刻（§9.4）。
     *
     * `null` 表示"还没开始播"——此时任何锚在段上的 cue 都**无法**求解，必须缓存
     * 而不是猜一个值。猜出来的值会让 `before` 类动作全部落空，而且落空得悄无声息。
     */
    var t0: Long? = null
        private set

    /** 实测的段时长（PCM 或者语音对齐给出），优先于 `dur_ms` 与 `est_ms`。 */
    private val measured = HashMap<Int, Long>()

    /** 每段的起点。`t0` 确定后才有值；长度恒等于 `segments.size`。 */
    private var starts: LongArray = LongArray(segments.size)

    val size: Int get() = segments.size

    fun contains(idx: Int): Boolean = idx >= 0 && idx < size

    // -------------------------------------------------------------- 起点求解

    /**
     * `t0` 的第一种来源：audio sink 的 `absoluteCaptureTimestampMs`（§9.4）。
     *
     * ```
     * t0 = now_mono - (now_capture - first_capture_ts)
     * ```
     *
     * 这是整个协议里唯一需要跨时钟的地方，所以只做一次。误差被限制在一个 sink
     * 回调周期（通常 < 20 ms）。
     *
     * `firstCaptureTs` 是**这一句话第一帧**的采集时间戳，不是本次回调的。第一次
     * 回调时两者相同，公式退化为 `t0 = nowMono`；之后重复调用得到同一个值，正是
     * 想要的——重复调用不该把时间线越推越远。
     *
     * @return 是否接受了这次标定。已经 `CANCELLED` 的不接受。
     */
    fun startFromCapture(nowMono: Long, nowCaptureTs: Long, firstCaptureTs: Long): Boolean {
        if (state == State.CANCELLED) return false
        return startAt(nowMono - (nowCaptureTs - firstCaptureTs))
    }

    /**
     * `t0` 的第二种来源：sink 不给时间戳时退回首个非零帧的到达时刻（§9.4）。
     *
     * 误差上限约一个 jitter buffer，比第一种大，但总比没有强。
     */
    fun startNow(nowMono: Long): Boolean = startAt(nowMono)

    private fun startAt(t0Mono: Long): Boolean {
        // 作废的时间线不接受 t0。少了这一句，`startNow` 会把 state 从 CANCELLED
        // 改回 PLAYING——墓碑就被一条迟到的音频帧复活了。
        if (state == State.CANCELLED) return false
        if (t0 != null) return true
        t0 = t0Mono
        recomputeFrom(0)
        state = State.PLAYING
        return true
    }

    /**
     * 第 `idx` 段的起点；`t0` 未确定时返回 `null`。
     *
     * `idx == size` 是合法输入，等于"整句话的结尾"——`after` 挂在最后一段时要的
     * 就是这个数。
     */
    fun startOf(idx: Int): Long? {
        val base = t0 ?: return null
        if (idx <= 0) return starts.getOrElse(0) { base }
        if (idx >= size) return starts.getOrElse(size - 1) { base } + durationOf(size - 1)
        return starts[idx]
    }

    /** 第 `idx` 段的结束时刻。越界返回 `null`。 */
    fun endOf(idx: Int): Long? {
        if (!contains(idx)) return null
        return startOf(idx)?.plus(durationOf(idx))
    }

    /** 优先实测，其次 `dur_ms`，最后 `est_ms`（§9.4）。 */
    fun durationOf(idx: Int): Long {
        measured[idx]?.let { return it }
        val seg = segments.getOrNull(idx) ?: return 0L
        return seg.durMs ?: seg.estMs
    }

    // -------------------------------------------------------------- 校正

    /**
     * 实测到第 `idx` 段的时长。只影响它之后的段，不回头改已经发生的。
     */
    fun setMeasuredDuration(idx: Int, durMs: Long) {
        if (!contains(idx) || durMs < 0) return
        measured[idx] = durMs
        if (t0 != null) recomputeFrom(idx)
    }

    /**
     * 把第 `idx` 段的起点重设为 [observedMono]（§9.7 手段 1）。
     *
     * **这是丢弃累积误差的唯一入口。** 第 `idx` 段之前的起点保持不动——那些 cue
     * 要么已经执行、要么已经过期，回头改只会让已经发生的事"重放"一次。
     *
     * 调用方给的是**观测到该段开始的时刻**。`speech.segment.start_boundary` 就是
     * 这样一个观测，但它测的是消息到达而不是扬声器出声，系统性地偏早一个音频
     * 传输时延（几十毫秒量级）。这个偏差比它消除的累积误差小一个数量级，所以值得做；
     * 等端侧有了真正的段边界检测（停顿检测）再换掉来源。
     */
    fun correctStart(idx: Int, observedMono: Long) {
        if (!contains(idx) || t0 == null || state == State.CANCELLED) return
        starts[idx] = observedMono
        recomputeFrom(idx)
    }

    /**
     * 从 `idx` 之后重算。`idx` 自己的起点**不动**——它是新的锚点，重算等于把
     * 刚写入的校正值用旧的累计值冲掉。
     */
    private fun recomputeFrom(idx: Int) {
        if (t0 == null) return
        if (idx <= 0) starts[0] = t0!!
        for (i in maxOf(idx + 1, 1) until size) {
            starts[i] = starts[i - 1] + durationOf(i - 1)
        }
    }

    // -------------------------------------------------------------- 状态迁移

    /** `speech.end`：音频已全部排出。**不取消**尚未到点的 `after` cue。 */
    fun markDone() {
        if (state != State.CANCELLED) state = State.DONE
    }

    /**
     * `control.cancel` / 本地打断：该 id 永久作废（§10.3）。
     *
     * 作废之后不接受任何起点校正，也不再求解——墓碑语义是"这个 id 不会复活"。
     */
    fun markCancelled() {
        state = State.CANCELLED
    }

    companion object {
        /**
         * 解析 `speech.begin` 的 args（§9.3）。
         *
         * `segments` 为空或者字段坏掉返回 `null`——一条坏的 begin 不值得让整条
         * 数据通道停下来，但也不能拿一个空时间线去接后续的 cue。
         */
        fun fromBegin(args: JSONObject): SpeechTimeline? {
            val speechId = (args.opt("speech_id") as? String)?.takeIf { it.isNotBlank() } ?: return null
            val turnId = (args.opt("turn_id") as? String)?.takeIf { it.isNotBlank() }
            val voice = (args.opt("voice") as? String)?.takeIf { it.isNotBlank() }
            val arr = args.opt("segments") as? JSONArray ?: return null
            if (arr.length() == 0) return null

            val segments = ArrayList<Segment>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.opt(i) as? JSONObject ?: return null
                val idx = intOrNull(obj.opt("idx")) ?: return null
                val chars = intOrNull(obj.opt("chars")) ?: 0
                // `est_ms` 必需（§9.3）。缺了就无法给段起点，整条 begin 作废——
                // 与其猜一个时长，不如让这一轮退化成"没有时间锚点"，行为可预测。
                val estMs = longOrNull(obj.opt("est_ms")) ?: return null
                val durMs = longOrNull(obj.opt("dur_ms"))?.takeIf { it >= 0 }
                segments.add(Segment(idx = idx, chars = chars, estMs = estMs.coerceAtLeast(0), durMs = durMs))
            }

            val total = longOrNull(args.opt("total_est_ms"))
                ?: segments.sumOf { it.durMs ?: it.estMs }

            return SpeechTimeline(speechId, turnId, segments, total, voice)
        }

        private fun intOrNull(v: Any?): Int? = when (v) {
            is Int -> v
            is Long -> if (v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else null
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
