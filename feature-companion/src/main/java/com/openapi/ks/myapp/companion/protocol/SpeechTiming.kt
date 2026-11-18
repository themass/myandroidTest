package com.openapi.ks.myapp.companion.protocol

import org.json.JSONObject

/**
 * `at` 时间锚的四种形态（§9.2）。
 *
 * 这是整套协议里唯一一处"什么时候做"的表达。把它做成封闭集合而不是
 * `Map<String, Any>`，是为了让四种形态在编译期就穷尽——将来加第五种（比如锚在
 * 用户输入上）时，`when` 不补分支会直接编译不过，而不是在某个分支里静默当成
 * 立即执行。
 *
 * `max_late_ms` 与锚点同级：它不属于"什么时候"，而属于"晚了还要不要"。
 */
sealed interface Anchor {

    /** 晚于目标时刻超过这么多毫秒就丢弃（§9.5）。 */
    val maxLateMs: Long

    /** 1) 绝对时刻，epoch 毫秒。跨播放器/多端对齐时用，少见。 */
    data class Wall(val atMs: Long, override val maxLateMs: Long) : Anchor

    /** 2) 相对接收：收到之后再等这么久。给"不要立刻，过一会儿"兜底。 */
    data class NotBefore(val ms: Long, override val maxLateMs: Long) : Anchor

    /** 3) 锚在语音段上——主力形态。 */
    data class OnSpeech(
        val speechId: String,
        val seg: Int,
        val phase: Phase,
        val offsetMs: Long,
        override val maxLateMs: Long,
    ) : Anchor

    /** 4) 缺省：到达即生效。只给 `presentation.state` 这类"当前事实"用。 */
    data class Immediate(override val maxLateMs: Long) : Anchor
}

/**
 * `at.phase` 的四个取值（§9.2）。
 *
 * `START` 与 `DURING` 的目标时刻相同（`t_start + offset`），差别只在语义：
 * `during` 允许落在段内任意位置。分开保留是为了回读协议时不丢信息。
 */
enum class Phase { BEFORE, START, DURING, AFTER }

/**
 * `at` 的解析（§9.2）。
 *
 * 解析失败**不抛异常**：一条表现指令写坏了不该让下一条也进不来（§14.1）。返回
 * [Parsed.Bad] 让调用方计一个桶，然后当这条不存在。
 */
object AnchorParser {

    /**
     * `max_late_ms` 的上界。文档只写了"不允许设为无穷"（§10.4），没给数。
     * 取 60 s：比任何一句语音都长，所以它等于"不裁剪"，同时挡住 `max_late_ms:
     * 1e9` 这种把过期保护关掉的写法。
     */
    const val MAX_LATE_CEILING = 60_000L

    /**
     * 缺省截止期（§10.4）。键是事件类型，不是投递类——同是 B 类，
     * 状态错了让人立刻出戏（300 ms），情绪可以晚到还落在同一句里（1500 ms）。
     *
     * A 类（`speech.end` / `control.cancel`）给一个大值而不是无穷：它们靠序号与
     * 墓碑保证不丢，时间窗口只需要"别无限期挂着"。
     */
    fun defaultMaxLate(eventType: String): Long = when (eventType) {
        "presentation.state" -> 300L
        "presentation.gesture" -> 800L
        "presentation.gaze" -> 400L
        "presentation.emotion" -> 1_500L
        "speech.seg", "speech.end", "control.cancel", "presentation.resync" -> 5_000L
        else -> 800L
    }

    sealed interface Parsed {
        data class Ok(val anchor: Anchor) : Parsed
        data class Bad(val reason: String) : Parsed
    }

    fun parse(at: JSONObject?, eventType: String): Parsed {
        val defaultLate = defaultMaxLate(eventType)
        if (at == null || at.length() == 0) return Parsed.Ok(Anchor.Immediate(defaultLate))

        val maxLate = maxLateOf(at, defaultLate)

        // 四种形态按键互斥。同时出现两个属于服务端 bug：不猜优先级，整条丢掉。
        val present = listOf("wall", "not_before_ms", "speech").filter { at.has(it) && !at.isNull(it) }
        if (present.size > 1) return Parsed.Bad("ambiguous at: $present")

        when {
            present.isEmpty() -> return Parsed.Ok(Anchor.Immediate(maxLate))

            present[0] == "wall" -> {
                val wall = numberOrNull(at.opt("wall")) ?: return Parsed.Bad("wall not numeric")
                if (wall <= 0) return Parsed.Bad("wall must be positive epoch ms")
                return Parsed.Ok(Anchor.Wall(wall, maxLate))
            }

            present[0] == "not_before_ms" -> {
                val ms = numberOrNull(at.opt("not_before_ms")) ?: return Parsed.Bad("not_before_ms not numeric")
                if (ms < 0) return Parsed.Bad("not_before_ms negative")
                return Parsed.Ok(Anchor.NotBefore(ms, maxLate))
            }
        }

        val speechId = (at.opt("speech") as? String)?.takeIf { it.isNotBlank() }
            ?: return Parsed.Bad("speech missing")
        val seg = numberOrNull(at.opt("seg"))?.takeIf { it >= 0 && it <= Int.MAX_VALUE }?.toInt()
            ?: return Parsed.Bad("seg invalid")
        val phaseName = (at.opt("phase") as? String)
            ?: return Parsed.Bad("phase missing")
        val phase = phaseOf(phaseName) ?: return Parsed.Bad("phase unknown: $phaseName")

        // 文档规定 `offset_ms` 非负、方向由 `phase` 表达（§9.2）。真收到负数说明
        // 对端是把 `before -200` 当"向后推"写的。钳到 0 而不是丢整条：方向仍然对，
        // 丢掉的只是一个动作。
        val rawOffset = numberOrNull(at.opt("offset_ms")) ?: 0L
        val offset = rawOffset.coerceAtLeast(0L)

        return Parsed.Ok(Anchor.OnSpeech(speechId, seg, phase, offset, maxLate))
    }

    private fun phaseOf(name: String): Phase? = when (name.trim().lowercase()) {
        "before" -> Phase.BEFORE
        "start" -> Phase.START
        "during" -> Phase.DURING
        "after" -> Phase.AFTER
        else -> null
    }

    private fun maxLateOf(at: JSONObject, default: Long): Long {
        val raw = numberOrNull(at.opt("max_late_ms")) ?: return default
        if (raw <= 0) return default
        return raw.coerceAtMost(MAX_LATE_CEILING)
    }

    /**
     * `org.json` 的数字可能是 Int / Long / Double。`Double` 只有在整值时才接受：
     * 800.5 ms 这种输入没有意义，按坏值处理比四舍五入更诚实。
     */
    private fun numberOrNull(value: Any?): Long? = when (value) {
        is Int -> value.toLong()
        is Long -> value
        is Double -> if (value == Math.floor(value) && !value.isInfinite()) value.toLong() else null
        else -> null
    }
}
