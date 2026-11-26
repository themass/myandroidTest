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
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
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
 *
 * 这个假服务端有三条不能破的规矩（每一条都对应一次实测到的假故障，改动前先读完）：
 * 1. **终止块必须裸写**，不能被 [chunk] 再包一层（见 [writeResponse]）；
 * 2. **掐断用 close（FIN），不用 `setSoLinger(true, 0)`（RST）**（见 [writeResponse]）；
 * 3. **每条到达的连接都必须有交代**：要么按脚本应答，要么立刻断开（见 accept 线程）——
 *    [AgentChatRemote] 的事件流 `readTimeout=0`，一条没人应答的连接会让读永挂，
 *    测试就只能等 `runTest` 的 60s 墙钟超时（`UncompletedCoroutinesError`），
 *    把「脚本没安排好」这种测试 bug 伪装成「代码挂死」。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AgentSseReconnectIntegrationTest {

    /** 一条脚本：给某条连接写什么、写到第几帧掐断。 */
    private class Script(val frames: List<String>, val truncateAt: Int?) {
        /** accept 线程写完（或写失败）后放行；测试体靠它确认「这条连接真的已被应答」。 */
        val done = CountDownLatch(1)
        var failure: Throwable? = null
    }

    private class SseTestServer : AutoCloseable {
        private val server = ServerSocket(0)
        val port: Int = server.localPort
        /** 每个到达的请求：(请求行, Last-Event-ID 头或 null)。 */
        private val requests = LinkedBlockingQueue<Pair<String, String?>>()
        /** 脚本队列：第 N 条连接取第 N 条脚本；取不到就把连接直接断开（见类注释规矩 3）。 */
        private val scripts = LinkedBlockingQueue<Script>()
        /** 脚本里没安排、被直接断开的连接数——两个测试都断言它必须是 0。 */
        var unexpectedConnections = 0
            private set

        init {
            Thread {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: return@Thread
                    requests.put(readRequest(socket))
                    val script = scripts.poll(10, TimeUnit.SECONDS)
                    if (script == null) {
                        // 脚本没安排到这条连接：立刻断开，绝不留着没人应答的连接（规矩 3）。
                        unexpectedConnections++
                        runCatching { socket.close() }
                        continue
                    }
                    try {
                        writeResponse(socket, script)
                    } catch (e: Throwable) {
                        script.failure = e
                    } finally {
                        script.done.countDown()
                    }
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

        private fun chunk(out: BufferedOutputStream, s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            out.write("${bytes.size.toString(16)}\r\n".toByteArray(Charsets.US_ASCII))
            out.write(bytes)
            out.write("\r\n".toByteArray(Charsets.US_ASCII))
            out.flush()
        }

        /**
         * 应答一条连接：先写 SSE 响应头，然后逐帧写 chunked 块。
         * [Script.truncateAt] 为 null 时写终止块正常收尾；否则写完第 truncateAt 帧直接 close
         * （不写 `0` 终止块——OkHttp 报 unexpected end of stream，正是真实掉线的形状）。
         *
         * 终止块**必须裸写**：`0\r\n\r\n` 本身就是 chunked 框架的一部分，再用 [chunk] 包一层，
         * 线上就成了「一个 5 字节的数据块」——OkHttp 等不到真终止块，只能报 unexpected end of
         * stream（实测必抛），于是被脚本成「正常收尾」的那条流其实是假收尾，白惹一次重连。
         *
         * 掉线用裸 close（FIN），**不用** `setSoLinger(true, 0)`（RST）：RST 会让内核丢掉
         * 「已经 write 但对端还没取走」的帧，客户端可能一帧都没见到、重连时带不上
         * Last-Event-ID——这里测的是「掉线续传」，不能把「帧根本没发出去」混进来。
         */
        private fun writeResponse(socket: Socket, script: Script) {
            val out = BufferedOutputStream(socket.getOutputStream())
            // 状态行和响应头必须裸写：它们不属于 chunked 框架，写进 chunk 里 OkHttp 会当无效协议
            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\n\r\n".toByteArray(Charsets.US_ASCII))
            out.flush()
            script.frames.forEachIndexed { i, f ->
                chunk(out, f)
                if (script.truncateAt != null && i == script.truncateAt - 1) {
                    runCatching { socket.close() }
                    return
                }
            }
            out.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
            out.flush()
            runCatching { socket.close() }
        }

        /**
         * 排一条脚本并等它真的被写到连接上——所以它返回时，对应的请求已经入队，
         * 后面的 [headers] 一定能看到这一条（含重连那一条）。
         * 挂到真实 IO 线程上：accept/写都是阻塞调用，不能占用测试的虚拟调度线程。
         */
        suspend fun respond(frames: List<String>, truncateAt: Int? = null) {
            val script = Script(frames, truncateAt)
            withContext(Dispatchers.IO) {
                scripts.put(script)
                if (!script.done.await(10, TimeUnit.SECONDS)) throw IOException("脚本没被应答（连接没到达？）")
                script.failure?.let { throw it }
            }
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
            // respond 会阻塞到重连请求真的到达并被应答，所以它返回后两条请求都已入队。
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
            assertEquals("脚本只安排了 2 条连接", 0, server.unexpectedConnections)
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
                collector.collect({ rec.events += it }, null, "a1", null, { rec.persisted += it })
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
            // 这一条钉住「正常收尾」是真的收尾：终止块写对了，客户端就不会白重连一次。
            // 终止块一旦被 chunk() 包回去，这里会多出第 2 条连接，本行立刻失败——
            // 而不是让没人应答的那条读永挂、由 runTest 的 60s 超时来报信。
            assertEquals("正常收尾不该引发重连", 0, server.unexpectedConnections)
        }
    }
}
