package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 测试语音 rig 的纯逻辑：WAV(16k mono s16le) 解析、帧幅度、按 20ms 节拍推给 bridge。
 * 通话页 lab 里 adb broadcast 触发后，走的就是这条链路：
 * bridge.onRemotePcm(PCM) + bridge.onMouthLevel(幅度) + 可选 Gesture。
 */
class DhTestAudioRigTest {

    // ---- WAV 解析 ----

    private fun wav(
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        format: Int = 1, // 1 = PCM s16le
        data: ByteArray = ByteArray(0),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        fun s32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
        fun s16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
        out.write("RIFF".toByteArray()); s32(36 + data.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); s32(16); s16(format); s16(channels)
        s32(sampleRate); s32(sampleRate * channels * bitsPerSample / 8); s16(channels * bitsPerSample / 8); s16(bitsPerSample)
        out.write("data".toByteArray()); s32(data.size); out.write(data)
        return out.toByteArray()
    }

    @Test
    fun parses_a_16k_mono_s16le_wav_and_returns_exactly_the_data_block() {
        val pcm = ByteArray(640) { (it % 251).toByte() }
        val parsed = DhTestAudio.parseWavPcm16Mono16k(wav(16000, 1, 16, data = pcm))
        assertNotNull(parsed)
        assertEquals(640, parsed!!.size)
        assertTrue(pcm.contentEquals(parsed))
    }

    @Test
    fun rejects_44k_wav_because_engines_want_16k() {
        assertNull(DhTestAudio.parseWavPcm16Mono16k(wav(44100, 1, 16, data = ByteArray(128))))
    }

    @Test
    fun rejects_stereo_wav() {
        assertNull(DhTestAudio.parseWavPcm16Mono16k(wav(16000, 2, 16, data = ByteArray(128))))
    }

    @Test
    fun rejects_non_pcm_format() {
        assertNull(DhTestAudio.parseWavPcm16Mono16k(wav(16000, 1, 16, format = 3, data = ByteArray(128))))
    }

    @Test
    fun rejects_garbage_that_is_not_a_wav() {
        assertNull(DhTestAudio.parseWavPcm16Mono16k("not a wav at all".toByteArray()))
    }

    // ---- 幅度 ----

    @Test
    fun level_of_silence_is_zero() {
        assertEquals(0f, DhTestAudio.level(ByteArray(640)), 1e-6f)
    }

    @Test
    fun level_of_full_scale_is_maxed_after_gain() {
        val full = ByteArray(640)
        for (i in 0 until 320) { // 满幅方波
            full[i * 2] = 0; full[i * 2 + 1] = 0x7f.toByte()
        }
        assertEquals(1f, DhTestAudio.level(full), 1e-3f)
    }

    @Test
    fun level_of_quiet_signal_is_small_but_positive() {
        val quiet = ByteArray(640)
        for (i in 0 until 320) { // ~±800 幅度
            quiet[i * 2] = 0x20; quiet[i * 2 + 1] = 0x03.toByte()
        }
        val l = DhTestAudio.level(quiet)
        assertTrue("level=$l", l > 0f && l < 0.3f)
    }

    // ---- 播放节拍 ----

    /** 只记录 PCM/命令的假引擎。 */
    private class RecorderEngine(override val pcmDriven: Boolean = false) : DigitalHumanEngine {
        override val id = DhEngineId.SIMPLE
        override val capabilities = setOf<com.openapi.ks.myapp.avatar.AvatarCapability>()
        override fun availability(context: android.content.Context) =
            DhEngineAvailability(id, true)
        override fun bind(spec: com.openapi.ks.myapp.avatar.AvatarSpec) {}
        override fun onTouch(event: com.openapi.ks.myapp.avatar.AvatarTouch) =
            com.openapi.ks.myapp.avatar.TouchResult.Ignored
        override fun release() {}
        val pcm = mutableListOf<ByteArray>()
        val commands = mutableListOf<AvatarCommand>()
        override fun pushRemotePcm(pcm16leMono16k: ByteArray) { pcm += pcm16leMono16k.copyOf() }
        override fun dispatch(command: AvatarCommand) { commands += command }
    }

    private fun pcmBytes(ms: Int): ByteArray = ByteArray(ms * 32) // 16kHz*2B = 32B/ms

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun play_feeds_whole_pcm_in_batches_and_ends_with_total_byte_count() = runTest {
        val engine = RecorderEngine(pcmDriven = true)
        val bridge = DigitalHumanBridge(engine)
        val data = pcmBytes(100) // 100ms → 5×20ms 批
        val f = File.createTempFile("rig", ".wav")
        f.writeBytes(wav(16000, 1, 16, data = data))
        try {
            val n = DhTestAudio.play(f, bridge)
            assertEquals(data.size, n)
            assertEquals(5, engine.pcm.size)
            assertEquals(640, engine.pcm[0].size)
            assertEquals(data.size, engine.pcm.sumOf { it.size })
            // PCM 驱动引擎只收 PCM：再发 Mouth 会和真 PCM 抢嘴型（Duix dispatch 会合成假 PCM）。
            assertTrue(engine.commands.isEmpty())
        } finally { f.delete() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun mouth_driven_engine_gets_levels_not_pcm_and_silence_closes_mouth() = runTest {
        val engine = RecorderEngine(pcmDriven = false)
        val bridge = DigitalHumanBridge(engine)
        val loud = ByteArray(640)
        for (i in 0 until 320) { loud[i * 2] = 0; loud[i * 2 + 1] = 0x7f.toByte() } // 满幅
        val data = loud + ByteArray(640) // 20ms 响 + 20ms 静音
        val f = File.createTempFile("rig-m", ".wav")
        f.writeBytes(wav(16000, 1, 16, data = data))
        try {
            DhTestAudio.play(f, bridge)
            assertTrue(engine.pcm.isEmpty())
            assertEquals(
                listOf(AvatarCommand.Mouth(1f), AvatarCommand.Mouth(0f)),
                engine.commands,
            )
        } finally { f.delete() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun play_reports_zero_and_pushes_nothing_for_unparseable_file() = runTest {
        val engine = RecorderEngine()
        val bridge = DigitalHumanBridge(engine)
        val f = File.createTempFile("rig-bad", ".wav")
        f.writeBytes("garbage".toByteArray())
        try {
            assertEquals(0, DhTestAudio.play(f, bridge))
            assertTrue(engine.pcm.isEmpty())
        } finally { f.delete() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun play_with_motion_id_dispatches_gesture_before_first_pcm() = runTest {
        val engine = RecorderEngine()
        val bridge = DigitalHumanBridge(engine)
        val f = File.createTempFile("rig-m", ".wav")
        f.writeBytes(wav(16000, 1, 16, data = pcmBytes(20)))
        try {
            DhTestAudio.play(f, bridge, motionId = 3)
            assertEquals(listOf<AvatarCommand>(AvatarCommand.Gesture("3")), engine.commands)
        } finally { f.delete() }
    }

    @Test
    fun clip_names_discovered_from_directory_are_sorted_wavs_without_extension() {
        val dir = File(System.getProperty("java.io.tmpdir"), "rig-clips-${System.nanoTime()}")
        dir.mkdirs()
        File(dir, "long.wav").writeBytes(wav(16000, 1, 16))
        File(dir, "fast.wav").writeBytes(wav(16000, 1, 16))
        File(dir, "notes.txt").writeText("x")
        try {
            assertEquals(listOf("fast", "long"), DhTestAudio.clipNames(dir))
        } finally { dir.deleteRecursively() }
    }
}
