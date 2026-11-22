package com.openapi.feature.video.screening

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
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

class ScreeningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Mint, onPrimary = Ink, background = Ink,
                onBackground = White, surface = Ink, onSurface = White, surfaceVariant = Lift, onSurfaceVariant = Muted)) {
                ScreeningHome(this)
            }
        }
    }

    internal fun immersive(on: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            if (on) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

@Composable
private fun ScreeningHome(activity: ScreeningActivity) {
    val scope = rememberCoroutineScope()
    val store = remember { ScreeningFeedStore(activity) }
    var channelPage by remember { mutableStateOf<ScreeningChannelPage?>(null) }
    var selection by remember { mutableStateOf<ScreeningSource?>(null) }
    var gallery by remember { mutableStateOf(false) }
    /** 从首页图片卡进图集时带的 `channel=`；null 时图集页直接给空态，不演样例。 */
    var galleryChannel by remember { mutableStateOf<String?>(null) }
    /**
     * 首屏三个标签：0=频道 1=本地视频 2=图集。它们是同一屏的三种看法，不是三层页面——
     * 切换在页面内原地淡切（用户点过名「切换跳和闪」），所以三种内容共用的取数状态
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
    /** 「本地视频」标签：页对象与已看进度固定在首页层，来回切标签不重建、不重新取数。 */
    val localPage = remember {
        ScreeningChannelPage(samples.title, samples.category, null, samples.cover, null, samples)
    }
    val localPager = remember(localPage) {
        FeedPager(feedScope, fetch = { ScreeningFeedStore.Page(sampleReels(localPage.sample), null, false) })
    }
    val localProgress = remember { mutableStateMapOf<String, Long>() }
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
            // needLazyUrl 的条目这一步才拿到真正能播的地址；样例条目原样返回
            val url = store.playUrl(entry)
            val asset = entry.assetId()
            forward = true
            selection = if (asset != null) {
                ScreeningSource(entry.title, entry.description, asset = asset, key = entry.key)
            } else {
                ScreeningSource(entry.title, entry.description, uri = Uri.parse(url), key = entry.key)
            }
            opening = false
        }
    }
    val openChannel: (RemoteEntry) -> Unit = { entry ->
        forward = true
        channelPage = ScreeningChannelPage(
            title = entry.title,
            detail = entry.description,
            coverUrl = entry.imageUrl,
            coverAsset = entry.sampleCoverAsset(),
            // 原页面 VideoChannelListFragment.getUrl 用的是 vo.param。外链频道卡以前在这里
            // 被置空（掉回 WebView 就不用内页取数了）；B 线吸收后必须把 key 传下去，
            // 内页才拿它发 items.json。没有 key 的入口（样例频道）照旧走样片兜底。
            channelParam = entry.videoChannelKey(),
            sample = sampleChannelFor(entry),
        )
    }
    /** 外链/配置频道卡：交回原路由（`ConfigActionJump` 按 scheme 分流 WebView 或旧频道页）。 */
    val openWeb: (RemoteEntry) -> Unit = { entry ->
        CoreEvents.postConfigAction(ConfigActionEvent(activity, entry.actionUrl, entry.title, HashMap()))
    }
    val openGallery: (RemoteEntry?) -> Unit = { entry ->
        forward = true
        galleryChannel = entry?.galleryChannelParam()
        gallery = true
    }
    BackHandler(channelPage != null && selection == null) { forward = false; channelPage = null }
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
                        ScreeningPlayback(activity, target.source, target.channel, progress[target.source.key] ?: 0L,
                            onExit = { forward = false; selection = null }, onSave = { position ->
                                progress[target.source.key] = position
                                preferences.edit().putLong(progressKey(target.source.key), position).apply()
                            })
                    }
                }
                is GalleryScreen -> holder.SaveableStateProvider("gallery") {
                    ScreeningGallery(activity, store, target.channelParam) { forward = false; gallery = false }
                }
                is LibraryScreen -> holder.SaveableStateProvider(target.page.title) {
                    // 真频道的 pager 随这一页建、离开即弃（缓存策略不同）；本地标签那份在首页层。
                    // scope 用首页层的 feedScope：退场动画播的同时，在途请求不该被组合拆散牵连取消。
                    val param = target.page.channelParam
                    val pagePager = remember(target.page) {
                        FeedPager(feedScope) { start ->
                            if (param != null) store.videos(param, start)
                            else ScreeningFeedStore.Page(sampleReels(target.page.sample), null, false)
                        }
                    }
                    Library(target.page, progress, preferences, pagePager,
                        back = { forward = false; channelPage = null }, openEntry = openEntry,
                        onFile = { source -> selection = source })
                }
                else -> holder.SaveableStateProvider("channels") {
                    HomeTabs(store, feedScope, homeTab, { homeTab = it },
                        channelFilter, { channelFilter = it }, channelPagers,
                        localPage, localPager, progress, preferences,
                        onBack = { activity.finish() },
                        onChannel = openChannel, onPlay = openEntry, onWeb = openWeb,
                        onGallery = { entry -> openGallery(entry) },
                        onLocalFile = { source -> selection = source })
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
 * 首屏：标题行 + 三个标签 + 原地切换的内容。
 *
 * 「频道 / 本地视频 / 图集」是同一屏的三种看法，不是三层页面：切换只做小位移交叉淡入
 * （推页那种 24dp 滑动在 tab 尺度上读作「跳」），内容不先清空再填（那就是「闪」）。
 * 三种内容的取数状态都挂在调用方（ScreeningHome）：切走的 tab 不销毁 pager，
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
    localPage: ScreeningChannelPage,
    localPager: FeedPager,
    progress: MutableMap<String, Long>,
    preferences: android.content.SharedPreferences,
    onBack: () -> Unit,
    onChannel: (RemoteEntry) -> Unit,
    onPlay: (RemoteEntry) -> Unit,
    onWeb: (RemoteEntry) -> Unit,
    onGallery: (RemoteEntry?) -> Unit,
    /** 本地标签里选中的手机文件：直接进播放器，和从列表点开同一条路。 */
    onLocalFile: (ScreeningSource) -> Unit,
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
                1 -> Library(localPage, progress, preferences, localPager,
                    back = { onSelect(0) }, openEntry = onPlay, onFile = onLocalFile, embedded = true)
                2 -> GalleryFeed(
                    // 频道 pager 本来只在频道 tab 里建；从图集 tab 先开时补建同一份，
                    // 两个 tab 共用一次请求，不是各打各的。分类跟频道 tab 的默认值一致（电影）。
                    channelPagers.getOrPut(FeedCategory.MOVIE) {
                        FeedPager(feedScope) { start -> store.channels(FeedCategory.MOVIE, start) }
                    }, onGallery,
                )
                else -> ChannelsFeed(store, feedScope, channelPagers, channelFilter, onChannelFilter,
                    onChannel, onPlay, onWeb, onGallery)
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
private fun TabHeader(tab: Int, onBack: () -> Unit, onSelect: (Int) -> Unit) {
    val tabs = listOf("频道", "本地视频", "图集")
    Column(Modifier.padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            // 放映场是独立 Activity，系统返回之外得有个看得见的退出口
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
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
) {
    // 分类跟旧频道页同口径：只有电影/夜色。老装机存过已删的「全部」：取不到就落回电影，
    // 高亮和取数用同一个 category，不会出现没选中的空档。
    val category = FeedCategory.entries.firstOrNull { it.label == filter } ?: FeedCategory.MOVIE
    // 每个分类一个 pager 并留档在调用方：切回看过的分类直接展示已加载内容
    //（秒回、不闪、不重复打接口），没看过的从扫光占位开始。scope 也在上层：
    // 切走标签时 pager 组合离开，在途请求和失败自动重试不会被牵连取消。
    val pager = pagerCache.getOrPut(category) {
        FeedPager(feedScope, fetch = { start -> store.channels(category, start) })
    }
    val listState = remember(category) { LazyListState() }
    LaunchedEffect(pager) { pager.load() }
    LaunchedEffect(listState, pager) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged().collect { index -> if (index >= pager.items.size - 4) pager.loadMore() }
    }
    val cards = pager.items.filter { routeOf(it) != FeedRoute.PLAY }
    val hero = pager.items.firstOrNull { !it.isImageCard }
    val seen = remember(category) { mutableSetOf<String>() }
    LazyColumn(Modifier.fillMaxSize(), state = listState,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp)) {
        item {
            FeedError(pager.error, pager::reload)
            // 推荐位高度恒定：hero 没到位时同一块高度放扫光，数据落地只换内容不挪布局。
            // 屏小是用户点名的第三件事：推荐位再收到 16:9（216dp），首屏要能同时露出
            // 推荐位 + 分类 + 两行三列网格；卡上的字也整档收小，标题单行截断。
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
                            FeedRoute.WEB -> onWeb(entry)
                            FeedRoute.CONFIG -> onWeb(entry)
                        }
                    }) {
                        EntryCover(entry, Modifier.matchParentSize())
                        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x45202340), Ink))))
                        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                            Text(if (entry.isGroupEntry) "频道推荐" else "正在热映", Modifier.background(Peach, RoundedCornerShape(5.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp), color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(entry.title, color = White, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black,
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
                                    FeedRoute.WEB -> onWeb(entry)
                                    FeedRoute.CONFIG -> onWeb(entry)
                                }
                            }, shape = RoundedCornerShape(9.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                Text(if (entry.isGroupEntry) "看这个频道" else "看预告", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
            Heading("逛频道", if (pager.loading && cards.isEmpty()) "正在取数据…" else "${cards.size} 个频道")
            FilterRow(FeedCategory.entries.map { it.label }, filter, onFilter)
            Spacer(Modifier.height(9.dp))
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
                                        FeedRoute.WEB -> onWeb(entry)
                                        FeedRoute.CONFIG -> onWeb(entry)
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
            firstPageLoading = pager.loading && cards.isEmpty()) }
    }
}

