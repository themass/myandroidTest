package com.openapi.roles.domain

/** 角色分类（对应服务端 /api/roles/v1/divisions）。 */
data class RoleDivision(
    val id: String,
    val label: String,
    val icon: String,
    val colorArgb: Long,
    val count: Int,
)

/**
 * 目录里的一个角色。只有展示用字段：提示词留在服务端，端上选中后只传 [id]。
 */
data class RoleSummary(
    val id: String,
    val divisionId: String,
    val name: String,
    val description: String,
    val emoji: String,
    val colorArgb: Long,
    val vibe: String,
    val featured: Boolean,
    /** 来自端上打包的离线兜底目录（而不是服务端返回）。UI 可据此标注；选中后服务端需认得这个 id。 */
    val local: Boolean = false,
)

data class RolePage(
    val items: List<RoleSummary>,
    val total: Int,
    val offset: Int,
) {
    /** 还有没拉到的角色。 */
    val hasMore: Boolean get() = offset + items.size < total
}

/**
 * 当前选中的目录角色。名称和 emoji 随 id 一起存本地，这样对话页头部不用等目录请求回来才有内容。
 */
data class SelectedRole(
    val id: String,
    val name: String,
    val emoji: String,
    val colorArgb: Long,
)
