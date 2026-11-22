package com.openapi.ks.myapp.dh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 口型轨迹日志的节流门（真机验收的证据通道）：20ms 一帧的嘴型电平若全量打日志，
 * 一句话就是几百行；这里要求 10Hz 采样 + 「说完收口」补一条 0，其余静音不刷。
 */
class Live2dMouthTraceTest {

    private fun gate(nowMs: Long, lastMs: Long, openness: Float, wasOpen: Boolean) =
        Live2dMouthTrace.shouldLog(nowMs, lastMs, openness, wasOpen)

    @Test
    fun `说话中的电平以最多 10Hz 记录`() {
        // 模拟 20ms 节拍的喂数：500ms 内最多 5 条（含第一条）。
        var last = -1000L
        var logged = 0
        for (i in 0 until 25) {
            val t = i * 20L
            if (gate(t, last, 0.5f, true)) { last = t; logged++ }
        }
        assertTrue("至少要有样本轨迹（$logged）", logged >= 4)
        assertTrue("必须节流到 ≤10Hz（$logged）", logged <= 6)
    }

    @Test
    fun `静音电平不刷日志，但从有声到静音补一条收口`() {
        assertFalse(gate(1000, 950, 0.0f, false))     // 一直闭着：不打
        assertTrue(gate(1000, 950, 0.0f, true))       // 刚说完：收口补一条
        assertFalse(gate(1001, 1000, 0.01f, false))   // 低于地板：不打
    }

    @Test
    fun `说话首帧立刻记录，不等节流窗`() {
        assertTrue(gate(0, -1000, 0.3f, false))
    }
}
