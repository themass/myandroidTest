package com.openapi.ks.myapp.companion.protocol

/**
 * 一条待执行的 cue：**什么时候做** + **做什么**。
 *
 * `payload` 故意是泛型而不是具体的 `AvatarCommand`：调度逻辑不该知道形象层长什么样。
 * 形象层在 P1 的接线处把 `presentation.emotion` 翻译成 [Payload.AvatarJson]，调度器
 * 只负责在正确的时刻把它交出去。
 */
data class ScheduledCue<out P>(
    /** 用于日志与计数：`presentation.emotion` / `presentation.gesture`。 */
    val eventType: String,
    /** 信封里的 `id`，用来把"收到了"和"执行了"对上。 */
    val id: String,
    val anchor: Anchor,
    val payload: P,
)

/**
 * §9.5 的调度与裁剪。
 *
 * 整段逻辑只有三个出口：现在做、稍后做、不做。**"不做"必须是主动的、可计数的**，
 * 而不是让 cue 挂在某个 `delay` 里然后随着时间线一起消失——后者的问题是对不齐时
 * 没人知道它去哪了（§16 F2 要的正是"不发声地丢弃"的反面）。
 */
sealed interface Decision<out P> {
    /** 目标时刻还没到。 */
    data class Later<P>(val cue: ScheduledCue<P>, val targetMono: Long) : Decision<P>

    /** 目标时刻已过，但还在 `max_late_ms` 窗口内：补做。 */
    data class Now<P>(val cue: ScheduledCue<P>, val lateMs: Long) : Decision<P>

    /** 不做。`reason` 进 §14.3 的桶。 */
    data class Drop<P>(val cue: ScheduledCue<P>, val reason: Reason) : Decision<P>

    enum class Reason {
        /** `speech_id` 已作废（§10.3 墓碑）。 */
        CANCELLED,

        /** 晚了超过 `max_late_ms`（F2）。 */
        EXPIRED,

        /** `t0` 还没确定，段起点算不出来。**不是丢弃**，见 [Scheduler.enqueue] 的说明。 */
        NO_TIMEBASE,

        /** 段序号越界，或者 `speech.seg` 还没到。 */
        NO_ANCHOR,
    }
}

/**
 * 端侧调度决策器（§9.5）。
 *
 * 纯函数式：给定时间线与当前单调钟，回答"这条 cue 现在怎么办"。不做实际的 `delay`，
 * 不做并发——那是接线层的事。这样 §9.5 的七个用例可以全部用假时钟跑单测。
 *
 * ```
 * for each cue in timeline:
 *     target = resolve(at); now = mono_now()
 *     if speech.state == CANCELLED:            drop(cue, "cancelled")
 *     else if target > now:                    schedule(target)
 *     else if now - target <= cue.maxLateMs:   apply_now(cue)
 *     else:                                    drop(cue, "expired")
 * ```
 */
object Scheduler {

    /**
     * 对一条 cue 做判定。
     *
     * @param timeline 该 cue 锚定的 `speech_id` 的时间线；`wall` / `not_before_ms` /
     *   缺省锚不需要它，可以传 `null`。
     * @param receivedAtMono 收到这条 cue 的单调钟时刻。`not_before_ms` 从**接收**
     *   起算（§9.2"相对接收"），不是从执行起算——否则时钟一慢就会无限往后推。
     */
    fun <P> decide(
        cue: ScheduledCue<P>,
        timeline: SpeechTimeline?,
        nowMono: Long,
        receivedAtMono: Long,
    ): Decision<P> {
        // 墓碑排在一切之前：作废的 speech 上挂的任何东西都不做，包括 wall 锚。
        // 顺序是有意的——如果先解锚点再判状态，一条解析失败的 cue 会报 NO_ANCHOR，
        // 掩盖真正的原因（这句话已经被打断了）。
        if (timeline != null && timeline.state == SpeechTimeline.State.CANCELLED) {
            return Decision.Drop(cue, Decision.Reason.CANCELLED)
        }

        val target: Long = when (val a = cue.anchor) {
            is Anchor.Immediate -> nowMono
            is Anchor.Wall -> a.atMs
            is Anchor.NotBefore -> receivedAtMono + a.ms
            is Anchor.OnSpeech -> {
                val tl = timeline ?: return Decision.Drop(cue, Decision.Reason.NO_ANCHOR)
                if (!tl.contains(a.seg)) return Decision.Drop(cue, Decision.Reason.NO_ANCHOR)
                val start = tl.startOf(a.seg) ?: return Decision.Drop(cue, Decision.Reason.NO_TIMEBASE)
                when (a.phase) {
                    Phase.BEFORE -> start - a.offsetMs
                    Phase.START, Phase.DURING -> start + a.offsetMs
                    // `after` 锚在段尾。最后一段的 `endOf` 就是整句话的终点，
                    // 所以 `speech.end` 之后到达的 `after` 仍然算得准（§17 P1 验收）。
                    Phase.AFTER -> tl.endOf(a.seg)!! + a.offsetMs
                }
            }
        }

        val late = nowMono - target
        return when {
            target > nowMono -> Decision.Later(cue, target)
            late <= cue.anchor.maxLateMs -> Decision.Now(cue, late.coerceAtLeast(0))
            else -> Decision.Drop(cue, Decision.Reason.EXPIRED)
        }
    }

    /**
     * `not_before_ms` 的基准要在**收到**时冻结一次。
     *
     * 直接把 `receivedAtMono` 传进来会在每次重算时拿"现在"当基准，表现为一条
     * `not_before_ms: 800` 的 cue 永远差 800 ms 才到点。接线层应当在收到时算一次
     * `receivedAtMono` 并随 cue 一起存起来。
     */
    fun notBeforeBaseFor(nowMono: Long): Long = nowMono
}
