package com.openapi.common.net.glide

import android.app.Application
import com.openapi.common.net.http.NetConfig
import com.openapi.common.net.request.BaseRequest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A40「封面经常出不来」的服务端侧根因：CDN 按 UA 放行，而 Glide 的 OkHttp
 * 从来没带过 UA/Referer（旧 `GlideHttpClient` 只替换 DNS）。服务端密钥取
 * `User-Agent` 末 16 位（`BaseRequest.authkey`），所以封面这条链必须用与 API
 * **同一份** UA 生成逻辑——两处各写一份迟早漂移。这里把共享逻辑收进
 * [CoverRequestHeaders] + `UserAgent`，测试钉住两条链的 UA 身份段一致。
 *
 * 注意 UA 的签名段（`,IE` + 时间戳 + Md5）每次调用都不同，这是旧实现就有的性质；
 * 所以比对的是身份段（`,IE` 之前），不是整串。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CoverRequestHeadersTest {

    @After
    fun resetConfig() {
        NetConfig.init("", "", "", null)
    }

    @Test
    fun `封面 UA 含渠道、cpu、版本后缀与签名段，与 API 同款形状`() {
        val ua = CoverRequestHeaders.userAgent(app())
        assertTrue("UA 应含 ',channel='：$ua", ua.contains(",channel="))
        assertTrue("UA 应含 ',cpu='：$ua", ua.contains(",cpu="))
        assertTrue("UA 应含 SEX/ 版本后缀：$ua", ua.contains("SEX/"))
        assertTrue("UA 应以 ',IE' + 时间戳签名收尾：$ua", ua.contains(",IE"))
    }

    @Test
    fun `封面 UA 的身份段与 BaseRequest 发出的 UA 逐字相同`() {
        val shared = CoverRequestHeaders.userAgent(app())
        // BaseRequest 构造器组装头；getHeaders() 就是它实际发出去的。
        val req = object : BaseRequest<String>(app(), 0, "https://example.invalid/x", null, { }, { }) {}
        val apiUa = req.getHeaders()["User-Agent"]
        assertNotNull("BaseRequest 必须带 User-Agent", apiUa)
        assertEquals(
            "封面链与 API 链的 UA 身份段必须一致（服务端按 UA 派生密钥）",
            apiUa!!.substringBefore(",IE"),
            shared.substringBefore(",IE"),
        )
    }

    @Test
    fun `服务端密钥就是 UA 末 16 位`() {
        val req = object : BaseRequest<String>(app(), 0, "https://example.invalid/x", null, { }, { }) {}
        val apiUa = req.getHeaders()["User-Agent"]!!
        assertEquals(apiUa.substring(apiUa.length - 16), req.authkey)
    }

    @Test
    fun `Referer 取 NetConfig 默认 referer`() {
        NetConfig.init("https://ref.example/", "tokenH", "devidH", null)
        val h = CoverRequestHeaders.build(app())
        assertEquals("https://ref.example/", h["Referer"])
        assertEquals(CoverRequestHeaders.userAgent(app()).substringBefore(",IE"), h["User-Agent"]!!.substringBefore(",IE"))
    }

    @Test
    fun `GlideHttpClient 实到的请求带 UA 与 Referer，且超时显式配置`() {
        NetConfig.init("http://www.ok123find.top/", "tokenH", "Devid", null)
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("x"))
        server.start()
        try {
            val client: OkHttpClient = GlideHttpClient.create(app())
            // 修复前这里没配过超时（OkHttp 默认 10s/10s），挂死的连接会占住 Glide 的并发位，
            // 后面的封面全排在它后面——这也是「经常出不来」的一部分。
            assertEquals("connect 超时应显式设为 15s", 15_000, client.connectTimeoutMillis)
            assertEquals("read 超时应显式设为 30s", 30_000, client.readTimeoutMillis)
            client.newCall(
                okhttp3.Request.Builder().url(server.url("/cover.jpg")).build()
            ).execute().use { resp -> assertEquals(200, resp.code) }
            val recorded = server.takeRequest()
            val ua = recorded.getHeader("User-Agent")
            assertTrue("Glide 请求必须带真 UA（CDN 按 UA 放行）：$ua", ua != null && ua.contains("SEX/"))
            assertEquals("http://www.ok123find.top/", recorded.getHeader("Referer"))
        } finally {
            server.shutdown()
        }
    }

    private fun app(): Application = RuntimeEnvironment.getApplication()
}
