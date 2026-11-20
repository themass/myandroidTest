package com.openapi.ks.myapp.companion.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * §10.1 本地打断闸：上升沿 + 冷却 + 必须有活跃 speech。
 */
class BargeInGateTest {

    private val gate = BargeInGate(cooldownMs = 400)

    @Test
    fun `rising edge with active speech fires once`() {
        assertNull(gate.onSelfLevel(0.001f, 0.003f, "sp_1", now = 1_000))
        assertEquals("sp_1", gate.onSelfLevel(0.02f, 0.003f, "sp_1", now = 1_010))
        // 继续说：不是上升沿
        assertNull(gate.onSelfLevel(0.03f, 0.003f, "sp_1", now = 1_020))
    }

    @Test
    fun `no active speech means no barge`() {
        assertNull(gate.onSelfLevel(0.02f, 0.003f, activeSpeechId = null, now = 1_000))
    }

    @Test
    fun `cooldown blocks a second rising edge`() {
        assertEquals("sp_1", gate.onSelfLevel(0.02f, 0.003f, "sp_1", now = 1_000))
        // 先静音再开口：上升沿，但还在冷却里
        gate.onSelfLevel(0.0f, 0.003f, "sp_1", now = 1_100)
        assertNull(gate.onSelfLevel(0.02f, 0.003f, "sp_1", now = 1_200))
        // 冷却过了
        gate.onSelfLevel(0.0f, 0.003f, "sp_1", now = 1_500)
        assertEquals("sp_1", gate.onSelfLevel(0.02f, 0.003f, "sp_1", now = 1_500))
    }

    @Test
    fun `reset clears cooldown and speaking latch`() {
        assertEquals("sp_1", gate.onSelfLevel(0.02f, 0.003f, "sp_1", now = 1_000))
        gate.reset()
        assertEquals("sp_1", gate.onSelfLevel(0.02f, 0.003f, "sp_1", now = 1_050))
    }
}
