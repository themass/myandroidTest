package com.openapi.ks.myapp.companion

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * HttpCompanionApi 的请求与错误映射。用拦截器代替网络：不起服务、不依赖公网，
 * 所以服务端没部署（nginx 404）时它照样能跑——它验证的是客户端自己的行为。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HttpCompanionApiTest {

    private class Fake(var code: Int = 200, var body: String = "{}") : Interceptor {
        val seen = mutableListOf<Request>()
        val bodies = mutableListOf<String>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            seen += req
            bodies += req.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
            return Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private fun api(fake: Fake, token: String? = "tok", base: String = "https://example.test/") =
        HttpCompanionApi(base, { token }, OkHttpClient.Builder().addInterceptor(fake).build())

    private fun failure(block: suspend () -> Unit): CompanionApiException {
        try {
            runBlocking { block() }
        } catch (e: CompanionApiException) {
            return e
        }
        fail("expected CompanionApiException")
        throw AssertionError()
    }

    @Test
    fun `requests carry the bearer token and the base url has no double slash`() {
        val fake = Fake(body = """{"items":[],"total":0}""")
        runBlocking { api(fake).characters(limit = 5, offset = 10) }
        val req = fake.seen.single()
        assertEquals("Bearer tok", req.header("Authorization"))
        assertEquals("https://example.test/v1/characters?limit=5&offset=10", req.url.toString())
        assertEquals("GET", req.method)
    }

    @Test
    fun `no token fails as 401 before any request is made`() {
        val fake = Fake()
        val e = failure { api(fake, token = null).inbox() }
        assertEquals(401, e.statusCode)
        assertTrue("no network call without a login", fake.seen.isEmpty())
        assertEquals(401, failure { api(fake, token = "  ").inbox() }.statusCode)
    }

    @Test
    fun `http errors map to readable messages and keep the status code`() {
        val expected = mapOf(401 to "登录已失效", 403 to "没有权限", 404 to "内容不存在", 422 to "不符合要求", 500 to "请求失败（500）")
        expected.forEach { (code, text) ->
            val e = failure { api(Fake(code = code)).preferences() }
            assertEquals(code, e.statusCode)
            assertTrue("$code → ${e.message}", e.message!!.contains(text))
        }
    }

    @Test
    fun `a gateway html 404 means the service is down, not that the content was deleted`() {
        // 服务没部署时 nginx 回 HTML 404；提示成「内容已删除」会让用户以为自己的数据丢了
        val e = failure { api(Fake(code = 404, body = "<html><h1>404 Not Found</h1>nginx</html>")).characters() }
        assertEquals(404, e.statusCode)
        assertTrue(e.message!!, e.message!!.contains("暂时不可用"))
        assertTrue(!e.message!!.contains("已删除"))
    }

    @Test
    fun `a service json 404 still means the content does not exist`() {
        val e = failure { api(Fake(code = 404, body = """{"detail":"not found"}""")).character("x") }
        assertTrue(e.message!!.contains("不存在"))
    }

    @Test
    fun `gateway errors 502 503 504 are reported as unavailable`() {
        listOf(502, 503, 504).forEach { code ->
            val e = failure { api(Fake(code = code, body = "<html>bad gateway</html>")).preferences() }
            assertTrue("$code → ${e.message}", e.message!!.contains("暂时不可用"))
        }
    }

    @Test
    fun `the server error body is not shown to the user`() {
        // 5xx 的正文可能带堆栈或内部地址，界面只能显示固定文案
        val e = failure { api(Fake(code = 500, body = "java.lang.NullPointerException at com.secret.Internal")).preferences() }
        assertTrue(!e.message!!.contains("NullPointer"))
    }

    @Test
    fun `a non json success body is reported instead of crashing`() {
        val e = failure { api(Fake(body = "<html>nginx</html>")).preferences() }
        assertNull(e.statusCode)
        assertTrue(e.message!!.contains("格式"))
    }

    @Test
    fun `an array endpoint rejects an object body`() {
        failure { api(Fake(body = """{"not":"array"}""")).inbox() }
    }

    @Test
    fun `an empty body is an empty object for write endpoints`() {
        runBlocking { api(Fake(code = 200, body = "")).deleteMemory("linche", "m1") }
    }

    @Test
    fun `ids are url encoded so a hostile id cannot add path segments`() {
        val fake = Fake()
        runBlocking { api(fake).deleteEvent("a/b?x=1") }
        val url = fake.seen.single().url.toString()
        assertEquals("https://example.test/v1/memory/events/a%2Fb%3Fx%3D1", url)
        assertEquals("DELETE", fake.seen.single().method)
    }

    @Test
    fun `create session posts the character id and parses the room fields`() {
        val fake = Fake(body = """{"session_id":"s1","room_name":"companion-s1","token":"lk","livekit_url":"wss://lk","expires_at":"2026-11-12T10:00:00Z"}""")
        val s = runBlocking { api(fake).createSession("linche") }
        assertEquals("POST", fake.seen.single().method)
        assertEquals("linche", JSONObject(fake.bodies.single()).getString("character_id"))
        assertEquals("companion-s1", s.roomName)
        assertEquals("wss://lk", s.livekitUrl)
        // 老服务端没有 provider 字段：解析结果必须落到默认线路 livekit，不能让旧响应变成未知线路
        assertEquals("livekit", s.provider)
    }

    @Test
    fun `create session keeps the provider and provider config the server chose`() {
        val fake = Fake(body = """{"session_id":"s1","room_name":"r","token":"t","livekit_url":"wss://lk","expires_at":"e","provider":"volcengine","provider_config":{"app_id":"a1","room_id":"r9"}}""")
        val s = runBlocking { api(fake).createSession("linche") }
        assertEquals("volcengine", s.provider)
        assertEquals("a1", s.providerConfig?.getString("app_id"))
        assertEquals("r9", s.providerConfig?.getString("room_id"))
    }

    @Test
    fun `create script session posts character and case id to script-sessions`() {
        val fake = Fake(body = """{"session_id":"s9","room_name":"script-barge-x","token":"lk","livekit_url":"wss://lk","expires_at":"2026-11-19T10:00:00Z"}""")
        val s = runBlocking { api(fake).createScriptSession("linche", "barge_mid_sentence") }
        assertEquals("POST", fake.seen.single().method)
        assertEquals("https://example.test/v1/voice/script-sessions", fake.seen.single().url.toString())
        val body = JSONObject(fake.bodies.single())
        assertEquals("linche", body.getString("character_id"))
        assertEquals("barge_mid_sentence", body.getString("case_id"))
        assertEquals("script-barge-x", s.roomName)
        assertEquals("s9", s.sessionId)
        assertEquals("livekit", s.provider)
    }

    @Test
    fun `confirm action sends the decision and returns the status`() {
        val fake = Fake(body = """{"status":"declined"}""")
        val status = runBlocking { api(fake).confirmAction("c/1", accepted = false) }
        assertEquals("declined", status)
        assertTrue(fake.seen.single().url.toString().endsWith("/v1/actions/c%2F1/confirm"))
        assertEquals(false, JSONObject(fake.bodies.single()).getBoolean("accepted"))
    }

    @Test
    fun `update preferences sends nulls as json null so a field can be cleared`() {
        val fake = Fake(body = """{"events_enabled":true,"greetings_enabled":false,"call_enabled":false,
            "quiet_start":"23:00","quiet_end":"08:00","max_per_day":2,"max_per_week":6,"paused_until":null}""")
        runBlocking { api(fake).updatePreferences(mapOf("paused_until" to null, "greetings_enabled" to true)) }
        val sent = JSONObject(fake.bodies.single())
        assertTrue(sent.has("paused_until") && sent.isNull("paused_until"))
        assertEquals(true, sent.getBoolean("greetings_enabled"))
        assertEquals("PUT", fake.seen.single().method)
    }

    @Test
    fun `setting an event status uses PATCH with a json body`() {
        // 旧服务端是 POST /events/{id}/{status}；新接口统一成 PATCH + {"status": ...}
        val fake = Fake()
        runBlocking { api(fake).setEventStatus("e1", "done") }
        val req = fake.seen.single()
        assertEquals("PATCH", req.method)
        assertTrue(req.url.toString().endsWith("/v1/memory/events/e1"))
        assertEquals("done", JSONObject(fake.bodies.single()).getString("status"))
    }

    // -------------------------------------------------------------- 用户档案（Markdown）

    @Test
    fun `profile doc get scopes by role and parses markdown version source`() {
        val fake = Fake(body = """{"markdown":"## 基本情况\n做前端","version":3,"source":"llm"}""")
        val doc = runBlocking { api(fake).profileDoc("lin che") }
        assertTrue(fake.seen.single().url.toString().endsWith("/v1/memory/profile_doc?role_id=lin+che"))
        assertEquals("## 基本情况\n做前端", doc.markdown)
        assertEquals(3, doc.version)
        assertEquals("llm", doc.source)
    }

    @Test
    fun `profile doc with null version parses as null not zero`() {
        val fake = Fake(body = """{"markdown":"","version":null,"source":""}""")
        val doc = runBlocking { api(fake).profileDoc("linche") }
        assertEquals("", doc.markdown)
        assertNull(doc.version)
        assertNull(doc.source)
    }

    @Test
    fun `profile doc save is a PUT with the markdown body and no return parse`() {
        val fake = Fake(body = "")   // 201 空响应体
        runBlocking { api(fake).updateProfileDoc("linche", "## 基本情况\n我改的") }
        val req = fake.seen.single()
        assertEquals("PUT", req.method)
        assertTrue(req.url.toString().endsWith("/v1/memory/profile_doc?role_id=linche"))
        assertEquals("## 基本情况\n我改的", JSONObject(fake.bodies.single()).getString("markdown"))
    }

    // -------------------------------------------------------------- 历史附件

    @Test
    fun `history returns oldest first and keeps the upload ids of image attachments`() {
        // 服务端实测按新→旧返回，且 meta.attachments 原样回带。
        val fake = Fake(
            body = """{"items":[
                      {"role":"assistant","content":"看到了","attachments":
                        [{"upload_id":"01JOK","kind":"image","name":"a.jpg"}]},
                      {"role":"user","content":"看这张"}]}""",
        )
        val items = runBlocking { api(fake).history(42L) }
        assertTrue(fake.seen.single().url.toString().endsWith("/v1/sessions/42/messages?limit=30"))
        assertEquals("user", items[0].role)
        assertEquals(emptyList<String>(), items[0].attachmentIds)
        assertEquals("assistant", items[1].role)
        assertEquals(listOf("01JOK"), items[1].attachmentIds)
    }

    @Test
    fun `an image-only history row survives while an empty row is still dropped`() {
        // 「对话」页和陪伴页共用同一段历史，那边能发纯图消息：文字为空但有附件的行
        // 不能按老规矩丢掉（否则图片在那边发的、这边看不见）。没字又没附件的行照旧隐藏。
        val fake = Fake(
            body = """{"items":[
                      {"role":"user","content":"","attachments":[{"upload_id":"u1","kind":"image"}]},
                      {"role":"user","content":"   "}]}""",
        )
        val items = runBlocking { api(fake).history(7L) }
        assertEquals(1, items.size)
        assertEquals(listOf("u1"), items.single().attachmentIds)
    }

    @Test
    fun `attachment url comes from the shared uploads endpoint with the bearer token`() {
        // 委托 :common 的 MediaUploader：路径 /v1/uploads/{id}，GET，带 Bearer。
        val fake = Fake(body = """{"url":"https://cdn.example.test/a.jpg?sig=x"}""")
        val url = runBlocking { api(fake).attachmentUrl("u42") }
        assertEquals("https://cdn.example.test/a.jpg?sig=x", url)
        val req = fake.seen.single()
        assertEquals("GET", req.method)
        assertEquals("https://example.test/v1/uploads/u42", req.url.toString())
        assertEquals("Bearer tok", req.header("Authorization"))
    }

    @Test
    fun `a refused or broken attachment url is null, never an exception`() {
        // 一张图换不到 URL 只让那一张退化成空路径，不能把整页历史拖垮。
        assertNull(runBlocking { api(Fake(code = 404)).attachmentUrl("u1") })
        assertNull(runBlocking { api(Fake(body = "not json")).attachmentUrl("u1") })
        assertNull(runBlocking { api(Fake(body = """{"upload_id":"u1"}""")).attachmentUrl("u1") })
    }

    @Test
    fun `no token means no network call for an attachment url`() {
        val fake = Fake()
        assertNull(runBlocking { api(fake, token = null).attachmentUrl("u1") })
        assertTrue(fake.seen.isEmpty())
    }
}
