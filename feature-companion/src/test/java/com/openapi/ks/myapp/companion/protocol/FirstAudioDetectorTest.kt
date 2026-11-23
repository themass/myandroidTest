package com.openapi.ks.myapp.companion.protocol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstAudioDetectorTest {
    // 生产实测：引导静音 rms≈0.0025，真声 rms 到 0.1 以上；阈值卡在两者之间。
    private val threshold = 0.02

    @Test
    fun `guide silence frames do not fire`() {
        val d = FirstAudioDetector(threshold)
        assertFalse(d.onFrame(0.0))
        assertFalse(d.onFrame(0.0025))
        assertFalse(d.onFrame(0.02))
    }

    @Test
    fun `first energetic frame fires exactly once`() {
        val d = FirstAudioDetector(threshold)
        assertFalse(d.onFrame(0.0025))
        assertTrue(d.onFrame(0.12))
        assertFalse(d.onFrame(0.3))
    }

    @Test
    fun `reset reopens the gate for the next attempt`() {
        val d = FirstAudioDetector(threshold)
        assertTrue(d.onFrame(0.5))
        d.reset()
        assertTrue(d.onFrame(0.5))
    }
}
