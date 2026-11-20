package com.openapi.mediaview.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 点选 / 选中框：判断点击落在哪一条标注上，并给出选中框。
 *
 * 纯函数、不含 Android 类型，所以是普通 JVM 单测（见 EditHitTestTest）。
 *
 * 输入输出都是**显示坐标系**（旋转、镜像之后，用户看到的画面）下的 0..1 归一化值。
 * 距离一律换算成**显示像素**再比较：归一化坐标在非正方形图片上 x、y 的量纲不同，
 * 直接比会让横向和纵向的点选范围不一样大。
 */
object EditHitTest {

    /** 手指可点范围，相对显示短边。0.04 约等于手机上 12~15dp，够手指点中细线。 */
    const val DEFAULT_TOLERANCE = 0.04f

    /** 一个中文字符与西文字符的宽度（以字号为 1）。估算值，只用来决定点击范围和选中框。 */
    private const val CJK_WIDTH = 1.0f
    private const val LATIN_WIDTH = 0.58f
    private const val LINE_HEIGHT = 1.2f

    /** 从最上层往下找，返回命中的操作下标；没有命中返回 null。 */
    fun findOpAt(edits: PhotoEdits, displayNorm: Offset, tolerance: Float = DEFAULT_TOLERANCE): Int? {
        val w = edits.displayWidth.toFloat()
        val h = edits.displayHeight.toFloat()
        if (w <= 0f || h <= 0f) return null
        val shortest = min(w, h)
        val tolPx = tolerance * shortest
        val px = displayNorm.x * w
        val py = displayNorm.y * h
        // 倒序：后画的在上层，先命中它
        for (index in edits.ops.indices.reversed()) {
            when (val op = edits.ops[index]) {
                is EditOp.Stroke ->
                    if (nearPolyline(px, py, op.points, edits, w, h, op.widthRatio * shortest / 2f + tolPx)) return index
                is EditOp.Mosaic ->
                    if (nearPolyline(px, py, op.points, edits, w, h, op.widthRatio * shortest / 2f + tolPx)) return index
                is EditOp.Text -> {
                    val r = textBoundsPx(op, edits)
                    if (px >= r.left - tolPx && px <= r.right + tolPx && py >= r.top - tolPx && py <= r.bottom + tolPx) {
                        return index
                    }
                }
                else -> Unit // 调整/旋转/镜像没有可点的形状
            }
        }
        return null
    }

    /**
     * 选中框（显示归一化坐标）。涂鸦/马赛克按笔迹外接矩形外扩半个笔宽，文字按估算的文本框。
     * 没有可画的框（调整、旋转等）返回 null。
     */
    fun selectionBounds(op: EditOp, edits: PhotoEdits): Rect? {
        val w = edits.displayWidth.toFloat()
        val h = edits.displayHeight.toFloat()
        if (w <= 0f || h <= 0f) return null
        val shortest = min(w, h)
        return when (op) {
            is EditOp.Stroke -> polylineBounds(op.points, edits, op.widthRatio * shortest / 2f, w, h)
            is EditOp.Mosaic -> polylineBounds(op.points, edits, op.widthRatio * shortest / 2f, w, h)
            is EditOp.Text -> textBoundsPx(op, edits).let { Rect(it.left / w, it.top / h, it.right / w, it.bottom / h) }
            else -> null
        }
    }

    /** 文字块的显示像素包围盒（左上角锚点，水平排版，与 [PhotoRenderer] 的绘制方式一致）。 */
    fun textBoundsPx(op: EditOp.Text, edits: PhotoEdits): Rect {
        val w = edits.displayWidth.toFloat()
        val h = edits.displayHeight.toFloat()
        val fontPx = op.sizeRatio * min(w, h)
        val lines = op.value.split('\n')
        val widest = lines.maxOf { line -> line.sumOf { unitWidth(it).toDouble() }.toFloat() }.coerceAtLeast(0.5f)
        val anchor = EditCoords.toDisplayNorm(op.x, op.y, edits)
        val left = anchor.x * w
        val top = anchor.y * h
        return Rect(left, top, left + widest * fontPx, top + lines.size * fontPx * LINE_HEIGHT)
    }

    private fun unitWidth(c: Char): Float = if (c.code > 0x2E7F) CJK_WIDTH else LATIN_WIDTH

    // ---- 几何 ----

    private fun nearPolyline(
        px: Float, py: Float, points: List<Offset>, edits: PhotoEdits, w: Float, h: Float, radiusPx: Float,
    ): Boolean {
        if (points.isEmpty()) return false
        val pts = points.map { EditCoords.toDisplayNorm(it, edits).let { d -> Offset(d.x * w, d.y * h) } }
        if (pts.size == 1) return distance(px, py, pts[0].x, pts[0].y) <= radiusPx
        for (i in 0 until pts.size - 1) {
            if (distanceToSegment(px, py, pts[i], pts[i + 1]) <= radiusPx) return true
        }
        return false
    }

    private fun polylineBounds(points: List<Offset>, edits: PhotoEdits, padPx: Float, w: Float, h: Float): Rect? {
        if (points.isEmpty()) return null
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        points.forEach {
            val d = EditCoords.toDisplayNorm(it, edits)
            l = min(l, d.x * w); r = max(r, d.x * w)
            t = min(t, d.y * h); b = max(b, d.y * h)
        }
        return Rect((l - padPx) / w, (t - padPx) / h, (r + padPx) / w, (b + padPx) / h)
    }

    private fun distanceToSegment(px: Float, py: Float, a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 1e-6f) return distance(px, py, a.x, a.y)
        val t = (((px - a.x) * dx + (py - a.y) * dy) / lenSq).coerceIn(0f, 1f)
        return distance(px, py, a.x + t * dx, a.y + t * dy)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }
}
