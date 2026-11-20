package com.openapi.mediaview.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
 *
 * 同一个函数既用于**导出**（长边 4096），也用于**编辑时的预览**（长边更小），
 * 所以界面看到的和保存出来的完全一致。
 */
object PhotoRenderer {

    private const val TAG = "PhotoRenderer"

    /** 导出结果的上限：长边超过就等比缩小，避免在低端机上 OOM。 */
    const val MAX_OUTPUT_LONG_EDGE = 4096

    /**
     * @param source 原图（不会被回收）
     * @param edits 操作列表
     * @param maxLongEdge 输出长边上限，默认 [MAX_OUTPUT_LONG_EDGE]；编辑时传屏幕尺寸减少开销
     * @return 渲染后的新位图；失败（OOM 等）返回原图引用
     */
    fun render(source: Bitmap, edits: PhotoEdits, maxLongEdge: Int = MAX_OUTPUT_LONG_EDGE): Bitmap {
        // 旋转/镜像也要渲染：它们在预览里同样要可见
        if (edits.ops.isEmpty()) return source
        return try {
            renderInternal(source, edits, maxLongEdge)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "render out of memory, falling back to source", e)
            source
        } catch (e: RuntimeException) {
            Log.e(TAG, "render failed, falling back to source", e)
            source
        }
    }

    private fun renderInternal(source: Bitmap, edits: PhotoEdits, maxLongEdge: Int): Bitmap {
        val rotated = rotate(source, edits.quarterTurns)
        val mirrored = if (edits.isFlipped) mirror(rotated) else rotated
        val adjusted = applyAdjust(mirrored, edits.adjust)
        val scaled = downscaleIfNeeded(adjusted, maxLongEdge)

        // 标注和马赛克都要往位图上写，而上面几步在没有旋转/调整时会把原图直接返回（不可变）。
        // 这里保证拿到的是一张可变位图，否则 Canvas(bitmap) 会抛 IllegalStateException。
        // scaled === source 时即使它可变也不能直接画：那是调用方的原图。
        val canvasTarget = if (scaled !== source && scaled.isMutable) scaled
        else scaled.copy(Bitmap.Config.ARGB_8888, true)

        val canvas = Canvas(canvasTarget)
        val w = canvasTarget.width.toFloat()
        val h = canvasTarget.height.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        edits.ops.forEach { op ->
            when (op) {
                is EditOp.Stroke -> drawStroke(canvas, op, w, h, edits, paint)
                is EditOp.Text -> drawText(canvas, op, w, h, edits, paint)
                is EditOp.Mosaic -> drawMosaic(canvasTarget, op, w, h, edits)
                else -> Unit // 旋转/镜像/调整已在上面一次性处理
            }
        }
        return canvasTarget
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

    private fun downscaleIfNeeded(source: Bitmap, maxLongEdge: Int): Bitmap {
        val longEdge = max(source.width, source.height)
        if (longEdge <= maxLongEdge) return source
        val ratio = maxLongEdge.toFloat() / longEdge
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

    // ---- 坐标映射（纯函数在 EditCoords，便于单测）----

    /** 原图归一化坐标 → 输出位图像素坐标。 */
    internal fun mapPoint(xNorm: Float, yNorm: Float, edits: PhotoEdits, outputW: Float, outputH: Float): Pair<Float, Float> {
        val d = EditCoords.toDisplayNorm(xNorm, yNorm, edits)
        return (d.x * outputW) to (d.y * outputH)
    }

    // ---- 标注 ----

    private fun drawStroke(canvas: Canvas, op: EditOp.Stroke, w: Float, h: Float, edits: PhotoEdits, paint: Paint) {
        if (op.points.size < 2) return
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = op.colorArgb.toInt()
        paint.strokeWidth = op.widthRatio * min(w, h)
        val path = Path()
        op.points.forEachIndexed { index, point ->
            val (x, y) = mapPoint(point.x, point.y, edits, w, h)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }

    /**
     * 文字以**左上角**为锚点（[EditOp.Text.x] / [.y]），因此拖动时手指按住的位置
     * 就是文字块的一角，位置和看到的一致。多行按 \n 拆开，行距 1.2 倍。
     */
    private fun drawText(canvas: Canvas, op: EditOp.Text, w: Float, h: Float, edits: PhotoEdits, paint: Paint) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = op.colorArgb.toInt()
        paint.textSize = op.sizeRatio * min(w, h)
        paint.typeface = if (op.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val (x, y) = mapPoint(op.x, op.y, edits, w, h)
        val lines = op.value.split('\n')
        val lineHeight = paint.textSize * 1.2f
        // drawText 的 y 是基线，不是顶边：用 fontMetrics 把「顶边 y」换算成基线
        val baselineOffset = -paint.fontMetrics.ascent
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, x, y + baselineOffset + i * lineHeight, paint)
        }
    }

    /**
     * 马赛克：**沿笔迹**打码。
     *
     * 做法：先算出笔迹的外接矩形，把这块区域按 [EditOp.Mosaic.blockRatio] 聚合成色块，
     * 再用「笔迹路径」做 DST_IN 裁剪，最后贴回原位。只有笔迹覆盖到的像素被替换，
     * 所以手指滑到哪里就打到哪里，曲线、来回涂抹都成立。
     *
     * 只处理外接矩形内的区域，不整图处理（整图会慢一个数量级）。
     */
    private fun drawMosaic(bitmap: Bitmap, op: EditOp.Mosaic, w: Float, h: Float, edits: PhotoEdits) {
        if (op.points.isEmpty()) return
        val bw = bitmap.width
        val bh = bitmap.height
        if (bw < 2 || bh < 2) return

        // 笔迹转成输出像素坐标
        val pts = op.points.map { mapPoint(it.x, it.y, edits, w, h) }
        val halfWidth = max(1f, op.widthRatio * min(w, h) / 2f)

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        pts.forEach {
            minX = min(minX, it.first); maxX = max(maxX, it.first)
            minY = min(minY, it.second); maxY = max(maxY, it.second)
        }
        val left = (minX - halfWidth).toInt().coerceIn(0, bw - 1)
        val top = (minY - halfWidth).toInt().coerceIn(0, bh - 1)
        // 左边界被夹紧时右边界也要跟着补偿，否则笔迹靠边时马赛克会整体偏移
        val right = (maxX + halfWidth).roundToInt().coerceIn(left + 2, bw)
        val bottom = (maxY + halfWidth).roundToInt().coerceIn(top + 2, bh)
        val rw = right - left
        val rh = bottom - top
        if (rw < 2 || rh < 2) return

        val block = (op.blockRatio * min(bw, bh)).roundToInt().coerceAtLeast(2)
        val cols = max(1, rw / block)
        val rows = max(1, rh / block)
        val pixels = IntArray(cols * rows)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val sx = left + col * rw / cols
                val sy = top + row * rh / rows
                pixels[row * cols + col] = bitmap.getPixel(
                    sx.coerceIn(0, bw - 1),
                    sy.coerceIn(0, bh - 1),
                )
            }
        }

        // 1) 色块图铺满整个外接矩形
        val layer = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
        val blockBitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        blockBitmap.setPixels(pixels, 0, cols, 0, 0, cols, rows)
        val layerCanvas = Canvas(layer)
        layerCanvas.drawBitmap(
            blockBitmap,
            null,
            Rect(0, 0, rw, rh),
            Paint().apply { isFilterBitmap = false }, // 关闭插值，保持方块边缘
        )

        // 2) 只保留笔迹覆盖的部分（DST_IN：目标 alpha ×= 笔迹 alpha）
        val localPath = Path()
        pts.forEachIndexed { index, p ->
            val lx = p.first - left
            val ly = p.second - top
            if (index == 0) localPath.moveTo(lx, ly) else localPath.lineTo(lx, ly)
        }
        if (pts.size == 1) {
            // 点一下也要有一个圆点效果
            localPath.addCircle(pts[0].first - left, pts[0].second - top, halfWidth, Path.Direction.CW)
        }
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = if (pts.size == 1) Paint.Style.FILL else Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = halfWidth * 2f
            color = android.graphics.Color.WHITE
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        layerCanvas.drawPath(localPath, maskPaint)

        // 3) 贴回原位
        Canvas(bitmap).drawBitmap(layer, left.toFloat(), top.toFloat(), null)

        blockBitmap.recycle()
        layer.recycle()
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
