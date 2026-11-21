package com.openapi.feature.chat.data.remote

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * `AgentChatRemote.upload` 委托给 [com.openapi.common.media.MediaUploader] 之后，**错误语义必须与委托前逐条一致**（Task 2 Step 5 的回归面）。
 *
 * 被测的是 `AgentChatRemote.upload` → `MediaUploader.uploadBlocking` 这一段委托与它下面的 `toFailure()`。
 *
 * 为什么测到这里而不是直接测 `toFailure()`：转换只有被 `upload()` 的 catch 接住才成立，
 * 而「网络异常原样外抛、不被 catch 吞掉」这一条只能从 `upload()` 这一层看到——
 * 直接单测 `toFailure()` 测不到它，也测不到 `upload()` 真的委托了。所以这里走真实入口：
 * 真 HTTP、真 OkHttp、真 [AgentChatRemote]，只是服务端是本地桩。
 *
 * 每条断言都对着一种具体的漂移写法（评审点名的四条）：
 * 1. `serverBody?.let { failure(status, it) }` 若被改成**真值判断**（`isNotEmpty()` / `isNullOrBlank()`），
 *    空 body 的非 2xx 就掉不出 `/v1` 话术 → [emptyBodyNon2xxStillGoesThroughTheStatusTalk] 变红；
 * 2. 转换若**丢了 status** → 所有用例的 status 断言变红；
 * 3. 本地判定（未登录 / 坏 JSON / 缺 upload_id）若被**一并路由进 `failure()`**，
 *    会多一条 warn 日志、且 message 被状态码话术替换掉（「未登录」→「登录已过期，请重新登录」）
 *    → 那三条用例同时钉住 message 与「没有 /v1 失败日志」；
 * 4. 若把 catch 放宽成 `catch (e: Exception)`，[networkFailurePropagatesAsIOException] 变红。
 *
 * 不用 MockWebServer：它不在 :feature-chat 的离线依赖里，这里只需要一个「按队列吐状态码和 body」的
 * 最简应答，跟 [AgentSseReconnectIntegrationTest] 一样用 ServerSocket 手写。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AgentChatRemoteUploadTest {

    private lateinit var server: HttpStub
    private lateinit var tempFile: File

    @Before
    fun setUp() {
        ShadowLog.clear()
        server = HttpStub()
        tempFile = File.createTempFile("upload_", ".jpg").apply { writeBytes(byteArrayOf(9, 8, 7)) }
    }

    @After
    fun tearDown() {
        server.close()
        tempFile.delete()
    }

    private fun remote(token: String? = "t-1") = AgentChatRemote(
        context = ApplicationProvider.getApplicationContext(),
        baseUrl = "http://127.0.0.1:${server.port}",
        tokenProvider = { token },
    )

    /** `failure()` 翻译时打的那条日志（它用的是单参 LogUtil.w，tag 是 LogUtil 自己的，所以只认正文）。 */
    private fun translateLogs() = ShadowLog.getLogs().filter { it.msg.contains("/v1 失败") }

    private fun uploadFailure(token: String? = "t-1"): AgentChatRemote.Failure =
        assertThrows(AgentChatRemote.Failure::class.java) { remote(token).upload(tempFile, "image/jpeg") }

    // ------------------------------------------------ 服务端拒绝：必须走 failure() 翻译

    @Test
    fun non2xxWithServerErrorBodyIsTranslatedByTheExistingFailureTranslator() {
        server.reply(409, """{"detail":{"code":"turn_in_progress","reason":"上一轮回复还没结束"}}""")

        val f = uploadFailure()

        // message 来自服务端的 detail.reason、code 来自 detail.code、status 是 HTTP 码——
        // 三样同时成立只可能是走了原有的 failure()，不是新写的一套话。
        assertEquals("上一轮回复还没结束", f.message)
        assertEquals(409, f.status)
        assertEquals("turn_in_progress", f.code)
        assertEquals("翻译必须打那条 warn 日志", 1, translateLogs().size)
    }

    /**
     * 空 body 的 401 也要走 [AgentChatRemote.Failure] 的状态码话术。
     * 这条专门钉「把 `serverBody?.let` 改成真值判断」：真值判断下空 body 会被当成「服务端没回错误体」，
     * message 掉成异常自带的兜底话，用户看到的就不再是「登录已过期」。
     */
    @Test
    fun emptyBodyNon2xxStillGoesThroughTheStatusTalk() {
        server.reply(401, "")

        val f = uploadFailure()

        assertEquals("登录已过期，请重新登录", f.message)
        assertEquals(401, f.status)
        assertNull(f.code)
        assertEquals(1, translateLogs().size)
    }

    // ------------------------------------------------ 本地判定：message 用异常自带的，不翻译成话术

    @Test
    fun missingTokenKeepsTheNotLoggedInMessageAndNeverHitsTheTranslator() {
        val f = uploadFailure(token = null)

        assertEquals("未登录", f.message)
        assertEquals(401, f.status)
        assertEquals("unauthorized", f.code)
        assertTrue("本地判定不该走 failure()（会多打日志、并把「未登录」换成 401 话术）", translateLogs().isEmpty())
        assertEquals("无 token 不该打服务端", 0, server.requestLines.size)
    }

    @Test
    fun malformedUploadResponseBodyKeepsItsOwnMessageAndNeverHitsTheTranslator() {
        server.reply(200, "not json at all")

        val f = uploadFailure()

        assertEquals("上传响应格式不对", f.message)
        assertEquals(200, f.status)
        assertNull(f.code)
        assertTrue(translateLogs().isEmpty())
    }

    @Test
    fun missingUploadIdKeepsItsOwnMessageAndNeverHitsTheTranslator() {
        server.reply(200, """{"ok":true}""")

        val f = uploadFailure()

        assertEquals("上传没有返回 upload_id", f.message)
        assertEquals(200, f.status)
        assertNull(f.code)
        assertTrue(translateLogs().isEmpty())
    }

    // ------------------------------------------------ catch 面 + 正常路径

    /**
     * 网络断了要照原样抛 IOException：委托前 `upload` 没有 catch，`AttachmentApi.uploadImage` 的
     * `runCatching` 收到的是 IOException。catch 放宽成 `Exception` 就会在这里变红。
     * 打的是本机 1 端口（必然 connection refused），不依赖公网。
     */
    @Test
    fun networkFailurePropagatesAsIOExceptionNotFailure() {
        val dead = AgentChatRemote(
            context = ApplicationProvider.getApplicationContext(),
            baseUrl = "http://127.0.0.1:1",
            tokenProvider = { "t-1" },
        )

        assertThrows(java.io.IOException::class.java) { dead.upload(tempFile, "image/jpeg") }
    }

    @Test
    fun successfulUploadStillReturnsUploadId() {
        server.reply(200, """{"upload_id":"u42"}""")

        assertEquals("u42", remote().upload(tempFile, "image/jpeg"))
        // 请求真到了 /v1/uploads：这一条钉的是「upload() 真的委托了、路径没在中间被改掉」
        assertEquals("POST /v1/uploads HTTP/1.1", server.requestLines.poll(10, TimeUnit.SECONDS))
    }

    // ------------------------------------------------ 桩

    /** 按队列逐个应答的极简 HTTP 桩：读完请求头与请求体，回一个固定状态码与 body，然后断开。 */
    private class HttpStub : AutoCloseable {
        private val server = ServerSocket(0)
        val port: Int = server.localPort
        val requestLines = LinkedBlockingQueue<String>()
        private val replies = LinkedBlockingQueue<Pair<Int, String>>()

        init {
            Thread {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: return@Thread
                    runCatching { handle(socket) }
                    runCatching { socket.close() }
                }
            }.apply { isDaemon = true }.start()
        }

        fun reply(status: Int, body: String) {
            replies.put(status to body)
        }

        private fun handle(socket: Socket) {
            val input = socket.getInputStream()
            // 逐字节读到头部结束：OkHttp 不会开 Expect: 100-continue（body 只有几个字节），
            // 但必须先把请求体读干净再应答，否则应答会被当成对半截请求的响应。
            val header = StringBuilder()
            while (true) {
                val byte = input.read()
                if (byte < 0) return
                header.append(byte.toChar())
                if (header.endsWith("\r\n\r\n")) break
            }
            val text = header.toString()
            requestLines.put(text.substringBefore("\r\n"))
            var remaining = CONTENT_LENGTH.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val sink = ByteArray(8192)
            while (remaining > 0) {
                val read = input.read(sink, 0, minOf(remaining, sink.size))
                if (read < 0) break
                remaining -= read
            }
            val (status, body) = replies.poll(10, TimeUnit.SECONDS) ?: (500 to "")
            val payload = body.toByteArray(Charsets.UTF_8)
            val out = socket.getOutputStream()
            out.write(
                "HTTP/1.1 $status x\r\nContent-Type: application/json\r\nContent-Length: ${payload.size}\r\n\r\n"
                    .toByteArray(Charsets.US_ASCII),
            )
            out.write(payload)
            out.flush()
        }

        override fun close() {
            runCatching { server.close() }
        }

        private companion object {
            val CONTENT_LENGTH = Regex("Content-Length:\\s*(\\d+)", RegexOption.IGNORE_CASE)
        }
    }
}
