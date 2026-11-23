package com.openapi.feature.camerafx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraStickerPlacementTest {

    @Test
    fun `stickerAtHeadTop 中心与脸宽缩放`() {
        val map = CanvasMap(400f, 800f, 720, 960, fill = true, mirrorX = false)
        val head = Point2d(0.5f, 0.2f)
        val p = stickerAtHeadTop(map, head, faceWidthNorm = 0.3f, tiltDegForDisplay = -12f)
        val expected = map.map(head)
        assertEquals(expected.x, p.centerX, 0.5f)
        assertEquals(expected.y, p.centerY, 0.5f)
        assertEquals(-12f, p.rotationDeg, 0.01f)
        // 尺寸 = 脸宽(0.3×720 源px) × scalePx(800/960) × 0.85 = 153px。
        // 回归 2026-11-23：曾漏乘 imgW → 0.21px 被 clamp 成 24px 小点，真机上「看不到贴纸」。
        assertEquals(153f, p.sizePx, 1f)
    }

    @Test
    fun `sticker 尺寸随脸远离变小但不小于下限`() {
        val map = CanvasMap(1080f, 1920f, 720, 960, fill = true, mirrorX = true)
        val near = stickerAtHeadTop(map, Point2d(0.5f, 0.2f), faceWidthNorm = 0.6f, tiltDegForDisplay = 0f)
        val far = stickerAtHeadTop(map, Point2d(0.5f, 0.2f), faceWidthNorm = 0.01f, tiltDegForDisplay = 0f)
        assertTrue(near.sizePx > far.sizePx)
        assertEquals(24f, far.sizePx, 0.01f) // clamp 下限
        assertTrue(near.sizePx <= 512f) // clamp 上限
    }
}
