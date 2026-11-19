package com.openapi.ks.myapp.avatar

/**
 * 服务端事件 → [AvatarCommand] 的唯一转换点。
 *
 * 协议只在这里出现：agent 发什么 JSON、渲染器收什么指令，两边各自演进。未知
 * 事件类型、未知取值、缺字段一律返回 null，绝不抛异常——一个没见过的情绪标签
 * 不该让整条消息流断掉。
 */
object AvatarEventMapper {

    private val STATES = mapOf(
        "idle" to AvatarState.IDLE,
        "listening" to AvatarState.LISTENING,
        "thinking" to AvatarState.THINKING,
        "speaking" to AvatarState.SPEAKING,
    )

    /**
     * @param type 事件类型（见设计文档 5.5）：state / emotion / gesture / gaze
     * @param payload 事件内容；按类型取同名或 "emotion" / "id" 字段
     */
    fun map(type: String, payload: Map<String, Any?>): AvatarCommand? = when (type) {
        "state" -> STATES[payload.string("state")?.lowercase()]
            ?.let { state -> AvatarCommand.SetState(state) }

        "emotion" -> payload.string("emotion")
            ?.lowercase()
            ?.let { name -> AvatarCommand.Emotion(name) }

        "gesture" -> payload.string("id")
            ?.lowercase()
            ?.let { id -> AvatarCommand.Gesture(id) }

        "gaze" -> {
            val x = payload.number("x") ?: return null
            val y = payload.number("y") ?: return null
            AvatarCommand.Gaze(x, y)
        }

        // 客户端 → agent 的 touch 等未知类型落在这里：静默丢，不拖消息链。
        else -> null
    }

    /**
     * 从数据通道收到的 JSON 字符串里解出事件，解析失败返回 null。
     *
     * 字符串字段用手解析；gaze 的 x/y 是数字，用同一正则再转 Float。
     * 字段顺序无关，值里的转义引号不在协议内。
     */
    fun mapJson(raw: String): AvatarCommand? {
        val type = field(raw, "type") ?: return null
        return when (type) {
            "state" -> map(type, mapOf("state" to field(raw, "state")))
            "emotion" -> map(type, mapOf("emotion" to field(raw, "emotion")))
            "gesture" -> map(type, mapOf("id" to field(raw, "id")))
            "gaze" -> {
                val x = numberField(raw, "x") ?: return null
                val y = numberField(raw, "y") ?: return null
                map(type, mapOf("x" to x, "y" to y))
            }
            else -> null
        }
    }

    private fun field(raw: String, name: String): String? =
        Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1)

    private fun numberField(raw: String, name: String): Float? {
        val text = Regex("\"$name\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)")
            .find(raw)?.groupValues?.get(1) ?: return null
        return text.toFloatOrNull()
    }

    /** 空串按缺失处理：agent 偶尔会发 `"emotion":""` 表示「没有情绪」。 */
    private fun Map<String, Any?>.string(key: String): String? =
        (this[key] as? String)?.takeIf { it.isNotBlank() }

    private fun Map<String, Any?>.number(key: String): Float? = when (val value = this[key]) {
        is Float -> value.takeIf { it.isFinite() }
        is Double -> value.toFloat().takeIf { it.isFinite() }
        is Int -> value.toFloat()
        is Long -> value.toFloat()
        is Number -> value.toFloat().takeIf { it.isFinite() }
        is String -> value.toFloatOrNull()?.takeIf { it.isFinite() }
        else -> null
    }
}
