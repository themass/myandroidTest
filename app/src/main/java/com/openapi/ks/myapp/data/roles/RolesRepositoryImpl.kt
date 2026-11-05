package com.openapi.ks.myapp.data.roles

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.openapi.core.util.PreferenceUtils
import com.openapi.ks.myapp.constant.Constants
import com.openapi.roles.data.RolesParser
import com.openapi.roles.data.RolesRepository
import com.openapi.roles.domain.RoleDivision
import com.openapi.roles.domain.RolePage
import com.openapi.roles.domain.RoleSummary
import com.openapi.roles.domain.SelectedRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 角色目录的 OkHttp 实现。目录是公开只读内容（路径不带 .json，不走旧的加密/登录链路），
 * 直接取明文 JSON。
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
        if (Locale.getDefault().language.equals("zh", ignoreCase = true)) "zh" else "en"

    override suspend fun divisions(): List<RoleDivision>? {
        val url = Constants.getUrl("/roles/v1/divisions").toHttpUrl().newBuilder()
            .addQueryParameter("lang", lang())
            .build()
        return get(url.toString())?.let(RolesParser::parseDivisions)
    }

    override suspend fun roles(
        division: String?,
        query: String,
        featuredOnly: Boolean,
        limit: Int,
        offset: Int,
    ): RolePage? {
        val url = Constants.getUrl("/roles/v1/roles").toHttpUrl().newBuilder()
            .addQueryParameter("lang", lang())
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .apply {
                if (!division.isNullOrEmpty()) addQueryParameter("division", division)
                if (query.isNotBlank()) addQueryParameter("q", query.trim())
                if (featuredOnly) addQueryParameter("featured", "true")
            }
            .build()
        return get(url.toString())?.let(RolesParser::parseRolePage)
    }

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
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
        const val STORE = "roles_store"
        /** 与旧实现保持同一个 key，升级后已选的角色不丢。 */
        const val PREF_SELECTED_ROLE = "chat_selected_catalog_role"
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENTS = "recents"
        const val KEY_SESSION_PREFIX = "session_role_"
        const val MAX_RECENTS = 12
    }
}
