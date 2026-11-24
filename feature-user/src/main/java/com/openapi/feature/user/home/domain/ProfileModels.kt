package com.openapi.feature.user.home.domain

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

enum class ProfileAction { Login, PhotoSolve, LocalMusic, Favorites, Feedback, Share, AppRecommend, Donation, Settings, Logout, Avatar }

data class ProfileEntry(
    val action: ProfileAction,
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
)

/**
 * 「我的」页展示的内容。[levelRes] 为 0 表示不显示等级图标。
 *
 * [avatarFilePath] 是用户自己生成/确认过的头像（本地文件，见
 * `com.openapi.feature.user.avatar.UserAvatarStore`）；null = 没有，回落到 [avatarRes] 默认图。
 * 存路径而不是 URL：签名 URL 只有几百秒寿命，缓存它等于给用户留坏图。
 */
data class ProfileInfo(
    val loggedIn: Boolean,
    val name: String,
    val hint: String,
    @DrawableRes val avatarRes: Int,
    @DrawableRes val levelRes: Int,
    val entries: List<ProfileEntry>,
    val avatarFilePath: String? = null,
)

/** 「我的」页需要的能力：展示内容、执行菜单动作。 */
interface ProfileEntries {

    fun profile(context: Context): ProfileInfo

    fun perform(context: Context, action: ProfileAction)
}
