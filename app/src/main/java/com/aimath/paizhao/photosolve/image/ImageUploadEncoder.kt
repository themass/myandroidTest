package com.aimath.paizhao.photosolve.image

import android.graphics.Bitmap
import com.aimath.paizhao.photosolve.image.ImagePipeline.encode

/** 上传前的编码策略。默认 [JPEG] 保持现有行为；[WEBP_IF_SMALLER] 在体积明显更小时才换成 WebP。 */
enum class FormatPolicy { JPEG, WEBP_IF_SMALLER }

data class EncodedImage(val bytes: ByteArray, val format: OutputFormat, val quality: Int)

object ImageUploadEncoder {

    /** 目标体积上限：超过就降质量，直到满足或到达最低质量。 */
    const val DEFAULT_MAX_BYTES = 800 * 1024

    /** 质量阶梯，从高到低；越靠后清晰度损失越明显。 */
    private val QUALITY_LADDER = intArrayOf(90, 85, 80, 75)

    /** WebP 只有至少比 JPEG 小这么多才采用，避免为了几 KB 换格式。 */
    private const val MIN_SAVING_RATIO = 0.9

    fun encode(
        bitmap: Bitmap,
        policy: FormatPolicy = FormatPolicy.JPEG,
        maxBytes: Int = DEFAULT_MAX_BYTES,
    ): EncodedImage {
        val jpeg = encodeWithinBudget(bitmap, OutputFormat.JPEG, maxBytes)
        if (policy == FormatPolicy.JPEG) return jpeg

        val webp = encodeWithinBudget(bitmap, OutputFormat.WEBP, maxBytes)
        return if (webp.bytes.size < jpeg.bytes.size * MIN_SAVING_RATIO) webp else jpeg
    }

    private fun encodeWithinBudget(bitmap: Bitmap, format: OutputFormat, maxBytes: Int): EncodedImage {
        var last: EncodedImage? = null
        for (quality in QUALITY_LADDER) {
            val bytes = bitmap.encode(format, quality)
            last = EncodedImage(bytes, format, quality)
            if (bytes.size <= maxBytes) return last
        }
        return last!!
    }
}
