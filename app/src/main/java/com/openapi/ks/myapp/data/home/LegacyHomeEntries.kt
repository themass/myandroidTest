package com.openapi.ks.myapp.data.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.aimath.paizhao.PhotoSolveActivity
import com.openapi.core.CommonConstants
import com.openapi.core.ads.yewu.um.MobAgent
import com.openapi.core.event.ConfigActionEvent
import com.openapi.core.net.BaseService
import com.openapi.core.util.EventBusUtil
import com.openapi.core.util.PreferenceUtils
import com.openapi.core.util.ShareUtil
import com.openapi.core.util.StringUtils
import com.openapi.core.util.ToastUtil
import com.openapi.home.domain.Channel
import com.openapi.home.domain.ChannelCategory
import com.openapi.home.domain.ChannelPage
import com.openapi.home.domain.HomeEntries
import com.openapi.home.domain.ProfileAction
import com.openapi.home.domain.ProfileEntry
import com.openapi.home.domain.ProfileInfo
import com.openapi.ks.chat.R
import com.openapi.ks.myapp.base.MyApplication
import com.openapi.ks.myapp.bean.vo.NullReturnVo
import com.openapi.ks.myapp.bean.vo.RecommendVo
import com.openapi.ks.myapp.constant.Constants
import com.openapi.ks.myapp.data.ConnLogUtil
import com.openapi.ks.myapp.data.UserLoginUtil
import com.openapi.ks.myapp.data.VersionUpdater
import com.openapi.ks.myapp.ui.fragment.AppListFragment
import com.openapi.ks.myapp.ui.user.LoginActivity
import com.openapi.ks.myapp.ui.user.SettingActivity
import com.openapi.ks.myapp.video.PlaybackEngineHelper

/**
 * 用现有的网络层、账号缓存和旧页面实现 [HomeEntries]。
 *
 * 旧首页的「英语 / 数学 / 语文」三个标签请求的其实都是电影接口，和「电影」标签是同一份数据，
 * 所以这里只保留三个真正不同的分类：电影、夜间（受设置开关控制）、客户。
 */
class LegacyHomeEntries(private val app: Context) : HomeEntries {

    private val service by lazy { BaseService().also { it.setup(app) } }

    override fun categories(context: Context): List<ChannelCategory> = buildList {
        add(ChannelCategory(MOVIE, R.string.tab_movie))
        if (PreferenceUtils.getPrefBoolean(context, Constants.AREA_SWITCH, true)) {
            add(ChannelCategory(NIGHT, R.string.tab_ng))
        }
        add(ChannelCategory(CUSTOMER, R.string.tab_customer))
    }

