package com.openapi.ks.myapp.avatar

/**
 * 上层驱动形象的唯一输入，与渲染技术无关：换 Spine / Live2D / Rive 只换
 * [AvatarRenderer] 的实现，事件协议和客户端状态机都不动。
 *
 * 指令来自 agent 的**结构化输出**，不靠解析显示文本——文本解析会在多语言、
 * 表情符号嵌套、模型不听话时静默失效，而指令不会。
 */
sealed interface AvatarCommand {
    data class SetState(val state: AvatarState) : AvatarCommand

    /** 情绪名（如 "joy"），由 [AvatarSpec.expressions] 解析；没映射就保持当前表情。 */
    data class Emotion(val name: String) : AvatarCommand

    /** 嘴型开合 0..1，来自播放音频的振幅。 */
    data class Mouth(val openness: Float) : AvatarCommand

    /** 视线方向。P3b 由拖动与 agent 的 look-at 共同驱动；v1 忽略。 */
    data class Gaze(val x: Float, val y: Float) : AvatarCommand

    /** 一次性动作（waving、nodding）。v1 忽略。 */
    data class Gesture(val id: String) : AvatarCommand
}

enum class AvatarState { IDLE, LISTENING, THINKING, SPEAKING }

/**
 * 渲染器声明自己支持哪些能力，上层据此决定发不发某类指令。不支持的指令渲染器
 * 静默忽略、不报错——新渲染器可以逐步补齐能力，不必一次做完。
 */
enum class AvatarCapability { STATE, EMOTION, MOUTH, GAZE, GESTURE, TOUCH_DRAG }

/**
 * 主动回合（她主动找你）的来源。界面据此决定呈现方式：事件提醒与日常问候的
 * 视觉分量不同，电话更是完全不同的界面。
 */
enum class ProactiveKind {
    /** 普通回复，用户刚说了话。 */
    REPLY,

    /** 事件到期：面试前、生日当天。 */
    EVENT,

    /** 周期问候。 */
    GREETING,

    /** 主动来电。 */
    CALL,
}
