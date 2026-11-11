package com.openapi.mediaview.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 把 [PhotoEdits] 重放到一张位图上，得到最终图片。纯 CPU 实现，不依赖 OpenCV/GPU，
 * 保证 :mediaview 不带原生库（它要能被别的项目直接复用）。
 *
 * 顺序：旋转/镜像 → 颜色调整 → 涂鸦/文字/马赛克（后三者共用一套「原图坐标 → 画布坐标」的映射）。
 * 标注必须在旋转之后画，否则用户转一下图，之前画的字就横过来了。
 */
object PhotoRenderer {

    private const val TAG = "PhotoRenderer"

    /** 渲染结果的上限：长边超过就等比缩小，避免在低端机上 OOM（100 MP 的位图在 4G 堆里必崩）。 */
    const val MAX_OUTPUT_LONG_EDGE = 4096

    /**
     * @param source 原图（不会被回收）
     * @param edits 操作列表
     * @return 渲染后的新位图；失败（OOM 等）返回原图引用
     */
    fun render(source: Bitmap, edits: PhotoEdits): Bitmap {
        if (edits.ops.isEmpty()) return source
        return try {
            renderInternal(source, edits)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "render out of memory, falling back to source", e)
            source
        } catch (e: RuntimeException) {
            Log.e(TAG, "render failed, falling back to source", e)
            source
        }
    }

    private fun renderInternal(source: Bitmap, edits: PhotoEdits): Bitmap {
        // 顺序必须和 [mapPoint] 一致：先在原图坐标系里镜像，再旋转。
        // 之前图片是「先旋转再镜像」、标注是「先镜像再旋转」，旋转 90°/270° + 镜像时
        // 涂鸦会偏离照片上的物体（见 PhotoRendererTest.annotationsStayOnTheSameSpotUnderRotateAndFlip）。
        val mirrored = if (edits.ops.contains(EditOp.FlipHorizontal)) mirror(source) else source
        val rotated = rotate(mirrored, edits.quarterTurns)
        val adjusted = applyAdjust(rotated, edits.adjust)
        val output = if (adjusted === rotated) rotated else adjusted
        val scaled = writableCopy(downscaleIfNeeded(output), source)

        val canvas = Canvas(scaled)
        val w = scaled.width.toFloat()
        val h = scaled.height.toFloat()
        val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        edits.ops.forEach { op ->
            when (op) {
                is EditOp.Stroke -> drawStroke(canvas, op, w, h, edits, markerPaint)
                is EditOp.Text -> drawText(canvas, op, w, h, edits, markerPaint)
                is EditOp.Mosaic -> drawMosaic(scaled, op, edits)
                else -> Unit // 旋转/镜像/调整已在上面一次性处理
            }
        }
        return scaled
    }

    /**
     * 标注要直接画在位图上，所以这里必须拿到一张**可写、且不是调用方那张**的位图：
     *
     * - 解码器给的位图是不可变的，`Canvas(immutable)` 会抛 IllegalStateException；
     * - 前面的旋转/镜像/调整/缩放都没发生时，[candidate] 就是 [source] 本身，
     *   直接画会改掉编辑器预览正在显示的原图。
     *
     * `Bitmap.createBitmap(..., matrix, ...)` 和 `createScaledBitmap` 在某些情况下也会返回
     * 不可变位图，所以不按「是否新建」判断，只看「是否可写、是否与原图同一个对象」。
     */
    private fun writableCopy(candidate: Bitmap, source: Bitmap): Bitmap =
        if (candidate.isMutable && candidate !== source) {
            candidate
        } else {
            candidate.copy(Bitmap.Config.ARGB_8888, /* isMutable = */ true)
                ?: error("无法复制位图（内存不足？）")
        }

    // ---- 旋转 / 镜像 ----

    private fun rotate(source: Bitmap, turns: Int): Bitmap {
        if (turns % 4 == 0) return source
        val matrix = android.graphics.Matrix().apply { postRotate(90f * (turns % 4)) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun mirror(source: Bitmap): Bitmap {
        val matrix = android.graphics.Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun downscaleIfNeeded(source: Bitmap): Bitmap {
        val longEdge = max(source.width, source.height)
        if (longEdge <= MAX_OUTPUT_LONG_EDGE) return source
        val ratio = MAX_OUTPUT_LONG_EDGE.toFloat() / longEdge
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).roundToInt().coerceAtLeast(1),
            (source.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    // ---- 颜色调整 ----

    private fun applyAdjust(source: Bitmap, adjust: EditOp.Adjust): Bitmap {
        if (adjust.isIdentity) return source
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(buildColorMatrix(adjust))
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    /**
     * 亮度用「平移」、对比度用「围绕 128 缩放」、饱和度用 ColorMatrix 的标准饱和度矩阵。
     * 三者合成一个矩阵，一次绘制完成。
     *
     * 对外是 -100..100；映射到：
     * - 亮度 ±80（再多就全黑/全白）
     * - 对比度系数 0.5..1.8
     * - 饱和度系数 0..2
     */
    internal fun buildColorMatrix(adjust: EditOp.Adjust): ColorMatrix {
        val b = adjust.brightness.coerceIn(-100, 100) * 0.8f
        val c = 1f + adjust.contrast.coerceIn(-100, 100) / 100f * 0.8f
        val s = 1f + adjust.saturation.coerceIn(-100, 100) / 100f
        val sat = ColorMatrix().apply { setSaturation(s) }
        // 对比度 + 亮度：out = (in - 128) * c + 128 + b
        val contrast = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, 128f * (1f - c) + b,
                0f, c, 0f, 0f, 128f * (1f - c) + b,
                0f, 0f, c, 0f, 128f * (1f - c) + b,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        contrast.preConcat(sat)
        return contrast
    }

    // ---- 标注 ----

    /**
     * 原图（旋转前）坐标 → 渲染后画布坐标。
     *
     * 因为旋转只可能是 90° 的倍数、镜像只影响水平方向，这个映射是「线性 + 交换宽高」，不需要通用矩阵。
     */
    internal fun mapPoint(xNorm: Float, yNorm: Float, edits: PhotoEdits, outputW: Float, outputH: Float): Pair<Float, Float> {
        val srcW = edits.sourceWidth.toFloat()
        val srcH = edits.sourceHeight.toFloat()
        val px = xNorm * srcW
        val py = yNorm * srcH
        val flipped = edits.ops.contains(EditOp.FlipHorizontal)
        // 先镜像（在原图坐标系内），再旋转
        val mx = if (flipped) srcW - px else px
        val (rx, ry, rw, rh) = when (edits.quarterTurns % 4) {
            1 -> Quad(srcH - py, mx, srcH, srcW)      // 顺时针 90°：x' = H - y, y' = x
            2 -> Quad(srcW - mx, srcH - py, srcW, srcH) // 180°
            3 -> Quad(py, srcW - mx, srcH, srcW)      // 顺时针 270°
            else -> Quad(mx, py, srcW, srcH)
        }
        return (rx / rw * outputW) to (ry / rh * outputH)
    }

    /** 旋转后的坐标系尺寸：(w, h)，仅用于内部计算。 */
    private data class Quad(val x: Float, val y: Float, val w: Float, val h: Float)

    private fun drawStroke(canvas: Canvas, op: EditOp.Stroke, w: Float, h: Float, edits: PhotoEdits, paint: Paint) {
        if (op.points.size < 2) return
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = op.colorArgb.toInt()
        paint.strokeWidth = op.widthRatio * min(w, h)
        val path = android.graphics.Path()
        op.points.forEachIndexed { index, point ->
            val (x, y) = mapPoint(point.x, point.y, edits, w, h)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawText(canvas: Canvas, op: EditOp.Text, w: Float, h: Float, edits: PhotoEdits, paint: Paint) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = op.colorArgb.toInt()
        paint.textSize = op.sizeRatio * min(w, h)
        paint.typeface = Typeface.DEFAULT_BOLD
        val (x, y) = mapPoint(op.x, op.y, edits, w, h)
        // 多行：按 \n 拆开，行距 1.2 倍
        val lines = op.value.split('\n')
        val lineHeight = paint.textSize * 1.2f
        lines.forEachIndexed { i, line -> canvas.drawText(line, x, y + i * lineHeight, paint) }
    }

    /**
     * 马赛克：把目标矩形内的像素按 [EditOp.Mosaic.blockRatio] 聚合，再把每块放大回去。
     * 只处理矩形内的区域，不整图处理（整图会慢一个数量级）。
     */
    private fun drawMosaic(bitmap: Bitmap, op: EditOp.Mosaic, edits: PhotoEdits) {
        val w = bitmap.width
        val h = bitmap.height
        // 矩形也要走旋转/镜像映射；90° 的倍数旋转仍保持轴对齐，所以映射两个角再取 min/max 即可
        val (x1, y1) = mapPoint(op.left, op.top, edits, w.toFloat(), h.toFloat())
        val (x2, y2) = mapPoint(op.right, op.bottom, edits, w.toFloat(), h.toFloat())
        val left = min(x1, x2).roundToInt().coerceIn(0, w)
        val top = min(y1, y2).roundToInt().coerceIn(0, h)
        val right = max(x1, x2).roundToInt().coerceIn(left, w)
        val bottom = max(y1, y2).roundToInt().coerceIn(top, h)
        if (right - left < 2 || bottom - top < 2) return

        val block = (op.blockRatio * min(w, h)).roundToInt().coerceAtLeast(2)
        val cols = max(1, (right - left) / block)
        val rows = max(1, (bottom - top) / block)
        val pixels = IntArray(cols * rows)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val sx = left + col * (right - left) / cols
                val sy = top + row * (bottom - top) / rows
                pixels[row * cols + col] = bitmap.getPixel(
                    sx.coerceIn(0, w - 1),
                    sy.coerceIn(0, h - 1),
                )
            }
        }
        val blockBitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        blockBitmap.setPixels(pixels, 0, cols, 0, 0, cols, rows)
        val paint = Paint().apply { isFilterBitmap = false } // 关闭插值，保持方块边缘
        Canvas(bitmap).drawBitmap(
            blockBitmap,
            null,
            Rect(left, top, right, bottom),
            paint,
        )
        blockBitmap.recycle()
    }

    /** 供界面做「保存」时用：按 [PhotoEdits] 计算最终尺寸。 */
    fun outputSize(edits: PhotoEdits): Pair<Int, Int> {
        val w = edits.displayWidth
        val h = edits.displayHeight
        val longEdge = max(w, h)
        if (longEdge <= MAX_OUTPUT_LONG_EDGE) return w to h
        val ratio = MAX_OUTPUT_LONG_EDGE.toFloat() / longEdge
        return (w * ratio).roundToInt() to (h * ratio).roundToInt()
    }
}
