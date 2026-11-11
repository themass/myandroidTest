package com.openapi.common.image

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * 所有上传图片的统一压缩（聊天图片、「文件」里选的图片、拍照解题）。
 *
 * 参数（已确认）：
 * - 长边不超过 [MAX_LONG_EDGE_PX]（1600）；
 * - JPEG，质量从 [QUALITY]（82）开始，超过 [MAX_BYTES]（800 KB）就逐级降质量，最低 [MIN_QUALITY]；
 * - 仍超过就把长边再缩 0.8 倍重试，直到 [MIN_LONG_EDGE_PX]。
 *
 * 调用方负责解码（需要处理 EXIF 方向）；本类只做缩放与编码，输入的 bitmap 不会被回收。
 */
object ImageCompressor {
    const val MAX_LONG_EDGE_PX = 1600
    const val QUALITY = 82
    const val MIN_QUALITY = 60
    const val MAX_BYTES = 800 * 1024
    const val MIN_LONG_EDGE_PX = 640

    data class Result(val bytes: ByteArray, val width: Int, val height: Int, val quality: Int)

    fun compress(source: Bitmap): Result {
        var edge = MAX_LONG_EDGE_PX
        var current = scaleToLongEdge(source, edge)
        while (true) {
            var quality = QUALITY
            var bytes = encode(current, quality)
            while (bytes.size > MAX_BYTES && quality > MIN_QUALITY) {
                quality -= 6
                bytes = encode(current, quality.coerceAtLeast(MIN_QUALITY))
            }
            if (bytes.size <= MAX_BYTES || edge <= MIN_LONG_EDGE_PX) {
                return Result(bytes, current.width, current.height, quality.coerceAtLeast(MIN_QUALITY))
            }
            edge = (edge * 0.8f).roundToInt()
            if (current !== source) current.recycle()
            current = scaleToLongEdge(source, edge)
        }
    }

    /** 长边不超过 [maxEdge] 时原样返回（同一个对象）；否则返回缩小后的新 bitmap。 */
    fun scaleToLongEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longEdge
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
