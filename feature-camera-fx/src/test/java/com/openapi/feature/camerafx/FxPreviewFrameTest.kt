package com.openapi.feature.camerafx

import org.junit.Assert.assertEquals
import org.junit.Test

/** 预览直绘路径（真机 P0-A）的缩放决策：纯函数钉死，不碰 Bitmap。 */
class FxPreviewFrameTest {

    @Test
    fun `long edge is capped at max dim`() {
        val (w, h) = previewDownscaleSize(720, 960, maxDim = 360)
        assertEquals(270, w)
        assertEquals(360, h)
    }

    @Test
    fun `aspect ratio preserved within rounding`() {
        // 640x480：长边是宽 → 300 宽压满，高按比例 225。
        val (w, h) = previewDownscaleSize(640, 480, maxDim = 300)
        assertEquals(300, w)
        assertEquals(4f / 3f, w.toFloat() / h.toFloat(), 0.02f)
    }

    @Test
    fun `small frames are not upscaled`() {
        assertEquals(160 to 120, previewDownscaleSize(160, 120, maxDim = 360))
    }

    @Test
    fun `landscape input caps the width`() {
        val (w, h) = previewDownscaleSize(960, 720, maxDim = 360)
        assertEquals(360, w)
        assertEquals(270, h)
    }

    @Test
    fun `degenerate input returns zero size - caller skips publishing`() {
        assertEquals(0 to 0, previewDownscaleSize(0, 100))
        assertEquals(0 to 0, previewDownscaleSize(100, -1))
        assertEquals(0 to 0, previewDownscaleSize(100, 100, maxDim = 0))
    }

    @Test
    fun `never produces a zero side after rounding`() {
        // 极端细长：960x1 压到 360 时短边按整型会 0，必须钉到 1。
        val (w, h) = previewDownscaleSize(960, 1, maxDim = 360)
        assertEquals(360, w)
        assertEquals(1, h)
    }
}
