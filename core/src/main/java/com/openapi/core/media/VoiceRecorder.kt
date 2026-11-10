package com.openapi.core.media

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import kotlinx.coroutines.flow.MutableStateFlow
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 语音消息录制：AudioRecord 直接写 16kHz 单声道 16-bit 的 WAV。
 *
 * 之前是 MediaRecorder 录 AAC/m4a，识别前还要解码成 PCM；后端 ASR 要的就是裸 PCM，
 * 所以这里直接录成 PCM，文件头写成标准 WAV，既能被 Media3 播放，又能去掉 44 字节头
 * 直接作为 ASR 的输入（见 [readPcm]）。
 */
object VoiceRecorder {

    const val SAMPLE_RATE = 16_000
    private const val HEADER_BYTES = 44
    private const val BYTES_PER_SAMPLE = 2

    data class Recording(val filePath: String, val durationSeconds: Int)

    private var recorder: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var worker: Thread? = null

    /** 当前录音音量 0..1，录音线程每读一块数据更新一次，给声波动画用。 */
    val level = MutableStateFlow(0f)

    /** 实时识别用：每读到一块 PCM 回调一次（在录音线程上），回调里不要阻塞。 */
    @Volatile var pcmListener: ((ByteArray, Int) -> Unit)? = null
    @Volatile private var running = false

    /** 在这个时间点（elapsedRealtime）之前读到的声音写成静音：盖住切换状态时的震动声。 */
    @Volatile private var muteUntil = 0L

    /**
     * 接下来 [millis] 毫秒录到的声音写成静音（录音文件和实时识别都一样）。
     * 在震动之前调用；要覆盖马达余震和录音缓冲的延迟，默认 250 毫秒。
     */
    fun muteFor(millis: Long = 250L) {
        muteUntil = android.os.SystemClock.elapsedRealtime() + millis
    }
    private var outputFile: File? = null
    private var startedAtMillis: Long = 0L

    /** @return 是否成功开始录音；没权限或麦克风被占用时返回 false。 */
    @SuppressLint("MissingPermission")
    fun start(context: Context): Boolean {
        if (recorder != null) return false

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false

        val dir = File(context.cacheDir, "voice_messages").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.wav")

        return try {
            // VOICE_RECOGNITION：不做通话那套增益，人声更干净；再叠加系统降噪和回声消除（设备支持时）
            val audio = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2,
            )
            if (audio.state != AudioRecord.STATE_INITIALIZED) {
                audio.release()
                false
            } else {
                FileOutputStream(file).use { it.write(ByteArray(HEADER_BYTES)) }
                if (NoiseSuppressor.isAvailable()) noiseSuppressor = NoiseSuppressor.create(audio.audioSessionId)?.apply { enabled = true }
                if (AcousticEchoCanceler.isAvailable()) echoCanceler = AcousticEchoCanceler.create(audio.audioSessionId)?.apply { enabled = true }
                audio.startRecording()
                recorder = audio
                outputFile = file
                startedAtMillis = System.currentTimeMillis()
                running = true
                worker = Thread {
                    FileOutputStream(file, true).use { out ->
                        val buffer = ByteArray(minBuffer)
                        while (running) {
                            val read = audio.read(buffer, 0, buffer.size)
                            if (read > 0) {
                                if (android.os.SystemClock.elapsedRealtime() < muteUntil) {
                                    java.util.Arrays.fill(buffer, 0, read, 0.toByte())
                                }
                                out.write(buffer, 0, read)
                                level.value = rmsLevel(buffer, read)
                                pcmListener?.invoke(buffer, read)
                            }
                        }
                    }
                }.also { it.start() }
                true
            }
        } catch (e: Exception) {
            releaseCapture()
            file.delete()
            false
        }
    }

    /** 停止并返回录音结果；录音时长不足 1 秒（大概率是误触）时丢弃并返回 null。 */
    fun stop(): Recording? {
        if (recorder == null) return null
        val file = outputFile
        val durationMs = System.currentTimeMillis() - startedAtMillis
        releaseCapture()
        outputFile = null
        if (file == null) return null
        return if (durationMs < 1000) {
            file.delete()
            null
        } else {
            writeHeader(file)
            Recording(filePath = file.absolutePath, durationSeconds = (durationMs / 1000).toInt().coerceAtLeast(1))
        }
    }

    /** 录到一半取消（比如上滑取消的手势），丢弃文件。 */
    fun cancel() {
        releaseCapture()
        outputFile?.delete()
        outputFile = null
    }

    /** 去掉 WAV 头，得到 ASR 需要的裸 PCM（16kHz、单声道、16-bit 小端）。 */
    fun readPcm(file: File): ByteArray? {
        val bytes = file.readBytes()
        if (bytes.size <= HEADER_BYTES) return null
        return bytes.copyOfRange(HEADER_BYTES, bytes.size)
    }

    private fun releaseCapture() {
        level.value = 0f
        pcmListener = null
        running = false
        worker?.let { runCatching { it.join(2000) } }
        worker = null
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
        noiseSuppressor?.release()
        noiseSuppressor = null
        echoCanceler?.release()
        echoCanceler = null
    }

    /** 16-bit 小端 PCM 的 RMS，放大后归一化到 0..1；人声通常在 0.02~0.2 之间，所以乘 4。 */
    private fun rmsLevel(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        var n = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toInt()
            sum += sample.toDouble() * sample
            n++
            i += 2
        }
        if (n == 0) return 0f
        val rms = Math.sqrt(sum / n) / 32768.0
        return (rms * 4.0).coerceIn(0.0, 1.0).toFloat()
    }

    private fun writeHeader(file: File) {
        val dataSize = (file.length() - HEADER_BYTES).toInt().coerceAtLeast(0)
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(wavHeader(dataSize))
        }
    }

    private fun wavHeader(dataSize: Int): ByteArray =
        ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(SAMPLE_RATE)
            putInt(SAMPLE_RATE * BYTES_PER_SAMPLE)
            putShort(BYTES_PER_SAMPLE.toShort())
            putShort(16)
            put("data".toByteArray())
            putInt(dataSize)
        }.array()
}
