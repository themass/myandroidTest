package com.openapi.chat.data.remote

import android.util.Base64
import com.openapi.common.net.http.BaseService
import com.openapi.chat.domain.speech.SpeechToText
import com.openapi.core.media.VoiceRecorder
import com.openapi.common.net.awaitPost
import com.openapi.chat.data.remote.AsrContentForm
import com.openapi.chat.data.remote.AsrResponseVo
import com.openapi.core.config.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * [SpeechToText] 走后端 `/chat/ai/asr.json`：语音供应商的密钥留在服务端，客户端只发裸 PCM。
 * 换 ASR 供应商只改服务端，这里不用动。
 */
class BackendSpeechToText(private val baseService: BaseService) : SpeechToText {

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val TAG = "BackendSpeechToText"
    }

    override suspend fun transcribe(audioFilePath: String): String? = withContext(Dispatchers.IO) {
        val pcm = VoiceRecorder.readPcm(File(audioFilePath)) ?: return@withContext null
        val form = AsrContentForm().apply {
            content = Base64.encodeToString(pcm, Base64.NO_WRAP)
            id = System.currentTimeMillis().toString()
        }
        withTimeoutOrNull(TIMEOUT_MS) { post(form) }?.text?.takeIf { it.isNotBlank() }
    }

    private suspend fun post(form: AsrContentForm): AsrResponseVo? =
        baseService.awaitPost(Constants.getUrl(Constants.CHAT_ASR_URL), form, AsrResponseVo::class.java, TAG)

}
