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
}
