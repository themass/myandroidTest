package com.openapi.app.home.presentation


import com.openapi.feature.user.home.presentation.ProfileHeader
import com.openapi.feature.user.home.presentation.ProfileScreen
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.openapi.feature.chat.presentation.ui.ChatRoute
import com.openapi.common.ui.theme.AppColors
import com.openapi.app.home.HomeDependencies
import com.openapi.ks.myapp.companion.CompanionRoute
import com.openapi.feature.user.home.domain.ProfileAction
import com.openapi.app.R
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.openapi.feature.user.home.domain.ProfileEntry
import androidx.compose.foundation.layout.size

/**
 * 从右边滑入的「我的」页：顶部一条返回栏，内容是 [ProfileScreen]；
 * 从左边缘往右拖超过一定距离即关闭（和抽屉的关闭手势一致）。
 */
@Composable
internal fun ProfileSlidePage(onClose: () -> Unit) {
    var dragged by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.ChatBackground)
            .statusBarsPadding()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (dragged > 120f) onClose(); dragged = 0f },
                    onDragCancel = { dragged = 0f },
                    onHorizontalDrag = { _, amount -> dragged = (dragged + amount).coerceAtLeast(0f) },
                )
            },
    ) {
        // 顶栏单独占一行，返回箭头不再压在个人卡片上
        IconButton(onClick = onClose, modifier = Modifier.padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = AppColors.TextPrimary)
        }
        Box(Modifier.fillMaxSize()) {
            ProfileScreen()
        }
    }
}

// 拍照解题已从侧边栏移除；频道、本地音乐、收藏夹归入「测试」
internal val DrawerGroups = listOf(
    DrawerGroup(R.string.drawer_group_tools, listOf(ProfileAction.Feedback, ProfileAction.Share, ProfileAction.AppRecommend, ProfileAction.Donation)),
    DrawerGroup(R.string.drawer_group_test, listOf(ProfileAction.Favorites, ProfileAction.LocalMusic)),
    DrawerGroup(R.string.drawer_group_account, listOf(ProfileAction.Settings, ProfileAction.Logout)),
)

/** 侧边栏里的一项：与页面标签同一种行（同样的图标尺寸、间距和选中色）。 */
@Composable
internal fun DrawerEntryItem(entry: ProfileEntry, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(stringResource(entry.titleRes)) },
        icon = {
            Image(
                painter = painterResource(entry.iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        selected = false,
        onClick = onClick,
        colors = DrawerItemColors,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

/** 侧边栏选中 = 品牌色，未选中 = 次要文字色。 */
internal val DrawerItemColors
    @Composable get() = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = AppColors.Primary.copy(alpha = 0.12f),
        selectedIconColor = AppColors.Primary,
        selectedTextColor = AppColors.Primary,
        unselectedIconColor = AppColors.TextSecondary,
        unselectedTextColor = AppColors.TextSecondary,
    )

/** 底栏：选中 = 品牌色，未选中 = 次要文字色。 */
internal val BarItemColors
    @Composable get() = NavigationBarItemDefaults.colors(
        selectedIconColor = AppColors.Primary,
        selectedTextColor = AppColors.Primary,
        indicatorColor = AppColors.Primary.copy(alpha = 0.12f),
        unselectedIconColor = AppColors.TextSecondary,
        unselectedTextColor = AppColors.TextSecondary,
    )

@Composable
internal fun DrawerSectionTitle(@StringRes title: Int) {
    Text(
        stringResource(title),
        color = AppColors.TextSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
    )
}
