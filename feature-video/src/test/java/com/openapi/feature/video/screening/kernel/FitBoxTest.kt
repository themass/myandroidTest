package com.openapi.feature.video.screening.kernel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 画面槽尺寸换算（[fitBoxPx]）钉住「fit 绝不拉满容器」。
 * 真机回归：16:9 的片子放进 16:10 的播放区被拉扁（用户点名「有拉伸」），
 * 根因就是 fit 模式下画面槽一直是 MATCH_PARENT。
 */
class FitBoxTest {

    @Test
    fun `fit 宽屏视频在 16 比 10 容器里按宽铺满、高度留黑边`() {
        val (w, h) = fitBoxPx(1080f, 675f, 16f / 9f, fill = false)
        assertEquals(1080, w)
        // 1080 ÷ 1.777… = 607.5，取整 607；关键是必须小于容器高 675
        assertEquals(607, h)
    }

    @Test
    fun `fit 竖视频按高铺满、左右留边`() {
        val (w, h) = fitBoxPx(1080f, 675f, 9f / 16f, fill = false)
        assertEquals(379, w) // 675 × 0.5625 = 379.68 → 379
        assertEquals(675, h)
    }

    @Test
    fun `fill 放大裁边：宽屏视频进方容器，高边超出容器`() {
        val (w, h) = fitBoxPx(600f, 600f, 16f / 9f, fill = true)
        // 比例 1.78 > 容器 1.0 → 放大 1.78 倍再居中裁掉上下
        assertEquals(1066, w) // 600 × 1.777… = 1066.6 → 1066
        assertEquals(600, h)
    }

    @Test
    fun `比例未知先铺满，起播那一瞬不黑`() {
        assertEquals(1080 to 675, fitBoxPx(1080f, 675f, 0f, fill = false))
        // 容器尺寸还没定（measure 前）也不能给出 0 这种把 TextureView 弄没的值
        assertEquals(1 to 1, fitBoxPx(0f, 0f, 1.7f, fill = false))
    }
}
