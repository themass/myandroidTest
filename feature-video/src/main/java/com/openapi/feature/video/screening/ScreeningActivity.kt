package com.openapi.feature.video.screening

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.openapi.core.config.Constants
import com.openapi.core.event.ConfigActionEvent
import com.openapi.core.event.CoreEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal val Ink = Color(0xFF141729)
internal val Lift = Color(0xFF252A43)
internal val Muted = Color(0xFFBCC2D7)
internal val Mint = Color(0xFFADF7D9)
internal val Peach = Color(0xFFFFC1A4)
internal val White = Color(0xFFF7F5F0)
internal val Tile = RoundedCornerShape(13.dp)

/**
 * 放映场的独立 Activity 壳：返回键出口和全屏沉浸都挂在宿主 Activity 上，这个壳保留这套结构。
 * 正式入口不是它——首页「频道」标签内嵌 [ScreeningRoute]（同一套 UI）。它不导出、无 LAUNCHER，
 * 桌面没有图标（debug 期那个「放映场 · 原生预览」第二图标已随本次迁正式删掉）。
 */
class ScreeningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            ScreeningTheme { ScreeningHome(this, onBack = { finish() }) }
        }
    }
}

@Composable
internal fun ScreeningTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Mint, onPrimary = Ink, background = Ink,
        onBackground = White, surface = Ink, onSurface = White, surfaceVariant = Lift, onSurfaceVariant = Muted)) {
        content()
    }
}

/**
 * 放映场整屏内容，供首页「频道」标签直接内嵌（正式入口）。
 * [onBack] 非空时顶栏画返回箭头（独立 Activity 场景）；标签内嵌传 null——离开走切标签，
 * 这里不该有个把用户扔出首页的箭头。
 */
@Composable
fun ScreeningRoute(onBack: (() -> Unit)? = null) {
    val host = checkNotNull(LocalActivity.current as? ComponentActivity) {
        "ScreeningRoute 要挂在 ComponentActivity 里（独立入口或首页标签都是）"
    }
    ScreeningTheme { ScreeningHome(host, onBack) }
}

/**
 * 全屏沉浸切换：任何宿主 Activity 都行（独立的 ScreeningActivity、内嵌它的 HomeActivity
 * 走的是同一条）。横屏全屏收起系统栏，退出恢复并切回竖屏。
 */
internal fun Activity.setScreeningImmersive(on: Boolean) {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
        if (on) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
    }
    requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}

