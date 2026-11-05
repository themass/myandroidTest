package com.aimath.paizhao.photosolve.quality

import android.graphics.Bitmap
import com.aimath.paizhao.photosolve.CropNorm
import com.aimath.paizhao.photosolve.cropassist.CropAssistRegistry
import com.aimath.paizhao.photosolve.cropassist.CropAssistResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 端上题目图预处理：先找题目所在区域并裁掉背景，再对裁剪结果做清晰度与曝光判断。
 *
 * 检测顺序沿用 [CropAssistRegistry] 里已有的神经网络和 OpenCV 提供者，哪个可用用哪个；
 * 都找不到时不裁剪，只做质量判断。全程在设备上完成，不需要联网。
 */
class QuestionImagePreparer(private val registry: CropAssistRegistry) {

    /**
     * @param cropped 裁剪后的图；未检测到题目区域时为 null
     * @param cropBox 检测到的区域（归一化坐标，相对原图）
     * @param detector 产生结果的检测器名称，便于在界面上说明
     */
    data class Prepared(
        val source: Bitmap,
        val cropped: Bitmap?,
        val cropBox: CropNorm?,
        val detector: String?,
        val quality: QualityReport,
    ) {
        /** 默认建议发送的图：有裁剪用裁剪图，否则原图。 */
        val preferred: Bitmap get() = cropped ?: source
    }

    suspend fun prepare(source: Bitmap): Prepared = withContext(Dispatchers.Default) {
        val detection = detect(source)
        val cropped = detection?.let { cropTo(source, it.norm) }
        val target = cropped ?: source
        Prepared(
            source = source,
            cropped = cropped,
            cropBox = detection?.norm,
            detector = detection?.detector,
            quality = qualityOf(target),
        )
    }

    private data class Detection(val norm: CropNorm, val detector: String)

    private suspend fun detect(source: Bitmap): Detection? {
        val providers = listOf(registry.docQuadNet, registry.companyMnn, registry.openCv)
        for (provider in providers) {
            if (!provider.isAvailable()) continue
            val suggestion = provider.suggest(source) as? CropAssistResult.SuggestedBox ?: continue
            if (isAcceptable(suggestion.norm)) return Detection(suggestion.norm, provider.displayName)
        }
        return null
    }

    private fun cropTo(source: Bitmap, norm: CropNorm): Bitmap? {
        // 四周各留 2% 余量，避免检测框贴边时把题目首尾字符切掉
        val padX = source.width * PAD_FRACTION
        val padY = source.height * PAD_FRACTION
        val left = max(0, (norm.left * source.width - padX).roundToInt())
        val top = max(0, (norm.top * source.height - padY).roundToInt())
        val right = min(source.width, (norm.right * source.width + padX).roundToInt())
        val bottom = min(source.height, (norm.bottom * source.height + padY).roundToInt())
        val w = right - left
        val h = bottom - top
        if (w < MIN_CROP_PX || h < MIN_CROP_PX) return null
        return Bitmap.createBitmap(source, left, top, w, h)
    }

    /** 检测框太小（多半是噪声）或几乎占满整张图（等于没裁）都不采用。 */
    private fun isAcceptable(norm: CropNorm): Boolean {
        if (norm.width <= 0f || norm.height <= 0f) return false
        val area = norm.width * norm.height
        return area in MIN_AREA..MAX_AREA
    }

    /** 对一张已经确定要用的图做清晰度与曝光判断。 */
    suspend fun qualityOf(bitmap: Bitmap): QualityReport = withContext(Dispatchers.Default) {
        val shortSide = min(bitmap.width, bitmap.height)
        val scale = min(1f, ImageQualityAnalyzer.ANALYSIS_MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height))
        val w = max(1, (bitmap.width * scale).roundToInt())
        val h = max(1, (bitmap.height * scale).roundToInt())
        val scaled = if (w == bitmap.width && h == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) scaled.recycle()
        val gray = IntArray(pixels.size) { i ->
            val p = pixels[i]
            (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }
        ImageQualityAnalyzer.analyze(gray, w, h, shortSide)
    }

    private companion object {
        const val PAD_FRACTION = 0.02f
        const val MIN_AREA = 0.15f
        const val MAX_AREA = 0.95f
        const val MIN_CROP_PX = 64
    }
}
