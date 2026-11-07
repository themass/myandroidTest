package com.openapi.ks.myapp.data.roles

import android.content.Context
import com.openapi.roles.data.LocalRoleCatalog
import com.openapi.roles.data.RolesParser
import java.util.concurrent.ConcurrentHashMap

/**
 * 读取 APK 里 assets/personas/agency-agents-import.json，作为服务端目录失败或为空时的离线兜底。
 * 文件只读一次；按语言解析的结果缓存起来。
 */
class BundledRoleCatalog(private val context: Context) {

    private val body: String? by lazy {
        runCatching {
            context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
    }

    private val byLang = ConcurrentHashMap<String, LocalRoleCatalog>()

    /** 指定语言的兜底目录；资源缺失或解析失败返回 null。 */
    fun catalog(lang: String): LocalRoleCatalog? {
        byLang[lang]?.let { return it }
        val text = body ?: return null
        val parsed = RolesParser.parseLocalCatalog(text, lang) ?: return null
        byLang[lang] = parsed
        return parsed
    }

    private companion object {
        const val ASSET = "personas/agency-agents-import.json"
    }
}
