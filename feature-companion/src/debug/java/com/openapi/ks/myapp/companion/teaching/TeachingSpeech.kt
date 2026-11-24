package com.openapi.ks.myapp.companion.teaching

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.openapi.ks.myapp.companion.VoiceEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 教学演示的老师发声 + 唇形：`TTS.synthesizeToFile → [VoiceEnvelope] → 播放时逐窗推电平`。
 *
 * **全程不申请麦克风**——嘴型来自本机合成的 WAV，不是录音。这条路与通话页
 * [com.openapi.ks.myapp.companion.CallAudioPreview] 同源（共用 [VoiceEnvelope]），
 * 边界见 docs/design/companion-teaching-demo/redesign-v2-stage-camera.md §0。
 *
 * 生命周期由 Activity 管：[speak] 可连发（后发顶掉先发），[shutdown] 释放 TTS 与临时文件。
 */
class TeachingSpeech(context: Context) {

    private val app = context.applicationContext
    private val scope: CoroutineScope = MainScope()
    private val level = MutableStateFlow(0f)

    /** 喂给舞台的口型电平 0..1（非说话时回 0）。 */
    val mouthLevel = level.asStateFlow()

    /** 合成失败时的一次性说明（本机没有中文 TTS 引擎/语音包）。 */
    val error = MutableStateFlow<String?>(null)

    private var tts: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var generation = 0L
    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private val tempFiles = mutableListOf<File>()

    /** TTS 就绪前 speak 的请求存一个，就绪后补播（与 CallAudioPreview 一致的降级路径）。 */
    private var pending: String? = null

    init {
        tts = TextToSpeech(app) { status ->
            scope.launch {
                if (closed) return@launch
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    val lang = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                        ready = false
                        error.value = "本机中文 TTS 语音包缺失：文字演示仍可用，唇形不动"
                    }
                } else {
                    error.value = "本机语音引擎不可用：文字演示仍可用，唇形不动"
                }
                pending?.let { text -> pending = null; if (ready) speakNow(text) }
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) {
                val (gen, file) = parseId(id) ?: return
                scope.launch { if (!closed && gen == generation) play(file, gen) }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                val (gen, _) = parseId(id) ?: return
                scope.launch { if (!closed && gen == generation) level.value = 0f }
            }
        })
    }

    /** 播一句老师台词；引擎不可用时只出声失败、不改文字流程（文字流程本来就有）。 */
    fun speak(text: String) {
        stopPlayback()
        if (closed || text.isBlank()) return
        if (!ready) { pending = text; return }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        val engine = tts ?: return
        generation++
        val gen = generation
        val file = File.createTempFile("teaching-tts-", ".wav", app.cacheDir)
        tempFiles += file
        // utteranceId 带代数：回调回来时如果已被更新的一步顶掉（gen 不匹配），直接丢弃。
        if (engine.synthesizeToFile(text, null, file, "$gen") == TextToSpeech.ERROR) {
            error.value = "本机语音合成失败：文字演示仍可用，唇形不动"
        }
    }

    private fun parseId(id: String?): Pair<Long, File>? {
        val gen = id?.substringBefore('#')?.toLongOrNull() ?: return null
        val file = tempFiles.lastOrNull() ?: return null
        return gen to file
    }

    private suspend fun play(file: File, gen: Long) {
        val envelope = withContext(Dispatchers.IO) { runCatching { VoiceEnvelope.read(file) }.getOrNull() }
        if (closed || gen != generation) return
        if (envelope == null) {
            error.value = "语音引擎未生成 16-bit PCM WAV：唇形无法准确驱动"
            return
        }
        try {
            val audio = MediaPlayer()
            player = audio
            audio.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            audio.setDataSource(file.absolutePath)
            audio.prepare()
            audio.setOnCompletionListener { stopPlayback() }
            audio.setOnErrorListener { _, _, _ -> stopPlayback(); true }
            audio.start()
            ticker = scope.launch {
                while (audio.isPlaying) {
                    level.value = envelope.levels.getOrElse(audio.currentPosition / VoiceEnvelope.WINDOW_MS) { 0f }
                    delay(VoiceEnvelope.WINDOW_MS.toLong())
                }
                level.value = 0f
            }
        } catch (_: Exception) {
            stopPlayback()
        }
    }

    private fun stopPlayback() {
        ticker?.cancel(); ticker = null
        level.value = 0f
        player?.release(); player = null
    }

    fun stop() {
        generation++
        pending = null
        stopPlayback()
        tts?.stop()
    }

    fun shutdown() {
        closed = true
        stop()
        tts?.shutdown()
        tts = null
        scope.cancel()
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }
}
