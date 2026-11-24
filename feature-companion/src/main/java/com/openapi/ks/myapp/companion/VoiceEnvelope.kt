package com.openapi.ks.myapp.companion

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * 从 16-bit PCM WAV 里读出的逐窗包络：每 20ms 一个窗口。
 *
 * 这是**纯解析**（只吃文件字节，不碰播放器、不碰权限），所以通话页试听与教学演示共用一份，
 * 也便于单测（`VoiceEnvelopeTest`）。教学演示用它把 TTS 合成的语音变成嘴型电平，
 * **全程不申请麦克风**——见 docs/design/companion-teaching-demo/redesign-v2-stage-camera.md §0。
 */
class VoiceEnvelope(
    /** 视觉压缩后的音量 0..1，按窗口序号取用。 */
    val levels: FloatArray,
    /** 每窗口的频段能量（[VoiceSpectrum.BANDS] 个，低频在前）；静音窗口为全零。 */
    val bands: Array<FloatArray>,
) {
    companion object {
        /** 窗口长度（毫秒），与播放位置换算共用：`index = positionMs / WINDOW_MS`。 */
        const val WINDOW_MS = 20

        /**
         * 解析 WAV。不是 RIFF/WAVE、不是 16-bit PCM、或头残缺时返回 null（调用方决定怎么降级）。
         */
        fun read(file: File): VoiceEnvelope? {
            val bytes = file.readBytes()
            if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var channels = 0; var rate = 0; var bits = 0; var format = 0
            var offset = 12
            while (offset + 8 <= bytes.size) {
                val id = String(bytes, offset, 4)
                val count = b.getInt(offset + 4)
                if (count < 0) return null
                val start = offset + 8
                if (id == "fmt " && count >= 16 && start + 16 <= bytes.size) {
                    format = b.getShort(start).toInt(); channels = b.getShort(start + 2).toInt()
                    rate = b.getInt(start + 4); bits = b.getShort(start + 14).toInt()
                }
                if (id == "data") {
                    if (format != 1 || channels <= 0 || rate <= 0 || bits != 16) return null
                    val end = minOf(bytes.size.toLong(), start.toLong() + count).toInt()
                    val samplesPerWindow = (rate / (1000 / WINDOW_MS) * channels).coerceAtLeast(1)
                    val framesPerWindow = samplesPerWindow / channels
                    val windows = (end - start) / 2 / samplesPerWindow
                    val spectrum = VoiceSpectrum()
                    val mono = FloatArray(framesPerWindow)
                    val bands = arrayOfNulls<FloatArray>(windows)
                    val levels = FloatArray(windows) { window ->
                        var sum = 0.0
                        repeat(samplesPerWindow) { i ->
                            val sample = b.getShort(start + (window * samplesPerWindow + i) * 2) / 32768.0
                            sum += sample * sample
                            if (i % channels == 0) mono[i / channels] = sample.toFloat()
                        }
                        val rms = sqrt(sum / samplesPerWindow)
                        bands[window] =
                            if (rms < 0.003) FloatArray(VoiceSpectrum.BANDS) else spectrum.analyze(mono, 0, framesPerWindow, rate)
                        sqrt(rms / 0.25).coerceIn(0.0, 1.0).toFloat()
                    }
                    return VoiceEnvelope(levels, Array(windows) { bands[it] ?: FloatArray(VoiceSpectrum.BANDS) })
                }
                val next = start.toLong() + count + count % 2
                if (next > bytes.size) break
                offset = next.toInt()
            }
            return null
        }
    }
}
