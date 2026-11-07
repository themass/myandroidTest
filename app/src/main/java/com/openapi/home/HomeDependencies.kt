package com.openapi.home

import com.openapi.home.domain.HomeEntries

/**
 * 主页外壳的依赖注入点，用法同 [com.openapi.chat.ChatDependencies]：
 * app 在 Application.onCreate 里注入实现，外壳只依赖 [HomeEntries] 这个抽象。
 */
object HomeDependencies {

    @Volatile
    private var entries: HomeEntries? = null

    fun install(entries: HomeEntries) {
        this.entries = entries
    }

    fun require(): HomeEntries = checkNotNull(entries) {
        "HomeEntries 未注入：请在 Application.onCreate 中调用 HomeDependencies.install(...)"
    }
}
