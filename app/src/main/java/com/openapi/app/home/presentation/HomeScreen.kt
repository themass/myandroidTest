package com.openapi.app.home.presentation


import com.openapi.feature.user.home.presentation.ProfileHeader
import com.openapi.feature.user.home.presentation.ProfileScreen
import androidx.core.view.WindowCompat
import com.openapi.ks.myapp.agent.ui.AgentChatEntry
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import androidx.core.util.Consumer
import com.openapi.app.home.HomeActivity
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
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

/** 左侧抽屉与屏幕边缘留白，避免整屏铺满不像「抽屉」。 */
private val HomeDrawerInset = 12.dp
private val HomeDrawerCorner = 20.dp
private val HomeDrawerMaxWidth = 320.dp

internal enum class HomeTab(@StringRes val label: Int, val icon: ImageVector) {
    Chat(R.string.home_tab_chat, Icons.Filled.ChatBubble),
    // 角色扮演与旧对话是两条独立链路：旧对话走 /chat/stream.json，陪伴走 LiveKit 房间，
    // 所以它们是并列的标签而不是同一个页面里的模式切换。
    Companion(R.string.companion_tab, Icons.Filled.Favorite),
    Channels(R.string.home_tab_channels, Icons.Filled.VideoLibrary),
    // 新对话页（/v1，角色扮演 + 记忆 + 提醒）。先放在抽屉里，稳定后再决定是否替换「对话」标签。
    AgentChat(R.string.home_tab_agent_chat, Icons.Filled.Forum),
    Profile(R.string.home_tab_profile, Icons.Filled.Person),
}


