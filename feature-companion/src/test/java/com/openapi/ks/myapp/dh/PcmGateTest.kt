package com.openapi.ks.myapp.dh

import org.junit.Assert.assertEquals
import org.junit.Test

class PcmGateTest {

    /** n 个样本的块，全部填 ±sample。 */
    private fun block(sample: Int, n: Int = 160): ByteArray {
        val out = ByteArray(n * 2)
        for (i in 0 until n) {
            val v = if (i % 2 == 0) sample else -sample
            out[i * 2] = (v and 0xff).toByte()
            out[i * 2 + 1] = (v shr 8).toByte()
        }
        return out
    }

    private val voiced = block(8000) // 峰值 ~0.24，有声
    private val silence = block(0) // 全零
    private val noise = block(200) // 峰值 ~0.006，低于 0.02 门限

    @Test
    fun `silent block is skipped before any speech`() {
        val gate = PcmGate()
        assertEquals(PcmGate.Decision.SKIP, gate.feed(silence, nowMs = 0))
        assertEquals(PcmGate.Decision.SKIP, gate.feed(noise, nowMs = 200))
    }

    @Test
    fun `voiced block pushes`() {
        val gate = PcmGate()
        assertEquals(PcmGate.Decision.PUSH, gate.feed(voiced, nowMs = 0))
    }

    @Test
    fun `short gaps inside speech still push so words do not clip`() {
        val gate = PcmGate()
        assertEquals(PcmGate.Decision.PUSH, gate.feed(voiced, nowMs = 0))
        assertEquals(PcmGate.Decision.PUSH, gate.feed(silence, nowMs = 100))
        assertEquals(PcmGate.Decision.PUSH, gate.feed(silence, nowMs = 300))
    }

    @Test
    fun `end fires once after the hangover then stays skipped`() {
        val gate = PcmGate()
        assertEquals(PcmGate.Decision.PUSH, gate.feed(voiced, nowMs = 0))
        // 距最后一次有声 300ms < 400ms 余量 → 还推（尾音自然收口）。
        assertEquals(PcmGate.Decision.PUSH, gate.feed(silence, nowMs = 300))
        // 跨过 400ms → 报一次 END。
        assertEquals(PcmGate.Decision.END, gate.feed(silence, nowMs = 400))
        // END 只报一次，之后维持待机不再刷 END。
        assertEquals(PcmGate.Decision.SKIP, gate.feed(silence, nowMs = 600))
    }

    @Test
    fun `a new voiced block after end reopens the gate`() {
        val gate = PcmGate()
        assertEquals(PcmGate.Decision.PUSH, gate.feed(voiced, nowMs = 0))
        assertEquals(PcmGate.Decision.END, gate.feed(silence, nowMs = PcmGate.HANGOVER_MS))
        assertEquals(PcmGate.Decision.PUSH, gate.feed(voiced, nowMs = PcmGate.HANGOVER_MS + 500))
    }

    @Test
    fun `peak level normalises int16 to zero one range`() {
        assertEquals(0f, PcmGate.peakLevel(block(0)), 1e-6f)
        assertEquals(1f, PcmGate.peakLevel(block(-32768)), 1e-6f)
        assertEquals(0.5f, PcmGate.peakLevel(block(16384)), 1e-6f)
    }
}
