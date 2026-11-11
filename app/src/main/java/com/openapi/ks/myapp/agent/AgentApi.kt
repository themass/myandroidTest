package com.openapi.ks.myapp.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** 会话与消息的 REST 调用（D §7.1，前缀 /api/agent/v1）。 */
interface AgentApi {
    suspend fun createSession(roleId: String, locale: String, name: String = "新会话"): String
    suspend fun submit(sessionId: String, text: String, clientMessageId: String): SubmitReceipt
    suspend fun cancel(sessionId: String)
    suspend fun queue(sessionId: String): List<QueuedInput>
    suspend fun withdraw(sessionId: String, messageId: String)
    suspend fun clearQueue(sessionId: String)
    suspend fun retryFailedQueue(sessionId: String)
    suspend fun retryTurn(sessionId: String, turnId: String)
    suspend fun continueReply(sessionId: String, messageId: String)
    suspend fun regenerate(sessionId: String, messageId: String)
    suspend fun history(sessionId: String, before: String?, limit: Int = 30): List<AgentBubble>
    /** 删除一条消息。回合进行中返回 409（code = conflict），稍后重试。 */
    suspend fun deleteMessage(messageId: String)
    /** 编辑自己的一条消息，返回更新后的消息。回合进行中返回 409。 */
    suspend fun editMessage(messageId: String, content: String): AgentBubble
    /** 切换到这条候选回复（左右滑动）。只能在最新一轮切换；回合进行中或不是最新一轮返回 409。 */
    suspend fun selectAlternative(messageId: String): AgentBubble
    suspend fun reminders(includeDone: Boolean = false): List<AgentReminder>
    suspend fun cancelReminder(reminderId: String)
    /** 回应一个待确认的动作（例如建立提醒）。确认后由服务端执行并通过事件流回报结果。 */
    suspend fun confirmAction(confirmationId: String, accepted: Boolean)
    /** 页面重新进入时恢复还没处理的待确认动作（事件流只在发生时推一次）。 */
    suspend fun pendingActions(): List<PendingConfirmation>
}

class AgentApiException(message: String, val statusCode: Int?, val code: String?) : Exception(message)

/**
 * OkHttp 实现。鉴权一律来自登录凭据（Bearer），请求体不带身份。
 * 错误体 `{detail: {code, reason}}` 解析成 [AgentApiException]，界面只看 code（客户端设计 4.2）。
 */
