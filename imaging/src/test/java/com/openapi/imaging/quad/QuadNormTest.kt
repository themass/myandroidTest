package com.openapi.imaging.quad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [QuadNorm] 的纯几何逻辑：角点排序、有效性判定、输出尺寸。
 *
 * 这些是透视矫正最容易出错、又不需要设备就能验证的部分：
 * 顺序错一位，矫正出来的图就是旋转/镜像的。
 */
class QuadNormTest {

    private fun q(vararg xy: Float) = QuadNorm(
        (0 until xy.size / 2).map { QuadNorm.Point(xy[it * 2], xy[it * 2 + 1]) },
    )

    @Test
    fun ordersClockwiseFromTopLeftRegardlessOfInputOrder() {
        // 故意打乱输入顺序：右下图排在最前
        val quad = q(
            0.9f, 0.9f,
            0.1f, 0.1f,
            0.9f, 0.1f,
            0.1f, 0.9f,
        )
        val o = quad.ordered().corners
        // TL → TR → BR → BL
        assertEquals(0.1f, o[0].x, 1e-4f); assertEquals(0.1f, o[0].y, 1e-4f)
        assertEquals(0.9f, o[1].x, 1e-4f); assertEquals(0.1f, o[1].y, 1e-4f)
        assertEquals(0.9f, o[2].x, 1e-4f); assertEquals(0.9f, o[2].y, 1e-4f)
        assertEquals(0.1f, o[3].x, 1e-4f); assertEquals(0.9f, o[3].y, 1e-4f)
    }

    @Test
    fun ordersRotatedTrapezoidCorrectly() {
        // 模拟斜拍：上边窄、下边宽，整体略旋转
        val quad = q(
            0.30f, 0.20f,  // TL
            0.72f, 0.18f,  // TR
            0.95f, 0.85f,  // BR
            0.08f, 0.90f,  // BL
        )
        val o = quad.ordered().corners
        assertEquals(0.30f, o[0].x, 1e-4f)
        assertEquals(0.72f, o[1].x, 1e-4f)
        assertEquals(0.95f, o[2].x, 1e-4f)
        assertEquals(0.08f, o[3].x, 1e-4f)
        assertTrue(quad.isValid)
    }

    @Test
    fun areaOfHalfFrameIsHalf() {
        val quad = q(0f, 0f, 1f, 0f, 1f, 0.5f, 0f, 0.5f)
        assertEquals(0.5f, quad.area(), 1e-3f)
    }

    @Test
    fun degenerateQuadIsInvalid() {
        // 四点共线
        val line = q(0.1f, 0.1f, 0.4f, 0.1f, 0.7f, 0.1f, 0.9f, 0.1f)
        assertFalse(line.isValid)
        // 面积过小
        val tiny = q(0.10f, 0.10f, 0.13f, 0.10f, 0.13f, 0.13f, 0.10f, 0.13f)
        assertFalse(tiny.isValid)
    }

    @Test
    fun outOfFrameCornerIsInvalid() {
        val quad = q(-0.5f, 0.1f, 0.9f, 0.1f, 0.9f, 0.9f, 0.1f, 0.9f)
        assertFalse(quad.isValid)
    }

    @Test
    fun fullFrameIsValid() {
        assertTrue(QuadNorm.fullFrame().isValid)
    }

    @Test
    fun outputSizeFollowsLongerEdges() {
        // 正方形图、四边形占满一半：输出约 1024×1024
        val quad = q(0.0f, 0.0f, 0.5f, 0.0f, 0.5f, 0.5f, 0.0f, 0.5f)
        val (w, h) = quad.outputSizePx(sourceWidth = 2048, sourceHeight = 2048)
        assertEquals(1024, w)
        assertEquals(1024, h)
    }

    @Test
    fun outputSizeRespectsMaxLongEdge() {
        val quad = QuadNorm.fullFrame()
        val (w, h) = quad.outputSizePx(sourceWidth = 8000, sourceHeight = 6000, maxLongEdge = 1200)
        assertTrue(maxOf(w, h) <= 1200)
    }

    @Test
    fun boundsWrapAllCorners() {
        val b = q(0.3f, 0.2f, 0.8f, 0.25f, 0.7f, 0.9f, 0.1f, 0.85f).bounds()
        assertEquals(0.1f, b.left, 1e-4f)
        assertEquals(0.2f, b.top, 1e-4f)
        assertEquals(0.8f, b.right, 1e-4f)
        assertEquals(0.9f, b.bottom, 1e-4f)
    }

    @Test
    fun convexityCheckRejectsBowTie() {
        // 蝴蝶结（自交）：TL, TR, BL, BR 的顺序
        val bowTie = listOf(
            QuadNorm.Point(0.1f, 0.1f),
            QuadNorm.Point(0.9f, 0.1f),
            QuadNorm.Point(0.1f, 0.9f),
            QuadNorm.Point(0.9f, 0.9f),
        )
        assertFalse(isConvexish(bowTie))
        assertTrue(isConvexish(QuadNorm.fullFrame().corners))
    }
}
