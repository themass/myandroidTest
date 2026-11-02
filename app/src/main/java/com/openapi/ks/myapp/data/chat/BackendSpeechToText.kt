package com.openapi.ks.myapp.data.chat

import android.util.Base64
import com.openai.net.BaseService
import com.openai.net.request.CommonResponse
import com.openapi.chat.domain.speech.SpeechToText
import com.openapi.core.media.VoiceRecorder
import com.openapi.ks.myapp.bean.form.AsrContentForm
import com.openapi.ks.myapp.bean.vo.AsrResponseVo
import com.openapi.ks.myapp.constant.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

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

    private suspend fun post(form: AsrContentForm): AsrResponseVo? = suspendCancellableCoroutine { cont ->
        baseService.postData(
            Constants.getUrl(Constants.CHAT_ASR_URL),
            form,
            object : CommonResponse.ResponseOkListener<AsrResponseVo>() {
                override fun onResponse(response: AsrResponseVo) {
                    if (cont.isActive) cont.resume(response)
                }
            },
            object : CommonResponse.ResponseErrorListener() {
                override fun onError() {
                    if (cont.isActive) cont.resume(null)
                }
            },
            TAG,
            AsrResponseVo::class.java,
        )
    }
}