class HttpAgentApi(
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
    private val client: OkHttpClient,
    private val localeProvider: () -> String = { "zh-CN" },
) : AgentApi {

    override suspend fun createSession(roleId: String, locale: String, name: String): String {
        val json = call("POST", "/sessions", JSONObject().put("role_id", roleId).put("locale", locale).put("name", name))
        return json.getString("id")
    }

    override suspend fun submit(sessionId: String, text: String, clientMessageId: String): SubmitReceipt {
        val body = JSONObject().put("text", text).put("client_message_id", clientMessageId)
        val json = call("POST", "/sessions/$sessionId/messages", body)
        return SubmitReceipt(
            messageId = json.get("message_id").toString(),
            turnId = json.optNullableString("turn_id"),
            disposition = Disposition.parse(json.optNullableString("disposition")),
            queuePosition = null,
        )
    }

    override suspend fun cancel(sessionId: String) {
        call("POST", "/sessions/$sessionId/cancel", null)
    }

    override suspend fun queue(sessionId: String): List<QueuedInput> {
        val items = call("GET", "/sessions/$sessionId/queue", null).optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            QueuedInput(item.get("message_id").toString(), item.optString("text"), i + 1)
        }
    }

    override suspend fun withdraw(sessionId: String, messageId: String) {
        callNoBody("DELETE", "/sessions/$sessionId/queue/$messageId")
    }

    override suspend fun clearQueue(sessionId: String) {
        call("POST", "/sessions/$sessionId/queue/clear", null)
    }

    override suspend fun retryFailedQueue(sessionId: String) {
        call("POST", "/sessions/$sessionId/queue/retry", null)
    }

    override suspend fun retryTurn(sessionId: String, turnId: String) {
        call("POST", "/sessions/$sessionId/turns/$turnId/retry", null)
    }

    override suspend fun continueReply(sessionId: String, messageId: String) {
        call("POST", "/sessions/$sessionId/messages/$messageId/continue", null)
    }

    override suspend fun regenerate(sessionId: String, messageId: String) {
        call("POST", "/sessions/$sessionId/messages/$messageId/regenerate", null)
    }

    override suspend fun history(sessionId: String, before: String?, limit: Int): List<AgentBubble> {
        val query = buildString {
            append("?limit=$limit")
            if (!before.isNullOrEmpty()) append("&before=").append(enc(before))
        }
        val items = call("GET", "/sessions/$sessionId/messages$query", null).optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).map { items.getJSONObject(it) }
            .filter { it.optString("kind", "chat") != HIDDEN_KIND }
            .map { it.toBubble() }
    }

    override suspend fun deleteMessage(messageId: String) {
        callNoBody("DELETE", "/messages/$messageId")
    }

    override suspend fun editMessage(messageId: String, content: String): AgentBubble =
        call("PATCH", "/messages/$messageId", JSONObject().put("content", content)).toBubble()

    override suspend fun selectAlternative(messageId: String): AgentBubble =
        call("POST", "/messages/$messageId/select", null).toBubble()

    override suspend fun confirmAction(confirmationId: String, accepted: Boolean) {
        call("POST", "/actions/${enc(confirmationId)}/confirm", JSONObject().put("accepted", accepted))
    }

    override suspend fun pendingActions(): List<PendingConfirmation> {
        val items = call("GET", "/actions/pending", null).optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).map { items.getJSONObject(it) }.map {
            PendingConfirmation(it.optString("confirmation_id"), it.optString("title"), it.optString("summary"))
        }
    }

    override suspend fun reminders(includeDone: Boolean): List<AgentReminder> {
        val items = call("GET", "/reminders?include_done=$includeDone", null).optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).map { i ->
            val r = items.getJSONObject(i)
            AgentReminder(
                id = r.get("reminder_id").toString(), sessionId = r.get("session_id").toString(),
                content = r.optString("content"), dueLocal = r.optString("due_local"),
                timezone = r.optString("timezone"), status = r.optString("status"),
            )
        }
    }

    override suspend fun cancelReminder(reminderId: String) {
        callNoBody("DELETE", "/reminders/$reminderId")
    }

    private fun JSONObject.toBubble(): AgentBubble {
        val id = get("id").toString()
        val alts = optJSONArray("alt_ids")?.let { arr -> (0 until arr.length()).map { arr.get(it).toString() } }
            ?: emptyList()
        return AgentBubble(
            key = id, messageId = id, role = optString("role"), content = optString("content"),
            state = BubbleState.SYNCED, turnId = optNullableString("turn_id"),
            altIds = alts, altIndex = if (alts.isEmpty()) 0 else optInt("alt_index", 0),
        )
    }

    private suspend fun call(method: String, path: String, body: JSONObject?): JSONObject {
        val text = execute(method, path, body)
        return if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrNull()
            ?: throw AgentApiException("响应格式不对", null, null)
    }

    private suspend fun callNoBody(method: String, path: String) {
        execute(method, path, null)
    }

    private suspend fun execute(method: String, path: String, body: JSONObject?): String =
        withContext(Dispatchers.IO) {
            val token = tokenProvider()?.takeIf { it.isNotBlank() }
                ?: throw AgentApiException("未登录", 401, "unauthorized")
            val requestBody: RequestBody? = when {
                body != null -> body.toString().toRequestBody(JSON)
                method == "POST" || method == "PUT" || method == "PATCH" -> ByteArray(0).toRequestBody(JSON)
                else -> null
            }
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/api/agent/v1" + path)
                .method(method, requestBody)
                .header("Authorization", "Bearer $token")
                .header("Accept-Language", localeProvider())
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching { JSONObject(text).optJSONObject("detail") }.getOrNull()
                    throw AgentApiException(
                        message = detail?.optString("reason")?.takeIf { it.isNotEmpty() } ?: "请求失败（${response.code}）",
                        statusCode = response.code,
                        code = detail?.optString("code")?.takeIf { it.isNotEmpty() } ?: codeFor(response.code),
                    )
                }
                text
            }
        }

    private fun codeFor(status: Int): String = when (status) {
        401 -> "unauthorized"
        404 -> "not_found"
        409 -> "conflict"
        422 -> "validation"
        429 -> "rate_limited"
        503 -> "draining"
        else -> "unknown"
    }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

private fun JSONObject.optNullableString(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null
