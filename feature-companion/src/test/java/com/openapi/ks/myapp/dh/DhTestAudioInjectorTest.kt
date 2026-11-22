package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.companion.CompanionViewModel
import com.openapi.ks.myapp.companion.FakeCompanionApi
import com.openapi.ks.myapp.companion.FakeCompanionTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * rig 的**生产注入路径**（mock harness 的第一判据「不许旁路」的机器钉桩）：
 * [DhTestAudio.play] 拿到 injector 后，每批 PCM 都必须原样进 injector（同一路
 * 20ms 节拍、640B 分块），bridge 一个字都不许再收——下游（电平提取 → mouthLevel
 * → bridge → setMouth）归生产代码独占。injector 拒收（Fake 替身/连接没有音频通路）
 * 时才降级回 bridge 直喂。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DhTestAudioInjectorTest {

    private fun wav16k(pcm: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun s32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
        fun s16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
        out.write("RIFF".toByteArray()); s32(36 + pcm.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); s32(16); s16(1); s16(1)
        s32(16000); s32(32000); s16(2); s16(16)
        out.write("data".toByteArray()); s32(pcm.size); out.write(pcm)
        return out.toByteArray()
    }

    private class SilentEngine : DigitalHumanEngine {
        override val id = DhEngineId.LIVE2D
        override val capabilities = setOf<com.openapi.ks.myapp.avatar.AvatarCapability>()
        override fun availability(context: android.content.Context) = DhEngineAvailability(id, true)
        override fun bind(spec: com.openapi.ks.myapp.avatar.AvatarSpec) {}
        override fun onTouch(event: com.openapi.ks.myapp.avatar.AvatarTouch) =
            com.openapi.ks.myapp.avatar.TouchResult.Ignored
        override fun release() {}
        val pcm = mutableListOf<ByteArray>()
        val commands = mutableListOf<AvatarCommand>()
        var flushes = 0
        override val pcmDriven = true
        override fun pushRemotePcm(pcm16leMono16k: ByteArray) { pcm += pcm16leMono16k.copyOf() }
        override fun dispatch(command: AvatarCommand) { commands += command }
        override fun flushRemotePcm() { flushes++ }
    }

    private fun wavFile(pcm: ByteArray): File {
        val f = File.createTempFile("inj", ".wav")
        f.writeBytes(wav16k(pcm))
        return f
    }

    @Test
    fun `接受注入时每批 PCM 都进 injector，bridge 一个字都不收`() = runTest {
        val engine = SilentEngine()
        val bridge = DigitalHumanBridge(engine)
        val data = ByteArray(100 * 32) { (it % 251).toByte() } // 100ms → 5×640B
        val f = wavFile(data)
        val batches = mutableListOf<ByteArray>()
        try {
            val n = DhTestAudio.play(f, bridge) { batches += it.copyOf(); true }
            assertEquals(data.size, n)
            assertEquals(5, batches.size)
            assertEquals(640, batches[0].size)
            assertEquals(data.toList(), batches.flatten().map { it.toInt() and 0xff })
            // 「不许旁路」：bridge 侧完全静默（手势都没有——没传 motionId）。
            assertTrue("bridge 不该收 PCM: ${engine.pcm.size}", engine.pcm.isEmpty())
            assertTrue("bridge 不该收命令: ${engine.commands}", engine.commands.isEmpty())
            // PCM 驱动引擎播完仍要收尾（引擎自己的推送段状态机需要它）。
            assertEquals(1, engine.flushes)
        } finally { f.delete() }
    }

    @Test
    fun `第一块被拒时降级回 bridge 直喂——Fake 替身路径不变`() = runTest {
        val engine = SilentEngine()
        val bridge = DigitalHumanBridge(engine)
        val data = ByteArray(60 * 32) // 60ms → 3 批
        val f = wavFile(data)
        var injectCalls = 0
        try {
            val n = DhTestAudio.play(f, bridge) { injectCalls++; false }
            assertEquals(data.size, n)
            assertEquals("只探测一次", 1, injectCalls)
            assertEquals(3, engine.pcm.size)
        } finally { f.delete() }
    }

    @Test
    fun `中途被拒：已注入部分不重喂，剩余部分走 bridge`() = runTest {
        val engine = SilentEngine()
        val bridge = DigitalHumanBridge(engine)
        val data = ByteArray(100 * 32) // 5 批
        val f = wavFile(data)
        var injectCalls = 0
        try {
            val n = DhTestAudio.play(f, bridge) {
                injectCalls++
                injectCalls <= 2 // 第 3 批起被拒
            }
            assertEquals(data.size, n)
            assertEquals(3, injectCalls)
            assertEquals("剩余 3 批落 bridge", 3, engine.pcm.size)
            assertEquals(640 * 2 + 640 * 3, data.size) // 总量守恒 sanity
        } finally { f.delete() }
    }

    @Test
    fun `ViewModel 透传：Fake 替身没有注入通路，返回 false 供 rig 降级`() {
        val vm = CompanionViewModel(
            api = FakeCompanionApi(),
            transportFactory = { FakeCompanionTransport(reply = "") },
            inboxPollMillis = 60_000,
        )
        assertTrue(!vm.injectLabAudioPcm16k(ByteArray(640)))
        vm.stopListening()
    }
}
