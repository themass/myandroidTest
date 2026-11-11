package com.openapi.imaging.quad

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.openapi.imaging.CropNorm

/**
 * 按 [QuadNorm] 的四个角把题目区域「拉正」。
 *
 * 两条实现路径，谁可用用谁：
 * 1. OpenCV：`Imgproc.getPerspectiveTransform` + `warpPerspective`（真正的透视矫正，斜拍的题目会变正）；
 * 2. 兜底：裁剪四边形的外接矩形（不矫正透视，但一定可用，且不会把题目裁掉）。
 *
 * OpenCV 在 :imaging 里已经是依赖（[com.openapi.imaging.image.ImagePipeline] 用它做增强），
 * 这里不再额外引入库；加载失败时自动走兜底路径，不会抛异常。
 */
object QuadWarp {

    private const val TAG = "QuadWarp"

    /**
     * @param source 原图
     * @param quad 归一化四边形（顺序任意，内部会规范成 TL→TR→BR→BL）
     * @param maxLongEdge 输出长边上限
     * @param perspective 是否允许透视矫正；false 时直接裁外接矩形
     * @return 矫正后的新位图；失败时返回外接矩形裁剪结果，再失败返回原图
     */
    fun warp(
        source: Bitmap,
        quad: QuadNorm,
        maxLongEdge: Int = 2048,
        perspective: Boolean = true,
    ): Bitmap {
        val ordered = quad.ordered()
        if (!ordered.isValid) {
            Log.w(TAG, "invalid quad, returning source")
            return source
        }
        val (outW, outH) = ordered.outputSizePx(source.width, source.height, maxLongEdge)

        if (perspective) {
            openCvWarp(source, ordered, outW, outH)?.let { return it }
        }
        return boundsCrop(source, ordered.bounds(), maxLongEdge)
    }

    private fun openCvWarp(source: Bitmap, quad: QuadNorm, outW: Int, outH: Int): Bitmap? {
        if (!org.opencv.android.OpenCVLoader.initLocal()) {
            Log.w(TAG, "OpenCV unavailable, falling back to bounding-box crop")
            return null
        }
        val rgba = org.opencv.core.Mat()
        val src = org.opencv.core.Mat()
        val dst = org.opencv.core.Mat()
        try {
            org.opencv.android.Utils.bitmapToMat(source, rgba)
            org.opencv.imgproc.Imgproc.cvtColor(rgba, src, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)
            val sw = source.width.toFloat()
            val sh = source.height.toFloat()
            val c = quad.corners
            val srcPts = org.opencv.core.MatOfPoint2f(
                org.opencv.core.Point(c[0].x * sw.toDouble(), c[0].y * sh.toDouble()),
                org.opencv.core.Point(c[1].x * sw.toDouble(), c[1].y * sh.toDouble()),
                org.opencv.core.Point(c[2].x * sw.toDouble(), c[2].y * sh.toDouble()),
                org.opencv.core.Point(c[3].x * sw.toDouble(), c[3].y * sh.toDouble()),
            )
            val dstPts = org.opencv.core.MatOfPoint2f(
                org.opencv.core.Point(0.0, 0.0),
                org.opencv.core.Point(outW.toDouble(), 0.0),
                org.opencv.core.Point(outW.toDouble(), outH.toDouble()),
                org.opencv.core.Point(0.0, outH.toDouble()),
            )
            val matrix = org.opencv.imgproc.Imgproc.getPerspectiveTransform(srcPts, dstPts)
            try {
                org.opencv.imgproc.Imgproc.warpPerspective(
                    src,
                    dst,
                    matrix,
                    org.opencv.core.Size(outW.toDouble(), outH.toDouble()),
                    org.opencv.imgproc.Imgproc.INTER_LINEAR,
                )
                val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                val outRgba = org.opencv.core.Mat()
                try {
                    org.opencv.imgproc.Imgproc.cvtColor(dst, outRgba, org.opencv.imgproc.Imgproc.COLOR_RGB2RGBA)
                    org.opencv.android.Utils.matToBitmap(outRgba, out)
                } finally {
                    outRgba.release()
                }
                return out
            } finally {
                matrix.release()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "perspective warp failed", e)
            return null
        } finally {
            rgba.release()
            src.release()
            dst.release()
        }
    }

    /** 兜底：裁外接矩形。 */
    fun boundsCrop(source: Bitmap, bounds: CropNorm, maxLongEdge: Int = 2048): Bitmap {
        val left = (bounds.left * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (bounds.top * source.height).toInt().coerceIn(0, source.height - 1)
        val w = (bounds.width * source.width).toInt().coerceIn(1, source.width - left)
        val h = (bounds.height * source.height).toInt().coerceIn(1, source.height - top)
        var cropped = Bitmap.createBitmap(source, left, top, w, h)
        val longEdge = maxOf(cropped.width, cropped.height)
        if (longEdge > maxLongEdge) {
            val ratio = maxLongEdge.toFloat() / longEdge
            val scaled = Bitmap.createScaledBitmap(
                cropped,
                (cropped.width * ratio).toInt().coerceAtLeast(1),
                (cropped.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== cropped) cropped.recycle()
            cropped = scaled
        }
        return cropped
    }

    /** 把四边形画在预览上（调试/给用户看检测结果）。 */
    fun drawOverlay(
        canvas: Canvas,
        quad: QuadNorm,
        viewWidth: Float,
        viewHeight: Float,
        paint: Paint,
    ) {
        val c = quad.ordered().corners
        val path = android.graphics.Path()
        c.forEachIndexed { i, p ->
            val x = p.x * viewWidth
            val y = p.y * viewHeight
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    /** 供测试/调试：把位图缩放后塞进指定矩形（不做透视）。 */
    internal fun fitInto(source: Bitmap, rect: RectF): Bitmap {
        val out = Bitmap.createBitmap(
            rect.width().toInt().coerceAtLeast(1),
            rect.height().toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        Canvas(out).drawBitmap(source, null, RectF(0f, 0f, out.width.toFloat(), out.height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
