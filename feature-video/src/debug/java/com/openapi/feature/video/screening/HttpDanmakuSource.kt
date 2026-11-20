package com.openapi.feature.video.screening

import com.google.gson.Gson
import com.openapi.core.config.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [TimedCommentSource] 的真 HTTP 实现：分窗拉取（带游标分页）、`clientMessageId` 幂等发送、撤回。
 * 接口形状和错误约定照 `docs/design/media-suite.md`「通用弹幕」一节，
 * [DanmakuFeed] 与渲染完全不知道来源换成了网络。
 *
 * 传输抽成 [DanmakuTransport]：真机走 OkHttp，JVM 单测注入假 dispatcher，不发真请求。
 * base url 和路径前缀都可配置，默认挂到项目现有的 api host（`Constants.getUrl`）。
 *
 * 失败语义对齐 [DanmakuFeed] 的状态机：
 * - [loadWindow] 直接抛，Feed 的 runCatching 会把它变成面板上的错误行；
 * - [post] / [retract] 返回 `Result.failure(异常)`，Feed 据此回退到 Failed（可重试）；
 * - 协程取消（换来源、退出页面）原样抛 [CancellationException]，绝不吞成「发送失败」。
 */
internal class HttpDanmakuSource(
    override val timelineKey: TimelineKey,
    private val transport: DanmakuTransport,
    /** 默认挂到项目现有 api host；换环境传自己的 base（不带结尾斜杠）。 */
    private val endpoint: DanmakuEndpoint = DanmakuEndpoint(),
    /** 一页多少条；服务端可以只认自己的上限并回更小的一页，游标语义由服务端定。 */
    private val pageSize: Int = 200,
    /** 鉴权头取值；null 表示未登录，不带 token 直接请求，由服务端决定回什么。 */
    private val accessToken: () -> String? = { null },
    private val gson: Gson = Gson(),
) : TimedCommentSource {

    override suspend fun loadWindow(startMs: Long, endMs: Long, cursor: String?): CommentWindow {
        val url = timelinePath().toHttpUrl().newBuilder()
            .addQueryParameter("startMs", startMs.toString())
            .addQueryParameter("endMs", endMs.toString())
            .addQueryParameter("limit", pageSize.toString())
        cursor?.let { url.addQueryParameter("cursor", it) }
        val data = request("GET", url.build().toString(), body = null)
        return CommentWindow(
            startMs,
            endMs,
            data.comments.map { it.toDomain() },
            nextCursor = data.nextCursor?.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * 发一条弹幕。重试由 [DanmakuFeed] 带着同一个 [clientMessageId] 再进来，
     * 服务端据此去重并回同一条定稿（含正式 id），所以这里不做任何本地缓存。
     */
    override suspend fun post(clientMessageId: String, atMs: Long, text: String): Result<TimedComment> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("弹幕不能为空"))
        val body = gson.toJson(PostBody(clientMessageId, atMs, trimmed))
        // 整段都包进 runCatchingTransport：约定 post 的失败一律走 Result.failure，
        // 只有取消例外（DanmakuFeed.finish 直接在调用栈上等这条 suspend 链）
        return runCatchingTransport {
            request("POST", timelinePath(), body).comment?.toDomain()
                ?: throw DanmakuProtocolException("发送成功但没有回弹幕内容")
        }
    }

    override suspend fun retract(serverId: Int): Result<Unit> =
        runCatchingTransport { request("DELETE", "${timelinePath()}/$serverId", body = null) }.map { }

    /** 把传输和业务错误包成 Result；取消不参与打包，原样上抛。 */
    private suspend fun <T> runCatchingTransport(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            Result.failure(t)
        }

    /** 一次请求 + 信封拆解：HTTP 非 2xx 或 errno != 0 都抛异常，不吞。 */
    private suspend fun request(method: String, url: String, body: String?): EnvelopeData {
        val response = transport.execute(
            DanmakuRequest(
                method = method,
                url = url,
                body = body,
                headers = buildMap {
                    put("Accept", "application/json")
                    if (body != null) put("Content-Type", "application/json")
                    accessToken()?.takeIf { it.isNotBlank() }?.let { put("Authorization", "Bearer $it") }
                },
            ),
        )
        if (response.status !in 200..299) {
            throw DanmakuHttpException("服务返回 HTTP ${response.status}")
        }
        val envelope = try {
            // gson 的报错类型散在 JsonParseException 和 IOException 两支（MalformedJsonException 在后者），
            // 这里解析的是同步调用，不会抛取消，一律收成协议错误最省事
            gson.fromJson(response.body, Envelope::class.java)
        } catch (t: Exception) {
            throw DanmakuProtocolException("返回不是合法的 JSON")
        }
        if (envelope == null) throw DanmakuProtocolException("返回为空")
        if (envelope.errno != 0) {
            throw DanmakuServerException(envelope.error?.takeIf { it.isNotBlank() } ?: "弹幕服务返回错误 ${envelope.errno}")
        }
        return envelope.data ?: throw DanmakuProtocolException("返回缺少 data")
    }

    /** `…/{namespace}/{contentId}/{editionId}/comments`，时间轴的每一段命名空间互不串。 */
    private fun timelinePath(): String =
        "${endpoint.baseUrl.trimEnd('/')}/${timelineKey.namespace}/${timelineKey.contentId}/${timelineKey.editionId}/comments"

    private fun CommentDto.toDomain() = TimedComment(id, atMs, text, author = author ?: "")

    // ---- 传输层 ----

    /** 一次待发的请求。[url] 已经拼好查询串，测试直接断言它。 */
    internal data class DanmakuRequest(
        val method: String,
        val url: String,
        val body: String?,
        val headers: Map<String, String> = emptyMap(),
    )

    internal data class DanmakuResponse(val status: Int, val body: String)

    /** 传输注入点：真机用 [OkHttpDanmakuTransport]，测试用假 dispatcher。 */
    internal interface DanmakuTransport {
        /** 挂起直到有结果；协程取消时必须取消在途请求并把 [CancellationException] 抛回来。 */
        suspend fun execute(request: DanmakuRequest): DanmakuResponse
    }

    // ---- JSON 形状（信封照项目现有 ApiResult：errno / data / error）----
    // 字段名即 JSON 键名，所以不用 @SerializedName（Kotlin 里它会落到构造参数上，gson 读不到）。

    private class Envelope {
        @JvmField var errno: Int = 0
        @JvmField var data: EnvelopeData? = null
        @JvmField var error: String? = null
    }

    private class EnvelopeData {
        @JvmField var comments: List<CommentDto> = emptyList()
        @JvmField var nextCursor: String? = null
        @JvmField var comment: CommentDto? = null
    }

    private class CommentDto {
        @JvmField var id: Int = 0
        @JvmField var atMs: Long = 0
        @JvmField var text: String = ""
        @JvmField var author: String? = null
    }

    private class PostBody(
        @JvmField var clientMessageId: String,
        @JvmField var atMs: Long,
        @JvmField var text: String,
    )
}

