package com.openapi.feature.photosolve.presentation

import android.graphics.Bitmap

/** Which screen of the capture → crop → recognize → result flow is currently shown. */
enum class PhotoSolveStep {
    Home,
    Capture,
    /** 检测到题目边框后的四角微调页。 */
    QuadAdjust,
    /** 手动框选（没有检测器、或用户主动选择时）。 */
    Crop,
    Recognizing,
    Result,
}

/** 一道题的解题结果。[latex] / [requestId] 等是服务端新接口带来的可选信息，旧 mock 不填也能用。 */
data class PhotoSolveResult(
    val recognizedQuestion: String,
    val steps: List<String>,
    val finalAnswer: String,
    /** 一张图里识别出多道题时，除第一题以外的其余题目。 */
    val moreQuestions: List<PhotoSolveResult> = emptyList(),
    /** 最终答案的 LaTeX（服务端有就填，界面暂时按纯文本展示）。 */
    val latex: String? = null,
    /** 服务端请求 id，反馈与排查用。 */
    val requestId: String? = null,
    /** 实际使用的模型标识。 */
    val providerUsed: String? = null,
    /** 这张图里一共识别出几道题（当前界面展示第一道）。 */
    val questionCount: Int = 1,
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
    /** 正在检测题目边框或做透视矫正。 */
    val detecting: Boolean = false,
    /** 当前的四角框（进入 [PhotoSolveStep.QuadAdjust] 后用户可拖动）。 */
    val quad: com.openapi.imaging.quad.QuadNorm? = null,
    /** 端上几何检测的结果，界面据此提示「没找到题目」或「检测器不可用」。 */
    val frameCheck: QuestionFrameCheck? = null,
    /** 服务端错误码（[PhotoSolveError]），界面按它决定是否显示「重试」。 */
    val errorCode: Int? = null,
)

data class QualityWarning(
    val bitmap: Bitmap,
    val issues: List<com.openapi.imaging.quality.QualityIssue>,
)