@Composable
private fun ScreeningHome(activity: ComponentActivity, onBack: (() -> Unit)?) {
    val scope = rememberCoroutineScope()
    val store = remember { ScreeningFeedStore(activity) }
    var channelPage by remember { mutableStateOf<ScreeningChannelPage?>(null) }
    var selection by remember { mutableStateOf<ScreeningSource?>(null) }
    var gallery by remember { mutableStateOf(false) }
    var textHub by remember { mutableStateOf(false) }
    var textChannel by remember { mutableStateOf<String?>(null) }
    var soundHub by remember { mutableStateOf(false) }
    var soundChannel by remember { mutableStateOf<String?>(null) }
    /** 从首页图片卡进图集时带的 `channel=`；null 时图集页直接给空态，不演样例。 */
    var galleryChannel by remember { mutableStateOf<String?>(null) }
    /**
     * 首屏两个标签：0=频道 1=图集。它们是同一屏的两种看法，不是三层页面——
     * 切换在页面内原地淡切（用户点过名「切换跳和闪」），所以两种内容共用的取数状态
     * 全部挂在这一层：切走再回来不重建 pager、不重复打接口、不闪白。
     */
    var homeTab by rememberSaveable { mutableStateOf(0) }
    var channelFilter by rememberSaveable { mutableStateOf(FeedCategory.MOVIE.label) }
    /** 取数协程挂首页层：内容组件因切标签离开组合时，在途请求和失败自动重试不被牵连取消。 */
    val feedScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    DisposableEffect(Unit) { onDispose { feedScope.cancel() } }
    /** 频道 pager 按分类留档（进内页返回也不丢）；图集 pager 按频道留档，同理。 */
    val channelPagers = remember { mutableStateMapOf<FeedCategory, FeedPager>() }
    val galleryPagers = remember { mutableStateMapOf<String, FeedPager>() }
    /**
     * 内页片单 pager 挂在这一层，由 [openChannel] 建好再换页：进播放器退回来、切走再回来，
     * 片单原样留在屏上，不重新取数——用户点名「不是进入列表页都要刷新」。
     * 下钻换页（新的 ScreeningChannelPage）才换一份 pager。
     */
    var libraryPager by remember { mutableStateOf<Pair<ScreeningChannelPage, FeedPager>?>(null) }
    /** 懒播放地址在路上：点了卡到地址回来之间要有反馈，不能像没点上。 */
    var opening by remember { mutableStateOf(false) }
    val holder = rememberSaveableStateHolder()
    val preferences = remember { activity.getSharedPreferences("screening-preview", 0) }
    val progress = remember { mutableStateMapOf<String, Long>() }
    // 转场要知道「往里走还是往回退」：开新页时置真、返回时置假，滑动方向跟着翻。
    var forward by remember { mutableStateOf(true) }
    val openEntry: (RemoteEntry) -> Unit = { entry ->
        scope.launch {
            opening = true
            // needLazyUrl 的条目这一步才拿到真正能播的地址
            val url = store.playUrl(entry)
            forward = true
            selection = ScreeningSource(entry.title, entry.description, uri = Uri.parse(url), key = entry.key)
            opening = false
        }
    }
    val openChannel: (RemoteEntry) -> Unit = { entry ->
        forward = true
        // 取数键分层级（对齐旧页面）：第一层列子频道用 actionUrl 里的 channel=
        // （VideoChannleBodyFragment 就是拿 CONFIG_PARAM 的 channel 发 channel.json）；
        // 片单页才用 param 优先（VideoChannelListFragment.getUrl 用 vo.param）。
        // 以前不分层，config 卡的 param（若有）会顶掉 channelType 键，第一层就查空。
        val kind = when {
            isVideoUserHubEntry(entry) -> ScreeningPageKind.VIDEO_USERS
            channelPage?.kind == ScreeningPageKind.VIDEO_USERS -> ScreeningPageKind.VIDEO_USER_ITEMS
            else -> videoChannelPageKind(entry)
        }
        val fetchKey = when (kind) {
            ScreeningPageKind.SUBCHANNELS -> entry.channelParam
            ScreeningPageKind.VIDEO_USERS -> videoUserListChannelKey(entry)
            ScreeningPageKind.VIDEO_USER_ITEMS -> entry.actionUrl
            else -> entry.videoChannelKey()
        }
        val page = ScreeningChannelPage(
            title = entry.title,
            detail = entry.description,
            coverUrl = entry.imageUrl,
            // 原页面 VideoChannelListFragment.getUrl 用的是 vo.param。外链频道卡以前在这里
            // 被置空（掉回 WebView 就不用内页取数了）；B 线吸收后必须把 key 传下去，
            // 内页才拿它发 items.json。（旧「样例频道/随包封面」兜底已随本地样片库一并下线。）
            channelParam = fetchKey,
            // 夜色配置频道卡（hsex_all 这类）进去先列子频道，点一行再下钻片单
            kind = kind,
            // 片单查不到时给的回网页退路；内部频道卡没有网页为 null 就不画按钮
            webFallback = entry.webFallbackUrl(),
        )
        // pager 跟着这一页在这里建（不是建在 AnimatedContent 的分支里）：
        // 之后从播放器退回内页，换的是 screen 状态，pager 不动——列表原样在，不重新取数。
        val fetch: suspend (Int, String) -> ScreeningFeedStore.Page<RemoteEntry> = { start, keyword ->
            when {
                // 夜色配置频道页：先列子频道（channel.json），点一行再下钻片单
                fetchKey != null && kind == ScreeningPageKind.SUBCHANNELS ->
                    store.subchannels(fetchKey, start)
                fetchKey != null && kind == ScreeningPageKind.VIDEO_USERS ->
                    store.videoUsers(fetchKey, start, keyword)
                fetchKey != null && kind == ScreeningPageKind.VIDEO_USER_ITEMS ->
                    store.videoUserItems(fetchKey, start, keyword)
                fetchKey != null -> store.videos(fetchKey, start, keyword)
                // 没有取数键（路由判定下理论上不出现）：诚实空页，不拿随包样片填屏
                else -> ScreeningFeedStore.Page(emptyList(), null, false)
            }
        }
        // 同键（同一个频道再点一次）复用已加载的那份，不重新取；换频道才新建。
        val existing = libraryPager
        val pager = if (existing != null && sameLibraryPage(existing.first, page)) existing.second
        else FeedPager(feedScope, fetch = fetch)
        libraryPager = page to pager
        channelPage = page
        // 旧 MovieChannleActivity.java:50 等进频道页弹插屏；放映场对应这一步。
        // 时机闸门（静默期+最小间隔）在 tryInterstitialOnDrill 里，主干从来没有那道闸。
        tryInterstitialOnDrill(activity)
    }
    /** http(s) 地址交回原路由（`ConfigActionJump` 按 scheme 分流 WebView）。 */
    val openUrl: (String, String) -> Unit = { url, title ->
        CoreEvents.postConfigAction(ConfigActionEvent(activity, url, title, HashMap()))
    }
    /** 外链卡：交回原路由。 */
    val openWeb: (RemoteEntry) -> Unit = { entry -> openUrl(entry.actionUrl, entry.title) }
    val openGallery: (RemoteEntry?) -> Unit = { entry ->
        forward = true
        galleryChannel = entry?.galleryChannelParam()
        gallery = true
        // 旧 ImgChannleActivity 进页弹插屏；放映场对应「开图集」。闸门见 ScreeningAds。
        tryInterstitialOnDrill(activity)
    }
    val openText: (RemoteEntry) -> Unit = { entry ->
        forward = true
        textChannel = entry.channelParam
        textHub = true
        tryInterstitialOnDrill(activity)
    }
    val openSound: (RemoteEntry) -> Unit = { entry ->
        forward = true
        soundChannel = entry.channelParam
        soundHub = true
        tryInterstitialOnDrill(activity)
    }
    /**
     * 按 routeOf 派发一张卡的点击：内页里的行和首页网格走同一套判定，
     * 子频道行下钻开新内页、可播行进播放器、纯网页行交回 WebView——
     * 点深任何一层都不掉旧页面（B 线：客户端全收）。
     */
    val openRoute: (RemoteEntry) -> Unit = { entry ->
        when (routeOf(entry)) {
            FeedRoute.PLAY -> openEntry(entry)
            FeedRoute.CHANNEL -> openChannel(entry)
            FeedRoute.ALBUM -> openGallery(entry)
            FeedRoute.TEXT -> openText(entry)
            FeedRoute.SOUND -> openSound(entry)
            FeedRoute.WEB -> openWeb(entry)
        }
    }
    // A24（根因 CONFIRMED）：这里是状态机的返回总出口。原来它只认「频道内页」一种态，
    // 图集封面列表级、文字/声音馆选择器级这些子页自己深层的 BackHandler 此刻是 disabled 的，
    // 返回键没人接 → 穿透到系统默认 → 结束任务回桌面。现在枚举全部非根态，
    // 按下面 `screen` when 分支同一优先级清一个 flag 回到上一层（判定纯函数见 screeningBackStep）。
    // 播放器态刻意排除：ScreeningPlayback 自己的无条件 BackHandler 在 AnimatedContent 内层
    // 后组合、优先消费，这里既不抢也不与之同态竞争。同理，图集/文字/声音馆深层退层的
    // BackHandler（ScreeningGallery/ScreeningText/ScreeningSound）都在我们之后组合，
    // 它们 enabled 时先退一层，深层退层语义不变。
    val backStep = screeningBackStep(
        playing = selection != null,
        channelPageOpen = channelPage != null,
        galleryOpen = gallery,
        textHubOpen = textHub,
        soundHubOpen = soundHub,
    )
    BackHandler(backStep != ScreeningBackStep.NONE && backStep != ScreeningBackStep.PLAYER) {
        forward = false
        when (backStep) {
            ScreeningBackStep.CHANNEL_PAGE -> channelPage = null
            ScreeningBackStep.GALLERY -> gallery = false
            ScreeningBackStep.TEXT_HUB -> textHub = false
            ScreeningBackStep.SOUND_HUB -> soundHub = false
            else -> Unit
        }
    }
    val motion = remember { MotionPlan(isReduceMotion(activity)) }
    val density = LocalDensity.current
    val slidePx = remember(density) { with(density) { motion.slideDistanceDp().dp.toPx() } }
    // 当前页连同它要的数据一起装进 target：退场动画播的是旧页自己那份快照，
    // 不会在切走的一瞬间因为状态已变而渲染成空白。
    val selected = selection
    val current = channelPage
    // 优先级跟原来一致：播放 > 频道内页 > 图集 > 首页。
    val screen: Any = when {
        selected != null -> PlayScreen(selected, current?.title.orEmpty())
        current != null -> LibraryScreen(current)
        gallery -> GalleryScreen(galleryChannel)
        textHub -> TextScreen(textChannel)
        soundHub -> SoundScreen(soundChannel)
        else -> HomeScreen
    }
    Surface(Modifier.fillMaxSize()) {
        // 动效契约（含系统的减弱动态开关）在这里下发，深层卡片不逐层传参。
        CompositionLocalProvider(LocalScreeningMotion provides motion) {
        AnimatedContent(targetState = screen, transitionSpec = {
            screenSlide(motion, slidePx, forward)
        }, label = "screen") { target ->
            when (target) {
                is PlayScreen -> key(target.source) {
                    holder.SaveableStateProvider(target.source.key) {
                        // 进度兜底现读 prefs（真机 2026-11-23）：progress 这张内存表只有列表页
                        // items 就位那一刻才灌 prefs，冷启动没经过列表页就进片会漏掉存档——
                        // prefs 存着 1:58，冷启动却从头播。缺就现取一次（阈值判定在 storedProgress）。
                        val entryKey = target.source.key
                        val resumeAt = progress[entryKey] ?: storedProgress(preferences, entryKey).also { progress[entryKey] = it }
                        ScreeningPlayback(activity, target.source, target.channel, resumeAt,
                            onExit = { forward = false; selection = null }, onSave = { position ->
                                progress[target.source.key] = position
                                preferences.edit().putLong(progressKey(target.source.key), position).apply()
                            })
                    }
                }
                is GalleryScreen -> holder.SaveableStateProvider("gallery") {
                    ScreeningGallery(store, target.channelParam) { forward = false; gallery = false }
                }
                is TextScreen -> holder.SaveableStateProvider("text") {
                    ScreeningText(store, target.channelParam,
                        onExit = { forward = false; textHub = false },
                        onOpenWeb = { url, title -> openUrl(url, title) },
                    )
                }
                is SoundScreen -> holder.SaveableStateProvider("sound") {
                    ScreeningSound(store, target.channelParam) { forward = false; soundHub = false }
                }
                is LibraryScreen -> holder.SaveableStateProvider(target.page.title) {
                    // 片单 pager 由 openChannel 在首页层建好带过来（feedScope 也在首页层）：
                    // 从播放器退回内页、tab 切走再回来，列表原样在，不重建不重取
                    //（用户点名「不是进入列表页都要刷新」）。重取只由用户下拉触发。
                    val pagePager = libraryPager?.second
                    if (pagePager != null) {
                        Library(target.page, progress, preferences, pagePager,
                            back = { forward = false; channelPage = null }, openEntry = openRoute,
                            onWeb = { url, title -> openUrl(url, title) })
                    }
                    // libraryPager 与 channelPage 由 openChannel 成对写，为 null 理论上不发生；
                    // 真发生了就留空屏——不拿空 pager 现建一份去重复打接口。
                }
                else -> holder.SaveableStateProvider("channels") {
                    HomeTabs(store, feedScope, homeTab, { homeTab = it },
                        channelFilter, { channelFilter = it }, channelPagers,
                        progress, preferences,
                        onBack = onBack,
                        onChannel = openChannel, onPlay = openEntry, onWeb = openWeb, onText = openText, onSound = openSound,
                        onGallery = { entry -> openGallery(entry) })
                }
            }
        }
        val (fadeEnter, fadeExit) = screenFade(motion)
        AnimatedVisibility(visible = opening, enter = fadeEnter, exit = fadeExit, label = "opening") {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp, color = Mint)
            }
        }
        }
    }
}

