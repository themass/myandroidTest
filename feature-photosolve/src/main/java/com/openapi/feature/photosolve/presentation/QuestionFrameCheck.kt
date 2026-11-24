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

/**
 * 一张**已经拍下**的图在几何检测之后应该落到哪个界面。
 *
 * 关键规则：判负**不**把用户退回取景页。图已经在手里了，退回取景页等于把它丢掉，
 * 用户只能重拍；而检测判负是常态（见 [PhotoSolveViewModel.onPhotoCaptured] 里的真机取证），
 * 于是界面就死在「画面里没找到题目，请把题目放进取景框后再拍」——题本身是对的也发不出去。
 * 端上只做几何预筛（见本文件顶部的说明），语义判断归服务端，所以这里没有否决用户的资格：
 * 检测不到就交给用户手动对齐。
 *
 * 单独成表是为了让这条规则有地方被单测钉住：它以前直接写在状态机里，
 * 改回「退回取景页」不会有任何测试变红。
 */
object CaptureFlow {

    fun stepAfter(check: QuestionFrameCheck): PhotoSolveStep = when (check) {
        // 检测到真实边框 → 用户只需微调
        is QuestionFrameCheck.Found -> PhotoSolveStep.QuadAdjust
        // 判负 → 默认框 + 手动拖角（QuadAdjust 有四个角和四条边，比旧裁剪页好拖）
        is QuestionFrameCheck.NotFound -> PhotoSolveStep.QuadAdjust
        // 连检测器都没有 → 退化成旧的手动矩形框选
        is QuestionFrameCheck.DetectorUnavailable -> PhotoSolveStep.Crop
    }

    /** 界面上要不要说明「这个框不是自动找到的」，免得用户以为贴边的框是检测出来的。 */
    fun autoDetected(check: QuestionFrameCheck): Boolean = check is QuestionFrameCheck.Found
}

/**
 * 「把照片转 90°」时边框该怎么跟着动（纯函数，可单测；位图本身在 ViewModel 里转）。
 *
 * 真机取证（2026-11-24）：用户那张「题都是对的」的照片是**横倒**的——EXIF orientation=0
 * （没有方向标记），拍摄时手机是横着拿的。横倒的图对 DocQuadNet 和豆包视觉都是分布外输入：
 * 端上四角检测判负（已修：进手动微调），服务端更狠——直接 40002「没有识别到题目」，
 * 一次额度还不退。把同一张照片**转正**后原样重提交：第一次撞 60s 超时（50001，退额度），
 * 第二次完整出结果、`photosolve_history/img/1.jpg` 落盘。所以四角微调页必须能转方向，
 * 否则用户唯一的出路是退出重拍——而且重拍出来大概率还是横的。
 */
object RotationFlow {

    /** 图像顺时针转 90° 后，归一化坐标 (x, y) → (1 - y, x)。 */
    fun rotatedPoint(p: QuadNorm.Point): QuadNorm.Point =
        QuadNorm.Point(1f - p.y, p.x)

    fun rotateQuad(quad: QuadNorm): QuadNorm =
        QuadNorm(quad.corners.map(::rotatedPoint))

    /**
     * 旋转之后这张图**不算**「自动检测到」：模型是在旧方向上跑的，新方向没有检测结果，
     * 界面继续显示黄说明、让用户对着转正的图自己拖。
     */
    fun checkAfterRotate(): QuestionFrameCheck.NotFound =
        QuestionFrameCheck.NotFound("已旋转 90°，未重新检测")
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
