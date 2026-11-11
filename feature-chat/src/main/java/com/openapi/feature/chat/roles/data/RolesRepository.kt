package com.openapi.feature.chat.roles.data

import com.openapi.feature.chat.roles.domain.RoleDivision
import com.openapi.feature.chat.roles.domain.RolePage
import com.openapi.feature.chat.roles.domain.RoleSummary
import com.openapi.feature.chat.roles.domain.SelectedRole

/**
 * 角色目录入口。实现在 app 里（要用 OkHttp 和 Constants 的服务地址），
 * 本包只依赖这个抽象，和 ChatDependencies 是同一种接法。
 */
interface RolesRepository {

    /** 分类列表；失败返回 null。 */
    suspend fun divisions(): List<RoleDivision>?

    /**
     * 按分类/关键词/精选分页拉角色；失败返回 null（和"没有结果"区分开）。
     *
     * @param division 分类 id，null 表示不限
     * @param query 关键词，空表示不限
     */
    suspend fun roles(
        division: String? = null,
        query: String = "",
        featuredOnly: Boolean = false,
        limit: Int = 30,
        offset: Int = 0,
    ): RolePage?

    /** 上一次选中的目录角色（新会话默认用它），没选返回 null。 */
    fun selected(): SelectedRole?

    fun setSelected(role: SelectedRole?)

    /** 某个会话自己记住的角色；没有则返回 null，调用方回退到 [selected]。 */
    fun selectedFor(sessionId: Long): SelectedRole?

    fun setSelectedFor(sessionId: Long, role: SelectedRole?)

    /** 收藏的角色，按收藏时间倒序。保存的是摘要，离线也能显示。 */
    fun favorites(): List<RoleSummary>

    fun isFavorite(roleId: String): Boolean

    /** 切换收藏，返回切换后的状态。 */
    fun toggleFavorite(role: RoleSummary): Boolean

    /** 最近使用过的角色，最近的在前。 */
    fun recents(): List<RoleSummary>

    /** 记录一次使用（选中并开始对话）。 */
    fun recordUse(role: RoleSummary)
}

object RolesDependencies {
    @Volatile
    private var repository: RolesRepository? = null

    fun install(repository: RolesRepository) {
        this.repository = repository
    }

    fun require(): RolesRepository = checkNotNull(repository) {
        "RolesRepository 未注入：请在 Application.onCreate 中调用 RolesDependencies.install(...)"
    }
}
