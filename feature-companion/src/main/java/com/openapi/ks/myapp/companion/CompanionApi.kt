package com.openapi.ks.myapp.companion

import com.openapi.common.media.MediaUploader
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
    /**
     * 剧本房会话：进 `script-` 前缀房间，与 CLI `script_room` 会师同一 case。
     * 返回形状与 [createSession] 相同，走现有 connect / protocol 路径；不削弱 AGENT 门禁。
     */
    suspend fun createScriptSession(characterId: String, caseId: String): SessionResponse
    /**
     * 读回这段对话说过的话。
     *
     * 陪伴页的语音和文字写的是**同一段** `/v1` 聊天历史（服务端 `resolve_chat_session()`
     * 保证一个用户 + 一个角色只有一段），所以这里读到的既有语音回合，也有「对话」页里
     * 和同一个角色说过的话。不调用它的话，页面关掉重开就是空的——历史其实一直在写。
     */
    suspend fun history(sessionId: Long, limit: Int = 30): List<CompanionHistoryItem>
    /**
     * 按 upload_id 换一个**短期签名 GET URL**（服务端给 300 秒），用来渲染历史里的图片附件。
     *
     * 只带 id 回来、URL 现换，是因为签名 URL 生命周期极短：落进模型或缓存就是给用户留一张坏图。
     * 拿不到就返回 null（未登录、404、响应坏了、断网）——一张图换不到地址只让那一张退化成
     * 空路径（灰占位），不能让一张图把整页历史拖垮。URL 是 bearer secret：不进日志。
     *
     * 与「对话」页打的是同一套 `GET /v1/uploads/{id}`，实现委托 :common 的
     * [com.openapi.common.media.MediaUploader]，不在这里再写一遍 okhttp。
     */
    suspend fun attachmentUrl(uploadId: String): String?
    suspend fun characters(limit: Int = 20, offset: Int = 0): CharacterPage
    suspend fun character(id: String): CharacterDetail
    /**
     * her 注册表摘要（`GET /v1/hers`，服务端第一刀 ca_her_pack）：呈现实体的 renderer、
     * 包版本、特效/贴纸计数。引擎自动选择（[com.openapi.ks.myapp.dh.resolveCallStage]）读它。
     *
     * 服务端还没上这一刀时默认实现回空页——调用方（HerRegistryStore）因此永远拿到
     * 列表，老服务端零影响；网络失败由 store 宽出成过期缓存/空表，不白屏。
     */
    suspend fun hers(limit: Int = 20, offset: Int = 0): HerPage = HerPage(emptyList(), 0)
    /**
     * her 详情（`GET /v1/hers/{id}`，服务端第二刀）：形象包引用（model_ref）、装饰清单
     * （effects / stickers）、音色与人格卡片。详情抽屉（HerDetailSheet）与 HerPackSync 读它。
     *
     * 与 [hers] 同一纪律：服务端还没上这一刀时默认实现回 null——调用方（详情抽屉）显示
     * 「这份详情还取不到」，不白屏也不报错；老服务端零影响。HTTP 实现没上这一刀会抛 404，
     * 同样由调用方兜成这个空态。返回非 null 时字段缺省由各模型自己退化（见 [HerDetail.from]）。
     */
    suspend fun herDetail(herId: String): HerDetail? = null
    /** 记忆按角色隔离：必须带 roleId，否则读到的是「不属于任何角色」的空空间。 */
    suspend fun memory(roleId: String, limit: Int = 20, offset: Int = 0): FactPage
    suspend fun deleteMemory(roleId: String, id: String)
    /**
     * 她对你的了解（画像）。和一条条记忆分开，是服务端归纳的结果。
     *
     * 服务端还没上这一块时返回空列表即可——记忆页据此不显示画像标签，
     * 整页也不会因为这块读不到而失败。
     */
    suspend fun profile(roleId: String, includeHistory: Boolean = false): List<ProfileEntry> = emptyList()
    /**
     * AI 对你的档案（Markdown 长文）。服务端按 (用户, 角色) 存一份；读不到时服务端
     * 返回空 markdown + null version，据此显示空态而不是报错。
     */
    suspend fun profileDoc(roleId: String): ProfileDoc
    /** 用户改写档案：整篇覆盖（服务端版本号 +1，AI 下一批以新内容为底稿）。 */
    suspend fun updateProfileDoc(roleId: String, markdown: String)
    suspend fun suppressProfileEntry(roleId: String, id: String) = Unit
    suspend fun deleteProfileEntry(roleId: String, id: String) = Unit
    suspend fun events(roleId: String, includePast: Boolean = false): List<TimelineEventItem>
    suspend fun addEvent(roleId: String, title: String, atIso: String, kind: String = "other"): TimelineEventItem
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
) : CompanionApi {

    /**
     * 换签名 URL 的实现提公在 :common 的 [MediaUploader]（「对话」页打的是同一个
     * `/v1/uploads/{id}`）。复用外面的 [client]，不另建连接池，也不在这里再写一遍 okhttp。
     */
    private val mediaUploader = MediaUploader(baseUrl, tokenProvider, client)

    override suspend fun createSession(characterId: String): SessionResponse {
        // 建 LiveKit 房间与聊天会话是两种资源，前者在 /v1/voice 下
        val json = call("POST", "/v1/voice/sessions", JSONObject().put("character_id", characterId))
        return parseSessionResponse(json)
    }

    override suspend fun createScriptSession(characterId: String, caseId: String): SessionResponse {
        val body = JSONObject()
            .put("character_id", characterId)
            .put("case_id", caseId)
        val json = call("POST", "/v1/voice/script-sessions", body)
        return parseSessionResponse(json)
    }

    private fun parseSessionResponse(json: JSONObject): SessionResponse = SessionResponse(
        sessionId = json.optString("session_id"),
        roomName = json.optString("room_name"),
        token = json.optString("token"),
        livekitUrl = json.optString("livekit_url"),
        expiresAt = json.optString("expires_at"),
        // 服务端可能给 null（没绑定聊天会话时），用 0 表示「没有」而不是猜一个 id
        chatSessionId = json.optLong("chat_session_id", 0L).takeIf { it > 0 },
        // 线路选择权在服务端；这里只原样收下。老服务端不返回这个字段 → 归一成 livekit，
        // 下游（工厂、测试）因此永远不必处理空串。
        provider = json.optString("provider").takeIf { it.isNotBlank() }
            ?: CompanionTransports.PROVIDER_LIVEKIT,
        providerConfig = json.optJSONObject("provider_config"),
    )

    override suspend fun history(sessionId: Long, limit: Int): List<CompanionHistoryItem> {
        val json = call("GET", "/v1/sessions/$sessionId/messages?limit=$limit")
        val items = json.optJSONArray("items") ?: return emptyList()
        // 服务端是按新→旧返回的（实测先 assistant 再 user），界面上要正序
        return (0 until items.length()).mapNotNull { i ->
            // optJSONObject：条目不是对象（坏数据）时跳过，而不是抛出一个 JSONException 把整页历史弄没
            items.optJSONObject(i)?.let { CompanionHistoryItem.from(it) }
        }.filter {
            // 没字也没附件的行没有可画的东西，照旧隐藏。但**只有附件**的行必须留下：
            // 「对话」页和陪伴页读的是同一段历史，那边能发纯图消息，在这儿丢掉就等于
            // 用户自己发的图片在这边凭空消失。
            it.text.isNotBlank() || it.attachmentIds.isNotEmpty()
        }.reversed()
    }

    override suspend fun attachmentUrl(uploadId: String): String? = mediaUploader.signedUrl(uploadId)

    override suspend fun characters(limit: Int, offset: Int): CharacterPage {
        val json = call("GET", "/v1/characters?limit=$limit&offset=$offset")
        return CharacterPage(
            items = json.optJSONArray("items").toList { CharacterSummary.from(it) },
            total = json.optInt("total"),
        )
    }

    override suspend fun character(id: String): CharacterDetail =
        CharacterDetail.from(call("GET", "/v1/characters/${enc(id)}"))

    override suspend fun hers(limit: Int, offset: Int): HerPage {
        val json = call("GET", "/v1/hers?limit=$limit&offset=$offset")
        return HerPage(
            items = json.optJSONArray("items").toList { HerSummary.from(it) },
            total = json.optInt("total"),
        )
    }

    override suspend fun herDetail(herId: String): HerDetail =
        HerDetail.from(call("GET", "/v1/hers/${enc(herId)}"))

    override suspend fun memory(roleId: String, limit: Int, offset: Int): FactPage {
        // 按角色隔离：带上 role_id，否则读到的是用户级空空间
        val json = call("GET", "/v1/memory/facts?role_id=${enc(roleId)}&limit=$limit&offset=$offset")
        return FactPage(
            items = json.optJSONArray("items").toList { MemoryFact.from(it) },
            hasMore = json.optBoolean("has_more"),
        )
    }

    override suspend fun deleteMemory(roleId: String, id: String) {
        call("DELETE", "/v1/memory/facts/${enc(id)}?role_id=${enc(roleId)}")
    }

    override suspend fun profile(roleId: String, includeHistory: Boolean): List<ProfileEntry> {
        // 画像同样按角色隔离：不传 role_id 读到的是用户级空间（空的）
        val json = call("GET", "/v1/memory/profile?role_id=${enc(roleId)}&include_history=$includeHistory")
        return json.optJSONArray("items").toList { ProfileEntry.from(it) }
    }

    override suspend fun profileDoc(roleId: String): ProfileDoc =
        ProfileDoc.from(call("GET", "/v1/memory/profile_doc?role_id=${enc(roleId)}"))

    override suspend fun updateProfileDoc(roleId: String, markdown: String) {
        call("PUT", "/v1/memory/profile_doc?role_id=${enc(roleId)}", JSONObject().put("markdown", markdown))
    }

    override suspend fun suppressProfileEntry(roleId: String, id: String) {
        call("POST", "/v1/memory/profile/${enc(id)}/suppress?role_id=${enc(roleId)}")
    }

    override suspend fun deleteProfileEntry(roleId: String, id: String) {
        call("DELETE", "/v1/memory/profile/${enc(id)}?role_id=${enc(roleId)}")
    }

    override suspend fun events(roleId: String, includePast: Boolean): List<TimelineEventItem> {
        // 新接口统一是 {"items": [...]} 信封，不再是裸数组
        val json = call("GET", "/v1/memory/events?role_id=${enc(roleId)}&include_past=$includePast")
        return json.optJSONArray("items").toList { TimelineEventItem.from(it) }
    }

    override suspend fun addEvent(roleId: String, title: String, atIso: String, kind: String): TimelineEventItem {
        val body = JSONObject().put("title", title).put("at", atIso).put("kind", kind)
        return TimelineEventItem.from(call("POST", "/v1/memory/events?role_id=${enc(roleId)}", body))
    }

    override suspend fun setEventStatus(id: String, status: String) {
        // 状态转换：旧接口是 POST /{status}，新接口是 PATCH + body
        call("PATCH", "/v1/memory/events/${enc(id)}", JSONObject().put("status", status))
    }

    override suspend fun deleteEvent(id: String) {
        call("DELETE", "/v1/memory/events/${enc(id)}")
    }

    override suspend fun pendingActions(): List<PendingAction> =
        call("GET", "/v1/actions/pending").optJSONArray("items").toList { PendingAction.from(it) }

    override suspend fun confirmAction(confirmationId: String, accepted: Boolean): String {
        val body = JSONObject().put("accepted", accepted)
        return call("POST", "/v1/actions/${enc(confirmationId)}/confirm", body)
            .optString("status")
    }

    override suspend fun preferences(): CompanionPreferences =
        CompanionPreferences.from(call("GET", "/v1/prefs"))

    override suspend fun updatePreferences(patch: Map<String, Any?>): CompanionPreferences {
        val body = JSONObject()
        patch.forEach { (key, value) -> body.put(key, value ?: JSONObject.NULL) }
        return CompanionPreferences.from(call("PUT", "/v1/prefs", body))
    }

    override suspend fun inbox(): List<InboxMessage> =
        // 收件箱在新接口里仍是裸数组
        callArray("GET", "/v1/inbox?limit=20").toList { InboxMessage.from(it) }

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
                        message = messageFor(response.code, looksLikeJson(text)),
                        statusCode = response.code,
                    )
                }
                text
            }
        }

    /**
     * 业务服务返回的错误体是 JSON；网关（nginx）自己生成的 404/5xx 是 HTML 页面。
     * 服务没部署时 nginx 回 404 HTML，不能提示成「内容不存在或已删除」——用户会以为自己的数据没了。
     */
    private fun looksLikeJson(text: String): Boolean = text.trimStart().let { it.startsWith("{") || it.startsWith("[") }

    private fun messageFor(code: Int, jsonBody: Boolean): String = when {
        code == 401 -> "登录已失效，请重新登录"
        code == 403 -> "没有权限"
        code == 404 && jsonBody -> "内容不存在或已删除"
        code == 404 || code == 502 || code == 503 || code == 504 -> "陪伴服务暂时不可用，请稍后再试"
        code == 422 -> "提交的内容不符合要求"
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
