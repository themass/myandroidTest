package com.aimath.paizhao.photosolve

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

    /** Decodes a JPEG file and applies its EXIF orientation so the result is displayed upright. */
    fun decodeJpegApplyingExifOrientation(path: String): Bitmap? {
        val decoded = BitmapFactory.decodeFile(path) ?: return null
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
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
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
