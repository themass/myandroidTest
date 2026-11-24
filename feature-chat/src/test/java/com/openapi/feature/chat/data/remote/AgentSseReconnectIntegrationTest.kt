package com.openapi.feature.chat.data.remote

import androidx.test.core.app.ApplicationProvider
import com.openapi.feature.chat.domain.model.ChatStreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * [TurnEventCollectorTest] 里 `open` 是注入的假流——那只证明了重试策略。
 * 这里走真实的 [AgentChatRemote.events]：真 socket、真 SSE 解析、真 HTTP 请求。
 * 守住单元层测不到的三件事：
 * 1. 服务端 `id:` 行确实被解析进 [AgentSseFrame.id]（断点 id 的来源）；
 * 2. 重连请求真的带上了 `Last-Event-ID` 头（没这个头，服务端只能从头补发）；
 * 3. 服务端中途掐 socket 时 `events()` 抛异常而不是无声结束（重试环的触发条件）。
 *
 * 不用 MockWebServer：它不在离线依赖缓存里，而这里只需要一个会说 chunked SSE 的
 * 最简 HTTP 应答——用 ServerSocket 手写，断线就是 socket 直接 close（不写终止块）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AgentSseReconnectIntegrationTest {

    private class SseTestServer : AutoCloseable {
        private val server = ServerSocket(0)
        val port: Int = server.localPort
        /** 每个到达的请求：(请求行, Last-Event-ID 头或 null)。 */
        private val requests = LinkedBlockingQueue<Pair<String, String?>>()
        private val sockets = LinkedBlockingQueue<Socket>()

        init {
            Thread {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: return@Thread
                    requests.put(readRequest(socket))
                    sockets.put(socket)
                }
            }.apply { isDaemon = true }.start()
        }

        private fun readRequest(socket: Socket): Pair<String, String?> {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val getLine = reader.readLine() ?: return "" to null
            var lastEventId: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Last-Event-ID:", ignoreCase = true)) {
                    lastEventId = line.substringAfter(':').trim()
                }
            }
            return getLine to lastEventId
        }

        /**
         * 应答排队中的下一个请求：先写 SSE 响应头，然后逐帧写 chunked 块。
         * [truncateAt] 为 null 时写终止块正常收尾；否则写完第 truncateAt 帧直接掐 socket
         * （不写 `0` 终止块——OkHttp 报 unexpected end of stream，正是真实掉线的形状）。
         */
        suspend fun respond(frames: List<String>, truncateAt: Int? = null) = withContext(Dispatchers.IO) {
            val socket = sockets.poll(10, TimeUnit.SECONDS) ?: error("no request arrived")
            val out = BufferedOutputStream(socket.getOutputStream())
            fun chunk(s: String) {
                val bytes = s.toByteArray(Charsets.UTF_8)
                out.write("${bytes.size.toString(16)}\r\n".toByteArray(Charsets.US_ASCII))
                out.write(bytes)
                out.write("\r\n".toByteArray(Charsets.US_ASCII))
                out.flush()
            }
            // 状态行和响应头必须裸写：它们不属于 chunked 框架，写进 chunk 里 OkHttp 会当无效协议
            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\n\r\n".toByteArray(Charsets.US_ASCII))
            out.flush()
            frames.forEachIndexed { i, f ->
                chunk(f)
                if (truncateAt != null && i == truncateAt - 1) {
                    runCatching { socket.setSoLinger(true, 0); socket.close() }
                    return@withContext
                }
            }
            chunk("0\r\n\r\n")
            runCatching { socket.close() }
        }

        /** 取到目前为止全部请求的 Last-Event-ID（按到达顺序）。 */
        fun headers(): List<String?> = List(requests.size) { requests.poll(10, TimeUnit.SECONDS)?.second }

        override fun close() { runCatching { server.close() } }
    }

    private fun sse(id: String?, event: String, data: String): String = buildString {
        if (id != null) append("id: $id\n")
        append("event: $event\ndata: $data\n\n")
    }

    private fun remoteFor(server: SseTestServer) = AgentChatRemote(
        context = ApplicationProvider.getApplicationContext(),
        baseUrl = "http://127.0.0.1:${server.port}",
        tokenProvider = { "test-token" },
    )

    private class Recorded {
        val events = mutableListOf<ChatStreamEvent>()
        val persisted = mutableListOf<String>()
        fun deltas() = events.filterIsInstance<ChatStreamEvent.Delta>().map { it.text }
    }

    @Test
    fun reconnectCarriesLastEventIdOverARealSocket() = runTest {
        SseTestServer().use { server ->
            val remote = remoteFor(server)
            val collector = TurnEventCollector(
                open = { lastEventId -> remote.events(1L, lastEventId) },
                parse = ::parseAgentEvent,
                // 看门狗关掉：这两条测的是「真实 socket 上的重连环」，runTest 的虚拟时钟
                // 会在 server.respond 阻塞真实 IO 时飞速越过默认 45s，把还在收帧的 pump
                // 误杀。帧间看门狗本身由 TurnWatchdogTest 覆盖。
                frameTimeoutMs = 0,
            )
            val rec = Recorded()
            val job = backgroundScope.launch {
                collector.collect(
                    emit = { rec.events += it },
                    expectedTurnId = null,
                    assistantLocalId = "a1",
                    userMessageId = null,
                    persist = { rec.persisted += it },
                )
            }

            // 第 1 条连接：两帧后掐断
            val first = listOf(
                sse("1-0", "message.delta", """{"turn_id":"t1","delta":"你好"}"""),
                sse("1-1", "message.delta", """{"turn_id":"t1","delta":"，世界"}"""),
            )
            server.respond(first, 2)
            // 第 2 条连接：snapshot 全文（含已有部分）+ done。
            // respond 会阻塞到重连请求真的到达，所以它返回后两条请求都已入队。
            val second = listOf(
                sse(null, "turn.snapshot", """{"turn_id":"t1","text":"你好，世界！"}"""),
                sse("2-0", "message.done", """{"turn_id":"t1","assistant_message_id":9}"""),
            )
            server.respond(second)
            // 断点 id 来自服务端 id: 行，经 SSE 解析进 frame.id，重连时原样带回
            val headers = server.headers()
            assertNull("首次订阅不该带断点头", headers[0])
            assertEquals("重连必须带断点 id", "1-1", headers[1])
            job.join()

            assertEquals("断线前后不能重复出字", listOf("你好", "，世界", "！"), rec.deltas())
            val d = rec.events.last() as ChatStreamEvent.Done
            assertEquals("9", d.assistantId)
            assertEquals("你好，世界！", d.fullText)
            assertEquals(listOf("你好，世界！"), rec.persisted)
        }
    }

    @Test
    fun sseFrameIdsAndEventFieldsSurviveRealParsing() = runTest {
        SseTestServer().use { server ->
            val remote = remoteFor(server)
            val collector = TurnEventCollector(
                open = { lastEventId -> remote.events(1L, lastEventId) },
                parse = ::parseAgentEvent,
                // 同上：真实 socket + runTest 虚拟时钟下必须关掉帧间看门狗
                frameTimeoutMs = 0,
            )
            val rec = Recorded()
            val job = backgroundScope.launch {
                collector.collect({ rec.events += it }, null, "a1", null) { rec.persisted += it }
            }
            val frames = listOf(
                ": keepalive\n\n", // 心跳注释不该产生帧
                sse("9-0", "message.delta", """{"turn_id":"t1","delta":"独白"}"""),
                sse(null, "message.done", """{"turn_id":"t1","assistant_message_id":7}"""),
            )
            server.respond(frames)
            job.join()
            assertEquals(listOf("独白"), rec.deltas())
            assertTrue(rec.events.last() is ChatStreamEvent.Done)
            assertEquals(listOf("独白"), rec.persisted)
            val headers = server.headers() // 注意：会取走队列内容，只调一次
            assertEquals("只订阅过一次", 1, headers.size)
            assertNull(headers.single())
        }
    }
}
