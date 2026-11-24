package com.openapi.app.fxlab

/**
 * 教学模式的一步：一条舞台 cue + 视觉锚点 + 一句给用户的讲解文案。
 * 全部本地确定性数据，不模拟真实教学内容或语音（specs §11）。
 */
data class TeachingStep(
    val cue: String,
    val anchor: String,
    val caption: String,
)

/**
 * 教学脚本：四步手动上一步/下一步示例。cue 与生产词表同源
 * （`connected` / `pat_head` / `wave`），锚点用现有 FxAnchors 的名字。
 */
object FxTeachingScript {

    val steps: List<TeachingStep> = listOf(
        TeachingStep(cue = "connected", anchor = "FULL", caption = "讲解开始"),
        TeachingStep(cue = "pat_head", anchor = "HEAD", caption = "指给你看"),
        TeachingStep(cue = "wave", anchor = "HAND", caption = "跟着做"),
        TeachingStep(cue = "pat_head", anchor = "HEAD", caption = "小结"),
    )

    fun next(index: Int): Int = (index + 1).coerceIn(0, steps.lastIndex)

    fun prev(index: Int): Int = (index - 1).coerceIn(0, steps.lastIndex)
}