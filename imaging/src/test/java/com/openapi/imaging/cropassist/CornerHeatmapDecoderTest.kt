package com.openapi.imaging.cropassist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * [CornerHeatmapDecoder] 的纯数值单测。
 *
 * 钉死两个曾经的行为缺陷：
 * 1. 四个角点被压成轴对齐外接矩形（斜拍题目带进大量背景、斜边文字被截）；
 * 2. 峰值不设置信度门槛，任何噪声峰都被当成角点。
 */
class CornerHeatmapDecoderTest {

    private val hm = 64
    private val canvas = 256

    /** 原图 1280x960 → letterbox 到 256 画布：scale=0.2，上下各留 32px 黑边。 */
    private val srcW = 1280
    private val srcH = 960
    private val scale = 0.2f
    private val offsetX = 0
    private val offsetY = 32

    /** 全负背景 + 每通道一个峰（logit 空间；模型输出是原始 logits，没有 sigmoid）。 */
    private fun heatmaps(peaks: List<Pair<Int, Int>>, peakLogit: Float = 10f, background: Float = -10f): FloatArray {
        val out = FloatArray(4 * hm * hm) { background }
        peaks.forEachIndexed { channel, (hx, hy) ->
            out[channel * hm * hm + hy * hm + hx] = peakLogit
        }
        return out
    }

    private fun decode(peaks: List<Pair<Int, Int>>, peakLogit: Float = 10f) = CornerHeatmapDecoder.decode(
        heatmaps = heatmaps(peaks, peakLogit),
        heatmapSize = hm,
        canvasSize = canvas,
        offsetX = offsetX,
        offsetY = offsetY,
        scale = scale,
        srcW = srcW,
        srcH = srcH,
    )

    /** 热力图格心 → 期望的归一化坐标（和 decoder 的正变换严格互逆）。 */
    private fun expected(hx: Int, hy: Int): Pair<Float, Float> {
        val origX = ((hx + 0.5f) / hm * canvas - offsetX) / scale
        val origY = ((hy + 0.5f) / hm * canvas - offsetY) / scale
        return origX / srcW to origY / srcH
    }

    // 故意选成一个**斜的四边形**：TL、TR 的 y 差了 ~0.12，外接矩形会把它拍平。
    private val tl = 13 to 24
    private val tr = 50 to 18
    private val br = 52 to 44
    private val bl = 11 to 50

    @Test
    fun keepsFourCornersInsteadOfCollapsingToBoundingBox() {
        val quad = decode(listOf(tl, tr, br, bl)) ?: return fail("quad should be detected")
        val o = quad.ordered()
        val expectedCorners = listOf(tl, tr, br, bl).map { expected(it.first, it.second) }
        for (i in 0 until 4) {
            assertEquals(expectedCorners[i].first, o.corners[i].x, 0.001f)
            assertEquals(expectedCorners[i].second, o.corners[i].y, 0.001f)
        }
    }

    @Test
    fun tiltedPaperIsNotARectangle() {
        val quad = decode(listOf(tl, tr, br, bl)) ?: return fail("quad should be detected")
        val o = quad.ordered()
        // 旧实现输出外接矩形，TL 和 TR 的 y 永远相同；这里必须能看出倾斜。
        assertNotEquals(o.corners[0].y, o.corners[1].y)
        assertTrue("y gap expected ~0.12", kotlin.math.abs(o.corners[0].y - o.corners[1].y) > 0.05f)
    }

    @Test
    fun confidentCornersPassTheThreshold() {
        // 对照样本：四个角都是高置信度峰，必须能检出（防止下一条把门槛调得谁都过不了）。
        assertNotNull(decode(listOf(tl, tr, br, bl)))
    }

    @Test
    fun lowConfidenceCornerRejectsDetection() {
        // 把 ch3 的真峰抹掉，换成一个 logit=-0.2 的弱峰（sigmoid≈0.45，低于门槛）：
        // 模型自己都不认为那是角点。弱峰的位置让去掉门槛后其余三点仍能组成**合法**
        // 四边形——这样本测试失败的唯一原因就是门槛本身（上一版弱峰位置会让几何先拒掉，
        // 门槛去掉也不红，是假绿）。旧实现没有任何门槛，照样编出一个框。
        val weak = heatmaps(listOf(tl, tr, br, bl), peakLogit = 10f)
        weak[3 * hm * hm + bl.second * hm + bl.first] = -10f
        weak[3 * hm * hm + 52 * hm + 5] = -0.2f
        val quad = CornerHeatmapDecoder.decode(
            heatmaps = weak,
            heatmapSize = hm,
            canvasSize = canvas,
            offsetX = offsetX,
            offsetY = offsetY,
            scale = scale,
            srcW = srcW,
            srcH = srcH,
        )
        assertNull("低于门槛的角点必须让整次检测判负", quad)
    }

    @Test
    fun degenerateCornersAreRejected() {
        // 四个通道峰落在同一格：四点重合、面积 0，是噪声不是纸面。
        assertNull(decode(List(4) { 32 to 32 }))
    }

    @Test
    fun truncatedOutputBufferIsRejected() {
        val short = FloatArray(4 * hm * hm - 1) { -10f }
        assertNull(
            CornerHeatmapDecoder.decode(short, hm, canvas, offsetX, offsetY, scale, srcW, srcH),
        )
    }
}
