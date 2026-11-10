package com.openapi.home.domain

import android.content.Context

/**
 * 主页外壳需要的、由 app 提供的能力。外壳不直接依赖账号体系、旧页面和网络层，
 * 和 ChatDependencies 一样由 MyApplication 注入实现（见 HomeDependencies）。
 * 频道和「我的」两部分分别在 [ChannelEntries] 和 [ProfileEntries] 里。
 */
interface HomeEntries : ChannelEntries, ProfileEntries {

    /** 主页首次创建时调用一次：检查更新、上报连接日志、应用播放内核。 */
    fun onHomeStarted(context: Context)
}
