package com.openapi.ks.myapp.companion.protocol

import org.json.JSONObject

/**
 * `cmp.cue` 上的表现指令 → 界面层已经在吃的 v1 事件形状（§11.1）。
 *
 * v2 与 v1 只有一处不兼容：情绪的键叫 `name`，v1 叫 `emotion`。渲染器与 ViewModel
 * 都按 v1 的形状取值，与其让每一层各认两套键，不如在入口处一次性翻译——这样
 * 形象层完全不知道协议升过版。
 *
 * 翻译是**纯函数**：不碰 LiveKit、不抛异常。解析不了的字段返回 null，调用方丢弃
 * 这一条即可（§14.1）。
 */
object PresentationCue {

    /**
     * 服务端能产出的情绪名（与 v1 `to_avatar_events` 的规范名同一张表）。
     *
     * 不认识的取值要落到 `neutral` 而不是原样透传（§11.1）：透传的话渲染器查不到
     * 表达式就静默不动，而用户看到的是「她表情卡住了」——一个 neutral 至少是对的。
     */
    val EMOTIONS: Set<String> = setOf(
        "joy", "sad", "angry", "surprised", "shy",
        "tender", "excited", "afraid", "thinking", "neutral",
    )

    /** 一条解析过的情绪指令。[known] 为 false 表示名字不认识，[name] 已经替换成 neutral。 */
    data class Emotion(val name: String, val known: Boolean)

    /**
     * `presentation.emotion` 的 args：`{"name":"joy","intensity":0.7}`。
     *
     * `intensity` 当前不用：渲染器没有「半强度」的表达，丢掉它比假装支持更好。
     * 缺 `name` 返回 null——一条没有情绪名的 cue 不该触发任何表情变化。
     */
    fun parseEmotion(args: JSONObject): Emotion? {
        val raw = args.optString("name").trim().lowercase()
        if (raw.isEmpty()) return null
        return if (raw in EMOTIONS) Emotion(raw, known = true) else Emotion("neutral", known = false)
    }

    /**
     * P1 契约的状态词汇（docs/design/2026-11-24-state-cancel-contract-v2.md §1）。
     *
     * 与情绪同一口径:不认识的值丢弃而不是透传。渲染器查不到状态词时只是静默不动,
     * 但 UI state 会存下一个两端都不认识的词——「她卡住了」变成查不出来的问题。
     */
    val STATES: Set<String> = setOf("idle", "listening", "thinking", "speaking", "interrupted")

    /**
     * `presentation.state` 的 args：`{"state":"LISTENING"}`，取值大写、不带 `at`（§11.1）。
     *
     * v1 的形状是小写，ViewModel 也按小写存，所以这里统一转小写再交出去。
     */
    fun parseState(args: JSONObject): String? {
        val raw = args.optString("state").trim().lowercase()
        return raw.takeIf { it in STATES }
    }

    /** v1 事件串，供 [com.openapi.ks.myapp.companion.CompanionEvent.AvatarJson] 使用。 */
    fun emotionEventJson(name: String): String =
        """{"v":1,"type":"emotion","emotion":${JSONObject.quote(name)}}"""

    fun stateEventJson(state: String): String =
        """{"v":1,"type":"state","state":${JSONObject.quote(state)}}"""

    /**
     * `presentation.gesture` 的 args：§11.1 示例是 `{"id":"nod","repeat":1}`。
     *
     * 服务端 CueCompiler 也写 `id`；顺带认 `name`，免得旧片段或手写冒烟包被静默丢掉。
     */
    fun parseGesture(args: JSONObject): String? {
        val raw = sequenceOf("id", "name")
            .map { args.optString(it).trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return null
        return raw.lowercase()
    }

    /**
     * `presentation.gaze`：模型侧用简短 name；渲染器要的是方向。
     * 有数值 `x`/`y` 就用数值，否则按常见名字落到预设点，未知名默认看向用户。
     */
    data class Gaze(val name: String, val x: Float, val y: Float)

    fun parseGaze(args: JSONObject): Gaze? {
        val name = args.optString("name").trim().lowercase()
        val hasX = args.has("x")
        val hasY = args.has("y")
        if (name.isEmpty() && !hasX && !hasY) return null
        val preset = GAZE_PRESETS[name] ?: (0f to 0f)
        val x = if (hasX) args.optDouble("x", preset.first.toDouble()).toFloat() else preset.first
        val y = if (hasY) args.optDouble("y", preset.second.toDouble()).toFloat() else preset.second
        val label = name.ifEmpty { "custom" }
        return Gaze(label, x, y)
    }

    fun gestureEventJson(id: String): String =
        """{"v":1,"type":"gesture","id":${JSONObject.quote(id)}}"""

    fun gazeEventJson(name: String, x: Float, y: Float): String =
        JSONObject()
            .put("v", 1)
            .put("type", "gaze")
            .put("name", name)
            .put("x", x.toDouble())
            .put("y", y.toDouble())
            .toString()

    private val GAZE_PRESETS: Map<String, Pair<Float, Float>> = mapOf(
        "look_user" to (0f to 0f),
        "look_at_user" to (0f to 0f),
        "center" to (0f to 0f),
        "look_left" to (-0.5f to 0f),
        "look_right" to (0.5f to 0f),
        "look_up" to (0f to -0.35f),
        "look_down" to (0f to 0.35f),
    )
}
