package com.openapi.feature.photosolve.presentation

import com.openapi.imaging.CropNorm
import com.openapi.imaging.cropassist.CropAssistProvider
import com.openapi.imaging.cropassist.CropAssistResult
import com.openapi.imaging.quad.QuadNorm
import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** [QuestionQuadDetector.quadFrom]：provider 建议 → 四边形的取舍规则（纯逻辑，不碰模型）。 */
class QuestionQuadDetectorTest {

    private class StubProvider : CropAssistProvider {
        override val id = "stub"
        override val displayName = "stub"
        override suspend fun isAvailable() = true
        override suspend fun suggest(bitmap: Bitmap): CropAssistResult =
            throw UnsupportedOperationException("quadFrom 不应调用模型")
    }

    private val detector = QuestionQuadDetector(StubProvider())

    private fun suggestion(norm: CropNorm, quad: QuadNorm?) =
        CropAssistResult.SuggestedBox(norm = norm, confidenceLabel = null, elapsedMs = 0, quad = quad)

    private val rect = CropNorm(left = 0.1f, top = 0.1f, right = 0.6f, bottom = 0.6f)

    private val tiltedQuad = QuadNorm(
        listOf(
            QuadNorm.Point(0.10f, 0.30f),
            QuadNorm.Point(0.80f, 0.10f),
            QuadNorm.Point(0.85f, 0.70f),
            QuadNorm.Point(0.15f, 0.85f),
        ),
    )

    @Test
    fun prefersRealQuadOverBoundingBox() {
        val quad = detector.quadFrom(suggestion(norm = tiltedQuad.bounds(), quad = tiltedQuad))
        assertNotNull(quad)
        // 关键点：返回的是倾斜四边形本身，不是它的外接矩形（外接矩形的四个角是直角）。
        assertEquals(tiltedQuad.ordered().corners, quad!!.ordered().corners)
    }

    @Test
    fun fallsBackToRectWhenProviderHasNoQuad() {
        val quad = detector.quadFrom(suggestion(norm = rect, quad = null))
        assertNotNull(quad)
        assertEquals(rect.left, quad!!.bounds().left, 0.001f)
        assertEquals(rect.bottom, quad.bounds().bottom, 0.001f)
    }

    @Test
    fun rejectsQuadCoveringAlmostWholeFrame() {
        // 几乎整张图 = 检测没找到纸面，不能当成「纸面就是整张图」。
        // 注意 QuadNorm.fullFrame() 的面积只有 ~0.92，低于 MAX_AREA，不能用在这里。
        val whole = QuadNorm(
            listOf(
                QuadNorm.Point(0f, 0f),
                QuadNorm.Point(1f, 0f),
                QuadNorm.Point(1f, 1f),
                QuadNorm.Point(0f, 1f),
            ),
        )
        assertNull(detector.quadFrom(suggestion(norm = whole.bounds(), quad = whole)))
    }

    @Test
    fun tiltedQuadSurvivesWhereOldRectanglePathWasUsed() {
        // 旧路径（矩形建议）里 TL/TR 的 y 必然相同；真实四点必须保留倾斜。
        val quad = detector.quadFrom(suggestion(norm = tiltedQuad.bounds(), quad = tiltedQuad))!!
        val o = quad.ordered()
        org.junit.Assert.assertNotEquals(o.corners[0].y, o.corners[1].y)
    }
}
