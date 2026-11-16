package com.openapi.imaging.quad

import kotlin.math.abs
import kotlin.math.hypot

/** 四边形上可以被抓住的部位：四个角之一，或四条边之一。 */
sealed interface QuadHandle {
    data class Corner(val index: Int) : QuadHandle

    /** 边 [index] 连接角 [index] 和角 `(index + 1) % 4`：0 上、1 右、2 下、3 左（顺序为 TL→TR→BR→BL 时）。 */
    data class Edge(val index: Int) : QuadHandle
}

/**
 * 四角微调页的几何：点选哪个角/边、拖动一条边。纯函数，不含 Android 类型，可直接 JVM 单测。
 *
 * 为什么拖边时只沿**法线**移动：
 * 拖上边时，上边两端的角分别沿左边、右边的延长线滑动，左右两条边的方向不变。
 * 对一个矩形来说，这样拖完仍然是矩形；对梯形，仍然是梯形（两条腰保持原来的方向）。
 * 如果直接把边的两个端点都按手指位移平移，矩形会被拖成平行四边形，用户很难再拖回来。
 * 平行于边方向的手指位移被忽略（不会让边「滑」走）。
 *
 * 所有距离都在**像素空间**算（传入显示区域的宽高）：归一化坐标在非正方形图片上 x、y 的量纲不同，
 * 直接算法线会让边拖歪。
 */
object QuadEdit {

    /** 两条线夹角的正弦小于它就当平行（交点会飞到无穷远）。 */
    private const val PARALLEL_EPS = 1e-3f

    /** 边太短（几乎重合的两个角）时不处理。 */
    private const val MIN_EDGE_PX = 1e-3f

    /** 位移不合法时逐级缩小再试：边会在接触边界/变形临界点「停住」，而不是整个拖动被丢弃。 */
    private val SHRINK_STEPS = floatArrayOf(1f, 0.75f, 0.5f, 0.25f, 0.1f)

    private data class Vec(val x: Float, val y: Float)

    /**
     * 找到 (x, y)（相对显示区域左上角的像素）下的部位。角优先于边：角附近同时落在两条边的热区里，
     * 用户想抓的是角。
     *
     * @param width 显示区域宽（像素）
     * @param height 显示区域高（像素）
     * @param cornerRadius 角的触摸半径（像素）
     * @param edgeRadius 边的触摸半径（像素），整条边都可以抓，不只是中点
     */
    fun hitTest(
        quad: QuadNorm,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
        edgeRadius: Float,
    ): QuadHandle? {
        val c = quad.corners
        if (c.size != 4 || width <= 0f || height <= 0f) return null

        var bestCorner = -1
        var bestCornerDistance = Float.MAX_VALUE
        for (i in 0 until 4) {
            val d = hypot(x - c[i].x * width, y - c[i].y * height)
            if (d < bestCornerDistance) {
                bestCornerDistance = d
                bestCorner = i
            }
        }
        if (bestCorner >= 0 && bestCornerDistance <= cornerRadius) return QuadHandle.Corner(bestCorner)

        var bestEdge = -1
        var bestEdgeDistance = Float.MAX_VALUE
        for (i in 0 until 4) {
            val a = c[i]
            val b = c[(i + 1) % 4]
            val d = distanceToSegment(x, y, a.x * width, a.y * height, b.x * width, b.y * height)
            if (d < bestEdgeDistance) {
                bestEdgeDistance = d
                bestEdge = i
            }
        }
        return if (bestEdge >= 0 && bestEdgeDistance <= edgeRadius) QuadHandle.Edge(bestEdge) else null
    }

    /**
     * 把第 [edge] 条边沿它的法线平移，位移由手指位移 ([dx], [dy]，像素) 在法线上的投影决定。
     *
     * 边两端的角分别沿相邻两条边的延长线滑动，所以其余三条边的方向都不变。
     * 结果必须仍是合法的凸四边形（[QuadNorm.isValid]）且所有角在 [0, 1] 内；不合法就把位移缩小再试，
     * 都不行返回 null（调用方保持原样）。
     *
     * 角点顺序**保持不变**（不再调用 [QuadNorm.ordered]），这样拖动过程中「正在拖的边」的编号不会漂移。
     *
     * @param quad 顺序为 TL→TR→BR→BL 的四边形
     */
    fun moveEdge(
        quad: QuadNorm,
        edge: Int,
        dx: Float,
        dy: Float,
        width: Float,
        height: Float,
    ): QuadNorm? {
        val corners = quad.corners
        if (corners.size != 4 || edge !in 0..3 || width <= 0f || height <= 0f) return null

        val p = corners.map { Vec(it.x * width, it.y * height) }
        val a = p[edge]
        val b = p[(edge + 1) % 4]
        val before = p[(edge + 3) % 4] // 与 a 相连的另一端
        val after = p[(edge + 2) % 4] // 与 b 相连的另一端

        val ex = b.x - a.x
        val ey = b.y - a.y
        val length = hypot(ex, ey)
        if (length < MIN_EDGE_PX) return null
        val nx = -ey / length
        val ny = ex / length
        val travel = dx * nx + dy * ny // 手指位移在法线上的分量
        if (abs(travel) < 1e-6f) return null

        for (k in SHRINK_STEPS) {
            val s = travel * k
            val shifted = Vec(a.x + nx * s, a.y + ny * s)
            // 新的 a：平移后的这条边所在直线 与 「before→a」那条边所在直线 的交点
            val newA = intersect(shifted, ex, ey, before, a.x - before.x, a.y - before.y) ?: shifted
            // 新的 b：同一条直线 与 「b→after」那条边所在直线 的交点
            val newB = intersect(shifted, ex, ey, b, after.x - b.x, after.y - b.y)
                ?: Vec(b.x + nx * s, b.y + ny * s)

            val next = corners.toMutableList()
            next[edge] = QuadNorm.Point(newA.x / width, newA.y / height)
            next[(edge + 1) % 4] = QuadNorm.Point(newB.x / width, newB.y / height)
            if (next.any { it.x !in 0f..1f || it.y !in 0f..1f }) continue
            val candidate = QuadNorm(next)
            if (candidate.isValid) return candidate
        }
        return null
    }

    // ---- 几何 ----

    /** 直线 P + s·u 与 Q + t·v 的交点；近似平行返回 null。 */
    private fun intersect(p: Vec, ux: Float, uy: Float, q: Vec, vx: Float, vy: Float): Vec? {
        val cross = ux * vy - uy * vx
        if (abs(cross) < PARALLEL_EPS * hypot(ux, uy) * hypot(vx, vy)) return null
        val s = ((q.x - p.x) * vy - (q.y - p.y) * vx) / cross
        return Vec(p.x + s * ux, p.y + s * uy)
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        if (lengthSq <= 1e-6f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
