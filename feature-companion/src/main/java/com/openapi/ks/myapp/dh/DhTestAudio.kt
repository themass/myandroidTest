package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarCommand
import kotlinx.coroutines.delay
import java.io.File

/**
 * 测试语音 rig：读取 `seed-assets.sh audio` 推到 `filesDir/dh-lab-audio/` 的固定 WAV，
 * 按 20ms 节拍把 PCM 喂给当前引擎（bridge.onRemotePcm 给 Duix/Tao，onMouthLevel 给
 * Live2D/Simple），不经真实 TTS/LLM。通话页 lab 开关打开时由 adb broadcast 触发。
 */
object DhTestAudio {

    /** adb 触发射口型测试：am broadcast -a <ACTION> --es clip long [--ei motion 3] */
    const val ACTION_PLAY = "com.openapi.ks.chat.action.DH_TEST_AUDIO"
    const val EXTRA_CLIP = "clip"
    const val EXTRA_MOTION = "motion"

    /** 20ms @ 16kHz mono s16le。 */
    private const val BATCH_BYTES = 640

    /** 峰值增益：16k 语音峰值常 ~0.25，x4 后满幅映射到 1。 */
    private const val GAIN = 4f

    /** 幅度低于此值视为静音段，不发 Mouth（嘴应回落到闭）。 */
    private const val MOUTH_FLOOR = 0.02f

    /**
     * 解析只接受 16kHz / 单声道 / 16-bit PCM 的 WAV（rig 的输入契约，
     * 生成端 make-test-audio.sh 保证），其余一律 null——引擎喂错采样率只会静默劣化。
     */
    fun parseWavPcm16Mono16k(bytes: ByteArray): ByteArray? {
        if (bytes.size < 44) return null
        fun le16(o: Int) = (bytes[o].toInt() and 0xff) or ((bytes[o + 1].toInt() and 0xff) shl 8)
        fun le32(o: Int) = le16(o) or (le16(o + 2) shl 16)
        if (String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF") return null
        if (String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        var o = 12
        var fmt = -1
        var channels = -1
        var sampleRate = -1
        var bits = -1
        var dataStart = -1
        var dataLen = 0
        while (o + 8 <= bytes.size) {
            val id = String(bytes, o, 4, Charsets.US_ASCII)
            val size = le32(o + 4)
            val body = o + 8
            when (id) {
                "fmt " -> if (body + 16 <= bytes.size) {
                    fmt = le16(body)
                    channels = le16(body + 2)
                    sampleRate = le32(body + 4)
                    bits = le16(body + 14)
                }
                "data" -> {
                    dataStart = body
                    dataLen = minOf(size, bytes.size - body)
                }
            }
            if (id == "data") break // data 之后不再解析
            o = body + size + (size and 1)
        }
        if (dataStart < 0) return null
        if (fmt != 1 || channels != 1 || sampleRate != 16000 || bits != 16) return null
        return bytes.copyOfRange(dataStart, dataStart + dataLen)
    }

    /** 该 PCM 块的嘴部幅度 0..1（峰值包络，带增益），静音块返回 0。 */
    fun level(pcm: ByteArray): Float {
        var peak = 0
        var i = 0
        while (i + 1 < pcm.size) {
            val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xff)).toShort().toInt()
            val a = if (s < 0) -s else s
            if (a > peak) peak = a
            i += 2
        }
        return (peak / 32767f * GAIN).coerceIn(0f, 1f)
    }

    /**
     * 播放一个 WAV：先发 [motionId] 手势（可选，Duix 的 startMotion 通道），再按 20ms
     * 节拍喂给当前引擎：
     * - PCM 驱动引擎（[DigitalHumanEngine.pcmDriven]，如 Duix）只收真 PCM，不发 Mouth
     *   （Duix 的 Mouth 分支会合成假 PCM，和真 PCM 抢嘴型）；
     * - 其余引擎收折算幅度（Mouth 指令）：非静音块发峰值，进入静音段时发一次 0 收尾，
     *   之后不再刷 0。
     * 返回喂入的总字节数，解析失败返回 0 且不喂任何数据。
     */
    suspend fun play(file: File, bridge: DigitalHumanBridge, motionId: Int? = null): Int {
        val pcm = parseWavPcm16Mono16k(file.readBytes()) ?: return 0
        if (motionId != null) {
            bridge.onAvatarCommand(AvatarCommand.Gesture(motionId.toString()))
        }
        val pcmDriven = bridge.current().pcmDriven
        var sent = 0
        var off = 0
        var mouthOpen = false // 上一次发的是非零 Mouth 吗？静音时只补发一次 0。
        while (off < pcm.size) {
            val end = minOf(off + BATCH_BYTES, pcm.size)
            val batch = pcm.copyOfRange(off, end)
            if (pcmDriven) {
                bridge.onRemotePcm(batch)
            } else {
                val lvl = level(batch)
                if (lvl > MOUTH_FLOOR) {
                    bridge.onMouthLevel(lvl)
                    mouthOpen = true
                } else if (mouthOpen) {
                    bridge.onMouthLevel(0f)
                    mouthOpen = false
                }
            }
            sent += batch.size
            off = end
            if (off < pcm.size) delay(20)
        }
        if (pcmDriven) {
            // 播完必须收尾，否则推送段永远挂着，人物停在带摆动的待机口型循环。
            bridge.flushRemotePcm()
        }
        return sent
    }

    /** 目录里的可播放片段名（去 .wav 后缀、按名字排序）。 */
    fun clipNames(dir: File): List<String> =
        dir.listFiles { f: File -> f.isFile && f.name.endsWith(".wav") }
            ?.map { it.name.removeSuffix(".wav") }
            ?.sorted()
            ?: emptyList()

    /** 通话页 rig 用的素材目录（与 seed-assets.sh audio 的落点一致）。 */
    fun clipsDir(filesDir: File): File = File(filesDir, "dh-lab-audio")
}
