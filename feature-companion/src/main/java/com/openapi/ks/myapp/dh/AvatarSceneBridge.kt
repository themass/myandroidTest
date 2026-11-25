package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarCommand

/**
 * 官方引擎场景页（Spine / TalkingHead 的 WebView）指令——与 [VrmSceneCommand] 同一族，
 * 但走独立的桥类：页面侧钩子统一是 `window.dhCommand('<json>')`，字段与
 * [AvatarSceneBridge.encode] 一字不差，改动必须两边同步（[AvatarSceneBridgeTest] 钉死）。
 */
sealed interface AvatarSceneCommand {
    /** 引擎原生情绪（TalkingHead 内置 mood 词表）。 */
    data class Expression(val name: String) : AvatarSceneCommand

    /** 嘴型开合 0..1。 */
    data class Mouth(val openness: Float) : AvatarSceneCommand

    /** 注视目标 -1..1（x 右正、y 上正）。 */
    data class Look(val x: Float, val y: Float) : AvatarSceneCommand

    /** 直接点播引擎自己的动画组（Spine 官方动作；词表映射的产物）。 */
    data class Animation(val name: String) : AvatarSceneCommand
}

/** 线格式编码（同 [VrmBridge] 的纪律：浮点用 Kotlin 默认呈现，页面 JSON.parse 直接吃）。 */
object AvatarSceneBridge {

    fun jsCall(command: AvatarSceneCommand): String =
        "window.dhCommand('" + encode(command).replace("\\", "\\\\").replace("'", "\\'") + "')"

    fun encode(command: AvatarSceneCommand): String = when (command) {
        is AvatarSceneCommand.Expression -> """{"type":"expression","name":"${command.name}"}"""
        is AvatarSceneCommand.Mouth -> """{"type":"mouth","openness":${command.openness}}"""
        is AvatarSceneCommand.Look -> """{"type":"look","x":${command.x},"y":${command.y}}"""
        is AvatarSceneCommand.Animation -> """{"type":"animation","name":"${command.name}"}"""
    }
}

/**
 * Spine 官方样例（spineboy）：没有表情/口型参数，能接的只有官方动作组。
 * 词表按 spineboy-pro.json 的实际动画名（aim/death/hoverboard/idle/idle-turn/
 * jump/portal/run/run-to-idle/shoot/walk）；我们的手势词表里没有对应演出的，
 * 一律不发声（null），绝不瞎演。
 */
class SpineSceneBridge {
    private val gestures = mapOf(
        "walk" to "walk",
        "run" to "run",
        "jump" to "jump",
        // 没有挥手动画：转身是官方动作里最接近的演出。
        "wave" to "idle-turn",
    )

    fun sceneCommand(command: AvatarCommand): AvatarSceneCommand? =
        (command as? AvatarCommand.Gesture)?.let { g ->
            gestures[g.id.trim().lowercase()]?.let { AvatarSceneCommand.Animation(it) }
        }
}

/**
 * TalkingHead（MIT vendored）：情绪映射其内置 mood 词表（neutral/happy/angry/sad/
 * fear/disgust）；词表外情绪不发指令 = 保持当前（与 [VrmBridge] 同语义）。
 * 口型页面侧写 viseme/jawOpen 固定值通道，注视走 eyesRotateX/Y 混合通道。
 */
class TalkingHeadSceneBridge {
    private val emotions = mapOf(
        "joy" to "happy",
        "sad" to "sad",
        "angry" to "angry",
        "surprised" to "fear",
        "disgusted" to "disgust",
    )

    fun sceneCommand(command: AvatarCommand): AvatarSceneCommand? = when (command) {
        is AvatarCommand.Emotion -> emotions[command.name]?.let { AvatarSceneCommand.Expression(it) }
        is AvatarCommand.ClearEmotion -> AvatarSceneCommand.Expression("neutral")
        is AvatarCommand.Mouth -> AvatarSceneCommand.Mouth(command.openness.coerceIn(0f, 1f))
        is AvatarCommand.Gaze -> AvatarSceneCommand.Look(command.x.coerceIn(-1f, 1f), command.y.coerceIn(-1f, 1f))
        else -> null
    }
}

/**
 * 场景页指令时序（页面 ready 前排队的通用件，逻辑同 [VrmSceneController]）：
 * 映射由各引擎的 bridge 注入，send 收到的是 `window.dhCommand('…')` 整句 JS。
 */
class AvatarSceneController(
    private val bridge: (AvatarCommand) -> AvatarSceneCommand?,
    private val send: (String) -> Unit,
) {
    private val pending = mutableListOf<AvatarSceneCommand>()
    private var ready = false

    fun onCommand(command: AvatarCommand) {
        val scene = bridge(command) ?: return
        if (ready) send(AvatarSceneBridge.jsCall(scene)) else pending += scene
    }

    fun onPageReady() {
        ready = true
        val queued = pending.toList()
        pending.clear()
        queued.forEach { send(AvatarSceneBridge.jsCall(it)) }
    }
}
