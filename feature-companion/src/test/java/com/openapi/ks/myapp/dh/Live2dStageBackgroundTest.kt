package com.openapi.ks.myapp.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 通话页主题色 → Live2D 舞台 glClearColor 的纯换算。
 *
 * 真机验收 2026-11-22：换主题背景色「没效果，还是大黑色」——GLSurfaceView 每帧
 * 用上游写死的 glClearColor(0,0,0,1) 清屏，Compose 层的渐变永远透不出来。修法是
 * 把主题色推给 fork .so 当 clearColor（nativeSetBackgroundColor）。这条链路里
 * 唯一会算错的地方就是「三段渐变怎么折成一色」和「OLED 的 alpha=0 色值」，钉在这里。
 */
class Live2dStageBackgroundTest {

    @Test
    fun `三段渐变按 1-2-1 加权折成一色（人物占中部，中段权重最高）`() {
        // 纯黑三段 → 全黑；纯白三段 → 全白（各通道独立、不越界）。
        assertEquals(0xFF000000.toInt(), stageClearColorArgb(0, 0, 0))
        assertEquals(0xFFFFFFFF.toInt(), stageClearColorArgb(-1, -1, -1))
    }

    @Test
    fun `深海主题的折算色仍是不透明深蓝`() {
        val argb = stageClearColorArgb(
            0xFF161F3D.toInt(), 0xFF0B1122.toInt(), 0xFF05060C.toInt(),
        )
        val a = argb ushr 24 and 0xFF
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        assertEquals("clearColor 必须不透明（半透明会把 GLSurfaceView 打穿成黑）", 0xFF, a)
        // 加权中值：r=(0x16+2*0x0B+0x05)/4=12, b=(0x3D+2*0x22+0x0C)/4=35；b>r 保持深海冷调。
        assertEquals(12, r)
        assertEquals(35, b)
        assert(b > r)
    }

    @Test
    fun `OLED 主题的 alpha 0 色值被强制折成不透明纯黑`() {
        // oled 主题三段全是 0x000000（alpha=0）。alpha 直接当 clear alpha 用会在
        // GLSurfaceView 上开出透明洞露出窗口底色——颜色照用，alpha 一律钉成 FF。
        assertEquals(0xFF000000.toInt(), stageClearColorArgb(0x000000, 0x000000, 0x000000))
    }

    @Test
    fun `argb 拆成 native 要的 0 到 1 三色`() {
        val (r, g, b) = argbToNormRgb(0xFF161F3D.toInt())
        assertEquals(0x16 / 255f, r, 1e-6f)
        assertEquals(0x1F / 255f, g, 1e-6f)
        assertEquals(0x3D / 255f, b, 1e-6f)
    }

    @Test
    fun `只有颜色真的变了才重推 native`() {
        val same = 0xFF161F3D.toInt()
        val other = 0xFF241A14.toInt()
        assertEquals(same, stageBackgroundPush(same, null)) // 首次必推
        assertNull(stageBackgroundPush(same, same)) // 主题没变不推
        assertEquals(other, stageBackgroundPush(other, same))
        assertNull(stageBackgroundPush(null, same)) // 无主题（聊天页/调试舞台）不推
    }
}