/**
 * 首屏：标题行 + 两个标签 + 原地切换的内容。
 *
 * 「频道 / 图集」是同一屏的两种看法，不是三层页面：切换只做小位移交叉淡入
 * （推页那种 24dp 滑动在 tab 尺度上读作「跳」），内容不先清空再填（那就是「闪」）。
 * 两种内容的取数状态都挂在调用方（ScreeningHome）：切走的 tab 不销毁 pager，
 * 切回来直接展示已加载的内容，也不重复打接口。
 */
@Composable
private fun HomeTabs(
    store: ScreeningFeedStore,
    feedScope: CoroutineScope,
    tab: Int,
    onSelect: (Int) -> Unit,
    channelFilter: String,
    onChannelFilter: (String) -> Unit,
    channelPagers: MutableMap<FeedCategory, FeedPager>,
    progress: MutableMap<String, Long>,
    preferences: android.content.SharedPreferences,
    /** null = 内嵌首页标签（离开走切标签，不画返回箭头）；非 null = 独立 Activity。 */
    onBack: (() -> Unit)?,
    onChannel: (RemoteEntry) -> Unit,
    onPlay: (RemoteEntry) -> Unit,
    onWeb: (RemoteEntry) -> Unit,
    onGallery: (RemoteEntry?) -> Unit,
    onText: (RemoteEntry) -> Unit,
    onSound: (RemoteEntry) -> Unit,
) {
    val motion = LocalScreeningMotion.current
    // 切 tab 的位移：推页距离的一半不到（10dp），换算成像素给转场用
    val tabSlidePx = with(LocalDensity.current) { (motion.slideDistanceDp() / 2.4f).dp.toPx() }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TabHeader(tab, onBack, onSelect)
        AnimatedContent(
            targetState = tab,
            // 切 tab：位移只有推页的一半不到、时长减半，交叉淡入全程重叠不露底。
            // 方向感保留（往右切往右进），但读起来是「换看法」，不是「换一页」。
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                val enterMs = (motion.enterDurationMs() / 2).coerceAtLeast(120)
                val exitMs = (motion.exitDurationMs() / 2).coerceAtLeast(80)
                (slideInHorizontally(tween(enterMs, easing = EaseStandard)) { (dir * tabSlidePx).toInt() } +
                    fadeIn(tween(enterMs, easing = EaseStandard)))
                    .togetherWith(
                        fadeOut(tween(exitMs, easing = EaseExit)) +
                        slideOutHorizontally(tween(exitMs, easing = EaseExit)) { (-dir * tabSlidePx / 2f).toInt() },
                    )
            },
            label = "tabContent",
        ) { which ->
            when (which) {
                1 -> GalleryFeed(
                    // 频道 pager 本来只在频道 tab 里建；从图集 tab 先开时补建同一份，
                    // 两个 tab 共用一次请求，不是各打各的。分类跟频道 tab 的默认值一致（电影）。
                    channelPagers.getOrPut(FeedCategory.MOVIE) {
                        FeedPager(feedScope) { start, _ -> store.channels(FeedCategory.MOVIE, start) }
                    }, onGallery,
                )
                else -> ChannelsFeed(store, feedScope, channelPagers, channelFilter, onChannelFilter,
                    onChannel, onPlay, onWeb, onGallery, onText, onSound)
            }
        }
    }
}

