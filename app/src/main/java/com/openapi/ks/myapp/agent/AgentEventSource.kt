package com.openapi.ks.myapp.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AgentHttpException(val statusCode: Int, message: String) : Exception(message)

/**
 * 会话事件流的一次连接（D §7.3）。收集即连接，取消即断开，不做重连（那是 [AgentEventReconnector] 的事）。
 *
 * 用 OkHttp 直接按行读取，不引入 EventSource 库。读取是阻塞的，所以在 IO 线程上跑，
 * 并且用 invokeOnCompletion 在协程取消时关闭底层连接，保证断开是及时的。
 */
class AgentEventSource(
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
    private val client: OkHttpClient,
) {
    fun open(sessionId: String, lastEventId: String?): Flow<SseFrame> = flow {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: throw AgentHttpException(401, "未登录")
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/sessions/$sessionId/events")
            .header("Authorization", "Bearer $token")
            .header("Accept", "text/event-stream")
        if (!lastEventId.isNullOrEmpty()) builder.header("Last-Event-ID", lastEventId)
        val call = client.newCall(builder.build())
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }

        call.execute().use { response ->
            if (!response.isSuccessful) throw AgentHttpException(response.code, "事件流连接失败（${response.code}）")
            val source = response.body?.source() ?: throw AgentHttpException(-1, "事件流为空")
            val parser = SseFrameParser()
            while (true) {
                val line = source.readUtf8Line() ?: break
                parser.feed(line)?.let { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        /** 事件流的读超时要大于心跳间隔（15 秒），否则空闲会话会被误判为断开。 */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
