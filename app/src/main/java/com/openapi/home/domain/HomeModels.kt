package com.openapi.home.domain

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** 频道页顶部的分类（电影、客户等）。[id] 由实现方定义，界面只负责原样回传。 */
data class ChannelCategory(val id: String, @StringRes val titleRes: Int)

/**
 * 频道页里的一张卡片。
 *
 * [aspectRatio] 是 宽/高。[raw] 是实现方的原始数据，界面不看，点击时原样交回 [HomeEntries.openChannel]。
 */
data class Channel(
    val title: String,
    val description: String,
    val imageUrl: String?,
    val aspectRatio: Float,
    val raw: Any,
)

/** 一页频道。[nextStart] 是请求下一页时要传的起点（由服务端决定，不是简单累加）。 */
data class ChannelPage(val items: List<Channel>, val nextStart: Int, val hasMore: Boolean)

enum class ProfileAction { Login, PhotoSolve, LocalMusic, Feedback, Share, AppRecommend, Settings, Logout }

data class ProfileEntry(
    val action: ProfileAction,
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
)

/** 「我的」页展示的内容。[levelRes] 为 0 表示不显示等级图标。 */
data class ProfileInfo(
    val loggedIn: Boolean,
    val name: String,
    val hint: String,
    @DrawableRes val avatarRes: Int,
    @DrawableRes val levelRes: Int,
    val entries: List<ProfileEntry>,
)

/**
 * 主页外壳需要的、由 app 提供的能力。外壳不直接依赖账号体系、旧页面和网络层，
 * 和 ChatDependencies 一样由 MyApplication 注入实现（见 HomeDependencies）。
 */
interface HomeEntries {

    /** 当前可见的频道分类，可能随设置变化（每次回到前台会重新读取）。 */
    fun categories(context: Context): List<ChannelCategory>

    /** 阻塞式网络请求，必须在后台线程调用；失败直接抛出。 */
    fun loadChannels(categoryId: String, start: Int): ChannelPage

    /** 打开一个频道；内部负责登录和会员等级检查。 */
    fun openChannel(context: Context, channel: Channel)

    fun profile(context: Context): ProfileInfo

    fun perform(context: Context, action: ProfileAction)

    /** 主页首次创建时调用一次：检查更新、上报连接日志、应用播放内核。 */
    fun onHomeStarted(context: Context)
}
