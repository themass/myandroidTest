package com.openapi.imaging.cropassist

import android.graphics.Bitmap

/**
 * A pluggable backend that looks at a captured photo and suggests where the question's crop
 * box should be. Implementations range from an instant manual baseline to real on-device
 * detection (OpenCV contour detection, the ported company MNN model).
 *
 * Google's ML Kit Document Scanner is deliberately NOT a [CropAssistProvider]: its public API
 * only launches a full-screen scan-and-crop activity and returns a final image, with no way to
 * retrieve raw corner coordinates — see MlKitScanLauncher（:feature-photosolve）
 * for that separate, whole-flow entry point instead.
 */
interface CropAssistProvider {
    val id: String
    val displayName: String

    /** Whether this provider is usable right now (e.g. the company model's weights are loaded). */
    suspend fun isAvailable(): Boolean

    /** Suggests a crop box for [bitmap]. Always returns promptly; never throws. */
    suspend fun suggest(bitmap: Bitmap): CropAssistResult
}
