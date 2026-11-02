package com.openapi.core.media

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
    private var worker: Thread? = null
    @Volatile private var running = false
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
            val audio = AudioRecord(
                MediaRecorder.AudioSource.MIC,
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
                            if (read > 0) out.write(buffer, 0, read)
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
        running = false
        worker?.let { runCatching { it.join(2000) } }
        worker = null
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
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
