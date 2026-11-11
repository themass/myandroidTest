package com.openapi.ks.myapp.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * companion 服务的全部接口。ViewModel 只依赖这个接口，测试里换成假实现即可。
 *
 * 身份一律来自登录凭据（Bearer），请求体里不带 user_id：服务端不信请求体里的身份，
 * 客户端也就没有理由去填它。
 */
interface CompanionApi {
    suspend fun createSession(characterId: String): SessionResponse
    suspend fun characters(limit: Int = 20, offset: Int = 0): CharacterPage
    suspend fun character(id: String): CharacterDetail
    suspend fun memory(limit: Int = 20, offset: Int = 0): FactPage
    suspend fun deleteMemory(id: String)
    suspend fun events(includePast: Boolean = false): List<TimelineEventItem>
    suspend fun addEvent(title: String, atIso: String, kind: String = "other"): TimelineEventItem
    suspend fun setEventStatus(id: String, status: String)
    suspend fun deleteEvent(id: String)
    suspend fun pendingActions(): List<PendingAction>
    suspend fun confirmAction(confirmationId: String, accepted: Boolean): String
    suspend fun preferences(): CompanionPreferences
    suspend fun updatePreferences(patch: Map<String, Any?>): CompanionPreferences
    suspend fun inbox(): List<InboxMessage>
}

class CompanionApiException(message: String, val statusCode: Int? = null, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * 通过 HTTP 调 companion 服务。
 *
 * baseUrl 与 token 由调用方注入：同一份代码要跑测试、预发和生产三套服务，地址进
 * 构建配置；token 从现有登录态读，这个类不关心登录怎么做。
 */
class HttpCompanionApi(
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
    private val client: OkHttpClient = defaultClient(),
    /**
     * 给了它，记忆与事件就走新服务（/api/agent/v1/memory）并按这个角色的空间读写：
     * 新对话把记忆按角色隔离存放，管理页必须看同一个空间，否则「她记得的」永远是空的。
     * 不给就是旧服务（/companion/...），用户级空间。
     */
    private val roleIdProvider: (() -> String?)? = null,
) : CompanionApi {

    private val agentStack: Boolean get() = roleIdProvider != null

    private fun roleQuery(prefix: String): String =
        roleIdProvider?.invoke()?.takeIf { it.isNotBlank() }?.let { "$prefix" + "role_id=" + enc(it) }.orEmpty()

    override suspend fun createSession(characterId: String): SessionResponse {
        val json = call("POST", "/companion/sessions", JSONObject().put("character_id", characterId))
        return SessionResponse(
            sessionId = json.optString("session_id"),
            roomName = json.optString("room_name"),
            token = json.optString("token"),
            livekitUrl = json.optString("livekit_url"),
            expiresAt = json.optString("expires_at"),
        )
    }

    override suspend fun characters(limit: Int, offset: Int): CharacterPage {
        val json = call("GET", "/companion/characters?limit=$limit&offset=$offset")
        return CharacterPage(
            items = json.optJSONArray("items").toList { CharacterSummary.from(it) },
            total = json.optInt("total"),
        )
    }

    override suspend fun character(id: String): CharacterDetail =
        CharacterDetail.from(call("GET", "/companion/characters/${enc(id)}"))

    override suspend fun memory(limit: Int, offset: Int): FactPage {
        val json = if (agentStack) {
            call("GET", "/api/agent/v1/memory/facts?limit=$limit&offset=$offset${roleQuery("&")}")
        } else {
            call("GET", "/companion/memory?limit=$limit&offset=$offset")
        }
        return FactPage(
            items = json.optJSONArray("items").toList { MemoryFact.from(it) },
            hasMore = json.optBoolean("has_more"),
        )
    }

    override suspend fun deleteMemory(id: String) {
        if (agentStack) call("DELETE", "/api/agent/v1/memory/facts/${enc(id)}${roleQuery("?")}")
        else call("DELETE", "/companion/memory/${enc(id)}")
    }

    override suspend fun events(includePast: Boolean): List<TimelineEventItem> {
        if (agentStack) {
            val json = call("GET", "/api/agent/v1/memory/events?include_past=$includePast&limit=100${roleQuery("&")}")
            return json.optJSONArray("items").toList { TimelineEventItem.from(it) }
        }
        val raw = callArray("GET", "/companion/events?include_past=$includePast")
        return raw.toList { TimelineEventItem.from(it) }
    }

    override suspend fun addEvent(title: String, atIso: String, kind: String): TimelineEventItem {
        val body = JSONObject().put("title", title).put("at", atIso).put("kind", kind)
        val path = if (agentStack) "/api/agent/v1/memory/events${roleQuery("?")}" else "/companion/events"
        return TimelineEventItem.from(call("POST", path, body))
    }

    override suspend fun setEventStatus(id: String, status: String) {
        if (agentStack) call("PATCH", "/api/agent/v1/memory/events/${enc(id)}", JSONObject().put("status", status))
        else call("POST", "/companion/events/${enc(id)}/${enc(status)}")
    }

    override suspend fun deleteEvent(id: String) {
        if (agentStack) call("DELETE", "/api/agent/v1/memory/events/${enc(id)}")
        else call("DELETE", "/companion/events/${enc(id)}")
    }

    override suspend fun pendingActions(): List<PendingAction> =
        callArray("GET", "/companion/actions/pending").toList { PendingAction.from(it) }

    override suspend fun confirmAction(confirmationId: String, accepted: Boolean): String {
        val body = JSONObject().put("accepted", accepted)
        return call("POST", "/companion/actions/${enc(confirmationId)}/confirm", body)
            .optString("status")
    }

    override suspend fun preferences(): CompanionPreferences =
        CompanionPreferences.from(call("GET", "/companion/prefs"))

    override suspend fun updatePreferences(patch: Map<String, Any?>): CompanionPreferences {
        val body = JSONObject()
        patch.forEach { (key, value) -> body.put(key, value ?: JSONObject.NULL) }
        return CompanionPreferences.from(call("PUT", "/companion/prefs", body))
    }

    override suspend fun inbox(): List<InboxMessage> =
        callArray("GET", "/companion/inbox?limit=20").toList { InboxMessage.from(it) }

    // ---- 传输 ----

    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject {
        val text = execute(method, path, body)
        return if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrNull()
            ?: throw CompanionApiException("响应格式不对", statusCode = null)
    }

    private suspend fun callArray(method: String, path: String): org.json.JSONArray {
        val text = execute(method, path, null)
        return if (text.isBlank()) org.json.JSONArray() else runCatching { org.json.JSONArray(text) }.getOrNull()
            ?: throw CompanionApiException("响应格式不对")
    }

    private suspend fun execute(method: String, path: String, body: JSONObject?): String =
        withContext(Dispatchers.IO) {
            val token = tokenProvider()?.takeIf { it.isNotBlank() }
                ?: throw CompanionApiException("未登录", statusCode = 401)
            val requestBody: RequestBody? = body?.toString()?.toRequestBody(JSON)
                ?: if (method == "POST" || method == "PUT") ByteArray(0).toRequestBody(JSON) else null
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .method(method, requestBody)
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw CompanionApiException(
                        message = messageFor(response.code),
                        statusCode = response.code,
                    )
                }
                text
            }
        }

    private fun messageFor(code: Int): String = when (code) {
        401 -> "登录已失效，请重新登录"
        403 -> "没有权限"
        404 -> "内容不存在或已删除"
        422 -> "提交的内容不符合要求"
        else -> "请求失败（$code）"
    }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

private inline fun <T> org.json.JSONArray?.toList(map: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(map) }
}
