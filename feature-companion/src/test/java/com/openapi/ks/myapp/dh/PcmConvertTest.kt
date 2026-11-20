package com.openapi.ks.myapp.dh

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmConvertTest {

    private fun pcm(vararg v: Int): ByteBuffer {
        val b = ByteBuffer.allocateDirect(v.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        v.forEach { b.putShort(it.toShort()) }
        b.flip()
        return b
    }

    private fun ByteArray.samples(): List<Int> {
        val b = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        return List(size / 2) { b.short.toInt() }
    }

    @Test
    fun `48k mono is averaged down to 16k by 3`() {
        val out = PcmConvert.toMono16k(pcm(300, 600, 900, -300, -600, -900), 16, 48000, 1, 6)
        assertEquals(listOf(600, -600), out!!.samples())
    }

    @Test
    fun `stereo is mixed to mono`() {
        // 16k stereo: (100,300) (-200,-400) -> 200, -300
        val out = PcmConvert.toMono16k(pcm(100, 300, -200, -400), 16, 16000, 2, 2)
        assertEquals(listOf(200, -300), out!!.samples())
    }

    @Test
    fun `non 16 bit input is rejected`() {
        assertEquals(null, PcmConvert.toMono16k(pcm(1, 2), 8, 16000, 1, 2))
    }

    @Test
    fun `output length follows the sample-rate ratio`() {
        val out = PcmConvert.toMono16k(pcm(*IntArray(480) { 1000 }), 16, 48000, 1, 480)
        assertEquals(160, out!!.size / 2)
        assertTrue(out.samples().all { it == 1000 })
    }
}
