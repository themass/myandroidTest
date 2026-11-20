package com.openapi.feature.content.home.domain

import android.content.Context
import androidx.annotation.StringRes

/** 频道页顶部的分类（电影、客户等）。[id] 由实现方定义，界面只负责原样回传。 */
data class ChannelCategory(val id: String, @StringRes val titleRes: Int)

/**
 * 频道页里的一张卡片。
 *
 * [aspectRatio] 是 宽/高。[raw] 是实现方的原始数据，界面不看，点击时原样交回 [ChannelEntries.openChannel]。
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

/** 频道页需要的能力：分类、分页数据、打开频道。 */
interface ChannelEntries {

    /** 当前可见的频道分类，可能随设置变化（每次回到前台会重新读取）。 */
    fun categories(context: Context): List<ChannelCategory>

    /** 阻塞式网络请求，必须在后台线程调用；失败直接抛出。 */
    suspend fun loadChannels(categoryId: String, start: Int): ChannelPage

    /** 打开一个频道；内部负责登录和会员等级检查。 */
    fun openChannel(context: Context, channel: Channel)
}