/**
 * 图集 tab：服务端没有「全站长图集」这一个列表，册子都挂在图集频道下
 * （`img/channel.json` 必须带 channel=），所以这一屏先把图集频道当入口列出来，
 * 点进去才是册子网格（复用整屏 ScreeningGallery 的下钻转场）。
 * 数据直接吃频道分类已缓存的 pager，不为这个 tab 单开请求。
 */
@Composable
private fun GalleryFeed(allPager: FeedPager, onOpen: (RemoteEntry) -> Unit) {
    val books = allPager.items.filter { it.isImageCard }
    val seen = remember { mutableSetOf<String>() }
    LaunchedEffect(allPager) { allPager.load() }
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp)) {
        item {
            FeedError(allPager.error, allPager::reload)
            Heading("图集频道", if (allPager.loading && books.isEmpty()) "正在取数据…" else "${books.size} 个")
            if (allPager.loading && books.isEmpty()) {
                ListSkeleton(Modifier.padding(top = 2.dp), rows = 2, rowHeight = 150.dp)
            }
            if (!allPager.loading && books.isEmpty()) {
                Text("服务端这会儿没有图集频道，回频道页刷新试试。", Modifier.padding(14.dp), color = Muted)
            }
        }
        if (books.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                books.forEachIndexed { index, entry ->
                    Row(Modifier.fillMaxWidth().height(96.dp).enterFeedItem(seen, entry.key, index)
                        .pressable(Tile) { onOpen(entry) }) {
                        EntryCover(entry, Modifier.size(width = 148.dp, height = 96.dp).clip(Tile))
                        Column(Modifier.weight(1f).padding(start = 12.dp).padding(vertical = 14.dp),
                            verticalArrangement = Arrangement.Center) {
                            Text(entry.title, color = White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (entry.description.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(entry.description, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.padding(end = 12.dp))
                    }
                }
            }
        }
    }
}

/**
 * 顶栏：返回 + 品牌 + 三个标签。
 * 标签是页内状态不是新页面，所以选中态用下划线随状态滑动（滑动指示比「整块变粗」更便宜地
 * 说清「你在哪、点它会怎样」），点已有 pager 的标签瞬间切换、零请求。
 */
@Composable
private fun TabHeader(tab: Int, onBack: (() -> Unit)?, onSelect: (Int) -> Unit) {
    val tabs = listOf("频道", "图集")
    Column(Modifier.padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            // 独立 Activity 才画返回箭头；内嵌首页标签时返回会把人扔出首页，不该有
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            }
            Icon(Icons.Default.PlayArrow, null, tint = Mint)
            Text("放映场", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { index, label ->
                val selected = index == tab
                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(index) }
                    .semantics { this.selected = selected }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center) {
                    Text(label,
                        color = if (selected) White else Muted,
                        fontSize = if (selected) 18.sp else 16.sp,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.Normal)
                }
            }
        }
        // 指示线跟着选中 tab 滑：它把三个标签连成一个控件，而不是三个各管各的按钮。
        // 槽宽和上面 weight(1f) 的标签同构，线才会停在字下面。
        val indicator by animateFloatAsState(tab.toFloat(), label = "tabIndicator")
        BoxWithConstraints(Modifier.fillMaxWidth().height(3.dp)) {
            val slot = maxWidth / tabs.size
            Box(Modifier.width(26.dp).fillMaxHeight()
                .offset(x = (slot - 26.dp) / 2f + slot * indicator)
                .background(Mint, Tile))
        }
    }
}

