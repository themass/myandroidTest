package com.openapi.imaging.cropassist

import com.openapi.imaging.quad.QuadNorm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * [MaskQuadDecoder] 的纯数值单测。
 *
 * 背景（真纸样本离线取证，见 docs/qa/s2-ps4-ready-to-run-20261124.md §3）：
 * 「四角全过 0.5」对随手拍纸面过严——两张真纸只有 2/4 与 3/4 角过线，
 * 且 3/4 那张崩溃的角连几何都退化（非凸），放宽成 3/4 接受也出不了好框。
 * 但模型第二个输出 `mask_logits`（纸面轮廓掩码）在同一批样本上一直是对的。
 * 本解码器用掩码极值点补出四边形，作为四角路径判负后的兜底。
 */
class MaskQuadDecoderTest {

    private val hm = 64
    private val canvas = 256

    /** 与 CornerHeatmapDecoderTest 同一组 letterbox 参数：1280x960 → 256 画布、scale=0.2。 */
    private val srcW = 1280
    private val srcH = 960
    private val scale = 0.2f
    private val offsetX = 0
    private val offsetY = 32

    /** 全背景 + [x0..x1]×[y0..y1] 矩形前景（logit 空间，>=0 即 sigmoid>=0.5）。 */
    private fun rectMask(x0: Int, y0: Int, x1: Int, y1: Int): FloatArray {
        val out = FloatArray(hm * hm) { -10f }
        for (y in y0..y1) for (x in x0..x1) out[y * hm + x] = 10f
        return out
    }

    private fun decode(mask: FloatArray) = MaskQuadDecoder.decode(
        maskLogits = mask,
        heatmapSize = hm,
        canvasSize = canvas,
        offsetX = offsetX,
        offsetY = offsetY,
        scale = scale,
        srcW = srcW,
        srcH = srcH,
    )

    /** 格心 → 期望归一化坐标（与解码器正变换严格互逆）。 */
    private fun expected(hx: Int, hy: Int): Pair<Float, Float> {
        val origX = ((hx + 0.5f) / hm * canvas - offsetX) / scale
        val origY = ((hy + 0.5f) / hm * canvas - offsetY) / scale
        return origX / srcW to origY / srcH
    }

    @Test
    fun solidMaskCornersComeFromExtremes() {
        val quad = decode(rectMask(16, 16, 47, 47)) ?: return fail("solid mask should yield a quad")
        val o = quad.ordered()
        val expectedCorners = listOf(
            expected(16, 16), expected(47, 16), expected(47, 47), expected(16, 47),
        )
        for (i in 0 until 4) {
            assertEquals(expectedCorners[i].first, o.corners[i].x, 0.001f)
            assertEquals(expectedCorners[i].second, o.corners[i].y, 0.001f)
        }
    }

    @Test
    fun tiltedMaskKeepsItsShape() {
        // 斜放的纸面：沿 x 方向让 y 递增（上边缘不水平）。极值点必须保出倾斜
        // 的四边形，不是外接矩形——外接矩形的 TL/TR 的 y 永远相同。
        val mask = FloatArray(hm * hm) { -10f }
        for (x in 10..50) {
            val y0 = 10 + (x * 0.3f).toInt()
            for (y in y0 until y0 + 30) mask[y * hm + x] = 10f
        }
        val quad = decode(mask) ?: return fail("tilted mask should yield a quad")
        val o = quad.ordered()
        assert(kotlin.math.abs(o.corners[0].y - o.corners[1].y) > 0.05f)
    }

    @Test
    fun emptyMaskRejected() {
        assertNull(decode(FloatArray(hm * hm) { -10f }))
    }

    @Test
    fun singleCellMaskRejected() {
        // 单个前景格：四点重合、面积 0，几何退化必须挡掉。
        val mask = FloatArray(hm * hm) { -10f }
        mask[32 * hm + 32] = 10f
        assertNull(decode(mask))
    }

    @Test
    fun tinyMaskRejectedByMinArea() {
        // 6x6 格的碎块（真机对应 probe_5：屏幕只占画面一角、面积 0.054 < MIN_AREA 0.08）：
        // 这不是「题目纸面」，判负回默认框，不能自动贴合到一小块噪声上。
        assertNull(decode(rectMask(28, 28, 33, 33)))
    }

    @Test
    fun truncatedBufferRejected() {
        assertNull(
            MaskQuadDecoder.decode(FloatArray(hm * hm - 1) { -10f }, hm, canvas, offsetX, offsetY, scale, srcW, srcH),
        )
    }

    @Test
    fun resultIsValidQuadWithinBounds() {
        val quad = decode(rectMask(8, 12, 60, 52)) ?: return fail("mask should yield a quad")
        assert(quad.isValid)
        assert(quad.area() in QuadNorm.MIN_AREA..QuadNorm.MAX_AREA)
        quad.corners.forEach {
            assert(it.x in 0f..1f && it.y in 0f..1f)
        }
    }
}
