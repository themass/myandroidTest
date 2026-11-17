package com.openapi.ks.myapp.companion

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 把一小段 16-bit PCM 切成 [BANDS] 个对数频段的能量（0..1），驱动「你的声音」波形的形状。
 *
 * 为什么需要它：只有一个 RMS 数时，波形只能知道「多响」，形状只能靠随机数编，
 * 看起来就是「声音自己动自己的」。有了频段，低沉的声音和尖细的声音画出来不一样，
 * 元音、辅音切换时形状跟着变。
 *
 * 做法：取最近 [FFT_SIZE] 个采样（不足补零），加 Hann 窗做实数 FFT，按
 * [LOW_HZ]..[HIGH_HZ] 对数等分取各段平均功率，换成 dBFS 后线性映射到 0..1。
 * 语音能量集中在低频，高频天然偏弱，所以每升一个频段补 [TILT_DB_PER_BAND] dB，
 * 否则右半边永远是平的。
 *
 * 不是线程安全的：一个实例只给一条音频回调线程用（缓冲区复用，避免每 10ms 分配）。
 */
class VoiceSpectrum {
    private val re = FloatArray(FFT_SIZE)
    private val im = FloatArray(FFT_SIZE)
    private val window = FloatArray(FFT_SIZE) { i -> (0.5f - 0.5f * cos(2.0 * PI * i / (FFT_SIZE - 1))).toFloat() }

    /** 交错多声道的 16-bit 小端 PCM，只取第一声道。返回新数组，调用方可以直接发布。 */
    fun analyze(data: ByteBuffer, frames: Int, channels: Int, sampleRate: Int): FloatArray {
        if (frames <= 0 || channels <= 0 || sampleRate <= 0) return FloatArray(BANDS)
        val buf = data.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val n = min(frames, FFT_SIZE)
        val skip = (frames - n) * channels * 2
        if (skip > 0) buf.position(buf.position() + skip)
        for (i in 0 until FFT_SIZE) {
            if (i < n && buf.remaining() >= 2) {
                re[i] = buf.short / 32768f * window[i]
                if (channels > 1) buf.position(min(buf.limit(), buf.position() + (channels - 1) * 2))
            } else {
                re[i] = 0f
            }
            im[i] = 0f
        }
        return bands(sampleRate)
    }

    /** 已经是 -1..1 的单声道采样（本地试听解析出的 WAV 用）。 */
    fun analyze(samples: FloatArray, offset: Int, count: Int, sampleRate: Int): FloatArray {
        val n = min(count, FFT_SIZE)
        for (i in 0 until FFT_SIZE) {
            re[i] = if (i < n && offset + i < samples.size) samples[offset + i] * window[i] else 0f
            im[i] = 0f
        }
        return bands(sampleRate)
    }

    private fun bands(sampleRate: Int): FloatArray {
        fft()
        val out = FloatArray(BANDS)
        val binHz = sampleRate.toFloat() / FFT_SIZE
        val ratio = HIGH_HZ / LOW_HZ
        for (b in 0 until BANDS) {
            val lo = LOW_HZ * ratio.pow(b.toFloat() / BANDS)
            val hi = LOW_HZ * ratio.pow((b + 1f) / BANDS)
            val from = max(1, (lo / binHz).toInt())
            val to = min(FFT_SIZE / 2 - 1, max(from, (hi / binHz).toInt()))
            var power = 0f
            for (k in from..to) power += re[k] * re[k] + im[k] * im[k]
            power /= (to - from + 1)
            // 幅度归一：Hann 窗相干增益 0.5，FFT 幅度按 N/2 缩放。
            val amp = sqrt(power) / (FFT_SIZE * 0.25f)
            val db = 20f * log10(amp + 1e-9f) + b * TILT_DB_PER_BAND
            out[b] = ((db - FLOOR_DB) / RANGE_DB).coerceIn(0f, 1f)
        }
        return out
    }

    /** 原地迭代基 2 FFT。512 点每次约 2 万次乘加，10ms 一次，音频线程负担可以忽略。 */
    private fun fft() {
        var j = 0
        for (i in 1 until FFT_SIZE) {
            var bit = FFT_SIZE shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= FFT_SIZE) {
            val ang = (-2.0 * PI / len)
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < FFT_SIZE) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val tr = re[b] * cr - im[b] * ci
                    val ti = re[b] * ci + im[b] * cr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    companion object {
        const val BANDS = 8
        private const val FFT_SIZE = 512
        private const val LOW_HZ = 120f
        private const val HIGH_HZ = 6000f

        /** 映射区间：底噪附近（-66 dBFS）为 0，正常说话的强频段（约 -18 dBFS）接近 1。真机日志确认后再调。 */
        private const val FLOOR_DB = -66f
        private const val RANGE_DB = 48f

        /** 语音频谱大约每倍频程下降 6 dB；8 段跨约 5.6 个倍频程，每段补约 3 dB。 */
        private const val TILT_DB_PER_BAND = 3f
    }
}