/**
 * 转场的目标状态：一页一个类型，AnimatedContent 据此判断「换页」还是「同页刷新」。
 * 页面要渲染的数据都装在这里，退场动画期间渲染的是离开页自己的快照。
 */
private object HomeScreen
private data class LibraryScreen(val page: ScreeningChannelPage)
private data class GalleryScreen(val channelParam: String?)
private data class PlayScreen(val source: ScreeningSource, val channel: String)

/** 进度存的键：所有条目（样例和真实）都按 [RemoteEntry.key] 存，一条规则。 */
private fun progressKey(entryKey: String) = "position:$entryKey"

private fun storedProgress(preferences: android.content.SharedPreferences, entryKey: String): Long =
    preferences.getLong(progressKey(entryKey), if (entryKey == sampleReelKey("ridge")) 4200L else 0L)

@Composable
internal fun Cover(id: String, modifier: Modifier = Modifier) {
    val bitmap = coverBitmap(LocalContext.current, id)
    Box(modifier.background(Lift)) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** 一张卡的封面：远程地址走 Glide，随包 asset 走 assets，都没有就纯色底。 */
@Composable
internal fun EntryCover(entry: RemoteEntry, modifier: Modifier = Modifier) {
    val asset = entry.sampleCoverAsset() ?: entry.assetId()
    when {
        !entry.imageUrl.isNullOrBlank() -> RemoteCover(entry.imageUrl, modifier)
        asset != null -> Cover(asset, modifier)
        else -> Box(modifier.background(Lift))
    }
}


/**
 * 频道内页（也内嵌作「本地视频」标签，[embedded]）。片单 pager 由调用方注入：
 * 真频道页在页面层建（离开即弃）；本地标签的 pager 挂在首页层——切标签不销毁、
 * 回来不重新取数（同 [ChannelsFeed] 的 pager 留档理由）。
 */
@Composable
private fun Library(
    page: ScreeningChannelPage,
    progress: MutableMap<String, Long>,
    preferences: android.content.SharedPreferences,
    pager: FeedPager,
    back: () -> Unit,
    openEntry: (RemoteEntry) -> Unit,
    onFile: (ScreeningSource) -> Unit,
    embedded: Boolean = false,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(OpenDocument()) { uri -> uri?.let {
        onFile(ScreeningSource(documentName(context, it), "在此设备上播放", uri = it, key = "file:${it}"))
    } }
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
    LaunchedEffect(listState, pager) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged().collect { index -> if (index >= pager.items.size - 4) pager.loadMore() }
    }
    val shown = pager.items.filter { filter != "未观看" || (progress[it.key] ?: 0L) == 0L }
    val resume = pager.items.firstOrNull { (progress[it.key] ?: 0L) > 0L }
    val seen = remember(page) { mutableSetOf<String>() }
    LazyColumn(Modifier.fillMaxSize(), state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = if (embedded) 10.dp else 8.dp, bottom = 9.dp)) {
        item {
            // 内嵌进标签时不画返回键：标签本身就是位置，返回键会把人带回同一个列表（原地打转）
            if (!embedded) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回频道") }
            // 顶卡 168→148dp：屏小，头部只做到「认出这是哪个频道」就够，高度留给片单
            Box(Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(12.dp, 22.dp, 12.dp, 12.dp))) {
                if (page.coverAsset != null) Cover(page.coverAsset, Modifier.matchParentSize())
                else RemoteCover(page.coverUrl, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink))))
                Column(Modifier.align(Alignment.BottomStart).padding(13.dp)) {
                    // 这里原来印 Constants.BASE_IP（接口域名）——调试信息，不是给用户看的（用户点过名）
                    Text(if (page.channelParam != null) "放映场" else "离线样片库", color = Peach, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(page.title, color = White, fontWeight = FontWeight.Black, fontSize = 21.sp, lineHeight = 25.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (pager.loading) "正在取数据…" else "${pager.items.size} 条", color = Muted, fontSize = 11.sp)
                }
            }
            FeedError(pager.error, pager::reload)
            // 样式跟着下面的「继续看」行走：Lift 卡 + 薄荷图标（用户点名实验页不该再混旧的 tonal 按钮）
            if (page.sample != null) Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().clip(Tile).background(Lift).clickable { picker.launch(arrayOf("video/*")) }.padding(11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Mint)
                Text("打开手机里的视频", Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
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
                Text("全部视频", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Black)
                IconButton(onClick = { poster = !poster }) { Icon(if (poster) Icons.Default.ViewList else Icons.Default.GridView, if (poster) "切换列表视图" else "切换海报视图") }
            }
            FilterRow(listOf("全部", "未观看"), filter) { filter = it }
            // 首屏加载：用和条目等高的扫光骨架占位，而不是再来一个转圈
            //（用户点过名「2 个 loading 只要一个」：转圈和底部的转圈会同屏出现）
            if (pager.loading && pager.items.isEmpty()) {
                ListSkeleton(Modifier.padding(top = 8.dp), rowHeight = 64.dp)
            }
        }
        if (!pager.loading && shown.isEmpty()) item { Text("这一页没有内容。切回「全部」或者返回上一个频道。", Modifier.padding(14.dp), color = Muted) }
        if (poster) items(shown.chunked(3)) { row ->
            // 海报视图与首页网格同一档：三列，一屏多装一半
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { column, entry ->
                    EntryCard(entry, progress[entry.key] ?: 0L, true,
                        Modifier.weight(1f).enterFeedItem(seen, entry.key, column), openEntry)
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        } else items(shown, key = { it.key }) { entry ->
            EntryCard(entry, progress[entry.key] ?: 0L, false,
                Modifier.fillMaxWidth().enterFeedItem(seen, entry.key, pager.items.indexOf(entry)), openEntry)
        }
        item { FeedEndRow(pager.loading, pager.hasMore, pager::loadMore, pager.loading && pager.items.isEmpty()) }
    }
}

@Composable
private fun EntryCard(entry: RemoteEntry, position: Long, poster: Boolean, modifier: Modifier, open: (RemoteEntry) -> Unit) {
    val duration = entry.sampleDurationMs()
    val art: @Composable (Modifier) -> Unit = { m ->
        // 海报视图三列后格子很窄，比例再乱就是锯齿：统一 0.8 竖片比例，行行对齐。
        Box(m.aspectRatio(if (poster) 0.8f else 16f / 9).clip(Tile)) {
            EntryCover(entry, Modifier.matchParentSize())
            if (duration > 0L) Text(time(duration), Modifier.align(Alignment.BottomEnd).padding(6.dp)
                .background(Ink, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp), fontSize = 11.sp)
            if (position > 0 && duration > 0L) Box(Modifier.align(Alignment.BottomStart)
                .fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).height(3.dp).background(Mint))
        }
    }
    val title: @Composable () -> Unit = {
        Text(entry.title, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(when {
            duration > 0L && position >= duration -> "已看完"
            position > 0 -> "已看到 ${time(position)}"
            else -> "未观看"
        }, color = Muted, fontSize = 11.sp)
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
        Text(title, Modifier.weight(1f), color = White, fontSize = 19.sp, fontWeight = FontWeight.Black)
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
