package com.openapi.feature.photosolve.presentation

import com.openapi.imaging.quad.QuadNorm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 「四角微调页转 90°」时边框跟着动的规则。
 *
 * 真机取证（V2279A，2026-11-24）：用户报「拍照解题一直跑不通」的那张照片是**横倒**的
 * （EXIF orientation=0，横着拿手机翻拍屏幕）。横图对 DocQuadNet 和豆包视觉都是分布外输入：
 * 端上四角检测判负，服务端更直接——40002「没有识别到题目」，一次额度还不退。
 * 把同一张照片转正后原样重提交：第二次完整出结果、`photosolve_history/img/1.jpg` 落盘。
 *
 * 所以 QuadAdjust（主流程的微调页）必须有旋转入口——旧版旋转只在旧 Crop 页有，
 * 主流程用户唯一的出路是退出重拍，而重拍出来大概率还是横的。
 * 这一组测试钉住的是「转了之后边框必须还在原来的题目上」：坐标变换错一次，
 * 用户转完就看到框跑到别处，等于旋转按钮不可用。
 */
class RotationFlowTest {

    private fun p(x: Float, y: Float) = QuadNorm.Point(x, y)

    /** 左上角 (0,0) 顺时针转 90° 应落到右上角 (1,0)。 */
    @Test
    fun topLeftMovesToTopRight() {
        val r = RotationFlow.rotatedPoint(p(0f, 0f))
        assertEquals(1f, r.x, 1e-4f)
        assertEquals(0f, r.y, 1e-4f)
    }

    /** 一个贴着原图的四角，转完仍然覆盖整张图、仍然是合法四边形（不能缩掉或翻自交）。 */
    @Test
    fun fullFrameQuadStaysFullFrameAndValid() {
        val quad = QuadNorm(listOf(p(0f, 0f), p(1f, 0f), p(1f, 1f), p(0f, 1f)))

        val rotated = RotationFlow.rotateQuad(quad)

        assertEquals(4, rotated.corners.size)
        // 面积守恒：旋转不改变归一化面积
        assertEquals(quad.area(), rotated.area(), 1e-3f)
        assertFalse(!rotated.isValid)
    }

    /** 转四次回到原位（用户可能连点几下）。 */
    @Test
    fun fourRotationsReturnToStart() {
        val quad = QuadNorm(listOf(p(0.2f, 0.1f), p(0.9f, 0.1f), p(0.9f, 0.8f), p(0.2f, 0.8f)))
        var q = quad
        repeat(4) { q = RotationFlow.rotateQuad(q) }

        val got = q.ordered().corners
        val want = quad.ordered().corners
        for (i in 0 until 4) {
            assertEquals(want[i].x, got[i].x, 1e-3f)
            assertEquals(want[i].y, got[i].y, 1e-3f)
        }
    }

    /**
     * 旋转之后这张图**不算**自动检测到：模型是在旧方向上跑的。
     * 界面要继续显示黄说明，让用户对着转正的图自己拖，而不是假装框是模型认出来的。
     */
    @Test
    fun rotateLeavesTheQuadMarkedAsNotAutoDetected() {
        assertFalse(CaptureFlow.autoDetected(RotationFlow.checkAfterRotate()))
    }
}
