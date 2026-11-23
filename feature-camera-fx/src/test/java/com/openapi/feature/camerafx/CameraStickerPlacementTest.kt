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
        assertTrue(p.sizePx > 0f)
    }
}
