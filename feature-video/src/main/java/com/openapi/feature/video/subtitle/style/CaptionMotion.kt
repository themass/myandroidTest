package com.openapi.feature.video.subtitle.style

/**
 * 长句放不下的处理计划——纯决策，不碰画布。
 *
 * 输入全是已经量好的数（文本宽、可视宽、行数、行高，单位自洽即可），输出是「这一句怎么动」。
 * 渲染层只管照计划画像素，所有「什么时候该动、动多快」的判断都能在这里被 JVM 测试钉死。
 *
 * 时长不取字幕自身的展示时长（解析层 `LocalSubtitleTrack` 在 main、不归这里动）：
 * 一律按固定阅读速度换算，渲染层在句子还在屏上的时候循环这条计划。
 */
sealed class CaptionPlan {
    /** 静止显示（放得下，或用户就是要静止）。 */
    data object Static : CaptionPlan()

    /** 横向走字：文本从可视区右缘外滚到左缘外，位移 [travel]，耗时 [durationMs]。 */
    data class Marquee(val travel: Float, val durationMs: Long) : CaptionPlan()

    /** 纵向滚动：把多出来的 [travel] 高度按阅读速度自底向上滚完。 */
    data class ScrollUp(val travel: Float, val durationMs: Long) : CaptionPlan()
}

/** 走字速度：每秒走的长度是字号的这个倍数——太快看不清，太慢句子结束了还没走完。 */
internal const val MARQUEE_SPEED_FONT_UNITS = 1.9f

/** 多行滚动的阅读速度：每 [LINE_SCROLL_SECONDS] 秒滚出一行的高度。 */
internal const val LINE_SCROLL_SECONDS = 1.8f

fun planCaptionMotion(
    mode: CaptionMotionMode,
    /** 最长单行文本的实测宽度（与 [viewport] 同单位）。 */
    textWidth: Float,
    /** 可视区宽度。 */
    viewport: Float,
    /** 排版后的总行数。 */
    lineCount: Int,
    /** 最多允许几行（[CaptionStyle.maxLines]）。 */
    maxLines: Int,
    /** 行高（与 [viewport] 同单位）。 */
    lineHeight: Float,
    /** 字号，走字速度按它算。 */
    fontSize: Float,
): CaptionPlan {
    if (mode == CaptionMotionMode.OFF) return CaptionPlan.Static
    if (lineCount > maxLines) {
        val travel = (lineCount - maxLines) * lineHeight
        return CaptionPlan.ScrollUp(travel, (travel / lineHeight * LINE_SCROLL_SECONDS * 1000L).toLong())
    }
    val marquee = textWidth > viewport || (mode == CaptionMotionMode.SCROLL && lineCount <= 1)
    if (marquee) {
        // 起点整行藏在对侧右缘外，终点整行出左缘：位移 = 可视宽 + 文本宽
        val travel = viewport + textWidth
        val duration = (travel / (fontSize * MARQUEE_SPEED_FONT_UNITS) * 1000f).toLong().coerceAtLeast(1200L)
        return CaptionPlan.Marquee(travel, duration)
    }
    return CaptionPlan.Static
}

/**
 * 动画进度：计划已跑了 [elapsedMs]，位移该完成的比例（0..1）。
 * 起头留 10% 时间让人先把句子看个开头，收尾留 10% 让结尾停稳。
 */
fun motionProgress(plan: CaptionPlan, elapsedMs: Long): Float {
    val duration = when (plan) {
        is CaptionPlan.Marquee -> plan.durationMs
        is CaptionPlan.ScrollUp -> plan.durationMs
        CaptionPlan.Static -> return 0f
    }
    val t = (elapsedMs.toFloat() / duration).coerceIn(0f, 1f)
    return ((t - 0.1f) / 0.8f).coerceIn(0f, 1f)
}
