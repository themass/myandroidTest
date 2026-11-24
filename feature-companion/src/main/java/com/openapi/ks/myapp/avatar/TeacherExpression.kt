package com.openapi.ks.myapp.avatar

/**
 * 立绘的「表情」：只映射到 [LayeredPose] 已有的字段（倾头、眯眼、脸红、抬手），
 * **不需要任何新美术**——用的就是 `demo-avatar-v1` 现有 19 层里的眼变体与手层。
 *
 * 这是纯数据 + 纯函数（[teacherPoseTargets]），渲染在 [LayeredAvatarEngine] 里平滑过渡。
 * 默认 [IDLE] 时所有目标都是零，行为与加这个概念之前完全一致（通话页零影响）。
 * 见 docs/design/companion-teaching-demo/redesign-v2-stage-camera.md §1.3。
 */
enum class TeacherExpression {
    /** 待机：呼吸 + 眨眼，无附加姿态。 */
    IDLE,

    /** 倾听：微微歪头。 */
    LISTEN,

    /** 沉吟：反向微倾 + 眯眼。 */
    THINK,

    /** 表扬：笑眯眼 + 轻脸红 + 右手抬起。 */
    PRAISE,

    /** 关切：反向微倾 + 眯眼（幅度比 THINK 小）。 */
    CONCERN,
}

/**
 * 表情要收敛到的姿态增量（参考宽度 [LayeredPose.REF_WIDTH] 下的像素 / 度）。
 * [handLiftPx] 为负 = 向上抬，与 [LayeredPose.handRY] 同号。
 */
data class TeacherPoseTargets(
    val tiltDeg: Float = 0f,
    val eyesHalf: Boolean = false,
    val blush: Float = 0f,
    val handLiftPx: Float = 0f,
)

/** 表情 → 姿态目标。纯函数，JVM 单测钉住（TeachingExpressionTest）。 */
fun teacherPoseTargets(expression: TeacherExpression): TeacherPoseTargets = when (expression) {
    TeacherExpression.IDLE -> TeacherPoseTargets()
    TeacherExpression.LISTEN -> TeacherPoseTargets(tiltDeg = 7f)
    TeacherExpression.THINK -> TeacherPoseTargets(tiltDeg = -6f, eyesHalf = true)
    TeacherExpression.PRAISE -> TeacherPoseTargets(tiltDeg = 0f, eyesHalf = true, blush = 0.45f, handLiftPx = -18f)
    TeacherExpression.CONCERN -> TeacherPoseTargets(tiltDeg = -4f, eyesHalf = true)
}
