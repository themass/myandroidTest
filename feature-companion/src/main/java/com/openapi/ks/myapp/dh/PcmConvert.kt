package com.openapi.ks.myapp.dh

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 把 LiveKit 远端音频回调里的 PCM（任意采样率、1..N 声道、16-bit）转成
 * Duix / Tao 要的「16kHz 单声道 PCM16LE」。
 *
 * 真实通话以前只把 RMS 音量送到形象层，Duix 只能靠音量合成一段正弦波去驱动口型，
 * 和真实语音的音素/节奏对不上，且音量低时被门限吞掉 → 嘴不动。这里提供真 PCM。
 */
internal object PcmConvert {
    const val TARGET_RATE = 16000

    /** 返回 16k 单声道 PCM16LE；非 16-bit 或无数据返回 null。 */
    fun toMono16k(
        data: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        channels: Int,
        frames: Int,
    ): ByteArray? {
        if (bitsPerSample != 16 || sampleRate <= 0 || channels <= 0 || frames <= 0) return null
        val src = data.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val available = minOf(frames, src.remaining() / 2 / channels)
        if (available <= 0) return null
        val mono = IntArray(available)
        for (f in 0 until available) {
            var sum = 0
            for (c in 0 until channels) sum += src.short.toInt()
            mono[f] = sum / channels
        }
        val outCount = (available.toLong() * TARGET_RATE / sampleRate).toInt()
        if (outCount <= 0) return null
        val out = ByteArray(outCount * 2)
        for (i in 0 until outCount) {
            val from = (i.toLong() * sampleRate / TARGET_RATE).toInt()
            val to = maxOf(from + 1, ((i + 1).toLong() * sampleRate / TARGET_RATE).toInt()).coerceAtMost(available)
            var acc = 0L
            for (k in from until to) acc += mono[k]
            val v = (acc / maxOf(1, to - from)).toInt().coerceIn(-32768, 32767)
            out[i * 2] = (v and 0xff).toByte()
            out[i * 2 + 1] = (v shr 8).toByte()
        }
        return out
    }
}
