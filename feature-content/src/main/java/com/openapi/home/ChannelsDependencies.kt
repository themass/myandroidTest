package com.openapi.home

import com.openapi.home.domain.ChannelEntries

/**
 * 频道页的依赖注入点，和 [HomeDependencies] 一样：app 在 Application.onCreate 里注入实现，
 * 频道页只依赖 [ChannelEntries] 这个抽象。
 */
object ChannelsDependencies {

    @Volatile
    private var entries: ChannelEntries? = null

    fun install(entries: ChannelEntries) {
        this.entries = entries
    }

    fun require(): ChannelEntries = checkNotNull(entries) {
        "ChannelEntries 未注入：请在 Application.onCreate 中调用 ChannelsDependencies.install(...)"
    }
}
