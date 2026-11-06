package com.openapi.home.presentation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.openapi.chat.presentation.ui.ChatRoute
import com.openapi.core.design.theme.AppColors
import com.openapi.home.HomeDependencies
import com.openapi.home.domain.ProfileAction
import com.openapi.ks.chat.R
import kotlinx.coroutines.launch

private enum class HomeTab(@StringRes val label: Int, val icon: ImageVector) {
    Chat(R.string.home_tab_chat, Icons.Filled.ChatBubble),
    Channels(R.string.home_tab_channels, Icons.Filled.VideoLibrary),
    Profile(R.string.home_tab_profile, Icons.Filled.Person),
}

@Composable
fun HomeScreen() {
    var selected by rememberSaveable { mutableIntStateOf(HomeTab.Chat.ordinal) }
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

                    DrawerSectionTitle(R.string.home_drawer_section_pages)
                    HomeTab.entries.forEach { tab ->
                        NavigationDrawerItem(
                            label = { Text(stringResource(tab.label)) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            selected = tab == current,
                            onClick = {
                                selected = tab.ordinal
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(vertical = 8.dp))

                    // 这一组把所有已转成 Compose 的页面入口集中放在侧边栏，方便逐个测试；
                    // 正式版入口的形式（侧边栏还是只留「我的」页）等测完再定
                    DrawerSectionTitle(R.string.home_drawer_section_test)
                    profile.entries.forEach { entry ->
                        ProfileRow(entry) {
                            scope.launch { drawerState.close() }
                            entries.perform(context, entry.action)
                            profile = entries.profile(context)
                        }
                    }
                }
            }
        },
    ) {
        Scaffold(
            bottomBar = {
                if (!imeVisible) {
                    NavigationBar {
                        HomeTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = tab == current,
                                onClick = { selected = tab.ordinal },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // 状态栏、导航栏的边距已经由 Scaffold 处理，子页面的 safeDrawingPadding 只需要再处理键盘
                    .consumeWindowInsets(innerPadding),
            ) {
                stateHolder.SaveableStateProvider(current.name) {
                    when (current) {
                        HomeTab.Chat -> ChatRoute(onMenuClick = openDrawer)
                        HomeTab.Channels -> ChannelsScreen()
                        HomeTab.Profile -> ProfileScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionTitle(@StringRes title: Int) {
    Text(
        stringResource(title),
        color = AppColors.TextSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
    )
}
