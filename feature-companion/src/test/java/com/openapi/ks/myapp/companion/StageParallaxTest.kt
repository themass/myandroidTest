package com.openapi.ks.myapp.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 手机姿态 → 背景视差的纯函数契约。
 *
 * 钉住的是「可感规则」而不是传感器数学本身：
 * - 静止/微抖（≤死区）必须输出 0——光晕跟着手抖是故障感，不是视差；
 * - 满倾（±45°）刚好打满归一化 1.0，越界收敛，不会出现光晕飞到屏幕外；
 * - 平滑因子是低通：新值只占 alpha，静止时输入不变输出不变。
 */
class StageParallaxTest {

    private val dead = PARALLAX_DEAD_ZONE_RAD

    @Test
    fun `死区内归零——手不抖光晕就不动`() {
        assertEquals(0f, parallaxAxis(0f), 1e-6f)
        assertEquals(0f, parallaxAxis(dead * 0.99f), 1e-6f)
        assertEquals(0f, parallaxAxis(-dead * 0.99f), 1e-6f)
    }

    @Test
    fun `满倾 45 度打满 1，越界收敛不超 1`() {
        assertEquals(1f, parallaxAxis(PARALLAX_FULL_TILT_RAD), 1e-4f)
        assertEquals(-1f, parallaxAxis(-PARALLAX_FULL_TILT_RAD), 1e-4f)
        assertEquals(1f, parallaxAxis(PARALLAX_FULL_TILT_RAD * 3f), 1e-6f)
    }

    @Test
    fun `死区外线性：半程倾角给半程位移`() {
        val mid = (dead + PARALLAX_FULL_TILT_RAD) / 2f
        assertEquals(0.5f, parallaxAxis(mid), 1e-4f)
    }

    @Test
    fun `零旋转矢量不产生任何倾角`() {
        val (pitch, roll) = tiltFromRotationVector(0f, 0f, 0f)
        assertEquals(0f, pitch, 1e-6f)
        assertEquals(0f, roll, 1e-6f)
    }

    @Test
    fun `绕屏幕法线旋转（打平面表）不产生 pitch 与 roll`() {
        val s = 0.1f // 绕 z 轴小角的旋转矢量只有 z 分量
        val (pitch, roll) = tiltFromRotationVector(0f, 0f, s)
        assertEquals(0f, pitch, 1e-5f)
        assertEquals(0f, roll, 1e-5f)
    }

    @Test
    fun `平滑是低通：目标不变时输出收敛且不超过目标`() {
        var out = 0f
        repeat(200) { out = smoothParallax(out, 1f, PARALLAX_SMOOTH_ALPHA) }
        assertEquals(1f, out, 1e-3f)
        // 单步只吃掉 alpha 的差距：一步不许跳到目标
        assertEquals(PARALLAX_SMOOTH_ALPHA, smoothParallax(0f, 1f, PARALLAX_SMOOTH_ALPHA), 1e-6f)
    }

    @Test
    fun `减弱动态时视差直接归零（调用侧用它短路）`() {
        assertTrue(parallaxMaxOffsetDp(reducedMotion = true) == 0f)
        assertTrue(parallaxMaxOffsetDp(reducedMotion = false) in 6f..24f)
    }
}
