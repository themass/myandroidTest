package com.openapi.feature.chat.data.remote

import com.openapi.common.util.log.LogUtil
import com.openapi.feature.chat.domain.model.ChatStreamEvent
import com.openapi.feature.chat.domain.model.VideoRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 图片上传与附件回合（整改清单 P3-17：从 AgentBackedChatRepository 拆出）。
 *
 * 「上传」是独立职责：直传/降级的编排、Content-Type 判定、等整轮文字收尾都只依赖
 * [AgentChatRemote]，不碰仓库类的会话/历史状态。纯移动，行为与拆分前逐行一致。
 *
 * [eventSource] 是事件流的注入缝（默认就是 `remote::events`）：让 [awaitTurnText]
 * 的看门狗行为可以在 JVM 测试里喂帧验证，不必起真连接。
 */
internal class AttachmentApi(
    private val remote: AgentChatRemote,
    private val eventSource: (sessionId: Long, lastEventId: String?) -> Flow<AgentSseFrame> = remote::events,
) {

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

    /**
     * 上传一个视频，返回 upload_id；任何一步失败返回 null（界面提示重试）。
     *
     * **视频没有降级通道**：中转 `/v1/uploads` 会把字节强制转码成 JPEG（服务端图片归一化管线），
     * 视频的完整 MP4 打进去必坏——所以签发或 PUT 失败只能报错，绝不能像图片那样掉回中转。
     * 大小与类型在**签发之前**拦（[videoPreflightReject]）：25 MiB 的视频传一半才被服务端
     * 踢回来，白花的是用户的流量。
     */
    suspend fun uploadVideo(localFile: File): String? =
        runCatching {
            withContext(Dispatchers.IO) {
                if (VideoRules.preflightReject(localFile.name, localFile.length()) != null) return@withContext null
                val (uploadId, putUrl) = remote.signUpload(SCENE_CHAT_VIDEO, "video/mp4", localFile.length())
                remote.putToPresigned(putUrl, localFile, "video/mp4")
                uploadId
            }
        }.getOrNull()

    private companion object {
        const val SCENE_CHAT_VIDEO = "chat-video"
    }

    /** 按扩展名给 Content-Type；服务端只认 jpeg/png/webp/heic，其他一律按 jpeg 试。 */
    private fun mimeOf(file: File): String = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        else -> "image/jpeg"
    }

    /**
     * 收一轮的完整文字（附件路径用：界面只关心最终那段回复，不需要逐字）。
     *
     * A39：折叠逻辑全部交给 [TurnEventCollector]（与主线 `collectTurn` 同一台机器）。
     * 旧实现在这里手搓 collect：`remote.events` 的 readTimeout 是 0、心跳注释不产生帧，
     * 「连接活着、模型不出字」时这条 `collect` 永远挂着——附件回合的 `attachmentJob`
     * 不结束，`streaming=attachmentActive()` 就一直为真，界面「正在生成」永不落地，
     * 用户看到的就是「agent 一直不结束、一直在生成」。委托之后白拿三样东西：
     * 帧间看门狗（45s 无帧 → 收尾）、掉线退避重连 + Last-Event-ID 续传、snapshot 补尾去重。
     */
    suspend fun awaitTurnText(sessionId: Long, expectedTurnId: String?): String {
        val acc = StringBuilder()
        TurnEventCollector(
            open = { lastEventId -> eventSource(sessionId, lastEventId) },
            parse = ::parseAgentEvent,
        ).collect(
                emit = { ev ->
                    when (ev) {
                        is ChatStreamEvent.Delta -> acc.append(ev.text)
                        // A40：合并重跑——重跑后的 delta 与 done 只含新一轮，旧半截作废。
                        is ChatStreamEvent.Restarted -> acc.setLength(0)
                        // 看门狗/服务端错误：acc 里是已收到的半截（或空），照常交出去，
                        // 上层按空回复/半截显示——绝不永远挂着。
                        is ChatStreamEvent.Error -> LogUtil.w("附件回合以错误收尾: ${ev.message}")
                        is ChatStreamEvent.Done -> Unit
                    }
                },
                expectedTurnId = expectedTurnId,
                assistantLocalId = "attachment-turn",
                userMessageId = null,
                persist = { fullText ->
                    // 权威全文：Done 收尾时用它对齐（snapshot 补尾与 delta 拼接都已折叠好）。
                    acc.setLength(0)
                    acc.append(fullText)
                },
            )
        return acc.toString()
    }
}
