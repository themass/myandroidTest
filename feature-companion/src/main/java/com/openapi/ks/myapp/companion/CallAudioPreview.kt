package com.openapi.ks.myapp.companion

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

data class CallAudioPreviewState(
    val speaker: String? = null,
    val level: Float = 0f,
    /** 当前 20ms 窗口的频段能量（[VoiceSpectrum.BANDS] 个，低频在前）；没在播放时为空。 */
    val bands: FloatArray = FloatArray(0),
    val preparing: Boolean = false,
    val text: String = "",
    val error: String? = null,
)

/** 完全本地的双语音试听：TTS 生成 WAV，按播放器位置读取 PCM 包络，不依赖 RTC。 */
class CallAudioPreview(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(CallAudioPreviewState())
    val state = mutable.asStateFlow()
    private var ready = false
    private var initialized = false
    private var closed = false
    private var pending: String? = null
    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private var request = 0L
    private val files = mutableListOf<File>()
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(app) { status ->
            scope.launch {
                if (closed) return@launch
                initialized = true
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    val language = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                        ready = false
                        mutable.value = CallAudioPreviewState(error = "请先在手机语音设置中安装中文 TTS 语音包")
                    } else {
                        pending?.let { synthesize(it) }
                    }
                } else {
                    mutable.value = CallAudioPreviewState(error = "本机语音引擎不可用，请安装中文 TTS 语音")
                }
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) {
                scope.launch {
                    val file = files.lastOrNull() ?: return@launch
                    if (id == request.toString() && !closed) play(file, request)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                scope.launch {
                    if (id == request.toString()) {
                        mutable.value = CallAudioPreviewState(error = "试听语音生成失败，请检查本机中文语音引擎")
                    }
                }
            }
        })
    }

    fun toggle(speaker: String) {
        if (mutable.value.speaker == speaker) { stop(); return }
        stop()
        pending = speaker
        mutable.value = CallAudioPreviewState(speaker = speaker, preparing = true)
        if (ready) synthesize(speaker)
        else if (initialized) mutable.value = CallAudioPreviewState(error = "请检查本机中文 TTS 引擎和语音包")
    }

    private fun synthesize(speaker: String) {
        pending = null
        request++
        val text = if (speaker == "user") {
            "你好，我想试一下声音。轻轻说一句，然后停一停。现在稍微大声一点，你听到了吗？"
        } else {
            "我听到了。我就在这里，慢慢说，不着急。你说话的时候，下面的声波会轻轻起伏。"
        }
        mutable.value = mutable.value.copy(text = text)
        val file = File.createTempFile("call-preview-", ".wav", app.cacheDir)
        files += file
        tts.setPitch(if (speaker == "user") 0.85f else 1.10f)
        tts.setSpeechRate(if (speaker == "user") 0.95f else 0.85f)
        if (tts.synthesizeToFile(text, null, file, request.toString()) == TextToSpeech.ERROR) {
            mutable.value = CallAudioPreviewState(error = "本机语音合成不可用")
        }
    }

    private suspend fun play(file: File, generation: Long) {
        val envelope = kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching { VoiceEnvelope.read(file) }.getOrNull()
        }
        if (closed || mutable.value.speaker == null || request != generation) return
        if (envelope == null) {
            mutable.value = CallAudioPreviewState(error = "语音引擎未生成支持的 16-bit PCM WAV，无法准确驱动声波")
            return
        }
        try {
            val audio = MediaPlayer()
            player = audio
            audio.setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
            audio.setDataSource(file.absolutePath)
            audio.prepare()
            audio.setOnCompletionListener { stop() }
            audio.setOnErrorListener { _, _, _ ->
                stop()
                mutable.value = CallAudioPreviewState(error = "本地试听播放失败")
                true
            }
            mutable.value = mutable.value.copy(preparing = false)
            audio.start()
            ticker = scope.launch {
                while (audio.isPlaying) {
                    val index = audio.currentPosition / VoiceEnvelope.WINDOW_MS
                    mutable.value = mutable.value.copy(
                        level = envelope.levels.getOrElse(index) { 0f },
                        bands = envelope.bands.getOrElse(index) { FloatArray(0) },
                    )
                    delay(20)
                }
            }
        } catch (_: Exception) {
            stop()
            mutable.value = CallAudioPreviewState(error = "本地试听播放失败")
        }
    }

    /** 每 20ms 一个窗口：音量与频段能量，解析在共用的 [VoiceEnvelope]。 */

    fun stop() {
        request++
        pending = null
        ticker?.cancel(); ticker = null
        if (::tts.isInitialized) tts.stop()
        player?.release(); player = null
        mutable.value = CallAudioPreviewState()
    }

    fun close() {
        closed = true
        stop()
        if (::tts.isInitialized) tts.shutdown()
        scope.cancel()
        files.forEach { it.delete() }
    }
}
