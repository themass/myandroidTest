package com.openapi.app.home.domain


import com.openapi.feature.user.home.domain.ProfileEntries
import android.content.Context

/**
 * 主页外壳需要的、由 app 提供的能力。外壳不直接依赖账号体系、旧页面和网络层，
 * 和 ChatDependencies 一样由 MyApplication 注入实现（见 HomeDependencies）。
 * 「我的」部分在 [ProfileEntries] 里；旧频道页的 ChannelEntries 随放映场正式化
 * （首页「频道」标签内嵌 ScreeningRoute）一并删除，不再继承。
 */
interface HomeEntries : ProfileEntries {

    /** 主页首次创建时调用一次：检查更新、上报连接日志、应用播放内核。 */
    fun onHomeStarted(context: Context)
}
