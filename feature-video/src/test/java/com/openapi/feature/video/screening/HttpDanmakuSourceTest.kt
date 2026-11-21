package com.openapi.feature.video.screening

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HttpDanmakuSource] 的 JVM 单测：注入假 [HttpDanmakuSource.DanmakuTransport]，不发真请求。
 * 覆盖分窗拼接、游标分页、幂等重发、服务端报错时状态回退、seek 期间在途回包作废、取消传播。
 */
class HttpDanmakuSourceTest {

    // ---------- 假传输 ----------

    private class FakeTransport(
        /** 非空时每个请求先等它完成，用来制造「在途」。 */
        var gate: CompletableDeferred<Unit>? = null,
        /** true 时请求永不返回，只观察取消。 */
        var hang: Boolean = false,
        var responder: (HttpDanmakuSource.DanmakuRequest, Int) -> HttpDanmakuSource.DanmakuResponse =
            { _, _ -> HttpDanmakuSource.DanmakuResponse(200, OK) },
    ) : HttpDanmakuSource.DanmakuTransport {
        val requests = ArrayList<HttpDanmakuSource.DanmakuRequest>()
        var cancelled = false

        override suspend fun execute(request: HttpDanmakuSource.DanmakuRequest): HttpDanmakuSource.DanmakuResponse {
            gate?.await()
            if (hang) {
                // 永不恢复：只有取消能把它掀起来，正好验证 call.cancel() 那条路
                suspendCancellableCoroutine<Unit> { continuation -> continuation.invokeOnCancellation { cancelled = true } }
            }
            requests.add(request)
            return responder(request, requests.size)
        }
    }

    private fun source(
        transport: HttpDanmakuSource.DanmakuTransport,
        pageSize: Int = 200,
        token: String? = null,
    ) = HttpDanmakuSource(
        TimelineKey.local("demo"),
        transport,
        endpoint = DanmakuEndpoint("https://api.test/timeline/v1"),
        pageSize = pageSize,
        accessToken = { token },
    )

    private fun bodyOf(request: HttpDanmakuSource.DanmakuRequest): String = request.body ?: ""

