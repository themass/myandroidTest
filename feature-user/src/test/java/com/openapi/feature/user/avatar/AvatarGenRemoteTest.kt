package com.openapi.feature.user.avatar

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * [AvatarGenRemote] 的请求形状与错误语义（MockWebServer 起真 HTTP）。
 *
 * 用 Gson 而不是 org.json 正是为了这里：**不用 Robolectric**——org.json 在 JVM 单测里是
 * stub，Gson 是纯 JVM 库，这三类（flow/remote/store）全是无 Android 依赖的快速单测。
 */
class AvatarGenRemoteTest {

    private lateinit var server: MockWebServer
    private val restClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private fun remote(token: String? = "t0k3n") =
        AvatarGenRemote(server.url("/").toString().trimEnd('/'), { token }, restClient)

    @Test
    fun generatePostsJsonWithBearerAndReturnsAvatarUploadId() {
        server.enqueue(MockResponse().setBody("""{"avatar_upload_id":"u_gen","style":"anime"}"""))
        val id = runBlocking { remote().generate("u_src", "anime") }
        assertEquals("u_gen", id)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/avatars/generate", request.path)
        assertEquals("Bearer t0k3n", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"source_upload_id\":\"u_src\""))
        assertTrue(body, body.contains("\"style\":\"anime\""))
    }

    @Test
    fun applyPostsJsonAndAcceptsOk() {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        runBlocking { remote().apply("u_gen") }
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/avatars/apply", request.path)
        assertTrue(request.body.readUtf8().contains("\"avatar_upload_id\":\"u_gen\""))
    }

    @Test
    fun missingTokenFailsAsNotLoggedInWithoutHittingNetwork() {
        val e = assertThrows(AvatarGenException::class.java) {
            runBlocking { remote(token = null).generate("u_src", "anime") }
        }
        assertEquals(AvatarUserReason.NOT_LOGGED_IN, e.reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun status401MapsToNotLoggedIn() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":{"code":"unauthorized"}}"""))
        val e = assertThrows(AvatarGenException::class.java) {
            runBlocking { remote().generate("u_src", "anime") }
        }
        assertEquals(AvatarUserReason.NOT_LOGGED_IN, e.reason)
    }

    @Test
    fun status413AndQuotaCodeMapToQuotaAndBadImage() {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"detail":{"code":"bad_image"}}"""))
        assertEquals(AvatarUserReason.BAD_IMAGE, assertThrows(AvatarGenException::class.java) {
            runBlocking { remote().generate("u_src", "anime") }
        }.reason)

        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"detail":{"code":"quota_exceeded"}}"""))
        assertEquals(AvatarUserReason.QUOTA, assertThrows(AvatarGenException::class.java) {
            runBlocking { remote().generate("u_src", "anime") }
        }.reason)
    }

    @Test
    fun providerErrorBodyIsNeverSurfacedAsReason() {
        // 网关/上游 provider 的原始报错（500 + HTML）只归类为服务不可用，绝不透传内容。
        server.enqueue(MockResponse().setResponseCode(500).setBody("<html>upstream traceback</html>"))
        val e = assertThrows(AvatarGenException::class.java) {
            runBlocking { remote().generate("u_src", "anime") }
        }
        assertEquals(AvatarUserReason.SERVICE_UNAVAILABLE, e.reason)
        assertTrue("exception must not carry upstream body", !e.message.orEmpty().contains("upstream"))
    }

    @Test
    fun successWithoutUploadIdIsServiceUnavailableNotCrash() {
        server.enqueue(MockResponse().setBody("""{"style":"anime"}"""))
        val e = assertThrows(AvatarGenException::class.java) {
            runBlocking { remote().generate("u_src", "anime") }
        }
        assertEquals(AvatarUserReason.SERVICE_UNAVAILABLE, e.reason)
    }

    @Test
    fun downloadDoesNotSendAuthorizationAndNullsOnFailure() {
        server.enqueue(MockResponse().setBody("bytes"))
        val bytes = runBlocking { remote().download(server.url("/signed/abc").toString()) }
        assertEquals("bytes", String(bytes!!))
        val request = server.takeRequest()
        // 签名 URL 本身就是凭证：不能再带 Bearer（日志与请求都不该出现 token）。
        assertEquals(null, request.getHeader("Authorization"))

        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(null, runBlocking { remote().download(server.url("/signed/expired").toString()) })
    }
}
