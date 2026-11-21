package com.openapi.feature.chat.data.remote

import android.content.Context
import com.openapi.common.util.log.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * `/v1` 会话接口的底层客户端（Python companion-agent）。
 *
 * 与旧的 [ChatRemote] 是两套协议，故意分开写而不是在旧类里加分支：
 * 旧的打 `/chat/` 下的表单接口、用 UA 签名鉴权，新的打 `/v1/` 下、用 Bearer，
 * 两者的错误语义、会话 id 类型、历史排序都不同，混在一个类里会让"现在到底走哪条路"变得不可读。
 *
 * 会话 id 在 `/v1` 里是整数，这里统一用 [Long] 对外，和 [com.openapi.feature.chat.domain.model.ChatSession] 对齐。
 */
class AgentChatRemote(
    private val context: Context,
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
    private val locale: () -> String = { "zh-CN" },
) {

    private companion object {
        const val TAG = "AgentChatRemote"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }

    /** REST 调用：超时明确，避免界面无限转圈。 */
    private val restClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 事件流：读超时必须为 0。回复多久吐完完全看模型，写死一个超时会把长回复截断；
     * 服务端每 15 秒发一次心跳，真正断开时 OkHttp 会先报 stream reset 而不是等超时。
     */
    private val streamClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /**
     * 直传 PUT 专用：URL 里已带签名，**不带登录 token**（凭证是 URL 上的 query，
     * 再加 Authorization 头反而可能让 TOS 把签名算错）。写超时给足 2 分钟——
     * 这是整条发送链路里最重的字节，与 AttachmentSender 的单附件超时对齐。
     */
    private val putClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    class Failure(message: String, val status: Int?, val code: String?) : Exception(message)

    // ---------------------------------------------------------------- 会话

    fun sessions(limit: Int = 50): List<AgentSessionVo> {
        val json = call("GET", "/sessions?limit=$limit", null)
        val items = json.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { items.getJSONObject(it).toSession() }
    }

    fun createSession(roleId: String, name: String): AgentSessionVo =
        call("POST", "/sessions", JSONObject().put("role_id", roleId).put("name", name).put("locale", locale()))
            .toSession()

    fun renameSession(sessionId: Long, name: String): AgentSessionVo =
        call("PATCH", "/sessions/$sessionId", JSONObject().put("name", name)).toSession()

    fun deleteSession(sessionId: Long) {
        callVoid("DELETE", "/sessions/$sessionId", null)
    }

    /** 清空一个会话的历史（别的会话、记忆、画像都不动）。 */
    fun clearHistory(sessionId: Long) {
        callVoid("DELETE", "/sessions/$sessionId/messages", null)
    }

    // ---------------------------------------------------------------- 消息

    /**
     * 一页历史。
     *
     * 服务端返回的是**从新到旧**（实测：先 assistant 再 user），而领域模型要求按时间正序，
     * 所以这里反转一次。`hasMore` 服务端不返回，用「这一页是否被填满」近似——
     * 填满说明可能还有更早的。拿不到精确值时，多给一次「加载更多」比少给好：
     * 少给会让用户永远翻不到最早的消息，多给只是一次空请求。
     */
    fun history(sessionId: Long, beforeId: Long?, limit: Int): AgentHistoryPage {
        val query = buildString {
            append("?limit=$limit")
            if (beforeId != null && beforeId > 0) append("&before=$beforeId")
        }
        val json = call("GET", "/sessions/$sessionId/messages$query", null)
        val items = json.optJSONArray("items") ?: return AgentHistoryPage(emptyList(), false)
        val rows = (0 until items.length()).map { items.getJSONObject(it).toMessage() }
        return AgentHistoryPage(rows.reversed(), rows.size >= limit)
    }

    /**
     * 发送一条消息，返回服务端回执。回合本身通过 [events] 观察。
     * [location] 非空时作为消息的顶层 `location` 字段发出（不是附件，服务端存成 kind=location）。
     */
    fun submit(sessionId: Long, text: String, clientMessageId: String, location: JSONObject? = null): AgentSubmitReceipt {
        val json = call("POST", "/sessions/$sessionId/messages", buildAgentSubmitBody(text, clientMessageId, null, location))
        return AgentSubmitReceipt(
            messageId = json.optLong("message_id", 0L),
            turnId = json.optString("turn_id", "").takeIf { it.isNotEmpty() },
        )
    }

    /** 带附件发送：附件先经 [upload] 拿到 upload_id，这里只带引用。 */
    fun submitWithAttachments(
        sessionId: Long,
        text: String,
        clientMessageId: String,
        attachments: List<JSONObject>,
        location: JSONObject? = null,
    ): AgentSubmitReceipt {
        val json = call(
            "POST", "/sessions/$sessionId/messages",
            buildAgentSubmitBody(text, clientMessageId, org.json.JSONArray(attachments), location),
        )
        return AgentSubmitReceipt(
            messageId = json.optLong("message_id", 0L),
            turnId = json.optString("turn_id", "").takeIf { it.isNotEmpty() },
        )
    }

    fun cancel(sessionId: Long) {
        callVoid("POST", "/sessions/$sessionId/cancel", null)
    }

    fun regenerate(sessionId: Long, userMessageId: Long) {
        callVoid("POST", "/sessions/$sessionId/messages/$userMessageId/regenerate", null)
    }

    fun editMessage(messageId: Long, content: String): AgentMessageVo =
        call("PATCH", "/messages/$messageId", JSONObject().put("content", content)).toMessage()

    fun deleteMessage(messageId: Long) {
        callVoid("DELETE", "/messages/$messageId", null)
    }

    // ---------------------------------------------------------------- 上传

    /**
     * 上传一张图片，返回 upload_id。
     *
     * `/v1/uploads` 收的是**原始字节**而不是 multipart（`routers/uploads.py` 直接读 `request.body()`），
     * 所以这里不能复用旧链路那套表单上传。
     */
    fun upload(file: File, mime: String): String {
        val token = requireToken()
        val request = Request.Builder()
            .url(url("/uploads"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", mime)
            .post(file.readBytes().toRequestBody(mime.toMediaType()))
            .build()
        restClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw failure(response.code, text)
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: throw Failure("上传响应格式不对", response.code, null)
            val id = json.optString("upload_id", "")
            if (id.isEmpty()) throw Failure("上传没有返回 upload_id", response.code, null)
            return id
        }
    }

    /**
     * 直传第一步：向服务端要一个单 key、只写、10 分钟的 PUT 凭证（ADR-011 同款）。
     * 返回 upload_id（发消息时引用它）与预签名 URL。URL 是 bearer secret：不打印、不进日志。
     */
    fun signUpload(scene: String, mime: String, sizeBytes: Long): Pair<String, String> {
        val body = JSONObject()
            .put("scene", scene)
            .put("content_type", mime)
            .put("size_bytes", sizeBytes)
        val json = call("POST", "/uploads/sign", body)
        val uploadId = json.optString("upload_id", "")
        val putUrl = json.optJSONObject("upload")?.optString("url").orEmpty()
        if (uploadId.isEmpty() || putUrl.isEmpty()) throw Failure("签发响应缺少 upload_id/url", 200, null)
        return uploadId to putUrl
    }

    /** 直传第二步：字节 PUT 进桶。签名在 URL 里，所以这个请求不带任何鉴权头。 */
    fun putToPresigned(putUrl: String, file: File, mime: String) {
        val request = Request.Builder()
            .url(putUrl)
            .put(file.readBytes().toRequestBody(mime.toMediaType()))
            .build()
        putClient.newCall(request).execute().use { response ->
            // 失败原因里不放 URL（里面有签名），只给状态码
            if (!response.isSuccessful) throw Failure("直传失败 ${response.code}", response.code, null)
        }
    }

    /**
     * 按 upload_id 换一个**短期签名 GET URL**（服务端给 300 秒）。
     * 历史消息的图片只有 upload_id，渲染前现换；URL 是 bearer secret：不打印、不进日志、不落缓存。
     */
    fun attachmentUrl(uploadId: String): String? =
        runCatching { call("GET", "/uploads/$uploadId", null).optString("url").takeIf { it.isNotEmpty() } }
            .getOrNull()

    /** 把一个签名 GET URL 的内容下载成文件（历史图片拉回本机缓存）。 */
    fun downloadTo(url: String, target: File): Boolean = runCatching {
        restClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) return@use false
            val src = response.body?.byteStream() ?: return@use false
            src.use { input -> target.outputStream().use { input.copyTo(it) } }
            true
        }
    }.getOrDefault(false)

    // ---------------------------------------------------------------- 事件流

    /**
     * 订阅会话事件流。[lastEventId] 非空时断点续传（服务端按 Last-Event-ID 补发）。
     * 收集即连接，取消即断开。
     */
    fun events(sessionId: Long, lastEventId: String?): Flow<AgentSseFrame> = flow {
        val token = requireToken()
        val builder = Request.Builder()
            .url(url("/sessions/$sessionId/events"))
            .header("Authorization", "Bearer $token")
            .header("Accept", "text/event-stream")
        if (!lastEventId.isNullOrEmpty()) builder.header("Last-Event-ID", lastEventId)
        val call = streamClient.newCall(builder.build())
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }

        call.execute().use { response ->
            if (!response.isSuccessful) {
                val body = runCatching { response.body?.string() }.getOrNull().orEmpty()
                throw failure(response.code, body)
            }
            val source = response.body?.source() ?: throw Failure("事件流为空", response.code, null)
            // 按行解析 SSE：空行分帧，`:` 开头是心跳注释，多行 data 用换行拼接。
            var id: String? = null
            var event = "message"
            val data = StringBuilder()
            var hasData = false
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isEmpty()) {
                    if (hasData) {
                        emit(AgentSseFrame(id, event, data.toString().removeSuffix("\n")))
                        id = null
                        event = "message"
                        data.setLength(0)
                        hasData = false
                    }
                    continue
                }
                if (line.startsWith(":")) continue
                val colon = line.indexOf(':')
                val field = if (colon < 0) line else line.substring(0, colon)
                var value = if (colon < 0) "" else line.substring(colon + 1)
                if (value.startsWith(" ")) value = value.substring(1)
                when (field) {
                    "id" -> id = value
                    "event" -> event = value
                    "data" -> { data.append(value).append('\n'); hasData = true }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    // ---------------------------------------------------------------- 内部

    private fun url(path: String): String = baseUrl.trimEnd('/') + "/v1" + path

    private fun requireToken(): String =
        tokenProvider()?.takeIf { it.isNotBlank() } ?: throw Failure("未登录", 401, "unauthorized")

    private fun requestBuilder(method: String, path: String, body: JSONObject?, token: String): Request {
        val requestBody: RequestBody? = when {
            body != null -> body.toString().toRequestBody(JSON)
            method == "POST" || method == "PUT" || method == "PATCH" -> ByteArray(0).toRequestBody(JSON)
            else -> null
        }
        return Request.Builder()
            .url(url(path))
            .method(method, requestBody)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("Accept-Language", locale())
            .build()
    }

    /**
     * 同步执行一次 REST 调用。故意不是 suspend：所有调用方都在 [withContext] 的 IO 线程里，
     * 再包一层 withContext 只是多一次调度，不会让任何东西变快。
     */
    private fun call(method: String, path: String, body: JSONObject?): JSONObject {
        val token = requireToken()
        restClient.newCall(requestBuilder(method, path, body, token)).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw failure(response.code, text)
            if (text.isBlank()) return JSONObject()
            return runCatching { JSONObject(text) }.getOrNull()
                ?: throw Failure("响应格式不对", response.code, null)
        }
    }

    private fun callVoid(method: String, path: String, body: JSONObject?) {
        val token = requireToken()
        restClient.newCall(requestBuilder(method, path, body, token)).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw failure(response.code, text)
        }
    }

    /**
     * 把服务端错误翻译成一句人话。
     *
     * `/v1` 的错误体是 `{"detail": {"code": "...", "reason": "..."}}`，只报 HTTP 码对定位没用：
     * 401 是登录过期、409 是「回合进行中」、422 是内容被拒，三者的处理方式完全不同。
     */
    private fun failure(status: Int, body: String): Failure {
        val detail = runCatching { JSONObject(body).optJSONObject("detail") }.getOrNull()
        val code = detail?.optString("code")?.takeIf { it.isNotEmpty() }
        val reason = detail?.optString("reason")?.takeIf { it.isNotEmpty() }
        val message = when {
            reason != null -> reason
            status == 401 -> "登录已过期，请重新登录"
            status == 409 -> "上一轮回复还没结束"
            status == 503 -> "服务暂时不可用，请稍后重试"
            else -> "请求失败（HTTP $status）"
        }
        LogUtil.w("$TAG /v1 失败 status=$status code=$code body=${body.take(200)}")
        return Failure(message, status, code)
    }

    private fun JSONObject.toSession(): AgentSessionVo = AgentSessionVo(
        id = optLong("id", 0L),
        roleId = optString("role_id", "").takeIf { it.isNotEmpty() },
        name = optString("name", ""),
        updatedAt = optString("updated_at", ""),
    )

    private fun JSONObject.toMessage(): AgentMessageVo = toAgentMessageVo()
}

