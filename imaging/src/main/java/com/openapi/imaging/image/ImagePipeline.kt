package com.openapi.imaging.image

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream

enum class OutputFormat { JPEG, PNG, WEBP }

object ImagePipeline {

    private const val TAG = "ImagePipeline"

    fun Bitmap.rotated(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    fun Bitmap.encode(format: OutputFormat, quality: Int = 90): ByteArray {
        val compressFormat = when (format) {
            OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
            OutputFormat.WEBP -> if (Build.VERSION.SDK_INT >= 30) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        }
        val effectiveQuality = if (format == OutputFormat.PNG) 100 else quality
        return ByteArrayOutputStream().use { out ->
            compress(compressFormat, effectiveQuality, out)
            out.toByteArray()
        }
    }

    /**
     * 文档增强：Lab 空间上对亮度做 CLAHE（自适应直方图均衡，提升局部对比度），再做一次非锐化掩膜。
     * OpenCV 未能加载时返回原图，不影响后续流程。
     */
    fun Bitmap.enhanceForDocument(): Bitmap {
        if (!OpenCVLoader.initLocal()) {
            Log.w(TAG, "OpenCV unavailable, skipping document enhancement")
            return this
        }
        val rgba = Mat()
        val rgb = Mat()
        val lab = Mat()
        val blur = Mat()
        try {
            Utils.bitmapToMat(this, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)

            Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
            val channels = ArrayList<Mat>()
            Core.split(lab, channels)
            try {
                Imgproc.createCLAHE(2.0, Size(8.0, 8.0)).apply(channels[0], channels[0])
                Core.merge(channels, lab)
            } finally {
                channels.forEach { it.release() }
            }
            Imgproc.cvtColor(lab, rgb, Imgproc.COLOR_Lab2RGB)

            Imgproc.GaussianBlur(rgb, blur, Size(0.0, 0.0), 3.0)
            Core.addWeighted(rgb, 1.5, blur, -0.5, 0.0, rgb)

            Imgproc.cvtColor(rgb, rgba, Imgproc.COLOR_RGB2RGBA)
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, out)
            return out
        } finally {
            rgba.release()
            rgb.release()
            lab.release()
            blur.release()
        }
    }
}
