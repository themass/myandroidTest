package com.openapi.app.data.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.openapi.core.config.CommonConstants
import com.openapi.common.ads.analytics.MobAgent
import com.openapi.core.event.ConfigActionEvent
import com.openapi.common.net.http.BaseService
import com.openapi.common.net.awaitInfoList
import com.openapi.core.event.CoreEvents
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.common.util.android.ToastUtil
import com.openapi.feature.content.home.domain.Channel
import com.openapi.feature.content.home.domain.ChannelCategory
import com.openapi.feature.content.home.domain.ChannelEntries
import com.openapi.feature.content.home.domain.ChannelPage
import com.openapi.app.R
import com.openapi.core.model.RecommendVo
import com.openapi.core.config.Constants
import com.openapi.core.account.UserLoginUtil
import com.openapi.feature.user.login.LoginActivity

/** 频道页的实现：分类、分页数据和打开频道，沿用旧的网络层和配置页。 */
class LegacyChannelEntries(app: Context) : ChannelEntries {

    private val service by lazy { BaseService().also { it.setup(app) } }

    override fun categories(context: Context): List<ChannelCategory> = buildList {
        add(ChannelCategory(MOVIE, R.string.tab_movie))
        if (PreferenceUtils.getPrefBoolean(context, Constants.AREA_SWITCH, Constants.AREA_SWITCH_DEFAULT)) {
            add(ChannelCategory(NIGHT, R.string.tab_ng))
        }
        add(ChannelCategory(CUSTOMER, R.string.tab_customer))
    }

    override suspend fun loadChannels(categoryId: String, start: Int): ChannelPage {
        val api = when (categoryId) {
            MOVIE -> Constants.API_RECOMMEND_MOVIE_URL
            NIGHT -> Constants.API_RECOMMEND_NIGHT_URL
            CUSTOMER -> Constants.API_RECOMMEND_CUSTOME_URL
            else -> throw IllegalArgumentException("未知的频道分类：$categoryId")
        }
        val data = service.awaitInfoList(
            Constants.getUrlWithParam(api, start), RecommendVo::class.java, NET_TAG,
        )
        val items = data.voList
            .filter { it.dataType != RecommendVo.dataType_ADS && !it.actionUrl.isNullOrEmpty() }
            .map { vo ->
                // 旧数据里的 rate 是 长/宽，卡片要的是 宽/高；限制范围避免极端比例把列表撑坏
                val ratio = if (vo.rate > 0f) 1f / vo.rate else 1f
                Channel(
                    title = vo.title.orEmpty(),
                    description = vo.desc.orEmpty().trim(),
                    imageUrl = vo.img,
                    aspectRatio = ratio.coerceIn(0.5f, 2.5f),
                    raw = vo,
                )
            }
        return ChannelPage(items, nextStart = data.pageNum, hasMore = data.hasMore)
    }

    override fun openChannel(context: Context, channel: Channel) {
        val vo = channel.raw as RecommendVo
        if (vo.type > 0) {
            val user = UserLoginUtil.getUserCache()
            if (user == null) {
                startActivityFrom(context, LoginActivity::class.java)
                return
            }
            if (user.level < vo.type) {
                ToastUtil.showShort(R.string.vip_tips)
                return
            }
        }
        val param = HashMap<String, Any>()
        param[CommonConstants.ADS_SHOW_CONFIG] = vo.adsShow
        param[CommonConstants.ADS_POP_SHOW_CONFIG] = vo.adsPopShow
        CoreEvents.postConfigAction(ConfigActionEvent(context, vo.actionUrl, vo.title, param))
        MobAgent.onEventRecommondChannel(context, vo.title)
    }

    private companion object {
        const val MOVIE = "movie"
        const val NIGHT = "night"
        const val CUSTOMER = "customer"
        const val NET_TAG = "home_channels"
    }
}

/** 旧页面的启动方式：不在 Activity 里启动时加 NEW_TASK。 */
internal fun startActivityFrom(context: Context, target: Class<out Activity>) {
    val intent = Intent(context, target)
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
