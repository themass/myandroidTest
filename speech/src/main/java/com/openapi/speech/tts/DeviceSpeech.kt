package com.openapi.speech.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 设备自带 TTS 朗读（离线、免费）。
 *
 * 只在设备上有可用的中文语音时使用；否则返回 false，由调用方回退到后端 TTS。
 * 同一时间只朗读一条：新的朗读会打断旧的。
 */
object DeviceSpeech {

    private var engine: TextToSpeech? = null
    private const val TAG = "DeviceSpeech"
    private var chineseReady: Boolean = false
    private var pending: CancellableContinuation<Boolean>? = null

    /**
     * 朗读一段文字。
     * @return true 表示设备端朗读完成；false 表示设备没有可用的中文语音或朗读失败，应回退到后端。
     */
    suspend fun speakChinese(context: Context, text: String): Boolean {
        if (text.isBlank()) return false
        val tts = ensureEngine(context) ?: return false
        if (!chineseReady) return false
        return suspendCancellableCoroutine { cont ->
            pending?.takeIf { it.isActive }?.resume(false)
            pending = cont
            val utteranceId = UUID.randomUUID().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) = finish(true)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finish(false)

                override fun onError(utteranceId: String?, errorCode: Int) = finish(false)
            })
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result != TextToSpeech.SUCCESS) finish(false)
            cont.invokeOnCancellation { tts.stop() }
        }
    }

    /** 停止当前朗读。 */
    fun stop() {
        engine?.stop()
        pending?.takeIf { it.isActive }?.resume(false)
        pending = null
    }

    private fun finish(ok: Boolean) {
        val cont = pending ?: return
        pending = null
        if (cont.isActive) cont.resume(ok)
    }

    /** 初始化引擎（异步）；返回 null 表示引擎初始化失败。 */
    private suspend fun ensureEngine(context: Context): TextToSpeech? {
        engine?.let { return it }
        return suspendCancellableCoroutine { cont ->
            var created: TextToSpeech? = null
            created = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val tts = created
                    val lang = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    chineseReady = lang != null &&
                        lang != TextToSpeech.LANG_MISSING_DATA &&
                        lang != TextToSpeech.LANG_NOT_SUPPORTED
                    if (tts != null) engine = tts
                } else {
                    chineseReady = false
                }
                if (cont.isActive) cont.resume(engine)
            }
        }
    }
}
