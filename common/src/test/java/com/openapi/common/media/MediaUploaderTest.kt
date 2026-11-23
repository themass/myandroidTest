package com.openapi.common.media

import android.app.Application
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * [MediaUploader] 的请求形状与错误语义（MockWebServer 起真 HTTP，不走公网）。
 *
 * org.json 在 JVM 单测里是 stub，所以整个类走 Robolectric（与 :feature-chat、:feature-companion 同款）。
 *
 * 这几个用例同时钉住 `AgentChatRemote` 委托之后必须原样保留的行为：
 * - 上传是**原始字节** POST `/v1/uploads`（不是 multipart），带 Bearer 与 Content-Type；
 * - 非 2xx 抛 [MediaUploadException] 且**把状态码与响应体带出去**——chat 靠这两样还原成
 *   它自己的 `Failure`（`detail.reason`、401/409/503 的话术都在 chat 侧翻译）；
 * - 换签名 URL 任何失败都只返回 null，绝不让一张图拖垮整页历史。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MediaUploaderTest {

    private lateinit var server: MockWebServer

    /** 用例里造的待传文件，收尾统一删掉，不往 tmp 目录里堆垃圾。 */
    private val tempFiles = mutableListOf<File>()

    private val restClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    private fun uploader(token: String? = "t-1") =
        MediaUploader(server.url("/").toString().trimEnd('/'), { token }, restClient)

    private fun tempImage(): File =
        File.createTempFile("img_", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            tempFiles += this
        }

    // ------------------------------------------------------------ upload

    @Test
    fun uploadPostsRawBytesAndReturnsUploadId() {
        server.enqueue(MockResponse().setBody("""{"upload_id":"u42"}"""))
        val file = tempImage()

        val id = runBlocking { uploader().upload(file, "image/jpeg") }

        assertEquals("u42", id)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/v1/uploads", request.path)
        assertEquals("Bearer t-1", request.getHeader("Authorization"))
        assertEquals("image/jpeg", request.getHeader("Content-Type"))
        // 原始字节：不是 multipart，体就是文件本身（5 个字节，一个表单都不该多）
        assertEquals(5L, request.bodySize)
        assertEquals(5L, request.body.size)
    }

    @Test
    fun uploadThrowsWhenServerRejectsCarryingStatusAndBody() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"detail":{"code":"token_expired","reason":"登录已过期，请重新登录"}}"""),
        )

        val e = assertThrows(MediaUploadException::class.java) { uploader().uploadBlocking(tempImage(), "image/jpeg") }

        assertEquals(401, e.status)
        // chat 的 Failure 翻译要的是**原始响应体**，不是这里给的话
        assertTrue(e.serverBody?.contains("token_expired") == true)
        assertTrue(e.serverBody?.contains("登录已过期，请重新登录") == true)
    }

    @Test
    fun uploadThrowsWhenNoTokenAndNeverHitsServer() {
        val e = assertThrows(MediaUploadException::class.java) { uploader(token = null).uploadBlocking(tempImage(), "image/jpeg") }

        assertEquals(401, e.status)
        assertEquals("unauthorized", e.code)
        assertEquals("未登录", e.message)
        assertNull(e.serverBody)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun uploadThrowsWhenResponseIsNotJson() {
        server.enqueue(MockResponse().setBody("<html>gateway</html>"))

        val e = assertThrows(MediaUploadException::class.java) { uploader().uploadBlocking(tempImage(), "image/jpeg") }

        assertEquals("上传响应格式不对", e.message)
        assertEquals(200, e.status)
        assertNull(e.serverBody)
    }

    @Test
    fun uploadThrowsWhenUploadIdMissing() {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        val e = assertThrows(MediaUploadException::class.java) { uploader().uploadBlocking(tempImage(), "image/jpeg") }

        assertEquals("上传没有返回 upload_id", e.message)
        assertEquals(200, e.status)
        assertNull(e.serverBody)
    }

    // ------------------------------------------------------------ signedUrl

    @Test
    fun signedUrlReturnsUrlFromServer() {
        server.enqueue(MockResponse().setBody("""{"url":"https://x/y?sig=1"}"""))

        val url = runBlocking { uploader().signedUrl("u42") }

        assertEquals("https://x/y?sig=1", url)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("GET", request.method)
        assertEquals("/v1/uploads/u42", request.path)
        assertEquals("Bearer t-1", request.getHeader("Authorization"))
    }

    @Test
    fun signedUrlReturnsNullWhenNotFound() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":{"code":"not_found"}}"""))

        assertNull(runBlocking { uploader().signedUrl("missing") })
    }

    @Test
    fun signedUrlReturnsNullWhenUnauthorized() {
        server.enqueue(MockResponse().setResponseCode(401))

        assertNull(runBlocking { uploader().signedUrl("u42") })
    }

    @Test
    fun signedUrlReturnsNullOnMalformedBodyOrMissingUrl() {
        server.enqueue(MockResponse().setBody("not json at all"))
        assertNull(runBlocking { uploader().signedUrl("u42") })

        server.enqueue(MockResponse().setBody("""{"upload_id":"u42"}"""))
        assertNull(runBlocking { uploader().signedUrl("u42") })

        server.enqueue(MockResponse().setBody("""{"url":""}"""))
        assertNull(runBlocking { uploader().signedUrl("u42") })

        assertEquals(3, server.requestCount)
    }

    @Test
    fun signedUrlReturnsNullWhenNoTokenAndNeverHitsServer() {
        assertNull(runBlocking { uploader(token = null).signedUrl("u42") })
        assertEquals(0, server.requestCount)
    }

    // ------------------------------------------------------------ MediaStager

    /** 读不出图（这里是不存在的 content uri）只能返回 null，不抛：调用方据此提示重试。 */
    @Test
    fun stageImageReturnsNullWhenUriCannotBeDecoded() {
        val context = RuntimeEnvironment.getApplication()

        assertNull(MediaStager.stageImage(context, android.net.Uri.parse("content://no.such.provider/1"), context.filesDir, 1L))
    }

    // ------------------------------------------------------------ 默认 client

    @Test
    fun defaultClientUsesRestTimeouts() {
        val client = MediaUploader.defaultClient()
        assertEquals(15_000L, client.connectTimeoutMillis.toLong())
        assertEquals(30_000L, client.readTimeoutMillis.toLong())
        assertEquals(30_000L, client.writeTimeoutMillis.toLong())
    }

    // ------------------------------------------------------------ 真实 JSON 可用（防 stub 混进用例）

    @Test
    fun jsonIsRealImplementation() {
        assertEquals("u42", JSONObject("""{"upload_id":"u42"}""").getString("upload_id"))
    }
}
