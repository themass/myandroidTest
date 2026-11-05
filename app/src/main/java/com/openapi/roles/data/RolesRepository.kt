package com.openapi.roles.data

import com.openapi.roles.domain.RoleDivision
import com.openapi.roles.domain.RolePage
import com.openapi.roles.domain.SelectedRole

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

    /** 当前选中的目录角色，没选返回 null。 */
    fun selected(): SelectedRole?

    fun setSelected(role: SelectedRole?)
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
