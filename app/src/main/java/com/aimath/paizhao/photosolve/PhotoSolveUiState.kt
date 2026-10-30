package com.aimath.paizhao.photosolve

import android.graphics.Bitmap

/** Which screen of the capture → crop → recognize → result flow is currently shown. */
enum class PhotoSolveStep {
    Home,
    Capture,
    Crop,
    Recognizing,
    Result,
}

/** Normalized (0..1) rectangle relative to a bitmap's width/height — the shared coordinate type
 *  every [com.aimath.paizhao.photosolve.cropassist.CropAssistProvider] returns a suggestion in. */
data class CropNorm(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        /** Centered box covering ~84% of width and ~50% of height — a reasonable default for a single question. */
        val Default = CropNorm(left = 0.08f, top = 0.25f, right = 0.92f, bottom = 0.75f)
    }
}

/** A canned "solve" response — recognized question text, worked steps, and the final answer. */
data class PhotoSolveResult(
    val recognizedQuestion: String,
    val steps: List<String>,
    val finalAnswer: String,
)

data class PhotoSolveUiState(
    val step: PhotoSolveStep = PhotoSolveStep.Home,
    val capturedBitmap: Bitmap? = null,
    val croppedBitmap: Bitmap? = null,
    val recognizingStatusIndex: Int = 0,
    val result: PhotoSolveResult? = null,
    val errorMessage: String? = null,
)