/**
 * 历史 JSON → [AgentMessageVo]（顶层函数：AgentVoMappersTest 之外，映射单测也直接打这里）。
 *
 * `location` 只在 kind=="location" 的行出现（服务端契约）；`duration_s` 只在视频附件上出现，
 * 缺失与 0 都归一成 null——发送时没带时长的视频本来就没有可信值可显示。
 */
internal fun JSONObject.toAgentMessageVo(): AgentMessageVo = AgentMessageVo(
    id = optLong("id", 0L),
    role = optString("role", ""),
    content = optString("content", ""),
    kind = optString("kind", "chat"),
    createdAt = optString("created_at", ""),
    interrupted = optBoolean("interrupted", false),
    clientMessageId = optString("client_message_id", "").takeIf { it.isNotEmpty() },
    attachments = optJSONArray("attachments").orEmptyObjects().mapNotNull { a ->
        a.optString("upload_id").takeIf { it.isNotEmpty() }?.let {
            AgentAttachmentVo(
                uploadId = it,
                kind = a.optString("kind", "image"),
                name = a.optString("name", ""),
                durationSeconds = a.optJSONObject("meta")?.let { m ->
                    m.optInt("duration_s", -1).takeIf { d -> d in 1..86_400 }
                } ?: a.optInt("duration_s", -1).takeIf { d -> d in 1..86_400 },
            )
        }
    },
    location = optJSONObject("location")?.let { loc ->
        val lat = loc.optDouble("lat", Double.NaN)
        val lng = loc.optDouble("lng", Double.NaN)
        if (lat.isNaN() || lng.isNaN()) null
        else AgentLocationVo(lat, lng, loc.optInt("accuracy_m", -1).takeIf { it >= 0 })
    },
)

