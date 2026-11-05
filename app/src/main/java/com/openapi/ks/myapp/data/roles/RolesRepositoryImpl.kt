package com.openapi.ks.myapp.data.roles

import android.content.Context
import com.google.gson.Gson
import com.openapi.core.util.PreferenceUtils
import com.openapi.ks.myapp.constant.Constants
import com.openapi.roles.data.RolesParser
import com.openapi.roles.data.RolesRepository
import com.openapi.roles.domain.RoleDivision
import com.openapi.roles.domain.RolePage
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
 * 所以这里不用 Volley 那一套，直接取明文 JSON。
 */
class RolesRepositoryImpl(private val context: Context) : RolesRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

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

    override fun selected(): SelectedRole? {
        val json = PreferenceUtils.getPrefString(context, PREF_SELECTED_ROLE, "")
        if (json.isNullOrEmpty()) return null
        return runCatching { gson.fromJson(json, SelectedRole::class.java) }.getOrNull()
            ?.takeIf { it.id.isNotEmpty() }
    }

    override fun setSelected(role: SelectedRole?) {
        PreferenceUtils.setPrefString(context, PREF_SELECTED_ROLE, role?.let { gson.toJson(it) } ?: "")
    }

    private companion object {
        const val PREF_SELECTED_ROLE = "chat_selected_catalog_role"
    }
}
