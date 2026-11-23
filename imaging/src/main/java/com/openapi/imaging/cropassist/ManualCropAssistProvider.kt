package com.openapi.imaging.cropassist

import android.graphics.Bitmap
import com.openapi.imaging.CropNorm

/** Always-available baseline: instantly returns the same fixed centered box Phase 1 used. */
class ManualCropAssistProvider : CropAssistProvider {
    override val id: String = "manual"
    override val displayName: String = "手动(默认居中框)"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun suggest(bitmap: Bitmap): CropAssistResult =
        CropAssistResult.SuggestedBox(
            norm = CropNorm.Default,
            confidenceLabel = null,
            elapsedMs = 0L,
        )
}
