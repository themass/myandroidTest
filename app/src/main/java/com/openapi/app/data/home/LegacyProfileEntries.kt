package com.openapi.app.data.home

import android.content.Context
import com.openapi.feature.photosolve.PhotoSolveActivity
import com.openapi.ads.analytics.MobAgent
import com.openapi.network.http.BaseService
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.common.util.lang.StringUtils
import com.openapi.feature.user.home.domain.ProfileAction
import com.openapi.feature.user.home.domain.ProfileEntries
import com.openapi.feature.user.home.domain.ProfileEntry
import com.openapi.feature.user.home.domain.ProfileInfo
import com.openapi.app.R
import com.openapi.app.MyApplication
import com.openapi.core.model.NullReturnVo
import com.openapi.core.config.Constants
import com.openapi.core.account.UserLoginUtil
import com.openapi.feature.content.mine.AppListFragment
import com.openapi.feature.content.mine.DonationListFragment
import com.openapi.feature.content.mine.FavoriteFragment
import com.openapi.feature.user.login.LoginActivity
import com.openapi.feature.user.setting.SettingActivity

/** 「我的」页的实现：展示内容和菜单动作，沿用旧的账号缓存和页面。 */
class LegacyProfileEntries(app: Context) : ProfileEntries {

    private val service by lazy { BaseService().also { it.setup(app) } }

    override fun profile(context: Context): ProfileInfo {
        val user = UserLoginUtil.getUserCache()
        val entries = buildList {
            add(ProfileEntry(ProfileAction.PhotoSolve, R.drawable.ic_menu_photo_solve, R.string.menu_btn_photo_solve))
            add(ProfileEntry(ProfileAction.LocalMusic, R.drawable.ic_menu_fav, R.string.menu_btn_local_music))
            add(ProfileEntry(ProfileAction.Favorites, com.openapi.core.R.drawable.ic_menu_favorite_ed, R.string.menu_btn_favorite))
            add(ProfileEntry(ProfileAction.Feedback, R.drawable.ic_menu_help, R.string.menu_btn_feedback))
            add(ProfileEntry(ProfileAction.Share, com.openapi.uikit.R.drawable.ic_menu_share, R.string.menu_btn_share))
            if (!MyApplication.isTemp) {
                add(ProfileEntry(ProfileAction.AppRecommend, R.drawable.ic_menu_recomm, R.string.menu_btn_app))
                add(ProfileEntry(ProfileAction.Donation, R.drawable.ic_menu_app, R.string.menu_btn_donation))
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
            ProfileAction.Login -> startActivityFrom(context, LoginActivity::class.java)
            ProfileAction.PhotoSolve -> PhotoSolveActivity.start(context)
            ProfileAction.LocalMusic ->
                startActivityFrom(context, com.openapi.retromusic.activities.MainActivity::class.java)
            ProfileAction.Favorites -> FavoriteFragment.startFragment(context)
            ProfileAction.Feedback -> com.openapi.feature.user.feedback.FeedbackActivity.start(context)
            ProfileAction.Share -> share(context)
            ProfileAction.AppRecommend -> AppListFragment.startFragment(context)
            ProfileAction.Donation -> DonationListFragment.startFragment(context)
            ProfileAction.Settings -> startActivityFrom(context, SettingActivity::class.java)
            ProfileAction.Logout -> logout(context)
        }
        MobAgent.onEventMenu(context, action.analyticsName())
    }

    private fun share(context: Context) {
        var url = PreferenceUtils.getPrefString(MyApplication.getInstance(), Constants.D_URL, null)
        if (!StringUtils.hasText(url)) url = com.openapi.network.http.NetConfig.defaultReferer()
        // 分享卡片（二维码 + 链接）走分享库，和 vpn 项目一样是图片分享
        com.openapi.uikit.share.ShareKit.shareCard(context, title = "爱Freedom", subtitle = "精彩你的生活", link = url)
    }

    private fun logout(context: Context) {
        // postData 只是入队异步发送，先发请求再清本地缓存，和旧抽屉的顺序一致
        service.postData(Constants.getUrl(Constants.API_LOGOUT_URL), null, null, null, null, NullReturnVo::class.java)
        UserLoginUtil.logout(context)
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
        ProfileAction.Favorites -> "收藏夹"
        ProfileAction.Feedback -> "反馈"
        ProfileAction.Share -> "分享"
        ProfileAction.AppRecommend -> "应用推荐"
        ProfileAction.Donation -> "捐赠"
        ProfileAction.Settings -> "设置"
        ProfileAction.Logout -> "登出"
    }
}