private fun org.json.JSONArray?.orEmptyObjects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

/**
 * 组装 `POST /sessions/{id}/messages` 的请求体。text 和 client_message_id 恒在；
 * attachments/location 只在有值时带——服务端把「三个字段全空的发送」判成 422 empty_message，
 * 而多带一个空的 location/attachments 对象同样是形态错误，所以宁可缺省也不放空壳。
 * location 是**消息顶层字段**，不是附件——服务端按 kind=="location" 存储，历史原样带回。
 */
internal fun buildAgentSubmitBody(text: String, clientMessageId: String, attachments: org.json.JSONArray?, location: JSONObject?): JSONObject =
    JSONObject()
        .put("text", text)
        .put("client_message_id", clientMessageId)
        .apply {
            if (attachments != null) put("attachments", attachments)
            if (location != null) put("location", location)
        }

/**
 * 领域坐标 → 服务端 `location` 对象。精度是可选 int：发送时拿不到精度就不带这个 key，
 * 而不是带一个假的 0（0 米精度是在声称不可能的准确度）。经纬度按原样精度发出。
 */
internal fun buildAgentLocationJson(latitude: Double, longitude: Double, accuracyMeters: Int?): JSONObject =
    JSONObject()
        .put("lat", latitude)
        .put("lng", longitude)
        .apply { accuracyMeters?.let { put("accuracy_m", it) } }

