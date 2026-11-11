package com.openapi.ks.myapp.avatar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder

/** 服务端上线的一个形象（GET /api/agent/v1/avatars 的一项）。 */
data class RemoteAvatar(
    val avatarKey: String,
    val version: Int,
    val name: String,
    val roleId: String?,
    val sha256: String,
    val sizeBytes: Long,
    val downloadUrl: String,
)

/**
 * 形象同步：向服务端问「这个角色现在有哪些上线的形象」，本地没有或版本更旧就下载安装。
 *
 * - 登录凭据只发给我们自己的接口；下载地址是对象存储的临时签名链接，**不带**登录凭据。
 * - 任何失败（断网、校验不过、包损坏）都保持本地已有的形象不变，返回当前可用的那个（可能是 null，
 *   调用方回退到内置默认形象）。
 * - 服务端下线的形象下次同步时不再出现；本地副本是否保留由 [AvatarPackStore] 的调用方决定，这里不删。
 */
class AvatarSync(
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
    private val client: OkHttpClient,
    private val store: AvatarPackStore,
    private val cacheDir: File,
    private val appVersion: Int,
) {
    /** 同步并返回该角色应使用的素材；没有任何可用形象返回 null（用内置默认）。 */
    suspend fun syncFor(roleId: String): AvatarFiles? = withContext(Dispatchers.IO) {
        val remote = runCatching { list(roleId) }.getOrNull()
        if (remote != null) {
            // 绑定了该角色的排在前面（服务端已排序）；取第一个作为这个角色的形象
            val chosen = remote.firstOrNull()
            if (chosen != null) {
                runCatching { ensureInstalled(chosen) }
                if (store.installedVersion(chosen.avatarKey) != null) store.bind(roleId, chosen.avatarKey)
            }
        }
        store.avatarFor(roleId)?.let { store.load(it) }
    }

    /** 本地已安装的、这个角色上次用的形象（不联网）。没有返回 null。 */
    fun localFor(roleId: String): AvatarFiles? = store.avatarFor(roleId)?.let { store.load(it) }

    private fun list(roleId: String): List<RemoteAvatar> {
        val token = tokenProvider() ?: throw IOException("未登录")
        val url = "$baseUrl/api/agent/v1/avatars?role_id=${URLEncoder.encode(roleId, "UTF-8")}&app_version=$appVersion"
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val items = JSONObject(response.body?.string().orEmpty()).optJSONArray("items") ?: return emptyList()
            return (0 until items.length()).map { items.getJSONObject(it) }.mapNotNull { o ->
                RemoteAvatar(
                    avatarKey = o.optString("avatar_key"), version = o.optInt("version", 0), name = o.optString("name"),
                    roleId = o.optString("role_id").takeIf { it.isNotEmpty() && it != "null" },
                    sha256 = o.optString("sha256"), sizeBytes = o.optLong("size_bytes", 0),
                    downloadUrl = o.optString("download_url"),
                ).takeIf { it.avatarKey.isNotEmpty() && it.version > 0 && it.downloadUrl.startsWith("http") }
            }
        }
    }

    private fun ensureInstalled(avatar: RemoteAvatar) {
        val installed = store.installedVersion(avatar.avatarKey)
        if (installed != null && installed >= avatar.version) return
        val tmp = File.createTempFile("avatar-", ".zip", cacheDir)
        try {
            // 签名链接本身就是授权：不要把登录凭据带给对象存储
            client.newCall(Request.Builder().url(avatar.downloadUrl).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("下载失败 HTTP ${response.code}")
                val body = response.body ?: throw IOException("空响应")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            store.install(avatar.avatarKey, avatar.version, avatar.sha256, tmp)
        } finally {
            tmp.delete()
        }
    }
}
