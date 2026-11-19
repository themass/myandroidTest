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
     * @param type 事件类型（见设计文档 5.5），v1 只认 "state" 与 "emotion"
     * @param payload 事件内容；按类型取同名或 "emotion" 字段
     */
    fun map(type: String, payload: Map<String, Any?>): AvatarCommand? = when (type) {
        "state" -> mapState(payload.string("state"))
        "emotion" -> mapEmotion(payload.string("emotion"))

        // v1 不实现的能力（视线、手势）与客户端 → agent 的 touch 事件都落在这里。
        else -> null
    }

    /** UI state 里的状态字符串直连渲染器指令；未知/空值返回 null，不兜底成 IDLE。 */
    fun mapState(name: String?): AvatarCommand? =
        name?.lowercase()?.takeIf { it.isNotBlank() }?.let { STATES[it] }
            ?.let { state -> AvatarCommand.SetState(state) }

    /** UI state 里的情绪名直连渲染器指令；空串按「没有情绪」处理。 */
    fun mapEmotion(name: String?): AvatarCommand? =
        name?.lowercase()?.takeIf { it.isNotBlank() }?.let { AvatarCommand.Emotion(it) }

    /**
     * UI state 的 `emotion` 字段 → 渲染器指令，与 [mapEmotion] 的分工：
     * mapper 回答「有没有一条 cue」，这里回答「画面该变成什么」。
     * state 为 null/空串意味着「此刻没有表情」，画面必须回到立绘，
     * 而不是「什么都不做」——否则降级清场后脸上的旧表情永远下不去。
     */
    fun emotionCommand(name: String?): AvatarCommand =
        mapEmotion(name) ?: AvatarCommand.ClearEmotion

    /**
     * 从数据通道收到的 JSON 字符串里解出事件，解析失败返回 null。
     *
     * 只取两个顶层字符串字段，所以不上 Gson：事件体是固定的短结构，手解析不必
     * 为一次映射引入模型类和反射。字段顺序无关，值里的转义引号不在协议内。
     */
    fun mapJson(raw: String): AvatarCommand? {
        val type = field(raw, "type") ?: return null
        val key = when (type) {
            "state" -> "state"
            "emotion" -> "emotion"
            else -> return null
        }
        return map(type, mapOf(key to field(raw, key)))
    }

    private fun field(raw: String, name: String): String? =
        Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1)

    /** 空串按缺失处理：agent 偶尔会发 `"emotion":""` 表示「没有情绪」。 */
    private fun Map<String, Any?>.string(key: String): String? =
        (this[key] as? String)?.takeIf { it.isNotBlank() }
}
