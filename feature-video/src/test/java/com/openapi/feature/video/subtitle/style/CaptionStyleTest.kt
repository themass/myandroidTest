package com.openapi.feature.video.subtitle.style

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通用字幕层的纯逻辑测试：样式解析和滚动决策都是无 Android 依赖的函数，全在这里钉死。
 */
class CaptionStyleTest {

    @Test
    fun parseHexColor_acceptsThreeSixAndEightDigitForms() {
        assertEquals(0xFFFFFFFF.toInt(), CaptionStyle.parseHexColor("#FFF"))
        assertEquals(0xFFFFE066.toInt(), CaptionStyle.parseHexColor("#FFE066"))
        assertEquals(0x80FFFFFF.toInt(), CaptionStyle.parseHexColor("#80FFFFFF"))
        // 大小写与首尾空格不敏感
        assertEquals(0xFFFFFFFF.toInt(), CaptionStyle.parseHexColor(" fFf "))
    }

    @Test
    fun parseHexColor_rejectsJunk() {
        assertNull(CaptionStyle.parseHexColor(null))
        assertNull(CaptionStyle.parseHexColor(""))
        assertNull(CaptionStyle.parseHexColor("#12345"))
        assertNull(CaptionStyle.parseHexColor("red"))
    }

    @Test
    fun presets_areDistinctAndIndexed() {
        assertTrue(captionPresets.size >= 4)
        assertEquals(captionPresets.map { it.title }.distinct().size, captionPresets.size)
        // 存取越界一律夹回合法区间，坏数据不能让面板崩
        assertEquals(0, captionPresetIndex(-5))
        assertEquals(captionPresets.lastIndex, captionPresetIndex(999))
    }

    @Test
    fun strokeOnlyWhenNoReadableBackground() {
        val plain = captionPresets.first { it.title == "默认" }.style
        // 有底衬（78% 黑）的预设不叠描边——底衬本身就是可读性方案，别糊字
        assertEquals(0, captionStrokeArgbOf(plain))
        // 「纯净」= 无底衬，必须给描边，否则白字压亮画面不可读（装机踩过）
        val pure = captionPresets.first { it.title == "纯净" }.style
        assertEquals(FALLBACK_STROKE_ARGB, captionStrokeArgbOf(pure))
        // 显式描边优先于兜底
        val explicit = pure.copy(strokeArgb = 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), captionStrokeArgbOf(explicit))
    }

    @Test
    fun nearTransparentBackgroundStillGetsFallbackStroke() {
        // 底衬薄到看不见（<10%）视同无底衬：服务端下发这种样式也不给不可读放行
        val thin = captionPresets.first { it.title == "默认" }.style.copy(backgroundAlpha = .05f)
        assertEquals(FALLBACK_STROKE_ARGB, captionStrokeArgbOf(thin))
    }
}

class CaptionMotionTest {

    private val fit = planCaptionMotion(
        CaptionMotionMode.AUTO, textWidth = 100f, viewport = 400f,
        lineCount = 2, maxLines = 3, lineHeight = 30f, fontSize = 20f,
    )

    @Test
    fun shortCueStaysStill() {
        assertEquals(CaptionPlan.Static, fit)
    }

    @Test
    fun offModeNeverMoves() {
        val plan = planCaptionMotion(
            CaptionMotionMode.OFF, textWidth = 999f, viewport = 400f,
            lineCount = 9, maxLines = 3, lineHeight = 30f, fontSize = 20f,
        )
        assertEquals(CaptionPlan.Static, plan)
    }

    @Test
    fun wideSingleLineMarquees() {
        val plan = planCaptionMotion(
            CaptionMotionMode.AUTO, textWidth = 900f, viewport = 400f,
            lineCount = 1, maxLines = 3, lineHeight = 30f, fontSize = 20f,
        )
        assertTrue(plan is CaptionPlan.Marquee)
        val marquee = plan as CaptionPlan.Marquee
        // 起点在右缘外、终点出左缘：位移 = 可视宽 + 文本宽
        assertEquals(1300f, marquee.travel, 0.001f)
        assertTrue(marquee.durationMs >= 1200L)
    }

    @Test
    fun scrollModeMarqueesEvenWhenItFits() {
        val plan = planCaptionMotion(
            CaptionMotionMode.SCROLL, textWidth = 100f, viewport = 400f,
            lineCount = 1, maxLines = 1, lineHeight = 30f, fontSize = 20f,
        )
        assertTrue(plan is CaptionPlan.Marquee)
        assertEquals(500f, (plan as CaptionPlan.Marquee).travel, 0.001f)
    }

    @Test
    fun tooManyLinesScrollsUpAtReadingSpeed() {
        val plan = planCaptionMotion(
            CaptionMotionMode.AUTO, textWidth = 300f, viewport = 400f,
            lineCount = 5, maxLines = 3, lineHeight = 30f, fontSize = 20f,
        )
        assertTrue(plan is CaptionPlan.ScrollUp)
        val scroll = plan as CaptionPlan.ScrollUp
        // 多出的行数 × 行高
        assertEquals(60f, scroll.travel, 0.001f)
        // 每行 1.8 秒的阅读速度
        assertEquals(3600L, scroll.durationMs)
    }

    @Test
    fun lineOverflowBeatsWidthOverflow() {
        val plan = planCaptionMotion(
            CaptionMotionMode.AUTO, textWidth = 900f, viewport = 400f,
            lineCount = 4, maxLines = 2, lineHeight = 30f, fontSize = 20f,
        )
        assertTrue(plan is CaptionPlan.ScrollUp)
    }

    @Test
    fun progressRampsBetweenHeadAndTail() {
        val plan = CaptionPlan.Marquee(travel = 100f, durationMs = 1000L)
        assertEquals(0f, motionProgress(plan, -10L), 0.001f)
        assertEquals(0f, motionProgress(plan, 50L), 0.001f)      // 起头 10% 不动
        assertEquals(0.5f, motionProgress(plan, 500L), 0.001f)   // 中点
        assertEquals(1f, motionProgress(plan, 950L), 0.001f)     // 收尾 10% 已到顶
        assertEquals(1f, motionProgress(plan, 5_000L), 0.001f)
        assertEquals(0f, motionProgress(CaptionPlan.Static, 500L), 0.001f)
    }
}
