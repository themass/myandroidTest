package com.openapi.ks.myapp.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 共用包络解析的钉桩测试：教学演示的唇形靠它，通话页试听也靠它（不申请麦克风这条边界的前提）。
 */
class VoiceEnvelopeTest {

    /** 造一个 16-bit PCM 单声道 WAV：前一半满幅方波、后一半静音。 */
    private fun wav(rate: Int, seconds: Int, loudFirstHalf: Boolean = true): ByteArray {
        val frames = rate * seconds
        val dataBytes = frames * 2
        val out = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray())
        out.putInt(36 + dataBytes)
        out.put("WAVE".toByteArray())
        out.put("fmt ".toByteArray())
        out.putInt(16)
        out.putShort(1)          // PCM
        out.putShort(1)          // mono
        out.putInt(rate)
        out.putInt(rate * 2)
        out.putShort(2)
        out.putShort(16)         // 16-bit
        out.put("data".toByteArray())
        out.putInt(dataBytes)
        repeat(frames) { i ->
            val loud = if (loudFirstHalf) i < frames / 2 else i >= frames / 2
            out.putShort(if (loud) 12000 else 0)
        }
        return out.array()
    }

    private fun file(bytes: ByteArray): File =
        File.createTempFile("env-test", ".wav").apply { writeBytes(bytes); deleteOnExit() }

    @Test
    fun `16-bit 单声道按 20ms 分窗响段有电平静段为零`() {
        val env = VoiceEnvelope.read(file(wav(16000, 2)))
        assertNotNull(env)
        env!!
        // 2 秒 = 100 个 20ms 窗
        assertEquals(100, env.levels.size)
        assertEquals(100, env.bands.size)
        val firstHalf = env.levels.take(50)
        val secondHalf = env.levels.takeLast(50)
        org.junit.Assert.assertTrue("响段电平应大于 0.5", firstHalf.average() > 0.5)
        org.junit.Assert.assertTrue("静段电平应为 0", secondHalf.all { it == 0f })
        org.junit.Assert.assertTrue("电平必须在 0..1", env.levels.all { it in 0f..1f })
    }

    @Test
    fun `非 WAV 文件返回 null`() {
        assertNull(VoiceEnvelope.read(file("not a wav at all".toByteArray())))
    }

    @Test
    fun `非 PCM 格式返回 null`() {
        val bytes = wav(16000, 1)
        // fmt 的 format 字段在偏移 20（12 + 8 chunk 头）。改成 3 = IEEE float。
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putShort(20, 3)
        assertNull(VoiceEnvelope.read(file(bytes)))
    }

    @Test
    fun `窗口序号与播放毫秒的换算一致`() {
        // 播放侧用 positionMs / WINDOW_MS 取电平：20ms 必须对上 levels 的索引步长。
        assertEquals(20, VoiceEnvelope.WINDOW_MS)
        val env = VoiceEnvelope.read(file(wav(16000, 2)))
        assertNotNull(env)
        // 第 1000ms 落在响段（前半 1000ms 是响的边界）
        assertEquals(50, 1000 / VoiceEnvelope.WINDOW_MS)
    }
}