/**
 * 频道 tab：推荐位 + 分类过滤 + 频道网格。pager 缓存与筛选值都由上层持有，
 * 切走再回来不重建、不闪。加载信号全页只有扫光一种（用户点过名「2 个 loading 只要一个」）。
 */
@Composable
private fun ChannelsFeed(
    store: ScreeningFeedStore,
    feedScope: CoroutineScope,
    pagerCache: MutableMap<FeedCategory, FeedPager>,
    filter: String,
    onFilter: (String) -> Unit,
    onChannel: (RemoteEntry) -> Unit,
    onPlay: (RemoteEntry) -> Unit,
    onWeb: (RemoteEntry) -> Unit,
    onGallery: (RemoteEntry?) -> Unit,
    onText: (RemoteEntry) -> Unit,
    onSound: (RemoteEntry) -> Unit,
) {
    // 分类跟旧频道页同口径：只有电影/夜色。老装机存过已删的「全部」：取不到就落回电影，
    // 高亮和取数用同一个 category，不会出现没选中的空档。
    val category = FeedCategory.entries.firstOrNull { it.label == filter } ?: FeedCategory.MOVIE
    // 每个分类一个 pager 并留档在调用方：切回看过的分类直接展示已加载内容
    //（秒回、不闪、不重复打接口），没看过的从扫光占位开始。scope 也在上层：
    // 切走标签时 pager 组合离开，在途请求和失败自动重试不会被牵连取消。
    val pager = pagerCache.getOrPut(category) {
        FeedPager(feedScope, fetch = { start, _ -> store.channels(category, start) })
    }
    val listState = remember(category) { LazyListState() }
    LaunchedEffect(pager) { pager.load() }
    feedShouldLoadMore(listState, pager)
    val cards = pager.items.filter { routeOf(it) != FeedRoute.PLAY }
    // 疑点（台账外备注，PLAUSIBLE 未改）：这条过滤把 routeOf==PLAY 的卡挡在网格外，
    // 而 hero 取的是未过滤的 items——若服务端有数据但全是可播卡，就会出现
    // 「推荐位有内容、网格 0 个」。要不要放行由取证定口径，这里只标疑点不动逻辑。
    val hero = pager.items.firstOrNull { !it.isImageCard }
    val seen = remember(category) { mutableSetOf<String>() }
    LazyColumn(Modifier.fillMaxSize(), state = listState,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp)) {
        item {
            FeedError(pager.error, pager::reload)
            // A21（用户点名「空数据仍占块」）：这里原设计是「推荐位高度恒定：hero 没到位时
            // 同一块高度放扫光，数据落地只换内容不挪布局」——但取到 0 条（或失败）时这块
            // 216dp 空盒仍占着首屏。以用户为准改口径：只有加载中或有 hero 才画这一块，
            // 空数据整块不出现。（屏小首屏要能装下推荐位 + 分类 + 网格，仍然成立。）
            if (hero != null || pager.loading) {
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().height(216.dp).clip(RoundedCornerShape(13.dp, 26.dp, 13.dp, 13.dp))) {
                    val entry = hero
                    if (entry == null) {
                        // 加载中扫一层缓慢移动的光（失败停成静态底）。这里不叠转圈——
                        // 转圈和扫光说的是同一件事，屏上留一个就够（用户点过名）。
                        if (pager.loading) ShimmerPlaceholder(Modifier.matchParentSize())
                        else Box(Modifier.matchParentSize().background(Lift))
                    } else {
                        Box(Modifier.matchParentSize().pressable(RoundedCornerShape(13.dp, 30.dp, 13.dp, 13.dp)) {
                            when (routeOf(entry)) {
                                FeedRoute.PLAY -> onPlay(entry)
                                FeedRoute.CHANNEL -> onChannel(entry)
                                FeedRoute.ALBUM -> onGallery(entry)
                                FeedRoute.TEXT -> onText(entry)
                                FeedRoute.SOUND -> onSound(entry)
                                FeedRoute.WEB -> onWeb(entry)
                            }
                        }) {
                            EntryCover(entry, Modifier.matchParentSize())
                            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x45202340), Ink))))
                            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                                Text(if (entry.isGroupEntry) "频道推荐" else "正在热映", Modifier.background(Peach, RoundedCornerShape(5.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp), color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(entry.title, color = White, fontSize = ScreeningTypography.page, lineHeight = ScreeningTypography.pageLineHeight, fontWeight = FontWeight.Black,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (entry.description.isNotBlank()) {
                                    Spacer(Modifier.height(3.dp))
                                    Text(entry.description, color = White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.height(9.dp))
                                Button(onClick = {
                                    when (routeOf(entry)) {
                                        FeedRoute.PLAY -> onPlay(entry)
                                        FeedRoute.CHANNEL -> onChannel(entry)
                                        FeedRoute.ALBUM -> onGallery(entry)
                                        FeedRoute.TEXT -> onText(entry)
                                FeedRoute.SOUND -> onSound(entry)
                                        FeedRoute.WEB -> onWeb(entry)
                                            }
                                }, shape = RoundedCornerShape(9.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)) {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                    Text(if (entry.isGroupEntry) "看这个频道" else "看预告", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
            // A21：取到 0 条（且不在加载）时「逛频道」标题和过滤条也不再占块——
            // 原来它们无条件渲染，空数据时屏上是孤零零的标题+两个过滤 chip。
            if (pager.loading || cards.isNotEmpty()) {
                Heading("逛频道", if (pager.loading && cards.isEmpty()) "正在取数据…" else "${cards.size} 个频道")
                FilterRow(FeedCategory.entries.map { it.label }, filter, onFilter)
                Spacer(Modifier.height(9.dp))
            }
            // 网格区首屏不再放第二块转圈：等待由上面推荐位的扫光统一表达，
            // 数据一到直接填网格，没有「占位块整块消失」的第二次跳动。
        }
        if (cards.isNotEmpty()) item {
            // 三列方格（用户点名：屏小，一屏要能装下更多）。窄格放不下一行描述，
            // 描述就让位——卡面本身比一行截断的字更有辨识度；点进去内页看全。
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { column ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        cards.filterIndexed { index, _ -> index % 3 == column }.forEachIndexed { index, entry ->
                            Box(Modifier.fillMaxWidth().aspectRatio(1f)
                                .enterFeedItem(seen, entry.key, index)
                                .pressable(Tile) {
                                    when (routeOf(entry)) {
                                        FeedRoute.PLAY -> onPlay(entry)
                                        FeedRoute.CHANNEL -> onChannel(entry)
                                        FeedRoute.ALBUM -> onGallery(entry)
                                        FeedRoute.TEXT -> onText(entry)
                            FeedRoute.SOUND -> onSound(entry)
                                        FeedRoute.WEB -> onWeb(entry)
                                                }
                                }) {
                                EntryCover(entry, Modifier.matchParentSize())
                                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Ink))))
                                Text(entry.title,
                                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3141729))))
                                        .padding(horizontal = 6.dp, vertical = 5.dp),
                                    color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        item { FeedEndRow(pager.loading, pager.hasMore, pager::loadMore,
            firstPageLoading = pager.loading && cards.isEmpty(), empty = cards.isEmpty()) }
    }
}

