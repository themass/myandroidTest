package com.openapi.feature.photosolve.presentation

import android.graphics.Bitmap
import com.openapi.imaging.quad.QuadNorm

/**
 * 「这张照片里有没有题目」的判断结果。
 *
 * 端上无法可靠地判断「是不是题目」（那需要语义理解），所以这里只做**几何层面**的判断：
 * 有没有检测到一个像纸面/屏幕的四边形、以及检测置信度如何。真正的语义判断交给服务端
 * （拿模型返回的 `isQuestion=false` / 错误码 40002）。这样：
 * - 明显没对准（没检测到四边形、画面全是桌面背景）时，本地立刻提示，省一次上传和等待；
 * - 检测到四边形但内容不是题目的，服务端返回 40002，界面同样提示「没找到题目」。
 */
sealed interface QuestionFrameCheck {

    /** 画面里找到了题目区域，可以用 [quad] 自动对齐。 */
    data class Found(val quad: QuadNorm, val detector: String, val elapsedMs: Long) : QuestionFrameCheck

    /** 没检测到有效四边形：提示用户把题目放进画面。 */
    data class NotFound(val reason: String) : QuestionFrameCheck

    /** 检测器不可用（模型没打进包里、OpenCV 没加载），只能手动框选。 */
    data class DetectorUnavailable(val reason: String) : QuestionFrameCheck
}

/** 拍照页的状态：取景 → 检测 → （找到就）进入角点微调。 */
data class CaptureState(
    val detecting: Boolean = false,
    val check: QuestionFrameCheck? = null,
)

/** 角点微调页的状态。 */
data class QuadAdjustState(
    val source: Bitmap,
    val quad: QuadNorm,
    /** 最近一次拖动的是哪个角（0..3，按 [QuadNorm.ordered] 的顺序）。null 表示没在拖。 */
    val activeCorner: Int? = null,
    /** 用户点了「自动识别」重新检测。 */
    val detecting: Boolean = false,
)
