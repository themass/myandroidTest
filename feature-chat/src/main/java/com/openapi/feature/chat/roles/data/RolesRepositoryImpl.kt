package com.openapi.feature.chat.roles.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.common.util.log.LogUtil
import com.openapi.common.net.http.NetConfig
import com.openapi.feature.chat.roles.data.RolesParser
import com.openapi.feature.chat.roles.data.RolesRepository
import com.openapi.feature.chat.roles.domain.RoleDivision
import com.openapi.feature.chat.roles.domain.RolePage
import com.openapi.feature.chat.roles.domain.RoleSummary
import com.openapi.feature.chat.roles.domain.SelectedRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 角色目录的 OkHttp 实现。
 *
 * **数据源是 Python companion-agent 的 `/v1`，不是 Java 的 `/vvv/api/roles/v1`。**
 * 原因不是风格问题：对话已经整体迁到 `/v1`，而服务端按 role_id 加载角色卡时，
 * 找不到就**不报错**、直接让回合产出空回复。所以选择器列出来的 id 必须与服务端
 * 那一份完全一致，否则用户选了个看起来正常的角色，得到的是一条空消息。
 * 旧 Java 端点还一直返回 400（缺 `-parameters` 编译参数），早就只剩兜底意义。
 *
 * 鉴权用 Bearer（与 `/v1` 其它接口一致），语言走 `Accept-Language`。
 * 目录是只读内容，但 `/v1/roles` 本身要求登录态，所以没 token 时直接走打包目录。
 *
 * 收藏、最近使用、会话级角色都存在本地，并保存角色摘要，所以离线也能列出来。
 */
class RolesRepositoryImpl(private val context: Context) : RolesRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val store = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    /** 目录有中文名称/简介时按系统语言取中文，缺失的条目服务端自动回退英文。 */
    private fun lang(): String =
        if (Locale.getDefault().language.equals("zh", ignoreCase = true)) "zh-CN" else "en"

    private fun token(): String? =
        PreferenceUtils.getPrefString(context, NetConfig.tokenHeader(), null)?.takeIf { it.isNotBlank() }

    private val bundled by lazy { BundledRoleCatalog(context.applicationContext) }

    /**
     * 服务端优先。服务端失败或返回空时，用打包的离线目录兜底，保证选择器离线也不是空的。
     * 两边都没有时，原样返回服务端结果（失败仍是 null，空仍是空列表）。
     */
    override suspend fun divisions(): List<RoleDivision>? {
        // /v1/divisions 的 label 直接按 Accept-Language 返回，不需要再传 lang 参数
        val server = get("/v1/divisions", emptyMap())?.let(RolesParser::parseAgentDivisions)
        if (!server.isNullOrEmpty()) return server
        val local = bundled.catalog(lang())?.divisions
        if (!local.isNullOrEmpty()) {
            // 兜底必须留痕：以前这里完全静默，于是「服务端目录坏了」和「本来就只有这些角色」
            // 长得一模一样，选择器里出现服务端不认识的 id 也查不出来。
            LogUtil.i(TAG, "role catalog unavailable, using bundled divisions (${local.size})")
            return local
        }
        LogUtil.e("role catalog divisions failed and no bundled copy")
        return server
    }
    override suspend fun roles(
        division: String?,
        query: String,
        featuredOnly: Boolean,
        limit: Int,
        offset: Int,
    ): RolePage? {
        val params = buildMap {
            put("limit", limit.toString())
            put("offset", offset.toString())
            if (!division.isNullOrEmpty()) put("division", division)
            if (query.isNotBlank()) put("q", query.trim())
            if (featuredOnly) put("featured", "true")
        }
        val server = get("/v1/roles", params)?.let(RolesParser::parseAgentRolePage)
        if (server != null && server.items.isNotEmpty()) return server
        val local = bundled.catalog(lang())?.page(division, query, featuredOnly, limit, offset)
        if (local != null && local.items.isNotEmpty()) {
            LogUtil.i(TAG, "role catalog unavailable, using bundled roles (${local.items.size})")
            return local
        }
        return server
    }

    /**
     * 请求 `/v1` 的一个只读路径。
     *
     * 没登录态时直接返回 null（调用方会退回打包目录）：`/v1/roles` 要 Bearer，
     * 拿一个空 token 去请求只会拿到 401，多一次无意义的往返。
     */
    private suspend fun get(path: String, params: Map<String, String>): String? = withContext(Dispatchers.IO) {
        val token = token() ?: return@withContext null
        runCatching {
            val url = (BASE_URL + path).toHttpUrl().newBuilder()
                .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
                .build()
            client.newCall(
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .header("Accept-Language", lang())
                    .get()
                    .build(),
            ).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else {
                    LogUtil.i(TAG, "GET $path -> HTTP ${resp.code}")
                    null
                }
            }
        }.getOrNull()
    }

    override fun selected(): SelectedRole? =
        readSelected(PreferenceUtils.getPrefString(context, PREF_SELECTED_ROLE, ""))

    override fun setSelected(role: SelectedRole?) {
        PreferenceUtils.setPrefString(context, PREF_SELECTED_ROLE, role?.let { gson.toJson(it) } ?: "")
    }

    override fun selectedFor(sessionId: Long): SelectedRole? =
        readSelected(store.getString(sessionKey(sessionId), null))

    override fun setSelectedFor(sessionId: Long, role: SelectedRole?) {
        store.edit().putString(sessionKey(sessionId), role?.let { gson.toJson(it) }).apply()
    }

    override fun favorites(): List<RoleSummary> = readList(KEY_FAVORITES)

    override fun isFavorite(roleId: String): Boolean = favorites().any { it.id == roleId }

    override fun toggleFavorite(role: RoleSummary): Boolean {
        val current = favorites()
        val exists = current.any { it.id == role.id }
        val next = if (exists) current.filterNot { it.id == role.id } else listOf(role) + current
        writeList(KEY_FAVORITES, next)
        return !exists
    }

    override fun recents(): List<RoleSummary> = readList(KEY_RECENTS)

    override fun recordUse(role: RoleSummary) {
        val next = (listOf(role) + recents().filterNot { it.id == role.id }).take(MAX_RECENTS)
        writeList(KEY_RECENTS, next)
    }

    private fun readSelected(json: String?): SelectedRole? {
        if (json.isNullOrEmpty()) return null
        return runCatching { gson.fromJson(json, SelectedRole::class.java) }.getOrNull()
            ?.takeIf { it.id.isNotEmpty() }
    }

    private fun readList(key: String): List<RoleSummary> {
        val json = store.getString(key, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<RoleSummary>>(json, object : TypeToken<List<RoleSummary>>() {}.type)
        }.getOrNull().orEmpty()
    }

    private fun writeList(key: String, roles: List<RoleSummary>) {
        store.edit().putString(key, gson.toJson(roles)).apply()
    }

    private fun sessionKey(sessionId: Long) = "$KEY_SESSION_PREFIX$sessionId"

    private companion object {
        const val TAG = "RolesRepository"
        const val STORE = "roles_store"
        /** 与旧实现保持同一个 key，升级后已选的角色不丢。 */
        const val PREF_SELECTED_ROLE = "chat_selected_catalog_role"
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENTS = "recents"
        const val KEY_SESSION_PREFIX = "session_role_"
        const val MAX_RECENTS = 12

        /**
         * `/v1` 与 Java 的 `/vvv/api` 同域不同前缀。用主站域名拼，
         * 与 `CompanionConfig.baseUrl` 是同一个地址，避免两处各写一份。
         */
        val BASE_URL: String get() = "https://" + com.openapi.core.config.Constants.BASE_IP
    }
}
