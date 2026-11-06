package com.openapi.ks.myapp.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/** 创建会话。失败时抛 [CompanionApiException]，由 ViewModel 转成可显示的错误。 */
interface CompanionApi {
    suspend fun createSession(characterId: String): SessionResponse
}

class CompanionApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 通过 HTTP 调 companion 服务。
 *
 * baseUrl 由调用方注入，不在这里写死：同一份代码要跑测试、预发和生产三套服务，
 * 地址进常量或构建配置，不进这个类。
 */
class HttpCompanionApi(
    private val baseUrl: String,
    private val userIdProvider: () -> String?,
    private val client: OkHttpClient = OkHttpClient(),
) : CompanionApi {

    override suspend fun createSession(characterId: String): SessionResponse =
        withContext(Dispatchers.IO) {
            val userId = userIdProvider()
                ?: throw CompanionApiException("未登录，无法创建会话")
            val body = JSONObject()
                .put("user_id", userId)
                .put("character_id", characterId)
                .toString()
                .toRequestBody(JSON)
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/companion/sessions")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw CompanionApiException("创建会话失败（${response.code}）")
                }
                val json = runCatching { JSONObject(text) }.getOrNull()
                    ?: throw CompanionApiException("会话响应格式不对")
                SessionResponse(
                    sessionId = json.optString("session_id"),
                    roomName = json.optString("room_name"),
                    token = json.optString("token"),
                    livekitUrl = json.optString("livekit_url"),
                    expiresAt = json.optString("expires_at"),
                )
            }
        }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
