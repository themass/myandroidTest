package com.openapi.feature.photosolve.presentation

import android.graphics.Bitmap

/** Which screen of the capture → crop → recognize → result flow is currently shown. */
enum class PhotoSolveStep {
    Home,
    Capture,
    Crop,
    Recognizing,
    Result,
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
    /** 裁剪后清晰度或曝光不合格，等待用户选择重拍或仍然识别。 */
    val qualityWarning: QualityWarning? = null,
)

data class QualityWarning(
    val bitmap: Bitmap,
    val issues: List<com.openapi.imaging.quality.QualityIssue>,
)
