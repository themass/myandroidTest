package com.openapi.imaging.cropassist

import com.openapi.imaging.CropNorm
import com.openapi.imaging.quad.QuadNorm

/** Outcome of asking a [CropAssistProvider] to suggest a crop box for a photo. */
sealed interface CropAssistResult {
    /**
     * A suggested crop rectangle, normalized to the source bitmap's width/height.
     *
     * @param quad 检测器如果本来就输出四个角点，这里带出真实的四边形（透视信息不丢）；
     * 只支持矩形框的检测器为 null。[norm] 始终等于四边形的 [QuadNorm.bounds]，
     * 让仍按矩形工作的调用方（手动裁剪页等）不受影响。
     */
    data class SuggestedBox(
        val norm: CropNorm,
        val confidenceLabel: String?,
        val elapsedMs: Long,
        val quad: QuadNorm? = null,
    ) : CropAssistResult

    /** The provider could not produce a suggestion (model missing, detection failed, etc.). */
    data class Unavailable(val reason: String) : CropAssistResult
}
