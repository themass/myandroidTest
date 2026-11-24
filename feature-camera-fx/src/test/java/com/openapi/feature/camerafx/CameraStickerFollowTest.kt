package com.openapi.feature.camerafx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CameraStickerFollowTest {

    private fun place(x: Float, y: Float, size: Float = 200f, rot: Float = 0f) =
        StickerPlacementPx(x, y, size, rot)

    @Test
    fun `首帧原样返回`() {
        val s = PlacementSmoother()
        val t = place(100f, 200f)
        assertEquals(t, s.smooth(t))
    }

    @Test
    fun `小抖动按 alpha 收敛而不是全跟`() {
        val s = PlacementSmoother(alpha = 0.5f)
        s.smooth(place(100f, 100f))
        val out = s.smooth(place(110f, 100f)) // 位移 10 < 200*0.45 → EMA
        assertEquals(105f, out.centerX, 0.01f)
        assertEquals(100f, out.centerY, 0.01f)
    }

    @Test
    fun `跳脸超过阈值直接 snap 不滑动`() {
        val s = PlacementSmoother(alpha = 0.35f)
        s.smooth(place(100f, 100f))
        val out = s.smooth(place(400f, 100f)) // 位移 300 > 200*0.45=90 → snap
        assertEquals(place(400f, 100f), out)
    }

    @Test
    fun `尺寸突变 snap`() {
        val s = PlacementSmoother(alpha = 0.5f)
        s.smooth(place(100f, 100f, size = 100f))
        val out = s.smooth(place(102f, 100f, size = 300f)) // 尺寸涨 3 倍 > 0.6
        assertEquals(300f, out.sizePx, 0.01f)
    }

    @Test
    fun `倾角过边界走最短弧`() {
        // 头歪从 170° 到 -170°：差 20°（最短弧），不是 340°
        assertEquals(20f, angleDelta(170f, -170f), 0.01f)
        val s = PlacementSmoother(alpha = 0.5f)
        s.smooth(place(100f, 100f, rot = 170f))
        val out = s.smooth(place(101f, 100f, rot = -170f))
        // 170 + 20*0.5 = 180；wrap180 约定 (-180, 180] → 保持 180
        assertEquals(180f, out.rotationDeg, 0.01f)
    }

    @Test
    fun `倾角大跳 snap`() {
        val s = PlacementSmoother(alpha = 0.5f)
        s.smooth(place(100f, 100f, rot = 0f))
        val out = s.smooth(place(101f, 100f, rot = 80f)) // 80 > 45 → snap
        assertEquals(80f, out.rotationDeg, 0.01f)
    }

    @Test
    fun `reset 后重新起步`() {
        val s = PlacementSmoother(alpha = 0.5f)
        s.smooth(place(100f, 100f))
        s.reset()
        val t = place(300f, 300f)
        assertEquals(t, s.smooth(t))
    }

    @Test
    fun `连续跟同一目标会收敛`() {
        val s = PlacementSmoother(alpha = 0.35f)
        s.smooth(place(0f, 0f))
        var out = place(0f, 0f)
        repeat(30) { out = s.smooth(place(50f, 0f)) } // 每帧 50 都小于 snap 阈值？
        // 注意：第一帧起 prev=0，目标 50：位移 50 < 51（200*0.45=90）→ EMA 逐帧逼近
        assertEquals(50f, out.centerX, 1f)
    }
}
