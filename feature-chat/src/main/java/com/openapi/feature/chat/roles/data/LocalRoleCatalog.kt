package com.openapi.feature.chat.roles.data

import com.openapi.feature.chat.roles.domain.RoleDivision
import com.openapi.feature.chat.roles.domain.RolePage
import com.openapi.feature.chat.roles.domain.RoleSummary

/**
 * 端上打包的离线角色目录（assets/personas/agency-agents-import.json 解析后的结果）。
 * 只在服务端失败或返回空时使用，服务端结果始终优先。纯 Kotlin，可在 JVM 上单测。
 */
class LocalRoleCatalog(
    val divisions: List<RoleDivision>,
    val roles: List<RoleSummary>,
) {

    /**
     * 与服务端 /roles/v1/roles 同样的筛选和分页语义，用于兜底。
     * 空 division 表示不限；query 按名称和简介做不区分大小写的包含匹配。
     */
    fun page(
        division: String?,
        query: String,
        featuredOnly: Boolean,
        limit: Int,
        offset: Int,
    ): RolePage {
        val q = query.trim()
        val filtered = roles.filter { r ->
            (division.isNullOrEmpty() || r.divisionId == division) &&
                (q.isEmpty() || r.name.contains(q, ignoreCase = true) || r.description.contains(q, ignoreCase = true)) &&
                (!featuredOnly || r.featured)
        }
        val safeOffset = offset.coerceAtLeast(0)
        val items = filtered.drop(safeOffset).take(limit.coerceAtLeast(0))
        return RolePage(items = items, total = filtered.size, offset = safeOffset)
    }
}