    override fun loadChannels(categoryId: String, start: Int): ChannelPage {
        val api = when (categoryId) {
            MOVIE -> Constants.API_RECOMMEND_MOVIE_URL
            NIGHT -> Constants.API_RECOMMEND_NIGHT_URL
            CUSTOMER -> Constants.API_RECOMMEND_CUSTOME_URL
            else -> throw IllegalArgumentException("未知的频道分类：$categoryId")
        }
        val data = service.getInfoListData(
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
                startActivity(context, LoginActivity::class.java)
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
        EventBusUtil.getEventBus().post(ConfigActionEvent(context, vo.actionUrl, vo.title, param))
        MobAgent.onEventRecommondChannel(context, vo.title)
    }

    override fun profile(context: Context): ProfileInfo {
        val user = UserLoginUtil.getUserCache()
        val entries = buildList {
            add(ProfileEntry(ProfileAction.PhotoSolve, R.drawable.ic_menu_photo_solve, R.string.menu_btn_photo_solve))
            add(ProfileEntry(ProfileAction.LocalMusic, R.drawable.ic_menu_fav, R.string.menu_btn_local_music))
            add(ProfileEntry(ProfileAction.Feedback, R.drawable.ic_menu_help, R.string.menu_btn_feedback))
            add(ProfileEntry(ProfileAction.Share, com.openapi.core.util.R.drawable.ic_menu_share, R.string.menu_btn_share))
            if (!MyApplication.isTemp) {
                add(ProfileEntry(ProfileAction.AppRecommend, R.drawable.ic_menu_recomm, R.string.menu_btn_app))
            }
            add(ProfileEntry(ProfileAction.Settings, R.drawable.ic_menu_setting, R.string.menu_btn_setting))
            if (user != null) {
                add(ProfileEntry(ProfileAction.Logout, R.drawable.ic_menu_delete, R.string.menu_btn_logout))
            }
        }
        return if (user != null) {
            ProfileInfo(
                loggedIn = true,
                name = user.name.orEmpty(),
                hint = context.getString(R.string.menu_btn_login_ready),
                avatarRes = if (Constants.SEX_M == user.sex) R.drawable.ic_default_nan else R.drawable.ic_default_nv,
                levelRes = levelIcon(user.level),
                entries = entries,
            )
        } else {
            ProfileInfo(
                loggedIn = false,
                name = context.getString(R.string.menu_btn_name_default),
                hint = context.getString(R.string.menu_btn_login),
                avatarRes = R.drawable.ic_login_default_header,
                levelRes = 0,
                entries = entries,
            )
        }
    }

    override fun perform(context: Context, action: ProfileAction) {
        when (action) {
            ProfileAction.Login -> startActivity(context, LoginActivity::class.java)
            ProfileAction.PhotoSolve -> PhotoSolveActivity.start(context)
            ProfileAction.LocalMusic ->
                startActivity(context, com.openapi.retromusic.activities.MainActivity::class.java)
            ProfileAction.Feedback -> com.openapi.ks.myapp.ui.feedback.FeedbackActivity.start(context)
            ProfileAction.Share -> share(context)
            ProfileAction.AppRecommend -> AppListFragment.startFragment(context)
            ProfileAction.Settings -> startActivity(context, SettingActivity::class.java)
            ProfileAction.Logout -> logout(context)
        }
        MobAgent.onEventMenu(context, action.analyticsName())
    }

    @OptIn(UnstableApi::class)
    override fun onHomeStarted(context: Context) {
        (context as? Activity)?.let { VersionUpdater.checkUpdate(it, false) }
        ConnLogUtil.sendAllLog(context)
        PlaybackEngineHelper.applyEngine(context)
    }

    private fun share(context: Context) {
        var url = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.D_URL, null)
        if (!StringUtils.hasText(url)) url = CommonConstants.DEFAULT_REFERER
        ShareUtil(context).shareText(null, null, "$url 爱Freedom，精彩你的生活", "爱Freedom", "精彩你的生活")
    }

    private fun logout(context: Context) {
        // postData 只是入队异步发送，先发请求再清本地缓存，和旧抽屉的顺序一致
        service.postData(Constants.getUrl(Constants.API_LOGOUT_URL), null, null, null, null, NullReturnVo::class.java)
        UserLoginUtil.logout(context)
    }

    private fun startActivity(context: Context, target: Class<out Activity>) {
        val intent = Intent(context, target)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun levelIcon(level: Int): Int = when (level) {
        Constants.UserLevel.LEVEL_FREE -> R.drawable.ic_level_free
        Constants.UserLevel.LEVEL_VIP -> R.drawable.ic_level_vip
        Constants.UserLevel.LEVEL_VIP2 -> R.drawable.ic_level_vip2
        Constants.UserLevel.LEVEL_VIP3 -> R.drawable.ic_level_vip3
        Constants.UserLevel.LEVEL_VIP4 -> R.drawable.ic_level_vip4
        else -> 0
    }

    private fun ProfileAction.analyticsName(): String = when (this) {
        ProfileAction.Login -> "登录"
        ProfileAction.PhotoSolve -> "拍照解题"
        ProfileAction.LocalMusic -> "本地音乐"
        ProfileAction.Feedback -> "反馈"
        ProfileAction.Share -> "分享"
        ProfileAction.AppRecommend -> "应用推荐"
        ProfileAction.Settings -> "设置"
        ProfileAction.Logout -> "登出"
    }

    private companion object {
        const val MOVIE = "movie"
        const val NIGHT = "night"
        const val CUSTOMER = "customer"
        const val NET_TAG = "home_channels"
    }
}