@Composable
fun HomeScreen() {
    var selected by rememberSaveable { mutableIntStateOf(HomeTab.Chat.ordinal) }
    // P0-4：聊天页的电话入口 = 跳到陪伴页并直接进通话。这里是跨标签的一次性信号，
    // CompanionRoute 消费后立即复位，回到本页不会重复弹通话。
    // 必须是进程内 remember，不能用 rememberSaveable：信号置位后进程若被杀
    // （麦克风权限弹窗挂着时被系统回收就是一例），savedState 会把 pending 带回来，
    // 重启后 LaunchedEffect 直接替用户拨电话（真机踩到，见 CallLaunchSignalTest）。
    var callLaunchPending by remember { mutableStateOf(false) }
    // 「我的」：从侧边栏进入，从右边滑入的独立页面，可以右滑关闭
    var profilePageOpen by rememberSaveable { mutableStateOf(false) }
    val current = HomeTab.entries[selected]
    // 每个标签各自保存 rememberSaveable 状态，切走再切回来输入框里的草稿等不会丢
    val stateHolder = rememberSaveableStateHolder()
    // 键盘弹出时收起底栏：边到边模式下键盘盖在底栏上，留着它只会在输入框下面多出一截空白
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val context = LocalContext.current
    val activity = context as ComponentActivity
    // singleTask 热启动只换 Intent：监听 onNewIntent，否则 Compose 读不到新的 script_case。
    var launchIntent by remember { mutableStateOf(activity.intent) }
    DisposableEffect(activity) {
        val listener = Consumer<Intent> { launchIntent = it }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
    val scriptCaseId = launchIntent.getStringExtra(HomeActivity.EXTRA_SCRIPT_CASE)?.takeIf { it.isNotBlank() }
    val scriptCharacterId = launchIntent.getStringExtra(HomeActivity.EXTRA_CHARACTER_ID)?.takeIf { it.isNotBlank() }
    LaunchedEffect(scriptCaseId) {
        if (scriptCaseId != null) {
            selected = HomeTab.Companion.ordinal
            profilePageOpen = false
        }
    }
    val entries = remember { HomeDependencies.require() }
    var profile by remember { mutableStateOf(entries.profile(context)) }
    LifecycleResumeEffect(Unit) {
        profile = entries.profile(context)
        onPauseOrDispose { }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    // 陪伴页的全屏通话覆盖层挂起时也要关抽屉滑动：通话里 VRM WebView 的单指拖动
    // 会被父级抽屉在 touch slop 处抢走（实测页面只收到 down + 1 个 move 就 CANCEL）。
    var companionCallActive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = {
        profile = entries.profile(context)
        scope.launch { drawerState.open() }
    }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    // 对话标签的顶栏是品牌色，状态栏图标用浅色；频道标签内嵌的放映场是深色底，图标同样要浅色；
    // 其余标签、以及侧边栏打开时（浅色面板）用深色图标
    val hostView = LocalView.current
    SideEffect {
        val window = (hostView.context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, hostView).isAppearanceLightStatusBars =
                (current != HomeTab.Chat && current != HomeTab.Channels) || drawerState.targetValue == DrawerValue.Open
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 对话页的按住录音、上滑取消是手势操作，边缘滑动会和它抢手势：对话标签里只用菜单按钮打开，
        // 其余标签可以从左边缘滑出来；已经打开时任何标签都可以滑动关闭
        gesturesEnabled = drawerSwipeEnabled(
            currentIsChat = current == HomeTab.Chat,
            drawerOpen = drawerState.isOpen,
            callVisible = companionCallActive,
        ),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(
                        start = HomeDrawerInset,
                        top = HomeDrawerInset,
                        bottom = HomeDrawerInset,
                    )
                    .widthIn(max = HomeDrawerMaxWidth),
                drawerShape = RoundedCornerShape(
                    topEnd = HomeDrawerCorner,
                    bottomEnd = HomeDrawerCorner,
                ),
            ) {
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
                                // 「本地视频」旧页：放映场正式口径里没有它（样片下线时把
                                // 「打开手机里的视频」入口一并删了），用户自己的文件仍从这条旧入口进。
                                NavigationDrawerItem(
                                    label = { Text(stringResource(R.string.localvideo_title)) },
                                    icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                                    selected = false,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        context.startActivity(
                                            Intent(context, com.openapi.feature.video.local.LocalVideoActivity::class.java))
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

                    // 实验室：debug 包才出现的 spike 入口（判定与 DhLabGate 同思路），不进生产导航
                    if (com.openapi.feature.camerafx.CameraFxLabGate.isEnabled(context)) {
                        HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(vertical = 8.dp))
                        DrawerSectionTitle(R.string.drawer_group_lab)
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.drawer_entry_camera_fx)) },
                            icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                com.openapi.feature.camerafx.CameraFxActivity.start(context)
                            },
                            colors = DrawerItemColors,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.drawer_entry_teaching_demo)) },
                            icon = { Icon(Icons.Filled.ChatBubble, contentDescription = null) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                context.startActivity(Intent().setClassName(
                                    context,
                                    "com.openapi.ks.myapp.companion.teaching.TeachingDemoActivity",
                                ))
                            },
                            colors = DrawerItemColors,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        // 特效引擎对比（多后端 demo）：只引用 main-source 的两个 const
                        // （EffectsLabLaunch），Activity/依赖/资产全在 debug source set 里。
                        NavigationDrawerItem(
                            label = { Text("特效引擎对比") },
                            icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                context.startActivity(
                                    Intent()
                                        .setClassName(
                                            context.packageName,
                                            com.openapi.app.fxlab.EffectsLabLaunch.CLASS,
                                        )
                                        .setAction(com.openapi.app.fxlab.EffectsLabLaunch.ACTION),
                                )
                            },
                            colors = DrawerItemColors,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
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
                        HomeTab.Chat -> ChatRoute(
                            onMenuClick = openDrawer,
                            // 拍照解题在 :feature-photosolve：feature 之间不依赖，由 app 在这里接上入口（P3-22）
                            onOpenPhotoSolve = { com.openapi.feature.photosolve.PhotoSolveActivity.start(context) },
                            // 语音通话在 :feature-companion：同样不建依赖边，跳陪伴标签并请求直接进通话（P0-4）
                            onStartVoiceCall = {
                                callLaunchPending = true
                                selected = HomeTab.Companion.ordinal
                            },
                        )
                        HomeTab.Companion -> CompanionRoute(
                            onMenuClick = openDrawer,
                            initialCharacterId = scriptCharacterId
                                ?: com.openapi.ks.myapp.companion.DEFAULT_CHARACTER_ID,
                            scriptCaseId = scriptCaseId,
                            launchCallOnOpen = callLaunchPending,
                            onLaunchCallHandled = { callLaunchPending = false },
                            onCallActiveChanged = { companionCallActive = it },
                            onLoginRequired = {
                                context.startActivity(
                                    Intent(context, com.openapi.feature.user.login.LoginActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                            // 通话页右下角人脸贴纸小窗：内容从组合根注入——
                            // feature 互不依赖（ModuleGraphTest），:app 可同时依赖两个 feature。
                            faceCameraPipContent = { mod, onGesture ->
                                com.openapi.feature.camerafx.CameraFxStickerPip(
                                    modifier = mod,
                                    onStageGesture = onGesture,
                                )
                            },
                        )
                        HomeTab.Channels -> ChannelsHub()
                        HomeTab.AgentChat -> AgentChatEntry()
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

/** 侧边栏的一组入口：标题 + 动作列表。 */
internal data class DrawerGroup(@StringRes val titleRes: Int, val actions: List<ProfileAction>)