data class AgentSessionVo(val id: Long, val roleId: String?, val name: String, val updatedAt: String)

/**
 * 历史里一条消息携带的附件引用（字节在对象存储，这里只有 id；渲染要再去换签名 URL）。
 * [durationSeconds] 只有 kind=="video" 才有值（服务端字段 `duration_s`，缺失为 null）。
 */
data class AgentAttachmentVo(
    val uploadId: String,
    val kind: String,
    val name: String,
    val durationSeconds: Int? = null,
)

/** 历史里一条位置消息的坐标（服务端 `location` 对象原样）。 */
data class AgentLocationVo(val lat: Double, val lng: Double, val accuracyMeters: Int?)

data class AgentMessageVo(
    val id: Long,
    val role: String,
    val content: String,
    val kind: String,
    val createdAt: String,
    val interrupted: Boolean,
    /** 客户端发起时带的本地 id：历史行与本地富内容行（图片/语音）靠它对齐。 */
    val clientMessageId: String? = null,
    val attachments: List<AgentAttachmentVo> = emptyList(),
    /** kind=="location" 的行携带坐标，其余行为 null。 */
    val location: AgentLocationVo? = null,
)

data class AgentHistoryPage(val items: List<AgentMessageVo>, val hasMore: Boolean)

data class AgentSubmitReceipt(val messageId: Long, val turnId: String?)

data class AgentSseFrame(val id: String?, val event: String, val data: String)
