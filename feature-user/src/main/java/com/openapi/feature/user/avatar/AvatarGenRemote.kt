package com.openapi.feature.user.avatar

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 二次元头像的两个服务端动作（`/v1/avatars/generate` / `/v1/avatars/apply`），契约见
 * `docs/design/2026-11-24-anime-avatar-mvp.md`。
 *
 * 形状与 `AgentPhotoSolveRemote` 同款：OkHttp + Bearer + 纯 JSON（Gson，不用 org.json——
 * JVM 单测里 org.json 是 stub，Gson 不用套 Robolectric）。生成侧 provider 的切换是服务端
 * 职责（`AnimeStyleProvider` 抽象），端只见这一个稳定契约。
 *
 * **生成是同步语义（MVP）**：一次请求内完成，readTimeout 给到 120s（模型出图比解题慢）。
 * 以后改异步任务（job id + 轮询）只换这个类，[AvatarFlow] 状态机不动。
 */
class AvatarGenException(val reason: AvatarUserReason) : Exception("avatar generation failed: ${reason.name}")

class AvatarGenRemote(
    baseUrl: String,
    private val tokenProvider: () -> String?,
    private val calls: OkHttpClient = defaultClient(),
) {
    private val base = baseUrl.trimEnd('/') + "/v1"

    /**
     * 用已上传的源图生成二次元风格头像，返回结果的 `upload_id`（可再走 `/v1/uploads/{id}` 换预览 URL）。
     *
     * @throws AvatarGenException 未登录 / 图片不合格 / 配额 / 服务不可用
     * @throws IOException 网络层失败（调用方按 NETWORK 话术处理）
     */
    @Throws(AvatarGenException::class, IOException::class)
    suspend fun generate(sourceUploadId: String, style: String): String = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            addProperty("source_upload_id", sourceUploadId)
            addProperty("style", style)
        }
        val text = post("/avatars/generate", body)
        val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
        val id = json?.get("avatar_upload_id")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        if (id.isEmpty()) throw AvatarGenException(AvatarUserReason.SERVICE_UNAVAILABLE)
        id
    }

    /**
     * 把生成结果设为当前头像（服务端真相）。成功后调用方才写本地指针——
     * 「失败不覆盖旧头像」的红线在服务端确认之后才允许本地换。
     */
    @Throws(AvatarGenException::class, IOException::class)
    suspend fun apply(avatarUploadId: String): Unit = withContext(Dispatchers.IO) {
        val body = JsonObject().apply { addProperty("avatar_upload_id", avatarUploadId) }
        post("/avatars/apply", body)
        Unit
    }

    /**
     * 下载生成结果字节（应用前本地落盘用）。签名 URL 本身就是凭证：**不带 Authorization、不进日志**。
     * 任何失败返回 null，调用方按「服务暂不可用，重试」处理。
     */
    suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).get().build()
            calls.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.bytes()
            }
        }.getOrNull()
    }

    private fun post(path: String, body: JsonObject): String {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: throw AvatarGenException(AvatarUserReason.NOT_LOGGED_IN)
        val request = Request.Builder()
            .url(base + path)
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        calls.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw AvatarGenException(mapStatus(response.code, text))
            return text
        }
    }

    /**
     * `/v1` 错误形状 `{"detail": {"code": "..."}}` 只用来分类，**绝不把服务端原始 body 透给用户**
     * （同 photosolve 的 decode 纪律：网关异常、URL、provider payload 都不外泄）。
     */
    private fun mapStatus(status: Int, body: String): AvatarUserReason {
        val code = runCatching {
            JsonParser.parseString(body).asJsonObject.getAsJsonObject("detail")?.get("code")?.asString
        }.getOrNull()
        return when {
            status == 401 || status == 403 -> AvatarUserReason.NOT_LOGGED_IN
            status == 413 || status == 415 || status == 422 -> AvatarUserReason.BAD_IMAGE
            status == 429 || code?.startsWith("quota") == true -> AvatarUserReason.QUOTA
            else -> AvatarUserReason.SERVICE_UNAVAILABLE
        }
    }

    companion object {
        /** 生成比解题慢：read 放宽到 120s，call 兜底 150s，界面不会无限转圈。 */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .build()
    }
}
