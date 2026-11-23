package com.openapi.feature.camerafx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锚点纯函数钉死测试。spike 的验收核心：后续 sticker 跟随就靠这几个量，
 * 语义必须在 JVM 层定死，不等到真机上靠肉眼调。
 */
class FxAnchorsTest {

    private fun pts(vararg xy: Pair<Float, Float>) = xy.map { Point2d(it.first, it.second) }

    @Test
    fun `faceBounds 空输入返回 null 不抛`() {
        assertNull(faceBounds(emptyList()))
    }

    @Test
    fun `faceBounds 取外接矩形`() {
        val r = faceBounds(pts(0.2f to 0.3f, 0.6f to 0.1f, 0.4f to 0.5f))!!
        assertEquals(0.2f, r.x, 1e-6f)
        assertEquals(0.1f, r.y, 1e-6f)
        assertEquals(0.4f, r.width, 1e-6f)
        assertEquals(0.4f, r.height, 1e-6f)
    }

    @Test
    fun `headTopAnchor 在轮廓最高点上方按框高抬高`() {
        // 框由同一轮廓算出：y 范围 0.12..0.5 → 框高 0.38；ratio=0.12 → 在最高点 0.12 之上再抬 0.38*0.12
        val outline = pts(0.3f to 0.5f, 0.5f to 0.12f, 0.7f to 0.5f)
        val bounds = faceBounds(outline)!!
        val anchor = headTopAnchor(outline, bounds)!!
        assertEquals(0.5f, anchor.x, 1e-6f) // 框水平中心
        assertEquals(0.12f - 0.38f * 0.12f, anchor.y, 1e-6f)
    }

    @Test
    fun `headTopAnchor 空轮廓或空框返回 null`() {
        assertNull(headTopAnchor(emptyList(), Rect2d(0f, 0f, 1f, 1f)))
        assertNull(headTopAnchor(pts(0f to 0f), null))
    }

    @Test
    fun `eyeTiltDegrees 水平线为 0`() {
        assertEquals(0f, eyeTiltDegrees(Point2d(0.3f, 0.4f), Point2d(0.6f, 0.4f)), 1e-6f)
    }

    @Test
    fun `eyeTiltDegrees 从左到右向下倾斜为正`() {
        // y 向下：second 在下方 → 正角
        val tilt = eyeTiltDegrees(Point2d(0.3f, 0.4f), Point2d(0.6f, 0.4f + 0.3f * tan30))
        assertEquals(30f, tilt, 1e-3f)
    }

    @Test
    fun `eyeTiltDegrees 反向为负`() {
        val tilt = eyeTiltDegrees(Point2d(0.3f, 0.4f + 0.3f * tan30), Point2d(0.6f, 0.4f))
        assertEquals(-30f, tilt, 1e-3f)
    }

    @Test
    fun `eyeTiltDegrees 两点重合返回 0 而不是 NaN`() {
        val tilt = eyeTiltDegrees(Point2d(0.5f, 0.5f), Point2d(0.5f, 0.5f))
        assertEquals(0f, tilt, 0f)
        assertTrue(!tilt.isNaN())
    }

    private val tan30 = 0.57735027f

    @Test
    fun `CanvasMap fill 模式 - 竖图塞进更方的画布按高铺满居中`() {
        // 图像 480x640，画布 800x800：s = max(800/480, 800/640) = 5/3
        val map = CanvasMap(canvasW = 800f, canvasH = 800f, imgW = 480, imgH = 640, mirrorX = false)
        val center = map.map(Point2d(0.5f, 0.5f))
        assertEquals(400f, center.x, 1e-3f)
        assertEquals(400f, center.y, 1e-3f)
        // 图像左上角 → 负偏移（被裁）
        val tl = map.map(Point2d(0f, 0f))
        assertEquals(400f - 0.5f * 480 * (5f / 3f), tl.x, 1e-3f)
        assertEquals(400f - 0.5f * 640 * (5f / 3f), tl.y, 1e-3f)
        assertEquals(5f / 3f, map.scalePx, 1e-3f)
    }

    @Test
    fun `CanvasMap mirrorX 翻转 x 不翻转 y`() {
        val map = CanvasMap(canvasW = 100f, canvasH = 100f, imgW = 100, imgH = 100, mirrorX = true)
        val left = map.map(Point2d(0.1f, 0.5f))
        val right = map.map(Point2d(0.9f, 0.5f))
        assertTrue("镜像后归一化 0.1 应落在 0.9 一侧", left.x > right.x)
        assertEquals(left.y, right.y, 1e-6f)
        // 画布中心镜像后仍是中心
        val c = map.map(Point2d(0.5f, 0.5f))
        assertEquals(50f, c.x, 1e-3f)
        assertEquals(50f, c.y, 1e-3f)
    }

    @Test
    fun `CanvasMap 零尺寸画布不产生 NaN`() {
        val map = CanvasMap(canvasW = 0f, canvasH = 0f, imgW = 480, imgH = 640)
        val o = map.map(Point2d(0.5f, 0.5f))
        assertEquals(0f, o.x, 0f)
        assertEquals(0f, o.y, 0f)
        assertEquals(0f, map.scalePx, 0f)
    }
}
