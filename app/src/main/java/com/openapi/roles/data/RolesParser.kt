package com.openapi.roles.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.openapi.roles.domain.RoleDivision
import com.openapi.roles.domain.RolePage
import com.openapi.roles.domain.RoleSummary

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
