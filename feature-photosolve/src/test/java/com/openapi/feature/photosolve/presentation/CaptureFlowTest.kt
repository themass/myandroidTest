package com.openapi.feature.photosolve.presentation

import com.openapi.imaging.quad.QuadNorm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「一张**已经拍下**的图，检测之后该落到哪一页」这条规则的回归。
 *
 * 用户报告（2026-11-23）：「拍照解题一直不对，提示边框之类的，就是不拍照，题都是对的」。
 * 现场取证：设备上那四轮拍照（22:40~22:42 的 `paizhao_capture_*.jpg`）拍的是**屏幕里的题目**
 * （翻拍屏幕）。把同一批图喂给随包的 DocQuadNet（`docquadnet256_trained_opset17.ort`）重跑，
 * 四个角点过 `MIN_CORNER_CONFIDENCE = 0.5` 的只有 0~2 个（sigmoid 0.03~0.39）——每次都判负。
 * 旧代码据此把用户**退回取景页**：图被丢掉、只能重拍，界面死在「画面里没找到题目」。
 * 解题历史目录在 22:40 建好，`img/` 里一张没有 → 确实一次都没提交过。
 *
 * 端上这道闸门只做几何预筛，「是不是题目」由服务端判（40002），所以它没有否决用户的资格：
 * 判负也必须把这张图接下去（手动对齐），而不是要求重拍。
 *
 * 之所以抽 [CaptureFlow] 出来测：这条决定以前直接写在 `PhotoSolveViewModel` 的协程里，
 * 而 `:feature-photosolve` 没有 Robolectric（`AndroidViewModel` + `viewModelScope` 在 JVM 上起不来），
 * 于是把它改回「退回取景页」**不会有任何测试变红**——这正是它能长期存在的原因。
 */
class CaptureFlowTest {

    private val quad = QuadNorm(
        listOf(
            QuadNorm.Point(0.1f, 0.1f),
            QuadNorm.Point(0.9f, 0.1f),
            QuadNorm.Point(0.9f, 0.9f),
            QuadNorm.Point(0.1f, 0.9f),
        ),
    )

    /** 检测到的边框：进四角微调，用户只需微调。 */
    @Test
    fun foundGoesToQuadAdjust() {
        assertEquals(
            PhotoSolveStep.QuadAdjust,
            CaptureFlow.stepAfter(QuestionFrameCheck.Found(quad, "DocQuadNet", 120)),
        )
    }

    /**
     * 检测判负：同样进四角微调（用默认框手动拖），**不能**是 Capture。
     * 这一条就是用户报的那个 bug 的钉子：`Capture` 意味着这张图被丢掉。
     */
    @Test
    fun notFoundStillAdvancesWithThePhotoItAlreadyHas() {
        val step = CaptureFlow.stepAfter(QuestionFrameCheck.NotFound("未检测到题目边缘"))

        assertEquals(PhotoSolveStep.QuadAdjust, step)
        // 明写一次：退回取景页就是回归本身
        assertFalse(step == PhotoSolveStep.Capture)
    }

    /** 连检测器都没有（模型没打进包）：退化成旧的手动矩形框选，同样不退回取景页。 */
    @Test
    fun detectorUnavailableFallsBackToManualCrop() {
        assertEquals(
            PhotoSolveStep.Crop,
            CaptureFlow.stepAfter(QuestionFrameCheck.DetectorUnavailable("检测模型未安装")),
        )
    }

    /** 只有真的检测到边框才算「自动对齐成功」——界面不能把默认框画成检测出来的样子。 */
    @Test
    fun onlyFoundCountsAsAutoDetected() {
        assertTrue(CaptureFlow.autoDetected(QuestionFrameCheck.Found(quad, "DocQuadNet", 120)))
        assertFalse(CaptureFlow.autoDetected(QuestionFrameCheck.NotFound("未检测到题目边缘")))
        assertFalse(CaptureFlow.autoDetected(QuestionFrameCheck.DetectorUnavailable("检测模型未安装")))
    }
}
