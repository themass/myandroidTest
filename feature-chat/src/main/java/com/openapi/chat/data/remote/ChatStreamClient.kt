package com.openapi.chat.data.remote

import android.content.Context
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatRole
import com.openapi.chat.domain.model.ChatStreamEvent
import com.openapi.common.net.http.ApiRequest
import com.openapi.common.net.request.BaseRequest
import com.openapi.common.util.log.LogUtil
import com.openapi.core.config.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 对话流式回复（SSE）：发送消息和重新生成。请求只带 sessionId、客户端消息 id、当轮 text，
 * 历史由服务端按 sessionId 读取。
 */
internal class ChatStreamClient(
    private val context: Context,
    private val cache: MessageCache,
    private val sessions: SessionStore,
) {

    /** SSE 连接必须不设读超时——回复多久吐完完全看大模型，不是一个固定时间能框住的。 */
    private val streamHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun sendMessageStream(
        sessionId: Long,
        userText: String,
        characterId: Int?,
        roleId: String?,
        persistUserMessage: Boolean,
        clientMessageId: String?,
    ): Flow<ChatStreamEvent> = streamChat(
        requestSessionId = sessionId,
        text = userText,
        regenerate = false,
        characterId = characterId,
        roleId = roleId,
        persistUserMessage = persistUserMessage,
        clientMessageId = clientMessageId,
    )

    fun regenerateStream(
        sessionId: Long,
        characterId: Int?,
        roleId: String?,
    ): Flow<ChatStreamEvent> = streamChat(
        requestSessionId = sessionId,
        text = null,
        regenerate = true,
        characterId = characterId,
        roleId = roleId,
        persistUserMessage = false,
        clientMessageId = null,
    )

    /**
     * 对话流式请求的公共实现。
     *
     * 请求只带 sessionId、客户端消息 id、当轮 text（或 regenerate=true 时不带 text），
     * 历史由服务端按 sessionId 读取，这里不再上传历史。
     *
     * 会话 id：请求里 sessionId<=0 表示新建会话，新 id 通过流里每一帧的 sessionId 返回，
     * 收到后写进 SP 作为当前会话。
     */
    private fun streamChat(
        requestSessionId: Long,
        text: String?,
        regenerate: Boolean,
        characterId: Int?,
        roleId: String?,
        persistUserMessage: Boolean,
        clientMessageId: String?,
    ): Flow<ChatStreamEvent> = callbackFlow {
        val now = System.currentTimeMillis()
        val clientMsgId = clientMessageId ?: "local-user-$now"
        val assistantId = "local-assistant-$now"
        // 服务端会把它回写到每一帧里；请求本身是新会话（<=0）时才采用这个 id
        var activeSessionId = requestSessionId

        if (!regenerate && persistUserMessage && requestSessionId > 0 && text != null) {
            cache.persist(
                requestSessionId,
                ChatMessage(
                    id = clientMsgId,
                    role = ChatRole.USER,
                    text = text,
                    createdAtMillis = now,
                ),
            )
        }

        val bodyBuilder = FormBody.Builder()
            .add("sessionId", requestSessionId.toString())
        if (regenerate) {
            bodyBuilder.add("regenerate", "true")
        } else {
            bodyBuilder.add("id", clientMsgId)
            bodyBuilder.add("text", text.orEmpty())
        }
        if (characterId != null && characterId > 0) {
            bodyBuilder.add("characterId", characterId.toString())
        }
        if (!roleId.isNullOrBlank()) {
            bodyBuilder.add("roleId", roleId)
        }

        val httpRequest = Request.Builder()
            .url(Constants.getUrl(Constants.CHAT_STREAM_URL))
            .headers(authHeaders())
            .post(bodyBuilder.build())
            .build()

        val call = streamHttpClient.newCall(httpRequest)
        val fullText = StringBuilder()

        fun adoptSessionId(id: Long) {
            if (requestSessionId > 0 || id <= 0 || id == activeSessionId) return
            activeSessionId = id
            sessions.setCurrentSessionId(id)
        }

        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                LogUtil.e(e)
                trySend(ChatStreamEvent.Error(e.message ?: "网络请求失败"))
                close()
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        trySend(ChatStreamEvent.Error("HTTP ${resp.code}"))
                        close()
                        return
                    }
                    val source = resp.body?.source()
                    if (source == null) {
                        trySend(ChatStreamEvent.Error("空响应"))
                        close()
                        return
                    }
                    try {
                        while (!call.isCanceled()) {
                            val line = source.readUtf8Line() ?: break
                            // 服务端出错时返回的是普通 JSON（{"errno":10000,"error":"..."}），不是 data: 帧；
                            // 不能当成空流收尾，要把错误原文交给界面
                            if (line.startsWith("{")) {
                                val body = runCatching { JSONObject(line) }.getOrNull()
                                if (body != null && body.optInt("errno", 0) != 0) {
                                    trySend(ChatStreamEvent.Error(body.optString("error", "").ifBlank { "服务器错误 ${body.optInt("errno")}" }))
                                    close()
                                    return
                                }
                                // 错误帧：{"error":"...","done":true,"userMessageId":N,"assistantMessageId":0}
                                val frameError = body?.optString("error", "").orEmpty()
                                if (frameError.isNotBlank()) {
                                    val errUserId = body!!.optLong("userMessageId", 0L).takeIf { it > 0 }?.toString()
                                    trySend(ChatStreamEvent.Error(frameError, errUserId))
                                    close()
                                    return
                                }
                            }
                            if (!line.startsWith("data:")) continue
                            val payload = line.removePrefix("data:").trim()
                            if (payload.isEmpty()) continue
                            val chunk = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                            adoptSessionId(chunk.optLong("sessionId", 0L))
                            val delta = chunk.optString("delta", "")
                            val done = chunk.optBoolean("done", false)
                            val error = chunk.optString("error", null)
                            if (error != null) {
                                val errUserId = chunk.optLong("userMessageId", 0L).takeIf { it > 0 }?.toString()
                                trySend(ChatStreamEvent.Error(error, errUserId))
                                close()
                                return
                            }
                            if (delta.isNotEmpty()) {
                                fullText.append(delta)
                                trySend(ChatStreamEvent.Delta(assistantId, delta))
                            }
                            if (done) {
                                val serverUserId = chunk.optLong("userMessageId", 0L)
                                val serverAssistantId = chunk.optLong("assistantMessageId", 0L)
                                cache.cacheTurn(
                                    sessionId = activeSessionId,
                                    clientUserId = clientMsgId,
                                    userText = if (!regenerate && (persistUserMessage || clientMessageId != null)) text else null,
                                    serverUserId = serverUserId,
                                    reply = fullText.toString(),
                                    serverAssistantId = serverAssistantId,
                                    localAssistantId = assistantId,
                                )
                                trySend(
                                    ChatStreamEvent.Done(
                                        assistantId = serverAssistantId.takeIf { it > 0 }?.toString(),
                                        userId = serverUserId.takeIf { it > 0 }?.toString(),
                                        fullText = fullText.toString(),
                                    ),
                                )
                                close()
                                return
                            }
                        }
                        // 流结束但没收到 done=true 帧（比如连接被提前挂断），按已收到的内容收尾，
                        // 而不是整段丢弃——用户至少能看到已经生成的部分。
                        if (!call.isCanceled() && fullText.isEmpty()) {
                            // 一个字都没收到就结束了：这是失败，不是「空回复」
                            trySend(ChatStreamEvent.Error("服务器没有返回内容，请稍后重试"))
                        } else if (!call.isCanceled()) {
                            // 没有 done 帧，服务端 id 未知：本地按临时 id 缓存，ViewModel 会兜底拉取历史
                            cache.cacheTurn(
                                sessionId = activeSessionId,
                                clientUserId = clientMsgId,
                                userText = null,
                                serverUserId = 0L,
                                reply = fullText.toString(),
                                serverAssistantId = 0L,
                                localAssistantId = assistantId,
                            )
                            trySend(ChatStreamEvent.Done(assistantId = null, userId = null, fullText = fullText.toString()))
                        }
                    } catch (e: Exception) {
                        LogUtil.e(e)
                        trySend(ChatStreamEvent.Error(e.message ?: "流式读取失败"))
                    } finally {
                        close()
                    }
                }
            }
        })

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    /**
     * 复用 [BaseRequest] 构造函数里的 UA 签名/devid/Token 组装逻辑，拿一份跟其它接口
     * 一致的 header——这里只是借它算 header，从不把这个 Request 交给网络层执行。
     */
    private fun authHeaders(): okhttp3.Headers {
        val probe = BaseRequest<Any>(context, ApiRequest.Method.POST, Constants.getUrl(Constants.CHAT_STREAM_URL), null, null, null)
        val builder = okhttp3.Headers.Builder()
        probe.headers.forEach { (k, v) -> builder.add(k, v) }
        return builder.build()
    }
}