/**
 * 返回一键要清的层。PLAYER 由 ScreeningPlayback 自己的无条件 BackHandler 消费
 * （AnimatedContent 内层后组合、优先于外层），外层把它从 enabled 排除、绝不抢；
 * NONE = 已在根层（频道/图集 tab），交回系统默认——独立 Activity 退出、标签内嵌切标签。
 */
internal enum class ScreeningBackStep { NONE, PLAYER, CHANNEL_PAGE, GALLERY, TEXT_HUB, SOUND_HUB }

/**
 * A24 状态机返回判定（纯函数，JVM 单测见 ScreeningBackNavigationTest）：
 * 优先级与 ScreeningHome 里 `screen` 的 when 分支完全一致
 * （播放 > 频道内页 > 图集 > 文字馆 > 声音馆），一次只清一层。
 * 根因正是原来只有频道内页这一层挂了出口，图集封面列表级 / 文字·声音馆选择器级
 * 自己深层的 BackHandler 此刻 disabled，返回穿透到系统默认 → 结束任务回桌面。
 */
internal fun screeningBackStep(
    playing: Boolean,
    channelPageOpen: Boolean,
    galleryOpen: Boolean,
    textHubOpen: Boolean,
    soundHubOpen: Boolean,
): ScreeningBackStep = when {
    playing -> ScreeningBackStep.PLAYER
    channelPageOpen -> ScreeningBackStep.CHANNEL_PAGE
    galleryOpen -> ScreeningBackStep.GALLERY
    textHubOpen -> ScreeningBackStep.TEXT_HUB
    soundHubOpen -> ScreeningBackStep.SOUND_HUB
    else -> ScreeningBackStep.NONE
}

