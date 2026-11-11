package com.openapi.chat.data.remote

import android.content.Context
import com.openapi.chat.domain.model.ChatReply
import com.openapi.chat.domain.model.MessageKind
import com.openapi.common.net.http.BaseService
import com.openapi.common.net.awaitUpload
import com.openapi.core.config.Constants
import com.openapi.chat.data.remote.Choice
import com.openapi.chat.data.remote.TtsResponseVo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 图片和文件上传（multipart），服务端回复的消息作为一条 assistant 回复返回。 */
internal class AttachmentUploader(private val baseService: BaseService) {

    suspend fun send(
        sessionId: Long,
        kind: MessageKind,
        localFile: File,
        fileName: String,
        placeholderId: String,
    ): ChatReply? {
        val isImage = kind == MessageKind.IMAGE
        val url = Constants.getUrl(if (isImage) Constants.CHAT_IMAGE_URL else Constants.CHAT_FILE_URL)
        // 服务端用这两个表单名区分图片与文件（recognize.json 读 image，file.json 读 file）
        val partName = if (isImage) "image" else "file"
        val param = AttachmentParam(id = placeholderId, sessionId = sessionId)
        return baseService.awaitUpload(url, param, listOf(localFile), partName, Choice::class.java, CHAT_TAG)
            ?.let { response ->
                ChatReply(
                    id = response.id ?: placeholderId,
                    text = response.message?.content.orEmpty(),
                )
            }
    }
}

/** 语音合成：服务端返回 base64 音频，保存成本地文件后返回 file:// 地址。 */
internal class SpeechRemote(
    private val context: Context,
    private val remote: ChatRemote,
) {

    suspend fun synthesize(messageId: String, text: String): String? {
        val vo = remote.postForResult(
            Constants.getUrl(Constants.CHAT_TTS_URL),
            SpeechParam(id = messageId, content = text),
            TtsResponseVo::class.java,
        ) ?: return null
        val data = vo.data?.takeIf { it.isNotEmpty() } ?: return null
        val dir = File(context.filesDir, "tts").apply { mkdirs() }
        val file = File(dir, vo.fileName?.takeIf { it.isNotBlank() } ?: "tts_$messageId.mp3")
        return withContext(Dispatchers.IO) {
            runCatching { file.writeBytes(android.util.Base64.decode(data, android.util.Base64.DEFAULT)) }
                .getOrNull()
                ?.let { "file://" + file.absolutePath }
        }
    }
}
