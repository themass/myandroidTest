package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarCommand

/**
 * 原生侧发进 VRM 场景页（assets/dh-vrm/index.html）的指令。
 * 线格式（JSON 一行）由 VrmBridgeTest 钉死，页面侧 handleSceneCommand 按同一格式解析，
 * 改动必须两边同步。
 */
sealed interface VrmSceneCommand {
    /** VRM 表情名（preset 或自定义 clip，页面侧大小写不敏感匹配）。 */
    data class Expression(val name: String) : VrmSceneCommand

    /** 嘴型开合 0..1。 */
    data class Mouth(val openness: Float) : VrmSceneCommand

    /** 注视目标，-1..1（x 右正，y 上正），驱动头部/视线。 */
    data class Look(val x: Float, val y: Float) : VrmSceneCommand
}

/**
 * AvatarCommand / 拖动 → [VrmSceneCommand] 的纯映射。
 *
 * 情绪表按协议语义「没映射就保持当前表情」返回 null（同 Live2D 适配器），
 * 只有 [AvatarCommand.ClearEmotion] 显式回 neutral。
 */
object VrmBridge {
    /** 我们的情绪词表（PresentationCue.EMOTIONS）→ VRM 表情名。shy 走自定义 clip。 */
    private val EMOTIONS = mapOf(
        "joy" to "happy",
        "sad" to "sad",
        "angry" to "angry",
        "surprised" to "surprised",
        "shy" to "embarrassed",
        "neutral" to "neutral",
    )

    fun sceneCommand(command: AvatarCommand): VrmSceneCommand? = when (command) {
        is AvatarCommand.Emotion -> EMOTIONS[command.name]?.let { VrmSceneCommand.Expression(it) }
        is AvatarCommand.ClearEmotion -> VrmSceneCommand.Expression("neutral")
        is AvatarCommand.Mouth -> VrmSceneCommand.Mouth(command.openness.coerceIn(0f, 1f))
        is AvatarCommand.Gaze -> VrmSceneCommand.Look(command.x.coerceIn(-1f, 1f), command.y.coerceIn(-1f, 1f))
        else -> null
    }

    fun lookFromDrag(x: Float, y: Float): VrmSceneCommand.Look =
        VrmSceneCommand.Look(x.coerceIn(-1f, 1f), y.coerceIn(-1f, 1f))

    /**
     * 发进场景页的完整 JS 调用。JSON 线格式用单引号 JS 字面量包住，
     * `\` 与 `'` 按 JS 规则转义（正常 encode 产物里没有，防御注入）。
     */
    fun jsCall(command: VrmSceneCommand): String =
        "window.vrmCommand('" + encode(command).replace("\\", "\\\\").replace("'", "\\'") + "')"

    fun encode(command: VrmSceneCommand): String = when (command) {
        is VrmSceneCommand.Expression -> """{"type":"expression","name":"${command.name}"}"""
        is VrmSceneCommand.Mouth -> """{"type":"mouth","openness":${command.openness}}"""
        is VrmSceneCommand.Look -> """{"type":"look","x":${command.x},"y":${command.y}}"""
    }
}