    private fun clientMessageIdOf(body: String): String =
        Regex("\"clientMessageId\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""

    // ---------- JSON 信封（照项目 ApiResult：errno / data / error）----------

    private companion object {
        const val OK = """{"errno":0,"data":{}}"""

        fun windowJson(vararg comments: String, nextCursor: String? = null): String {
            val cursor = if (nextCursor == null) "null" else "\"$nextCursor\""
            return """{"errno":0,"data":{"comments":[${comments.joinToString(",")}],"nextCursor":$cursor}}"""
        }

        fun commentJson(id: Int, atMs: Long, text: String, author: String = "甲"): String =
            """{"id":$id,"atMs":$atMs,"text":"$text","author":"$author"}"""

        fun postedJson(id: Int, atMs: Long, text: String): String =
            """{"errno":0,"data":{"comment":${commentJson(id, atMs, text, author = "我")}}}"""

        fun errJson(errno: Int, error: String): String = """{"errno":$errno,"error":"$error"}"""
    }

    // ---------- 分窗拉取 + 游标 ----------

    @Test
    fun `拉取按时间轴路径拼 URL，带上窗口、分页和游标参数`() = runBlocking {
        val transport = FakeTransport { _, _ ->
            HttpDanmakuSource.DanmakuResponse(
                200,
                windowJson(commentJson(11, 9000, "第一页"), commentJson(12, 9500, "第二页"), nextCursor = "c7"),
            )
        }
        val window = source(transport, pageSize = 2).loadWindow(8000, 16000, "c7")
        val request = transport.requests.single()
        assertEquals("GET", request.method)
        assertTrue(
            "路径带命名空间+内容+版本",
            request.url.startsWith("https://api.test/timeline/v1/local/demo/v1/comments?"),
        )
        assertTrue(request.url, "startMs=8000" in request.url && "endMs=16000" in request.url)
        assertTrue(request.url, "limit=2" in request.url)
        assertTrue("上一页的游标原样带回：${request.url}", "cursor=c7" in request.url)
        assertEquals(listOf(9000L, 9500L), window.comments.map { it.atMs })
        assertEquals("还有下一页", "c7", window.nextCursor)
    }

    @Test
    fun `服务端没给下一页时游标是 null，空串也不算`() = runBlocking {
        val transport = FakeTransport { _, _ -> HttpDanmakuSource.DanmakuResponse(200, windowJson(commentJson(1, 100, "一条"))) }
        assertNull(source(transport).loadWindow(0, 8000, null).nextCursor)
        val emptyCursor = FakeTransport { _, _ -> HttpDanmakuSource.DanmakuResponse(200, windowJson(nextCursor = "")) }
        assertNull(source(emptyCursor).loadWindow(0, 8000, null).nextCursor)
    }

    @Test
    fun `分窗拼接：当前窗和预取窗的弹幕合并排序`() = runBlocking {
        // 按 startMs 返回对应窗口的内容，模拟服务端
        val transport = FakeTransport { request, _ ->
            val start = Regex("startMs=(\\d+)").find(request.url)!!.groupValues[1].toLong()
            HttpDanmakuSource.DanmakuResponse(
                200,
                windowJson(
                    commentJson(start.toInt() + 1, start + 100, "窗$start"),
                    commentJson(start.toInt() + 2, start + 6000, "尾$start"),
                ),
            )
        }
        val feed = DanmakuFeed(source(transport), windowMs = 8000)
        feed.sync(1000) // 当前窗 [0,8000) + 预取窗 [8000,16000)
        assertEquals(
            "两个窗口的弹幕拼成一条按时间排序的时间轴",
            listOf(100L, 6000L, 8100L, 14000L),
            feed.state.comments.map { it.atMs },
        )
    }

    // ---------- 发送 / 撤回 ----------

    @Test
    fun `post 发 JSON 请求体、带鉴权头，并拿回服务端定稿`() = runBlocking {
        val transport = FakeTransport { _, _ -> HttpDanmakuSource.DanmakuResponse(200, postedJson(4001, 1234, "上线了")) }
        val saved = source(transport, token = "t0k").post("cm-1", 1234, "  上线了  ").getOrThrow()
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("Bearer 鉴权", "Bearer t0k", request.headers["Authorization"])
        assertTrue(bodyOf(request), bodyOf(request).contains("\"clientMessageId\":\"cm-1\""))
        assertTrue(bodyOf(request), bodyOf(request).contains("\"atMs\":1234"))
        assertEquals(4001, saved.id)
        assertEquals("服务端定稿会去掉首尾空格", "上线了", saved.text)
        assertEquals("我", saved.author)
    }

    @Test
    fun `空文本不发请求直接失败`() = runBlocking {
        val transport = FakeTransport()
        assertTrue(source(transport).post("cm-1", 0, "   ").isFailure)
        assertTrue("先做本地校验，不浪费一次往返", transport.requests.isEmpty())
    }

    @Test
    fun `撤回打 DELETE，权限由服务端判`() = runBlocking {
        val transport = FakeTransport { _, _ -> HttpDanmakuSource.DanmakuResponse(200, OK) }
        assertTrue(source(transport).retract(4001).isSuccess)
        val request = transport.requests.single()
        assertEquals("DELETE", request.method)
        assertTrue(request.url, request.url.endsWith("/timeline/v1/local/demo/v1/comments/4001"))
    }

    // ---------- 失败语义 ----------

    @Test
    fun `HTTP 非 2xx：拉取抛错、发送返回失败，原因都带状态码`() = runBlocking {
        val transport = FakeTransport { _, _ -> HttpDanmakuSource.DanmakuResponse(500, "boom") }
        val http = source(transport)
        val posted = http.post("cm-1", 0, "偏了")
        assertTrue(posted.isFailure)
        assertTrue("500" in (posted.exceptionOrNull()?.message ?: ""))
        val thrown = runCatching { http.loadWindow(0, 8000, null) }.exceptionOrNull()
        assertTrue("拉取的错误要能被 DanmakuFeed 接住显示", thrown is DanmakuHttpException)
    }

    @Test
    fun `errno 非 0 时把业务原因原样带出`() = runBlocking {
        val transport = FakeTransport { _, _ -> HttpDanmakuSource.DanmakuResponse(200, errJson(4001, "最多 40 个字")) }
        val failure = source(transport).post("cm-1", 0, "字太多").exceptionOrNull()!!
        assertTrue(failure is DanmakuServerException)
        assertEquals("最多 40 个字", failure.message)
    }

    @Test
    fun `返回不是合法 JSON 时报协议错误而不是崩溃`() = runBlocking {
        val transport = FakeTransport { _, _ -> HttpDanmakuSource.DanmakuResponse(200, "not json") }
        assertTrue(source(transport).post("cm-1", 0, "正常").exceptionOrNull() is DanmakuProtocolException)
    }

    // ---------- Feed 状态机联动 ----------

    @Test
    fun `发送失败回退到可重试，重试复用同一个 clientMessageId`() = runBlocking {
        val transport = FakeTransport { _, attempt ->
            if (attempt == 1) HttpDanmakuSource.DanmakuResponse(502, "bad gateway")
            else HttpDanmakuSource.DanmakuResponse(200, postedJson(4002, 2000, "重试我"))
        }
        val feed = DanmakuFeed(source(transport, token = "t0k"), windowMs = 8000)
        val first = feed.send(2000, "重试我")
        assertEquals(SendState.Failed, first.state)
        assertTrue("失败原因显示到面板", "502" in (first.error ?: ""))

        val retried = feed.retry(first.clientMessageId)!!
        assertEquals(SendState.Sent, retried.state)
        assertEquals(4002, retried.serverId)
        val posts = transport.requests.filter { it.method == "POST" }
        assertEquals(2, posts.size)
        assertEquals(
            "两次请求共用一个幂等 ID，服务端据此去重",
            clientMessageIdOf(bodyOf(posts[0])),
            clientMessageIdOf(bodyOf(posts[1])),
        )
    }

    @Test
    fun `seek 期间在途回包回来也不能贴上时间轴`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val transport = FakeTransport(
            gate = gate,
            responder = { request, _ ->
                val start = Regex("startMs=(\\d+)").find(request.url)!!.groupValues[1].toLong()
                HttpDanmakuSource.DanmakuResponse(200, windowJson(commentJson(start.toInt(), start, "窗$start")))
            },
        )
        val feed = DanmakuFeed(source(transport), windowMs = 8000)
        kotlinx.coroutines.coroutineScope {
            val loading = launch { feed.sync(1000) }
            delay(10) // 请求挂在 gate 上
            feed.seek(50_000)
            gate.complete(Unit) // 旧代回包这时才回来
            loading.join()
        }
        assertTrue("旧代回包被丢弃", feed.state.comments.isEmpty())
    }

    @Test
    fun `取消协程时请求被取消，不会误报成发送失败`() = runBlocking {
        val transport = FakeTransport(hang = true)
        val http = source(transport)
        var caught: Throwable? = null
        val job = launch {
            try {
                http.post("cm-1", 100, "取消我")
            } catch (cancellation: CancellationException) {
                caught = cancellation
                throw cancellation
            }
        }
        delay(10)
        job.cancelAndJoin()
        assertTrue("取消原样上抛，不吞成 Result.failure，实际 ${caught?.let { it::class.simpleName }}", caught is CancellationException)
        assertTrue("在途请求被真的取消", transport.cancelled)
    }
}
