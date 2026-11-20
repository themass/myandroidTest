package com.openapi.imaging.quad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadEditTest {

    private fun p(x: Float, y: Float) = QuadNorm.Point(x, y)

    /** 顺序 TL TR BR BL 的矩形。 */
    private fun rect(l: Float = 0.2f, t: Float = 0.2f, r: Float = 0.8f, b: Float = 0.8f) =
        QuadNorm(listOf(p(l, t), p(r, t), p(r, b), p(l, b)))

    private val w = 1000f
    private val h = 1000f

    // ---- 拖边：矩形保持矩形 ----

    @Test
    fun draggingTopEdgeDownKeepsARectangle() {
        val q = QuadEdit.moveEdge(rect(), edge = 0, dx = 0f, dy = 100f, width = w, height = h)!!
        // 上边下移 100px = 0.1，左右边仍竖直
        assertEquals(0.3f, q.corners[0].y, 1e-4f)
        assertEquals(0.3f, q.corners[1].y, 1e-4f)
        assertEquals(0.2f, q.corners[0].x, 1e-4f)
        assertEquals(0.8f, q.corners[1].x, 1e-4f)
        assertEquals(0.8f, q.corners[2].y, 1e-4f) // 下边不动
    }

    @Test
    fun draggingRightEdgeOutwardMovesOnlyTheRightEdge() {
        val q = QuadEdit.moveEdge(rect(), edge = 1, dx = 50f, dy = 0f, width = w, height = h)!!
        assertEquals(0.85f, q.corners[1].x, 1e-4f)
        assertEquals(0.85f, q.corners[2].x, 1e-4f)
        assertEquals(0.2f, q.corners[0].x, 1e-4f)
        assertEquals(0.2f, q.corners[3].x, 1e-4f)
    }

    @Test
    fun movementAlongTheEdgeDirectionIsIgnored() {
        // 沿上边（水平）方向拖，边不应该动
        assertNull(QuadEdit.moveEdge(rect(), edge = 0, dx = 120f, dy = 0f, width = w, height = h))
        // 斜着拖：只取垂直分量
        val q = QuadEdit.moveEdge(rect(), edge = 0, dx = 120f, dy = 40f, width = w, height = h)!!
        assertEquals(0.24f, q.corners[0].y, 1e-4f)
        assertEquals(0.2f, q.corners[0].x, 1e-4f)
    }

    // ---- 非正方形显示区域：位移按像素算 ----

    @Test
    fun nonSquareAreaUsesPixelSpaceNotNormalizedSpace() {
        // 2000x1000 的显示区域，上边下移 100 像素 = 归一化 y 的 0.1，而不是 0.05
        val q = QuadEdit.moveEdge(rect(), edge = 0, dx = 0f, dy = 100f, width = 2000f, height = 1000f)!!
        assertEquals(0.3f, q.corners[0].y, 1e-4f)
        // 左右边仍竖直（x 不变）
        assertEquals(0.2f, q.corners[0].x, 1e-4f)
        assertEquals(0.8f, q.corners[1].x, 1e-4f)
    }

    // ---- 梯形：腰的方向保持不变 ----

    @Test
    fun trapezoidKeepsItsLegsDirectionWhenTopEdgeMoves() {
        // 上窄下宽
        val trap = QuadNorm(listOf(p(0.3f, 0.2f), p(0.7f, 0.2f), p(0.9f, 0.8f), p(0.1f, 0.8f)))
        val q = QuadEdit.moveEdge(trap, edge = 0, dx = 0f, dy = 60f, width = w, height = h)!!
        // 左腰方向 (0.3-0.1, 0.2-0.8) 在移动前后平行
        fun dirX(a: QuadNorm.Point, b: QuadNorm.Point) = (a.x - b.x) / (a.y - b.y)
        assertEquals(dirX(trap.corners[0], trap.corners[3]), dirX(q.corners[0], q.corners[3]), 1e-3f)
        assertEquals(dirX(trap.corners[1], trap.corners[2]), dirX(q.corners[1], q.corners[2]), 1e-3f)
    }

    // ---- 边界与合法性 ----

    @Test
    fun edgeStopsAtImageBoundaryInsteadOfFlyingAway() {
        val q = QuadEdit.moveEdge(rect(), edge = 1, dx = 5000f, dy = 0f, width = w, height = h)
        // 要么停在合法位置（≤ 1），要么拒绝
        if (q != null) assertTrue(q.corners.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    @Test
    fun draggingOppositeEdgesPastEachOtherIsRejectedOrKeepsValidity() {
        // 把上边一直往下拖过下边：不能产生翻转/自交的四边形
        val q = QuadEdit.moveEdge(rect(), edge = 0, dx = 0f, dy = 900f, width = w, height = h)
        if (q != null) assertTrue(q.isValid)
    }

    @Test
    fun resultIsAlwaysValidWhenNotNull() {
        for (edge in 0..3) {
            for (d in listOf(-300f, -80f, 80f, 300f)) {
                val q = QuadEdit.moveEdge(rect(), edge, d, d, w, h)
                if (q != null) assertTrue("edge=$edge d=$d", q.isValid)
            }
        }
    }

    @Test
    fun cornerOrderIsPreservedSoTheActiveEdgeIndexDoesNotDrift() {
        val q = QuadEdit.moveEdge(rect(), edge = 2, dx = 0f, dy = -50f, width = w, height = h)!!
        // 下边上移后，各角仍在原来的位置序号上（BR、BL 的 y 变了，TL、TR 不变）
        assertEquals(0.2f, q.corners[0].y, 1e-4f)
        assertEquals(0.75f, q.corners[2].y, 1e-4f)
        assertEquals(0.75f, q.corners[3].y, 1e-4f)
    }

    @Test
    fun invalidInputsReturnNull() {
        assertNull(QuadEdit.moveEdge(rect(), 9, 10f, 10f, w, h))
        assertNull(QuadEdit.moveEdge(rect(), 0, 10f, 10f, 0f, h))
        assertNull(QuadEdit.moveEdge(QuadNorm(listOf(p(0f, 0f))), 0, 10f, 10f, w, h))
    }

    // ---- 点选 ----

    @Test
    fun hitCornerHasPriorityOverEdge() {
        val hit = QuadEdit.hitTest(rect(), 205f, 205f, w, h, cornerRadius = 40f, edgeRadius = 30f)
        assertEquals(QuadHandle.Corner(0), hit)
    }

    @Test
    fun hitAnywhereAlongAnEdgeNotJustItsMidpoint() {
        // 上边的 1/4 处
        val hit = QuadEdit.hitTest(rect(), 350f, 210f, w, h, cornerRadius = 40f, edgeRadius = 30f)
        assertEquals(QuadHandle.Edge(0), hit)
        // 右边靠下
        assertEquals(QuadHandle.Edge(1), QuadEdit.hitTest(rect(), 790f, 650f, w, h, 40f, 30f))
    }

    @Test
    fun hitInsideTheQuadMissesEverything() {
        assertNull(QuadEdit.hitTest(rect(), 500f, 500f, w, h, 40f, 30f))
        assertNull(QuadEdit.hitTest(rect(), 20f, 20f, w, h, 40f, 30f))
    }

    @Test
    fun nearestEdgeWins() {
        // 左下角附近但在角半径之外：离左边更近
        val hit = QuadEdit.hitTest(rect(), 205f, 650f, w, h, cornerRadius = 40f, edgeRadius = 30f)
        assertNotNull(hit)
        assertEquals(QuadHandle.Edge(3), hit)
    }
}
