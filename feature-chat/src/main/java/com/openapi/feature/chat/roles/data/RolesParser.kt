package com.openapi.feature.chat.roles.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.openapi.feature.chat.roles.domain.RoleDivision
import com.openapi.feature.chat.roles.domain.RolePage
import com.openapi.feature.chat.roles.domain.RoleSummary

/**
 * /api/roles/v1 响应的解析。纯函数，不碰 Android 类，可以直接在 JVM 上单测。
 *
 * 响应格式：{code, data, message}，code 为 0 表示成功。非 0、缺字段、不是 JSON 都按失败处理，
 * 返回 null，由调用方决定提示什么，而不是把半截数据塞进界面。
 */
object RolesParser {

    fun parseDivisions(body: String): List<RoleDivision>? {
        val data = data(body) ?: return null
        if (!data.isJsonArray) return null
        return data.asJsonArray.mapNotNull { el ->
            val o = el.asObjectOrNull() ?: return@mapNotNull null
            val id = o.string("id") ?: return@mapNotNull null
            RoleDivision(
                id = id,
                label = o.string("label") ?: id,
                icon = o.string("icon").orEmpty(),
                colorArgb = RoleColors.parse(o.string("color")),
                count = o.int("count"),
            )
        }
    }

    fun parseRolePage(body: String): RolePage? {
        val data = data(body)?.asObjectOrNull() ?: return null
        val items = data.get("items")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        return RolePage(
            items = items.mapNotNull { parseRole(it) },
            total = data.int("total"),
            offset = data.int("offset"),
        )
    }

    fun parseRole(body: String): RoleSummary? {
        val data = data(body) ?: return null
        return parseRole(data)
    }

    /**
     * 解析端上打包的离线目录文件（无 {code,data} 外壳，顶层是 divisions / roles）。
     * 名称和简介按 [lang] 取：zh 用 name/description，其它语言用 name_en/description_en，缺失时回退到 name/description。
     * 分类的 count 按实际条目数重新计算，忽略文件里的值。文件结构不对返回 null。
     */
    fun parseLocalCatalog(body: String, lang: String): LocalRoleCatalog? {
        val root = runCatching { JsonParser.parseString(body) }.getOrNull()?.asObjectOrNull() ?: return null
        val rolesJson = root.get("roles")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val zh = lang.equals("zh", ignoreCase = true)
        val roles = rolesJson.mapNotNull { el ->
            val o = el.asObjectOrNull() ?: return@mapNotNull null
            val id = o.string("id") ?: return@mapNotNull null
            val name = if (zh) o.string("name") ?: o.string("name_en") else o.string("name_en") ?: o.string("name")
            if (name == null) return@mapNotNull null
            val description = if (zh) o.string("description") ?: o.string("description_en").orEmpty()
            else o.string("description_en") ?: o.string("description").orEmpty()
            RoleSummary(
                id = id,
                divisionId = o.string("division").orEmpty(),
                name = name,
                description = description,
                emoji = o.string("emoji").orEmpty(),
                colorArgb = RoleColors.parse(o.string("color")),
                vibe = o.string("vibe").orEmpty(),
                featured = o.get("featured")?.let { !it.isJsonNull && it.asBoolean } ?: false,
                local = true,
            )
        }
        val divisionsJson = root.get("divisions")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
        val divisions = divisionsJson.mapNotNull { el ->
            val o = el.asObjectOrNull() ?: return@mapNotNull null
            val id = o.string("id") ?: return@mapNotNull null
            RoleDivision(
                id = id,
                label = o.string("label") ?: id,
                icon = o.string("icon").orEmpty(),
                colorArgb = RoleColors.parse(o.string("color")),
                count = roles.count { it.divisionId == id },
            )
        }.filter { it.count > 0 }
        return LocalRoleCatalog(divisions = divisions, roles = roles)
    }

    private fun parseRole(el: JsonElement): RoleSummary? {
        val o = el.asObjectOrNull() ?: return null
        val id = o.string("id") ?: return null
        val name = o.string("name") ?: return null
        return RoleSummary(
            id = id,
            divisionId = o.string("division").orEmpty(),
            name = name,
            description = o.string("description").orEmpty(),
            emoji = o.string("emoji").orEmpty(),
            colorArgb = RoleColors.parse(o.string("color")),
            vibe = o.string("vibe").orEmpty(),
            featured = o.get("featured")?.let { !it.isJsonNull && it.asBoolean } ?: false,
        )
    }

    private fun data(body: String): JsonElement? {
        val root = runCatching { JsonParser.parseString(body) }.getOrNull()?.asObjectOrNull() ?: return null
        if (root.int("code", default = -1) != 0) return null
        return root.get("data")?.takeIf { !it.isJsonNull }
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.int(key: String, default: Int = 0): Int =
        get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() } ?: default
}
