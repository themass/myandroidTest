package com.openapi.feature.chat.data.remote

import com.openapi.common.util.log.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * 图片上传与附件回合（整改清单 P3-17：从 AgentBackedChatRepository 拆出）。
 *
 * 「上传」是独立职责：直传/降级的编排、Content-Type 判定、等整轮文字收尾都只依赖
 * [AgentChatRemote]，不碰仓库类的会话/历史状态。纯移动，行为与拆分前逐行一致。
 */
internal class AttachmentApi(private val remote: AgentChatRemote) {

    /**
     * 上传图片拿 upload_id，不产生回复。图片附件走这条，文字与它合成一个回合（见 ChatRepository.uploadImage）。
     *
     * 优先**直传**（ADR-011 同款）：sign 要一个单 key 只写的短期凭证，字节直接 PUT 进桶，
     * 不再穿过应用服务器；发消息时服务端按 upload_id 绑定校验（归属/时效/HEAD 复核）。
     * 签发或 PUT 任何一步失败（配额、CORS、网络）都**降级回中转** `/v1/uploads`——
     * 旧通道同时是降级通道，直传挂了不能把整条发送带走。heic 不在直传白名单里，直接走中转。
     */
    suspend fun uploadImage(localFile: File): String? =
        runCatching {
            withContext(Dispatchers.IO) {
                val mime = mimeOf(localFile)
                if (mime != "image/heic") {
                    runCatching {
                        val (uploadId, putUrl) = remote.signUpload("chat-image", mime, localFile.length())
                        remote.putToPresigned(putUrl, localFile, mime)
                        uploadId
                    }.onFailure {
                        // 只记异常类型与消息：预签名 URL 是 bearer secret，绝不进日志
                        LogUtil.w("直传失败，已降级中转上传: ${it.message}")
                    }.getOrNull()?.let { return@withContext it }
                }
                remote.upload(localFile, mime)
            }
        }.getOrNull()

    /** 只走中转 `/v1/uploads` 的上传（sendAttachment 的历史路径，与直传白名单无关）。 */
    suspend fun uploadRelayed(localFile: File): String =
        withContext(Dispatchers.IO) { remote.upload(localFile, mimeOf(localFile)) }

    /** 按扩展名给 Content-Type；服务端只认 jpeg/png/webp/heic，其他一律按 jpeg 试。 */
    private fun mimeOf(file: File): String = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        else -> "image/jpeg"
    }

    /** 收一轮的完整文字（附件路径用：界面只关心最终那段回复，不需要逐字）。 */
    suspend fun awaitTurnText(sessionId: Long, expectedTurnId: String?): String {
        val acc = StringBuilder()
        var matchedTurn = expectedTurnId
        try {
            remote.events(sessionId, null).collect { frame ->
                val obj = runCatching { JSONObject(frame.data) }.getOrNull()
                if (obj != null) {
                    val turn = obj.optString("turn_id", "")
                    if (turn.isNotEmpty() && matchedTurn == null) matchedTurn = turn
                    val ours = turn.isNotEmpty() && turn == matchedTurn
                    when (frame.event) {
                        "turn.snapshot" -> if (ours) {
                            val text = obj.optString("text", "")
                            if (text.length > acc.length && text.startsWith(acc.toString())) {
                                acc.setLength(0)
                                acc.append(text)
                            }
                        }
                        "message.delta" -> if (ours) acc.append(obj.optString("delta", ""))
                        "message.done", "turn.cancelled" -> if (ours) throw TurnFinished
                        "error" -> if (ours) throw TurnFinished
                    }
                }
            }
        } catch (_: TurnFinished) {
            // 正常收尾：这一轮已经结束，不需要继续读事件流。
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogUtil.w("AttachmentApi 附件回合读取失败: ${e.message}")
        }
        return acc.toString()
    }

    /** 事件流里读到本轮结束的信号：用它退出 `collect`，比 `transformWhile` 的类型推断更可靠。 */
    private object TurnFinished : Exception() {
        private fun readResolve(): Any = TurnFinished
        override fun fillInStackTrace(): Throwable = this
    }
}
