package com.openapi.common.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Image decode/resize helpers ported from aixue's feature/cropper BitmapResize.kt
 * (internal telemetry calls removed; scaling/EXIF logic kept as-is).
 */
object ImageUtils {

    private const val THUMBNAIL_MAX_EDGE_PX = 256

    fun Bitmap.toThumbnail(): Bitmap = resizeSmart(THUMBNAIL_MAX_EDGE_PX)

    /**
     * Smart-resizes an image for upload: caps the long edge, caps total pixels, and protects
     * against an overly small short edge after scaling down.
     */
    fun Bitmap.resizeSmart(
        maxSide: Int = 1344,
        minShortSideAfterResize: Int = 160,
        maxPixels: Int = 2_000_000,
    ): Bitmap {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return this

        var scale = min(maxSide.toDouble() / max(w, h), 1.0)

        val scaledPixels = (w * scale) * (h * scale)
        if (scaledPixels > maxPixels) {
            scale *= sqrt(maxPixels / scaledPixels)
        }

        val shortAfterResize = min(w * scale, h * scale)
        if (shortAfterResize < minShortSideAfterResize) {
            scale = max(scale, min(minShortSideAfterResize.toDouble() / min(w, h), 1.0))
        }

        val newW = max(1, (w * scale).roundToInt())
        val newH = max(1, (h * scale).roundToInt())
        if (newW == w && newH == h) return this
        return Bitmap.createScaledBitmap(this, newW, newH, true)
    }

    /**
     * 裁剪页把 Bitmap 直接交给 easycrop 绘制到 Canvas。Canvas 单次绘制有约 100MB 的纹理上限，
     * 大图（如 6400x6400 解码后约 164MB）会直接抛
     * "Canvas: trying to draw too large bitmap" 把进程带崩。
     * 所以必须在解码阶段按需降采样，而不是先解出原图再缩放——后者峰值内存同样上百 MB，低端机会 OOM。
     */
    private const val DECODE_MAX_EDGE_PX = 2048

    /** Decodes a JPEG file and applies its EXIF orientation so the result is displayed upright. */
    fun decodeJpegApplyingExifOrientation(path: String): Bitmap? {
        val decoded = runCatching { decodeSampledFile(path, DECODE_MAX_EDGE_PX) }.getOrNull() ?: return null
        return runCatching {
            val exif = ExifInterface(path)
            applyExifOrientation(
                decoded,
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL),
            )
        }.getOrElse { decoded }
    }

    /** Decodes a gallery-picked [uri] and applies its EXIF orientation so the result is upright. */
    fun decodeUriApplyingExifOrientation(context: Context, uri: Uri): Bitmap? {
        val decoded = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                decodeSampledStream(it, DECODE_MAX_EDGE_PX)
            }
        }.getOrNull() ?: return null

        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrElse { ExifInterface.ORIENTATION_NORMAL }

        return applyExifOrientation(decoded, orientation)
    }

    private fun decodeSampledFile(path: String, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun decodeSampledStream(stream: java.io.InputStream, maxEdge: Int): Bitmap? {
        // InputStream 不能重复读，先读进内存再分两步解码以取得原始尺寸。
        val bytes = stream.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (max(width, height) / sample > maxEdge) {
            sample *= 2
        }
        return sample
    }

    private fun applyExifOrientation(decoded: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return decoded
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    fun Bitmap.toJpegByteArray(quality: Int = 90): ByteArray =
        ByteArrayOutputStream().use { stream ->
            compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray()
        }
}
