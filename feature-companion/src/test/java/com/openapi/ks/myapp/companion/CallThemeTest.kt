package com.openapi.ks.myapp.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通话页背景主题：纯数据 + 预设表。
 *
 * 钉住三件事：默认主题必须与旧版 CallColors 常量逐字节一致（没设置过主题的用户
 * 看到的画面不许变）；每个预设自带背景三段渐变 + 光晕 + 强调色；名字解析认得
 * 所有预设、认不出来回落默认（持久化的值可能是旧版本写的）。
 */
class CallThemeTest {

    @Test
    fun `默认主题是深海，且三档渐变与旧版常量一致`() {
        val def = CallTheme.Default
        assertEquals(CallTheme.Presets.first().id, def.id)
        // 旧版 CallColors：BackdropTop #161F3D / Mid #0B1122 / Bottom #05060C
        assertEquals(0xFF161F3D.toInt(), def.topArgb)
        assertEquals(0xFF0B1122.toInt(), def.midArgb)
        assertEquals(0xFF05060C.toInt(), def.bottomArgb)
        // 旧版主光晕 VioletDeep #5B4BC4，强调色 Violet #8B7CF6
        assertEquals(0xFF5B4BC4.toInt(), def.glowArgb)
        assertEquals(0xFF8B7CF6.toInt(), def.accentArgb)
    }

    @Test
    fun `预设至少有深海 暖夜 纯黑三套，id 不重复`() {
        val ids = CallTheme.Presets.map { it.id }
        assertTrue("至少三套预设", ids.size >= 3)
        assertTrue("id 必须唯一", ids.size == ids.toSet().size)
        assertTrue("深海 在第一（默认）", ids.first() == CallTheme.Default.id)
    }

    @Test
    fun `纯黑主题给 OLED，背景必须全黑且光晕比默认更收敛`() {
        val black = CallTheme.Presets.first { it.id == "oled" }
        assertEquals(0x000000, black.topArgb)
        assertEquals(0x000000, black.midArgb)
        assertEquals(0x000000, black.bottomArgb)
        assertTrue("OLED 光晕应更收敛", black.glowStrength <= CallTheme.Default.glowStrength)
    }

    @Test
    fun `名字解析认得所有预设，未知值回落默认`() {
        CallTheme.Presets.forEach { p ->
            assertEquals(p.id, CallTheme.fromName(p.id).id)
        }
        assertEquals(CallTheme.Default.id, CallTheme.fromName(null).id)
        assertEquals(CallTheme.Default.id, CallTheme.fromName("乱写的").id)
    }

    @Test
    fun `自定义主题由色相派生，同色相稳定复现且全不透明`() {
        val a = customCallTheme(260f)
        val b = customCallTheme(260f)
        assertEquals(CallTheme.CUSTOM_ID, a.id)
        assertEquals(a.topArgb, b.topArgb)
        assertEquals(a.glowArgb, b.glowArgb)
        for (argb in listOf(a.topArgb, a.midArgb, a.bottomArgb, a.glowArgb, a.accentArgb)) {
            assertEquals("背景色必须不透明", 0xFFL, (argb.toLong() shr 24) and 0xFF)
        }
    }

    @Test
    fun `不同色相背景不同，渐变从顶到底逐档变暗`() {
        val red = customCallTheme(0f)
        val green = customCallTheme(120f)
        assertTrue("色相必须改变画面", red.topArgb != green.topArgb)
        assertTrue("顶亮底暗", luma(red.topArgb) > luma(red.midArgb))
        assertTrue("中间还要再暗一档", luma(red.midArgb) > luma(red.bottomArgb))
        assertTrue("光晕比背景亮（要看得见光）", luma(red.glowArgb) > luma(red.topArgb))
    }

    @Test
    fun `色相环绕：360 与 0 同色`() {
        assertEquals(customCallTheme(0f).topArgb, customCallTheme(360f).topArgb)
    }

    @Test
    fun `hueDegrees 只有自定义主题携带`() {
        assertEquals(260f, customCallTheme(260f).hueDegrees)
        assertEquals(null, CallTheme.Default.hueDegrees)
    }

    @Test
    fun `背景菜单的自定义入口：选中带勾和角度，没选带省略号`() {
        assertEquals("　 自定义…", customThemeMenuLabel(CallTheme.Default))
        assertEquals("✓ 自定义（250°）", customThemeMenuLabel(customCallTheme(250f)))
        // 色相丢失的兜底路径也要有角度可显示（默认紫 248°），不能显示成 0°。
        assertEquals(
            "✓ 自定义（${CallTheme.DEFAULT_CUSTOM_HUE.toInt()}°）",
            customThemeMenuLabel(CallTheme.Default.copy(id = CallTheme.CUSTOM_ID, hueDegrees = null)),
        )
    }

    @Test
    fun `色轮取点：右0° 下90° 左180° 上270°，顺时针`() {
        // 圆心 (100,100)，半径 50。屏幕坐标 y 向下，所以「下」是 +y。
        assertEquals(0f, hueFromPoint(150f, 100f, 100f, 100f), 0.5f)
        assertEquals(90f, hueFromPoint(100f, 150f, 100f, 100f), 0.5f)
        assertEquals(180f, hueFromPoint(50f, 100f, 100f, 100f), 0.5f)
        assertEquals(270f, hueFromPoint(100f, 50f, 100f, 100f), 0.5f)
        // 左下 45°（屏幕坐标）= 色轮 135°
        assertEquals(135f, hueFromPoint(50f, 150f, 100f, 100f), 1f)
    }

    private fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return r + g + b
    }
}
