package com.openapi.imaging.cropassist

import com.openapi.imaging.CropNorm

/** Outcome of asking a [CropAssistProvider] to suggest a crop box for a photo. */
sealed interface CropAssistResult {
    /** A suggested crop rectangle, normalized to the source bitmap's width/height. */
    data class SuggestedBox(
        val norm: CropNorm,
        val confidenceLabel: String?,
        val elapsedMs: Long,
    ) : CropAssistResult

    /** The provider could not produce a suggestion (model missing, detection failed, etc.). */
    data class Unavailable(val reason: String) : CropAssistResult
}