/**
 * 转场的目标状态：一页一个类型，AnimatedContent 据此判断「换页」还是「同页刷新」。
 * 页面要渲染的数据都装在这里，退场动画期间渲染的是离开页自己的快照。
 */
private object HomeScreen
private data class LibraryScreen(val page: ScreeningChannelPage)
private data class GalleryScreen(val channelParam: String?)
private data class TextScreen(val channelParam: String?)
private data class SoundScreen(val channelParam: String?)
private data class PlayScreen(val source: ScreeningSource, val channel: String)

/** 进度存的键：所有条目（样例和真实）都按 [RemoteEntry.key] 存，一条规则。 */
private fun progressKey(entryKey: String) = "position:$entryKey"

/**
 * 「同一张内页」按取数口径判定：取数键和接口种类一致就算同一页。
 * 再点一次同一个频道卡时复用已加载的 pager——列表原样在，不重新打接口
 * （用户点名「不是进入列表页都要刷新」）。换频道才新建一份。
 */
private fun sameLibraryPage(a: ScreeningChannelPage, b: ScreeningChannelPage): Boolean =
    a.channelParam == b.channelParam && a.kind == b.kind

/** 续播阈值（用户点名「开头 10+ 秒被跳」）：只瞥了几秒不值得跳过片头，低于它就当没看过，从头播。 */
private const val RESUME_MIN_MS = 5_000L

private fun storedProgress(preferences: android.content.SharedPreferences, entryKey: String): Long {
    val stored = preferences.getLong(progressKey(entryKey), 0L)
    return if (stored >= RESUME_MIN_MS) stored else 0L
}

/** 一张卡的封面：远程地址走 Glide；没有地址也不留纯色块——用设计好的占位图
 * （用户点名：占位图被撤掉是回归，台账 A28）。 */
