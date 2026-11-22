package com.openapi.ks.myapp.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarStageTransformTest {

    @Test
    fun `scale clamps to the one-to-four band`() {
        assertEquals(1f, clampStageScale(0.2f), 0f)
        assertEquals(1f, clampStageScale(1f), 0f)
        assertEquals(2.5f, clampStageScale(2.5f), 0f)
        assertEquals(4f, clampStageScale(9f), 0f)
    }

    @Test
    fun `pan is fully clamped away at 1x`() {
        assertEquals(0f, clampStageOffset(120f, scale = 1f, viewportSize = 1000f), 0f)
        assertEquals(0f, clampStageOffset(-120f, scale = 1f, viewportSize = 1000f), 0f)
    }

    @Test
    fun `pan bound grows with zoom so the stage never shows a gap`() {
        // 2x of 1000px → content overhangs 1000px total → pan range ±500.
        assertEquals(500f, clampStageOffset(999f, scale = 2f, viewportSize = 1000f), 0f)
        assertEquals(-500f, clampStageOffset(-999f, scale = 2f, viewportSize = 1000f), 0f)
        // 4x → ±1500.
        assertEquals(1500f, clampStageOffset(9999f, scale = 4f, viewportSize = 1000f), 0f)
    }

    @Test
    fun `pan inside the bound passes through unchanged`() {
        assertEquals(200f, clampStageOffset(200f, scale = 2f, viewportSize = 1000f), 0f)
    }

    @Test
    fun `zooming back to 1 recenters pan automatically`() {
        val atEdge = clampStageOffset(999f, scale = 2f, viewportSize = 1000f)
        assertEquals(0f, clampStageOffset(atEdge, scale = 1f, viewportSize = 1000f), 0f)
    }

    @Test
    fun `gesture log names the broken link in the pinch chain`() {
        // 「捏合没反应」三连环：手指到没到 → 状态机动没动。文案是设备侧 logcat
        // 唯一的判据（adb 注入多指不成 gesture，只能真手指 + 这一行定性）。
        val moved = stageGestureLog(2, true, 1f, 2f, 0f, 0f, tap = false, longPress = false)
        assertTrue(moved.contains("fingers=2"))
        assertTrue(moved.contains("twoFinger=true"))
        assertTrue(moved.contains("moved=true"))
        // 两指到了但 scale 纹丝不动 → moved=false，问题在步进/事件流而非手势层缺席。
        assertTrue(stageGestureLog(2, true, 1f, 1f, 0f, 0f, tap = false, longPress = false).contains("moved=false"))
    }

    @Test
    fun `centered pinch keeps offset at zero`() {
        val (s, o) = stageStep(scale = 1f, offset = 0f, centroid = 500f, pan = 0f, zoom = 2f, viewportSize = 1000f)
        assertEquals(2f, s, 0f)
        assertEquals(0f, o, 0f)
    }

    @Test
    fun `pinch anchored at the top edge pushes the center content down`() {
        // 视口 1000px，锚点在屏幕顶（中心系 -500），1x→2x：中心内容点 q=0 移到 +500，
        // 锚点下的内容点 q=-500 保持在屏幕 -500（图查看器的标准语义）。
        val (s, o) = stageStep(scale = 1f, offset = 0f, centroid = 0f, pan = 0f, zoom = 2f, viewportSize = 1000f)
        assertEquals(2f, s, 0f)
        assertEquals(500f, o, 0.001f)
        // 不变式：锚点内容点变换前后屏幕位置不变。
        val q = (0f - 500f - 0f) / 1f // (c - t0)/s0 = -500
        assertEquals(-500f, q * s + o, 0.001f)
    }

    @Test
    fun `successive steps keep the anchor content point fixed`() {
        val (s1, t1) = stageStep(1f, 0f, 0f, 0f, 2f, 1000f)
        val (s2, t2) = stageStep(s1, t1, 0f, 0f, 1.5f, 1000f)
        assertEquals(3f, s2, 0f)
        // 锚点内容点 q = (c - t1)/s1 = (-500-500)/2 = -500；两步后仍在屏幕 -500。
        assertEquals(-500f, (-500f * s2) + t2, 0.001f)
    }

    @Test
    fun `zoom at the clamp still pans`() {
        val (s, o) = stageStep(scale = 4f, offset = 0f, centroid = 500f, pan = 30f, zoom = 2f, viewportSize = 1000f)
        assertEquals(4f, s, 0f)
        assertEquals(30f, o, 0.001f)
    }
}
