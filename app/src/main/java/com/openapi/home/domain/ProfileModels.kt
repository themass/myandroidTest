package com.openapi.home.domain

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

enum class ProfileAction { Login, PhotoSolve, LocalMusic, Favorites, Feedback, Share, AppRecommend, Donation, Settings, Logout }

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

/** 「我的」页需要的能力：展示内容、执行菜单动作。 */
interface ProfileEntries {

    fun profile(context: Context): ProfileInfo

    fun perform(context: Context, action: ProfileAction)
}