@Composable
internal fun EntryCover(entry: RemoteEntry, modifier: Modifier = Modifier) {
    when {
        !entry.imageUrl.isNullOrBlank() -> RemoteCover(entry.imageUrl, modifier)
        else -> Image(
            painter = painterResource(ImagePlaceholderRes),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}


/**
 * 频道内页。片单 pager 由调用方注入：pager 挂在首页层（见 openChannel），
 * 这里只渲染——进播放器退回来列表原样在，不重建不重取。
 */
@Composable
private fun Library(
    page: ScreeningChannelPage,
    progress: MutableMap<String, Long>,
    preferences: android.content.SharedPreferences,
    pager: FeedPager,
    back: () -> Unit,
    openEntry: (RemoteEntry) -> Unit,
    /** 片单为空时的网页退路（page.webFallback 非 null 才画按钮）。 */
    onWeb: (String, String) -> Unit = { _, _ -> },
) {
    var filter by rememberSaveable { mutableStateOf("全部") }
    // 默认就是三列网格（用户点名：屏小，一行两列要点好几屏才看完）；
    // 右上开关留着切到带观看进度的紧凑行视图。
    var poster by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()
    LaunchedEffect(pager) {
        pager.load()
        pager.items.forEach { entry ->
            if (!progress.containsKey(entry.key)) progress[entry.key] = storedProgress(preferences, entry.key)
        }
    }
    feedShouldLoadMore(listState, pager)
    val shown = pager.items.filter { filter != "未观看" || (progress[it.key] ?: 0L) == 0L }
    val resume = pager.items.firstOrNull { (progress[it.key] ?: 0L) > 0L }
    val seen = remember(page) { mutableSetOf<String>() }
    FeedListShell(
        pager = pager,
        state = listState,
        title = page.title,
        onBack = back,
        detail = feedToolbarDetail(pager.loading, pager.items.size, "条"),
        // 搜索（用户点名 2026-11-25）：片单页就是旧 VideoChannelListFragment——工具条挂搜索框，
        // 裸关键词直传 keyword=（服务端把 channel 置空、按频道行 channelType 跨频道检索）。
        searchable = page.kind != ScreeningPageKind.SUBCHANNELS,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 9.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            // 顶卡 168→148dp：屏小，头部只做到「认出这是哪个频道」就够，高度留给片单
            Box(Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(12.dp, 22.dp, 12.dp, 12.dp))) {
                RemoteCover(page.coverUrl, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink))))
                Column(Modifier.align(Alignment.BottomStart).padding(13.dp)) {
                    // 这里原来印 Constants.BASE_IP（接口域名）——调试信息，不是给用户看的（用户点过名）
                    Text("放映场", color = Peach, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(page.title, color = White, fontWeight = FontWeight.Black, fontSize = ScreeningTypography.page, lineHeight = ScreeningTypography.pageLineHeight,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (pager.loading) "正在取数据…" else "${pager.items.size} 条", color = Muted, fontSize = 11.sp)
                }
            }
            FeedError(pager.error, pager::reload)
            // 样式跟着下面的「继续看」行走：Lift 卡 + 薄荷图标（用户点名实验页不该再混旧的 tonal 按钮）
            resume?.let { entry ->
                Row(Modifier.padding(top = 10.dp).fillMaxWidth().clip(Tile).background(Lift).clickable { openEntry(entry) }.padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, tint = Mint)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text("继续看《${entry.title}》", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("从 ${time(progress.getValue(entry.key))} 接着播", color = Muted, fontSize = 11.sp)
                    }
                    Text("续播", color = Mint)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (page.kind) {
                        ScreeningPageKind.SUBCHANNELS -> "子频道"
                        ScreeningPageKind.VIDEO_USERS -> "用户"
                        ScreeningPageKind.VIDEO_USER_ITEMS -> "Ta 的视频"
                        else -> "全部视频"
                    },
                    Modifier.weight(1f), fontSize = ScreeningTypography.section, fontWeight = FontWeight.Black,
                )
                IconButton(onClick = { poster = !poster }) { Icon(if (poster) Icons.Default.ViewList else Icons.Default.GridView, if (poster) "切换列表视图" else "切换海报视图") }
            }
            FilterRow(listOf("全部", "未观看"), filter) { filter = it }
            // 首屏加载：用和条目等高的扫光骨架占位，而不是再来一个转圈
            //（用户点过名「2 个 loading 只要一个」：转圈和底部的转圈会同屏出现）
            if (pager.loading && pager.items.isEmpty()) {
                ListSkeleton(Modifier.padding(top = 8.dp), rowHeight = 64.dp)
            }
        }
        if (!pager.loading && shown.isEmpty()) item {
            // 空态要诚实（取到了 0 条就说 0 条），并给一条能走的路：
            // 外链频道卡被内页吸收后（B 线），片单查不到的频道退回它自己的网页，
            // 而不是掉回旧列表页；子频道页查不到就说这个配置频道还没有子频道。
            Column(Modifier.padding(14.dp)) {
                Text(
                    when {
                        // 搜索 0 结果先说「没搜到」再谈列表口径（用户 2026-11-26）。
                        pager.keyword.isNotBlank() -> feedEmptyCopy(pager.keyword, "")
                        page.kind == ScreeningPageKind.SUBCHANNELS -> "这个频道服务端还没配子频道。"
                        filter == "未观看" -> "这一页没有内容。切回「全部」或者返回上一个频道。"
                        else -> "这个频道服务端还没上片单。"
                    },
                    color = Muted,
                )
                if (page.webFallback != null && page.kind == ScreeningPageKind.VIDEOS) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(Tile).background(Lift)
                            .clickable { onWeb(page.webFallback, page.title) }.padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Mint)
                        Text("在网页里打开", Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
        if (poster) items(shown.chunked(3)) { row ->
            // 海报视图与首页网格同一档：三列，一屏多装一半
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { column, entry ->
                        EntryCard(entry, progress[entry.key] ?: 0L, true,
                            Modifier.weight(1f).enterFeedItem(seen, entry.key, column), openEntry)
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                // AD3：banner 按旧位置表插在整行下方（见 ScreeningAds 的注释）
                FeedGridAdSlot(shown.indexOf(row.last()))
            }
        } else items(shown, key = { it.key }) { entry ->
            Column {
                EntryCard(entry, progress[entry.key] ?: 0L, false,
                    Modifier.fillMaxWidth().enterFeedItem(seen, entry.key, pager.items.indexOf(entry)), openEntry)
                FeedRowAdSlot(pager.items.indexOf(entry))
            }
        }
        item { FeedEndRow(pager.loading, pager.hasMore, pager::loadMore,
            pager.loading && pager.items.isEmpty(), empty = pager.items.isEmpty()) }
    }
}

@Composable
private fun EntryCard(entry: RemoteEntry, position: Long, poster: Boolean, modifier: Modifier, open: (RemoteEntry) -> Unit) {
    val art: @Composable (Modifier) -> Unit = { m ->
        // 海报视图三列后格子很窄，比例再乱就是锯齿：统一 0.8 竖片比例，行行对齐。
        // 服务端推荐位不下发时长，卡片不画时长角标和进度条（分母未知）。
        Box(m.aspectRatio(if (poster) 0.8f else 16f / 9).clip(Tile)) {
            EntryCover(entry, Modifier.matchParentSize())
        }
    }
    val title: @Composable () -> Unit = {
        Text(entry.title, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (position > 0) "已看到 ${time(position)}" else "未观看", color = Muted, fontSize = 11.sp)
    }
    val clickable = modifier.pressable(Tile) { open(entry) }
    if (poster) Column(clickable) { art(Modifier.fillMaxWidth()); Spacer(Modifier.height(4.dp)); title() }
    else Row(clickable, verticalAlignment = Alignment.CenterVertically) {
        art(Modifier.width(112.dp)); Column(Modifier.padding(horizontal = 11.dp)) { title() }
    }
}

/** 区块标题：字大不等于占地方——行高与上下留白收小，标题本身仍压得住层级。 */
@Composable
internal fun Heading(title: String, detail: String = "") {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = White, fontSize = ScreeningTypography.section, fontWeight = FontWeight.Black)
        if (detail.isNotEmpty()) Text(detail, color = Muted, fontSize = 12.sp)
    }
}

@Composable
internal fun FilterRow(labels: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(labels) { name -> FilterChip(selected = name == selected, onClick = { onSelect(name) }, label = { Text(name) },
            shape = RoundedCornerShape(8.dp), colors = FilterChipDefaults.filterChipColors(containerColor = Lift,
                selectedContainerColor = Mint, labelColor = Muted, selectedLabelColor = Ink), border = null) }
    }
}
