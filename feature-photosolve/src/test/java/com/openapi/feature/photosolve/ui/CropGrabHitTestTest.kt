package com.openapi.feature.photosolve.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 裁剪把手命中（[pickGrab]）。
 *
 * 场景来自真机抱怨：初始框贴边，用户按在角上抓不住、一拖整个框在动。
 * 坐标取一个 100..900 的框，热区半径 36（对应 36dp），全部按「贴边角只能落在框内侧」来测。
 */
class CropGrabHitTestTest {

    private val rect = Rect(100f, 100f, 900f, 700f)
    private val radius = 36f

    // ---- 角：贴边时向框内侧扩展热区 ----

    @Test
    fun cornerGrabbedInsideFrameWhenFlushAgainstEdge() {
        // 左上角贴边：手指只能落在角右下方。按在角内侧 (128,128)（离角 28px，欧氏 39.6）
        // 必须命中左上角 —— 旧实现用欧氏距离，这里已经出圈，掉进 Move。
        assertEquals(Grab.TopLeft, pickGrab(Offset(128f, 128f), rect, radius))
    }

    @Test
    fun cornerGrabbedExactlyOnCorner() {
        assertEquals(Grab.TopLeft, pickGrab(Offset(100f, 100f), rect, radius))
        assertEquals(Grab.TopRight, pickGrab(Offset(900f, 100f), rect, radius))
        assertEquals(Grab.BottomLeft, pickGrab(Offset(100f, 700f), rect, radius))
        assertEquals(Grab.BottomRight, pickGrab(Offset(900f, 700f), rect, radius))
    }

    @Test
    fun nearestCornerWinsInsideCornerBand() {
        // 左边线上但靠近左上角：应判角而不是左边
        assertEquals(Grab.TopLeft, pickGrab(Offset(105f, 130f), rect, radius))
    }

    // ---- 边：整条边都算热区（可见粗横线只画了中间一小段） ----

    @Test
    fun edgeGrabbedAlongWholeEdgeNotJustMidpoint() {
        // 旧实现只认边中点一个点，四分之一处按下去会掉进 Move
        assertEquals(Grab.Top, pickGrab(Offset(300f, 118f), rect, radius))
        assertEquals(Grab.Bottom, pickGrab(Offset(300f, 682f), rect, radius))
        assertEquals(Grab.Left, pickGrab(Offset(118f, 400f), rect, radius))
        assertEquals(Grab.Right, pickGrab(Offset(882f, 400f), rect, radius))
    }

    // ---- 框内平移与框外空白 ----

    @Test
    fun interiorMovesWholeFrame() {
        assertEquals(Grab.Move, pickGrab(Offset(500f, 400f), rect, radius))
    }

    @Test
    fun outsideFrameIsIgnored() {
        // 框外、离边和角都超过半径 → null：把事件留给页面其它部分（顶栏按钮等）
        assertNull(pickGrab(Offset(20f, 20f), rect, radius))
        assertNull(pickGrab(Offset(960f, 400f), rect, radius))
    }

    // ---- apply 的夹取语义不回归 ----

    @Test
    fun cornerResizeStaysInsideImageAndMinSize() {
        val n = com.openapi.imaging.CropNorm(0f, 0f, 1f, 1f)
        // 向左上角外拖：不会把 left/top 拖成负数
        val shrunk = Grab.TopLeft.apply(n, -0.5f, -0.5f, 0.12f)
        assertEquals(0f, shrunk.left)
        assertEquals(0f, shrunk.top)
        // 角往里推过界：最小边长兜底
        val tiny = Grab.BottomRight.apply(n, -0.95f, -0.95f, 0.12f)
        assertEquals(0.12f, tiny.right - tiny.left, 1e-4f)
        assertEquals(0.12f, tiny.bottom - tiny.top, 1e-4f)
    }

    @Test
    fun moveKeepsFrameInsideBounds() {
        val n = com.openapi.imaging.CropNorm(0f, 0f, 0.5f, 0.5f)
        val moved = Grab.Move.apply(n, 5f, 5f, 0.12f)
        // 框宽 0.5：往右下最多平移到贴右下边，不越界也不改变大小
        assertEquals(0.5f, moved.left, 1e-4f)
        assertEquals(0.5f, moved.top, 1e-4f)
        assertEquals(1f, moved.right, 1e-4f)
        assertEquals(1f, moved.bottom, 1e-4f)
    }

    @Test
    fun cornerDragTowardOppositeEdgeDoesNotCrash() {
        // 崩溃回归：旧实现 l.coerceIn(0f, r - min) 在 r < min 时 min>max，抛 IllegalArgumentException。
        // 把一个角一路拖过对边（幅度超过整幅宽）不能再崩。
        val n = com.openapi.imaging.CropNorm(0f, 0f, 1f, 1f)
        val out = Grab.BottomRight.apply(n, -1.5f, -1.5f, 0.12f)
        assertEquals(0.12f, out.right - out.left, 1e-4f)
        assertEquals(0.12f, out.bottom - out.top, 1e-4f)
        val out2 = Grab.TopLeft.apply(n, 1.5f, 1.5f, 0.12f)
        assertEquals(0.12f, out2.right - out2.left, 1e-4f)
        assertEquals(1f, out2.right, 1e-4f)
    }
}