/** base url 与路径前缀：默认项目 api host，换环境只改这里。 */
internal data class DanmakuEndpoint(val baseUrl: String = Constants.getUrl("/timeline/v1"))

/** 网络不可达 / 超时 / HTTP 非 2xx。 */
internal class DanmakuHttpException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** 返回不是预期结构。 */
internal class DanmakuProtocolException(message: String) : IOException(message)

/** HTTP 2xx 但 errno != 0：服务端业务拒绝（超长、限频、无权撤回…）。 */
internal class DanmakuServerException(message: String) : IOException(message)

/**
 * 真机传输：OkHttp。挂起用 enqueue，调用方协程一取消就 `call.cancel()`，
 * 请求不会继续占连接，也不会把取消误报成网络错误。
 */
internal class OkHttpDanmakuTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
) : HttpDanmakuSource.DanmakuTransport {

    override suspend fun execute(request: HttpDanmakuSource.DanmakuRequest): HttpDanmakuSource.DanmakuResponse =
        withContext(Dispatchers.IO) {
            val call = client.newCall(
                okhttp3.Request.Builder()
                    .url(request.url)
                    .apply {
                        request.headers.forEach { (name, value) -> header(name, value) }
                        when (request.method) {
                            "GET" -> get()
                            "POST" -> post(request.body.orEmpty().toRequestBody(JSON))
                            "DELETE" -> delete(request.body?.toRequestBody(JSON))
                            else -> method(request.method, request.body?.toRequestBody(JSON))
                        }
                    }
                    .build(),
            )
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            // 调用方已取消时 resumeWithException 会被忽略，不会双重恢复
                            continuation.resumeWithException(
                                if (call.isCanceled()) CancellationException("请求已取消") else DanmakuHttpException("网络不可用", e),
                            )
                        }

                        override fun onResponse(call: Call, response: Response) {
                            try {
                                response.use {
                                    continuation.resume(HttpDanmakuSource.DanmakuResponse(it.code, it.body?.string() ?: ""))
                                }
                            } catch (t: Throwable) {
                                continuation.resumeWithException(t)
                            }
                        }
                    },
                )
            }
        }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
