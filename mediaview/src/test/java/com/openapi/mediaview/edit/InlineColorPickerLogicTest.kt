package com.openapi.mediaview.edit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A33 取色器的**可测部分**：常驻取色器的 UI（色相光谱条 + HSL 滑杆）靠手势，画布层不测；
 * 但「哪种颜色算自定义」「拇指落点对应哪个色相」是纯逻辑，钉在这里。
 *
 * 用户原话：「应该有个色板选择器，在上面选择任何一种颜色」——落到代码就是两条契约：
 * 1. 色板里放不下的颜色都算「自定义」（[EditSwatches.isCustom]），界面上据此强调预览块；
 * 2. 光谱条任意落点都能产出一个合法色相（[EditSwatches.hueAt]），配合 [hslToColor] 就是
 *    「任何一种颜色」都能被吐出去。
 */
class InlineColorPickerLogicTest {

    // ---- isCustom：色板放不下的就是自定义色 ----

    @Test
    fun everyPresetSwatchIsNotCustom() {
        for (c in EditSwatches.All) {
            assertFalse("预制档不该判成自定义：$c", EditSwatches.isCustom(c))
        }
    }

    @Test
    fun anArbitraryColorIsCustom() {
        // 光谱条中段调出来的典型色：不在 16 档里
        val arbitrary = Color(0xFF3FA85C)
        assertTrue("任意色应判为自定义：$arbitrary", EditSwatches.isCustom(arbitrary))
    }

    @Test
    fun customIsDecidedByExactArgbNotByName() {
        // 与某个预制档 argb 完全相等 → 不是自定义（本来就是同一个颜色，撞名无妨）
        val sameAsRed = Color(EditSwatches.Red.toArgb())
        assertFalse(EditSwatches.isCustom(sameAsRed))
        // 差一个通道 1/255 就是另一个颜色 → 自定义
        val nearRed = Color(
            red = (EditSwatches.Red.red + 0.01f).coerceIn(0f, 1f),
            green = EditSwatches.Red.green,
            blue = EditSwatches.Red.blue,
        )
        assertTrue(EditSwatches.isCustom(nearRed))
    }

    // ---- hueAt：光谱条落点 → 色相，且首尾都是红 ----

    @Test
    fun hueAtMapsEndpointsToRedOnBothSides() {
        assertEquals(0f, EditSwatches.hueAt(0f), 1e-4f)
        assertEquals(360f, EditSwatches.hueAt(1f), 1e-4f)
        // 0° 与 360° 在 hslToColor 里必须是同一个颜色，光谱条首尾才接得上
        assertEquals(hslToColor(0f, 100f, 50f).toArgb(), hslToColor(360f, 100f, 50f).toArgb())
    }

    @Test
    fun hueAtIsMonotonicAcrossTheBar() {
        val hues = (0..100).map { EditSwatches.hueAt(it / 100f) }
        for (i in 1 until hues.size) {
            assertTrue("光谱条必须单调扫过色相：$hues", hues[i] >= hues[i - 1])
        }
        // 覆盖整圈：从 0 扫到 360
        assertEquals(0f, hues.first(), 1e-4f)
        assertEquals(360f, hues.last(), 1e-4f)
    }

    @Test
    fun hueAtClampsOutOfRangeTouch() {
        // 手指按出轨道（负 x / 超宽）也要给出合法色相，不能产 NaN 或出界
        for (f in listOf(-1f, -0.5f, 1.5f, 2f)) {
            val h = EditSwatches.hueAt(f)
            assertTrue("色相必须落在 0..360：$f → $h", h in 0f..360f)
        }
    }

    /**
     * 「任何一种颜色」的可测形式：光谱条均匀取点，逐点喂 [hslToColor]，
     * 每一站都是合法不透明颜色，且相邻站之间色相确实变化（不是一片死色）。
     */
    @Test
    fun everyPointOnTheSpectrumYieldsADistinctValidColor() {
        val seen = HashSet<Int>()
        for (i in 0..36) {
            val h = EditSwatches.hueAt(i / 36f)
            val c = hslToColor(h, 100f, 50f)
            // 全饱和中间明度必然非黑非白、不透明
            assertEquals(255, (c.toArgb() ushr 24) and 0xFF)
            seen += c.toArgb()
        }
        // 36 站扫一圈，至少要有 30 个不同颜色（红首尾重复是预期）
        assertTrue("光谱条应扫出足够多的不同颜色，只有 ${seen.size}", seen.size >= 30)
    }
}
