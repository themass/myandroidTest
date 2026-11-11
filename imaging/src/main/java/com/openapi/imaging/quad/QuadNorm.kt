package com.openapi.imaging.quad

import com.openapi.imaging.CropNorm
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * 归一化（0..1，相对原图宽高）的四边形，用来表示题目纸张的四个角。
 *
 * 为什么不用 [CropNorm]（轴对齐矩形）：手机拍题目时纸面几乎不可能正对镜头，
 * 拍出来是一个梯形/旋转的四边形。只取外接矩形会把大量背景带进来，而且斜边上的文字会被截断。
 * 正确的做法是按四个角做透视矫正（[QuadWarp]）。
 *
 * 角点顺序任选，用 [ordered] 规范化成 TL → TR → BR → BL（从图像左上角开始顺时针）。
 */
data class QuadNorm(val corners: List<Point>) {

    /** 归一化点。 */
    data class Point(val x: Float, val y: Float)

    val isValid: Boolean
        get() {
            if (corners.size != 4) return false
            if (corners.any { it.x !in -0.05f..1.05f || it.y !in -0.05f..1.05f }) return false
            val o = ordered()
            // 退化情形：四点共线、或面积小到没有意义
            return o.area() >= MIN_AREA && !o.isSelfIntersecting()
        }

    /** 面积（0..1，归一化坐标系）。 */
    fun area(): Float {
        if (corners.size != 4) return 0f
        var sum = 0f
        for (i in 0 until 4) {
            val a = corners[i]
            val b = corners[(i + 1) % 4]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2f
    }

    /**
     * 规范化角点顺序：TL、TR、BR、BL。
     *
     * 做法是先取「最靠左上」和「最靠右下」的点作为对角，再把剩下两个点按叉积分到两侧。
     * 比单纯按 y 排序稳：纸面旋转 45° 时按 y 排序会把角点分错。
     */
    fun ordered(): QuadNorm {
        if (corners.size != 4) return this
        val tl = corners.minByOrNull { it.x + it.y }!!
        val br = corners.maxByOrNull { it.x + it.y }!!
        val rest = corners.filter { it !== tl && it !== br }
        if (rest.size != 2) return this
        // 其余两点：x-y 较大的一个是右上
        val tr = if (rest[0].x - rest[0].y > rest[1].x - rest[1].y) rest[0] else rest[1]
        val bl = if (tr === rest[0]) rest[1] else rest[0]
        // 交叉验证：如果 tl/br 选中的其实是同一条边上的两点，退回按角度排序
        val ordered = listOf(tl, tr, br, bl)
        return if (isConvexish(ordered)) QuadNorm(ordered) else QuadNorm(sortByAngle())
    }

    /** 以质心为中心按极角排序（逆时针），再旋转到从左上角开始。 */
    private fun sortByAngle(): List<Point> {
        val cx = corners.sumOf { it.x.toDouble() }.toFloat() / 4f
        val cy = corners.sumOf { it.y.toDouble() }.toFloat() / 4f
        val byAngle = corners.sortedBy { kotlin.math.atan2((it.y - cy).toDouble(), (it.x - cx).toDouble()) }
        val start = byAngle.indices.minByOrNull { byAngle[it].x + byAngle[it].y } ?: 0
        // atan2 递增方向是顺时针（y 向下），所以从 TL 开始依次是 TL、TR、BR、BL
        return List(4) { byAngle[(start + it) % 4] }
    }

    private fun isConvexish(): Boolean = isConvexish(corners)

    /** 供 [ordered] 在构造新四边形之前先校验候选顺序。 */
    private fun isConvexish(points: List<Point>): Boolean {
        if (points.size != 4) return false
        var sign = 0
        for (i in 0 until 4) {
            val a = points[i]
            val b = points[(i + 1) % 4]
            val c = points[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s != 0) {
                if (sign == 0) sign = s else if (sign != s) return false
            }
        }
        return true
    }

    private fun isSelfIntersecting(): Boolean = !isConvexish()

    /** 外接矩形，用于「不启用透视矫正」时的兜底裁剪。 */
    fun bounds(): CropNorm = CropNorm(
        left = corners.minOf { it.x }.coerceIn(0f, 1f),
        top = corners.minOf { it.y }.coerceIn(0f, 1f),
        right = corners.maxOf { it.x }.coerceIn(0f, 1f),
        bottom = corners.maxOf { it.y }.coerceIn(0f, 1f),
    )

    /**
     * 透视矫正后的输出尺寸（像素）：宽取上下两条边的较长者，高取左右两条边的较长者，
     * 再按原图尺寸换算，并限制在 [maxLongEdge] 内。
     */
    fun outputSizePx(sourceWidth: Int, sourceHeight: Int, maxLongEdge: Int = 2048): Pair<Int, Int> {
        val o = ordered()
        fun px(p: Point) = Point(p.x * sourceWidth, p.y * sourceHeight)
        val tl = px(o.corners[0])
        val tr = px(o.corners[1])
        val br = px(o.corners[2])
        val bl = px(o.corners[3])
        fun dist(a: Point, b: Point) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
        var w = max(dist(tl, tr), dist(bl, br)).toInt().coerceAtLeast(1)
        var h = max(dist(tl, bl), dist(tr, br)).toInt().coerceAtLeast(1)
        val longEdge = max(w, h)
        if (longEdge > maxLongEdge) {
            val ratio = maxLongEdge.toFloat() / longEdge
            w = (w * ratio).toInt().coerceAtLeast(1)
            h = (h * ratio).toInt().coerceAtLeast(1)
        }
        return w to h
    }

    companion object {
        /** 小于这个面积认为检测失败（多半是噪声或整张图）。 */
        const val MIN_AREA = 0.08f

        /** 面积上限：几乎占满整张图时说明没有可裁的目标。 */
        const val MAX_AREA = 0.985f

        /** 整张图作为四边形（用户没有检测结果时的初始值）。 */
        fun fullFrame(): QuadNorm = QuadNorm(
            listOf(
                Point(0.02f, 0.02f),
                Point(0.98f, 0.02f),
                Point(0.98f, 0.98f),
                Point(0.02f, 0.98f),
            ),
        )
    }
}
