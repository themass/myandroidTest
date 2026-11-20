package com.openapi.feature.user.home


import com.openapi.feature.user.home.domain.ProfileEntries

/**
 * 「我的」页的依赖注入点，和 :feature-content 的 ChannelsDependencies 一样：app 在 Application.onCreate 里注入实现，
 * 「我的」页只依赖 [ProfileEntries] 这个抽象。
 */
object ProfileDependencies {

    @Volatile
    private var entries: ProfileEntries? = null

    fun install(entries: ProfileEntries) {
        this.entries = entries
    }

    fun require(): ProfileEntries = checkNotNull(entries) {
        "ProfileEntries 未注入：请在 Application.onCreate 中调用 ProfileDependencies.install(...)"
    }
}
