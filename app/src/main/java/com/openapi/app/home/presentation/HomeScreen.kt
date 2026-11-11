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
import com.openapi.uikit.theme.AppColors
import com.openapi.app.home.HomeDependencies
import com.openapi.ks.myapp.companion.CompanionRoute
import com.openapi.feature.user.home.domain.ProfileAction
import com.openapi.app.R
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.openapi.feature.user.home.domain.ProfileEntry
import androidx.compose.foundation.layout.size

private enum class HomeTab(@StringRes val label: Int, val icon: ImageVector) {
    Chat(R.string.home_tab_chat, Icons.Filled.ChatBubble),
    // 角色扮演与旧对话是两条独立链路：旧对话走 /chat/stream.json，陪伴走 LiveKit 房间，
    // 所以它们是并列的标签而不是同一个页面里的模式切换。
    Companion(R.string.companion_tab, Icons.Filled.Favorite),
    Channels(R.string.home_tab_channels, Icons.Filled.VideoLibrary),
    Profile(R.string.home_tab_profile, Icons.Filled.Person),
}


@Composable
fun HomeScreen() {
    var selected by rememberSaveable { mutableIntStateOf(HomeTab.Chat.ordinal) }
    // 「我的」：从侧边栏进入，从右边滑入的独立页面，可以右滑关闭
    var profilePageOpen by rememberSaveable { mutableStateOf(false) }
    val current = HomeTab.entries[selected]
    // 每个标签各自保存 rememberSaveable 状态，切走再切回来输入框里的草稿等不会丢
    val stateHolder = rememberSaveableStateHolder()
    // 键盘弹出时收起底栏：边到边模式下键盘盖在底栏上，留着它只会在输入框下面多出一截空白
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val context = LocalContext.current
    val entries = remember { HomeDependencies.require() }
    var profile by remember { mutableStateOf(entries.profile(context)) }
    LifecycleResumeEffect(Unit) {
        profile = entries.profile(context)
        onPauseOrDispose { }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = {
        profile = entries.profile(context)
        scope.launch { drawerState.open() }
    }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    // 对话标签的顶栏是品牌色，状态栏图标用浅色；其它标签、以及侧边栏打开时（浅色面板）用深色图标
    val hostView = LocalView.current
    SideEffect {
        val window = (hostView.context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, hostView).isAppearanceLightStatusBars = current != HomeTab.Chat || drawerState.targetValue == DrawerValue.Open
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 对话页的按住录音、上滑取消是手势操作，边缘滑动会和它抢手势：对话标签里只用菜单按钮打开，
        // 其余标签可以从左边缘滑出来；已经打开时任何标签都可以滑动关闭
        gesturesEnabled = current != HomeTab.Chat || drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ProfileHeader(profile) {
                        scope.launch { drawerState.close() }
                        entries.perform(context, ProfileAction.Login)
                    }
                    HorizontalDivider(color = AppColors.Divider)

                    DrawerSectionTitle(R.string.drawer_group_common)
                    // 常用：三个主页面；频道移到「测试」分组
                    listOf(HomeTab.Chat, HomeTab.Companion, HomeTab.Profile).forEach { tab ->
                        NavigationDrawerItem(
                            label = { Text(stringResource(tab.label)) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            selected = tab == current && !(tab == HomeTab.Profile && profilePageOpen),
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (tab == HomeTab.Profile) profilePageOpen = true else selected = tab.ordinal
                            },
                            colors = DrawerItemColors,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(vertical = 8.dp))

                    // 工具 / 测试 / 账户：真实入口（同「我的」页的入口）；未登录时没有「登出」
                    DrawerGroups.forEach { group ->
                        val items = group.actions.mapNotNull { action -> profile.entries.find { it.action == action } }
                        val showChannels = group.titleRes == R.string.drawer_group_test
                        if (items.isNotEmpty() || showChannels) {
                            DrawerSectionTitle(group.titleRes)
                            if (showChannels) {
                                NavigationDrawerItem(
                                    label = { Text(stringResource(HomeTab.Channels.label)) },
                                    icon = { Icon(HomeTab.Channels.icon, contentDescription = null) },
                                    selected = current == HomeTab.Channels,
                                    onClick = {
                                        selected = HomeTab.Channels.ordinal
                                        scope.launch { drawerState.close() }
                                    },
                                    colors = DrawerItemColors,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                            }
                            items.forEach { entry ->
                                DrawerEntryItem(entry) {
                                    scope.launch { drawerState.close() }
                                    entries.perform(context, entry.action)
                                    profile = entries.profile(context)
                                }
                            }
                        }
                    }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
        Scaffold(
        ) { innerPadding ->
            // 对话标签的顶栏要画到状态栏底下（状态栏同色），所以这一标签不留顶部边距，由顶栏自己处理；
            // 只能把「实际留出的边距」标记为已消费，否则顶栏的 statusBarsPadding 会拿到 0
            val layoutDirection = LocalLayoutDirection.current
            val applied = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                top = if (current == HomeTab.Chat) 0.dp else innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(applied)
                    // 导航栏等边距已由 Scaffold 处理，子页面的 safeDrawingPadding 只需要再处理键盘
                    .consumeWindowInsets(applied),
            ) {
                stateHolder.SaveableStateProvider(current.name) {
                    when (current) {
                        HomeTab.Chat -> ChatRoute(onMenuClick = openDrawer)
                        HomeTab.Companion -> CompanionRoute()
                        HomeTab.Channels -> ChannelsHub()
                        HomeTab.Profile -> Unit // 「我的」由右侧覆盖页显示
                    }
                }
            }
        }
        // 「我的」：从右边滑入，右滑或返回键关闭
        AnimatedVisibility(
            visible = profilePageOpen,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
        ) {
            ProfileSlidePage(onClose = { profilePageOpen = false })
        }
        }
    }
}

/**
 * 从右边滑入的「我的」页：顶部一条返回栏，内容是 [ProfileScreen]；
 * 从左边缘往右拖超过一定距离即关闭（和抽屉的关闭手势一致）。
 */
@Composable
private fun ProfileSlidePage(onClose: () -> Unit) {
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

/** 侧边栏的一组入口：标题 + 动作列表。 */
private data class DrawerGroup(@StringRes val titleRes: Int, val actions: List<ProfileAction>)

// 拍照解题已从侧边栏移除；频道、本地音乐、收藏夹归入「测试」
private val DrawerGroups = listOf(
    DrawerGroup(R.string.drawer_group_tools, listOf(ProfileAction.Feedback, ProfileAction.Share, ProfileAction.AppRecommend, ProfileAction.Donation)),
    DrawerGroup(R.string.drawer_group_test, listOf(ProfileAction.Favorites, ProfileAction.LocalMusic)),
    DrawerGroup(R.string.drawer_group_account, listOf(ProfileAction.Settings, ProfileAction.Logout)),
)

/** 侧边栏里的一项：与页面标签同一种行（同样的图标尺寸、间距和选中色）。 */
@Composable
private fun DrawerEntryItem(entry: ProfileEntry, onClick: () -> Unit) {
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
private val DrawerItemColors
    @Composable get() = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = AppColors.Primary.copy(alpha = 0.12f),
        selectedIconColor = AppColors.Primary,
        selectedTextColor = AppColors.Primary,
        unselectedIconColor = AppColors.TextSecondary,
        unselectedTextColor = AppColors.TextSecondary,
    )

/** 底栏：选中 = 品牌色，未选中 = 次要文字色。 */
private val BarItemColors
    @Composable get() = NavigationBarItemDefaults.colors(
        selectedIconColor = AppColors.Primary,
        selectedTextColor = AppColors.Primary,
        indicatorColor = AppColors.Primary.copy(alpha = 0.12f),
        unselectedIconColor = AppColors.TextSecondary,
        unselectedTextColor = AppColors.TextSecondary,
    )

@Composable
private fun DrawerSectionTitle(@StringRes title: Int) {
    Text(
        stringResource(title),
        color = AppColors.TextSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
    )
}
