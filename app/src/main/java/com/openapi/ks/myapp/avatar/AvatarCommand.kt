package com.openapi.ks.myapp.avatar

/**
 * 上层驱动形象的唯一输入，与渲染技术无关：换 Spine / Live2D / Rive 只换
 * [AvatarRenderer] 的实现，事件协议和客户端状态机都不动。
 */
sealed interface AvatarCommand {
    data class SetState(val state: AvatarState) : AvatarCommand

    /** 情绪名（如 "joy"），由 [AvatarSpec.expressions] 解析；没映射就保持当前表情。 */
    data class Emotion(val name: String) : AvatarCommand

    /** 嘴型开合 0..1，来自播放音频的振幅。 */
    data class Mouth(val openness: Float) : AvatarCommand

    /** v1 不实现，渲染器按能力忽略。 */
    data class Gaze(val x: Float, val y: Float) : AvatarCommand

    /** v1 不实现，渲染器按能力忽略。 */
    data class Gesture(val id: String) : AvatarCommand
}

enum class AvatarState { IDLE, LISTENING, THINKING, SPEAKING }

/**
 * 渲染器声明自己支持哪些能力，上层据此决定发不发某类指令。不支持的指令渲染器
 * 静默忽略、不报错——新渲染器可以逐步补齐能力，不必一次做完。
 */
enum class AvatarCapability { STATE, EMOTION, MOUTH, GAZE, GESTURE, TOUCH_DRAG }
