package com.openapi.mediaview.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A34 的几何钉子：**轨道就是参数的预览**——所以「哪边大」必须由纯几何保证，
 * 而不是靠画布上碰巧画对。这里钉四件事：
 *
 * 1. [EditSliderTrack.fraction] 单调、夹在 0..1，range 退化不炸；
 * 2. [EditSliderTrack.snap] 与 Material `steps` 同口径（含端点共 steps+1 个位置）；
 * 3. [EditSliderTrack.samples] 含两端点、单调不减（轨道画渐变就靠它）；
 * 4. [EditSliderTrack.visualSizePx] 单调、落在 [minPx, maxPx]，**最细的一端仍看得见**
 *    （这正是用户抱怨「线应该有粗细的减变动过程」的可测形式）。
 */
class EditSliderTrackTest {

    private val brush = 0.003f..0.03f
    private val text = 0.025f..0.12f

    // ---- fraction ----

    @Test
    fun fractionMapsRangeEndsToZeroAndOne() {
        assertEquals(0f, EditSliderTrack.fraction(brush.start, brush), 1e-6f)
        assertEquals(1f, EditSliderTrack.fraction(brush.endInclusive, brush), 1e-6f)
        assertEquals(0.5f, EditSliderTrack.fraction((brush.start + brush.endInclusive) / 2f, brush), 1e-6f)
    }

    @Test
    fun fractionIsMonotonicAndClamped() {
        val values = listOf(-1f, 0f, 0.003f, 0.01f, 0.02f, 0.03f, 5f)
        val fs = values.map { EditSliderTrack.fraction(it, brush) }
        for (i in 1 until fs.size) {
            assertTrue("单调：$values", fs[i] >= fs[i - 1])
            assertTrue("夹在 0..1：$values", fs[i] in 0f..1f)
        }
    }

    @Test
    fun fractionOfDegenerateRangeIsZero() {
        // 不炸、不 NaN：range 两端相等时没有可拖的空间
        assertEquals(0f, EditSliderTrack.fraction(5f, 5f..5f), 1e-6f)
        assertFalseNaN(EditSliderTrack.fraction(5f, 5f..5f))
    }

    // ---- snap（档位化）----

    @Test
    fun snapLandsOnFivePositionsForFourSteps() {
        val snapped = (0..4).map { k ->
            EditSliderTrack.snap(brush.start + (brush.endInclusive - brush.start) * k / 4f, brush, 4)
        }
        for ((k, v) in snapped.withIndex()) {
            val expected = brush.start + (brush.endInclusive - brush.start) * k / 4f
            assertEquals("第 $k 档", expected, v, 1e-6f)
        }
    }

    @Test
    fun snapPicksTheNearestStep() {
        // 档间中点偏哪边就吸哪边；端点外的值先夹回来再吸
        val mid = brush.start + (brush.endInclusive - brush.start) * 0.2f // 第 0.8 档
        assertEquals(
            brush.start + (brush.endInclusive - brush.start) * 1f / 4f,
            EditSliderTrack.snap(mid, brush, 4),
            1e-6f,
        )
        assertEquals(brush.start, EditSliderTrack.snap(-9f, brush, 4), 1e-6f)
        assertEquals(brush.endInclusive, EditSliderTrack.snap(99f, brush, 4), 1e-6f)
    }

    @Test
    fun snapWithZeroStepsOnlyClamps() {
        // 连续模式（SelectionPanel）：不能把值吸成档位，否则破坏原有连续调节语义
        assertEquals(0.0123f, EditSliderTrack.snap(0.0123f, brush, 0), 1e-9f)
        assertEquals(brush.endInclusive, EditSliderTrack.snap(1f, brush, 0), 1e-9f)
    }

    @Test
    fun snappedValueSurvivesRoundTripThroughFraction() {
        // 档位值喂回 fraction 必须落在 k/steps 上：拇指位置与档位文本不会互相漂移
        for (k in 0..4) {
            val v = EditSliderTrack.snap(brush.start + (brush.endInclusive - brush.start) * k / 4f, brush, 4)
            assertEquals(k / 4f, EditSliderTrack.fraction(v, brush), 1e-5f)
        }
    }

    // ---- samples（轨道渐变的取样）----

    @Test
    fun samplesIncludeBothEndsAndGrow() {
        val s = EditSliderTrack.samples(brush, 24)
        assertEquals(24, s.size)
        assertEquals(brush.start, s.first(), 1e-9f)
        assertEquals(brush.endInclusive, s.last(), 1e-9f)
        for (i in 1 until s.size) assertTrue("轨道渐变必须单调变粗：$s", s[i] >= s[i - 1])
    }

    @Test
    fun samplesEdgeCounts() {
        assertEquals(0, EditSliderTrack.samples(brush, 0).size)
        assertEquals(0, EditSliderTrack.samples(brush, -3).size)
        assertEquals(listOf(brush.start), EditSliderTrack.samples(brush, 1))
    }

    @Test
    fun samplesCoverTheFontSizeRangeToo() {
        val s = EditSliderTrack.samples(text, 3)
        assertEquals(listOf(text.start, (text.start + text.endInclusive) / 2f, text.endInclusive), s.map { it.toFloat() })
    }

    // ---- visualSizePx（轨道即预览：数值 → 视觉尺寸）----

    @Test
    fun visualSizeGrowsAcrossTheWholeRange() {
        // 「减变动过程」：每往右拖一点，线宽就变一点，且最右端达到 maxPx
        val minPx = 1f
        val maxPx = 10f
        val sizes = EditSliderTrack.samples(brush, 24).map { EditSliderTrack.visualSizePx(it, brush, maxPx, minPx) }
        for (i in 1 until sizes.size) {
            assertTrue("线宽必须连续增加：$sizes", sizes[i] > sizes[i - 1])
        }
        assertEquals(minPx, sizes.first(), 1e-4f)
        assertEquals(maxPx, sizes.last(), 1e-4f)
    }

    @Test
    fun visualSizeNeverDisappearsAtTheThinEnd() {
        // A34 的底线：最细的一端也得看得见（不能映射成 0px 的隐形线）
        val s = EditSliderTrack.samples(brush, 24).map { EditSliderTrack.visualSizePx(it, brush, 10f, 1f) }
        assertTrue("最细端必须可见：$s", s.min() >= 1f)
    }

    @Test
    fun visualSizeIsClampedInsideItsBudget() {
        for (v in listOf(-100f, 0f, 0.03f, 100f)) {
            val px = EditSliderTrack.visualSizePx(v, brush, 12f, 3f)
            assertTrue("$v → $px 越出 [3,12]", px in 3f..12f)
        }
    }

    @Test
    fun visualSizeHandlesInvertedBudgetWithoutNaN() {
        // min/max 写反不崩不 NaN（映射退化成常数，安全）
        val px = EditSliderTrack.visualSizePx(0.02f, brush, 1f, 10f)
        assertFalseNaN(px)
        assertTrue(px in 1f..10f)
    }

    private fun assertFalseNaN(v: Float) {
        assertTrue("不该是 NaN", !v.isNaN())
    }
}
