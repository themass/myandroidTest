package com.openapi.feature.video.screening

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 图集原生页，三层：封面瀑布流 → 图集网格 → 全屏阅览。
 *
 * 数据来自真接口：列表用 `API_IMG_CHANNLE_URL`，册内图片用 `API_IMG_ITEMS_IMG_URL`，
 * 分页参数跟旧页面一致（内页的 `channel=` 取服务端的 `param` 字段，见 `ScreeningFeed.param`）。
 * 没通就报错误 + 重试，通了没数据就是空——这里没有任何随包样例数据。
 *
 * 与设计稿一致的点：
 * - 封面用三列受控瀑布流（按各自的宽高比），不是等高卡片；
 * - 图集内是规则缩略图网格，点第 N 张就从第 N 张全屏，返回还在原来的滚动位置（列表状态由
 *   `rememberLazyListState` 持有，没有被重建）；
 * - 全屏支持左右滑动、双指缩放、位置计数；缩放后拖动查看细节。
 */
@Composable
internal fun ScreeningGallery(
    store: ScreeningFeedStore,
    /** 从首页图片卡进来时带的频道；null 就直说没有可看的频道，不演样例。 */
    channelParam: String?,
    onExit: () -> Unit,
) {
    var album by remember { mutableStateOf<RemoteEntry?>(null) }
    var viewing by remember { mutableStateOf(-1) }
    val coverState = rememberLazyListState()

    BackHandler(enabled = viewing >= 0 || album != null) {
        when {
            viewing >= 0 -> viewing = -1
            album != null -> album = null
            else -> onExit()
        }
    }

    val pagerScope = rememberCoroutineScope()
    val covers = remember(channelParam) {
        // 图集列表级不带搜索：旧 ImgChannleBodyFragment 的 URL 就没有 keyword 槽位，
        // 服务端这个接口也从没按 keyword 查过——搜索只接在真正支持的四个列表页上。
        FeedPager(pagerScope, fetch = { start, _ -> store.albums(channelParam ?: "", start) })
    }
    val selected = album
    var tailItemUrl by remember(selected) { mutableStateOf<String?>(null) }
    val shots = remember(selected) {
        FeedPager(pagerScope, fetch = { start, keyword ->
            // 尾续只对「没有搜索词」生效（用户 2026-11-26：走到尾续后新搜索被静默丢词——
            // 旧写法只看 tailItemUrl，置位后 keyword 再也进不了请求）。带词一律走回
            // img/items/img.json；回到无词态才继续 item/page.json 的尾续。
            if (albumImagesUseTail(tailItemUrl, keyword)) store.imageItemPage(tailItemUrl!!, start)
            else store.albumImages(selected?.imageListKey() ?: "", start, keyword)
        })
    }
    // 没有频道参数或没有册子参数就没有可请求的 key：不发无意义的请求，直接给空态
    LaunchedEffect(covers, channelParam) { if (channelParam != null) covers.load() }
    LaunchedEffect(shots, selected) { if (selected?.imageListKey() != null) shots.load() }

    // 三层之间是「进一层/退一层」，跟放映场主页共用同一套转场契约。
    val motion = LocalScreeningMotion.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val slidePx = with(density) { motion.slideDistanceDp().dp.toPx() }
    val (fadeEnter, fadeExit) = screenFade(motion)

    Box(Modifier.fillMaxSize().background(Ink)) {
        AnimatedContent(
            targetState = if (selected == null) 0 else 1,
            transitionSpec = { screenSlide(motion, slidePx, targetState > initialState) },
            label = "albumLevel",
        ) { level ->
            if (level == 0) {
                AlbumCovers(covers, coverState, onOpen = { album = it }, onExit = onExit)
            } else {
                val book = selected ?: return@AnimatedContent
                AlbumContents(book, shots, onBack = { album = null }, onOpen = { viewing = it })
            }
        }
        val images = shots.items
        AnimatedVisibility(
            visible = viewing in images.indices,
            enter = fadeIn(tween(motion.enterDurationMs(), easing = EaseStandard)),
            exit = fadeOut(tween(motion.exitDurationMs(), easing = EaseExit)),
            label = "lightbox",
        ) {
            if (viewing in images.indices) Lightbox(
                images,
                viewing,
                onMove = { viewing = it },
                onTail = { current ->
                    galleryTailItemUrl(current)?.let { itemUrl ->
                        tailItemUrl = itemUrl
                        shots.loadMore()
                    }
                },
            )
        }
    }
}

/**
 * 首页图片频道卡 → 图集列表的分页参数。
 * 旧页面 `ImgChannleBodyFragment` 用的就是 actionUrl query 里的 `channel=`，跟它取同一个值。
 */
internal fun RemoteEntry.galleryChannelParam(): String? = channelParam

/**
 * 一册图集 → 册内图片的分页参数。
 * 旧页面 `ImgChannelImgListFragment.getUrl` 传的是 `vo.param`，不是 actionUrl 里那个 `channel=`，
 * 所以优先 [RemoteEntry.param]，没有才退回 actionUrl 的参数。
 */
internal fun RemoteEntry.imageListKey(): String? = param ?: channelParam

/** 全屏原图优先沿用旧页语义；imageUrl 只是网格缩略图。 */
internal fun lightboxImageUrl(shot: RemoteEntry): String? =
    shot.actionUrl.takeIf { it.isNotBlank() } ?: shot.imageUrl?.takeIf { it.isNotBlank() }

/** item/page.json 的 key 是尾部图片行自己的 actionUrl。 */
internal fun galleryTailItemUrl(shot: RemoteEntry?): String? =
    shot?.actionUrl?.takeIf { it.isNotBlank() }
/**
 * 封面瀑布流的列数。旧 View 版图集（`ImgChannleBodyFragment` 那条链）网格 span 本来就是 3，
 * Compose 迁移时手写瀑布流写成 2 列，和旧版、和同 App 其它图集页都对不上（台账 A27）。
 * 抽成常量 + [albumCoverColumn] 纯函数：列分配是纯索引算术，能被 JVM 单测钉住。
 */
internal const val ALBUM_COVER_COLUMNS = 3

/** 第 [index] 个封面落在哪一列：按列轮转，列内保持服务端返回顺序。 */
internal fun albumCoverColumn(index: Int): Int = index % ALBUM_COVER_COLUMNS

/** 第 [column] 列的封面（保持原顺序）。[items] 为服务端返回的完整列表。 */
internal fun <T> albumCoverColumnItems(items: List<T>, column: Int): List<T> =
    items.filterIndexed { index, _ -> albumCoverColumn(index) == column }

@Composable
private fun AlbumCovers(pager: FeedPager, state: LazyListState, onOpen: (RemoteEntry) -> Unit, onExit: () -> Unit) {
    val listState = state
    val seen = remember { mutableSetOf<String>() }
    feedShouldLoadMore(listState, pager)
    val shown = pager.items
    FeedListShell(
        pager = pager,
        state = listState,
        title = "图集",
        onBack = onExit,
        detail = feedToolbarDetail(pager.loading, shown.size, "本"),
    ) {
        item {
            // 同声音馆：单句限一行，不硬折行（台账 A29）。
            Text("一册一地方，翻到末页。", color = White, fontSize = ScreeningTypography.hero, lineHeight = ScreeningTypography.heroLineHeight,
                fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            FeedError(pager.error, pager::reload)
            Spacer(Modifier.height(12.dp))
            // 首屏加载只留扫光骨架一种信号，不再叠转圈（用户点过名）
            if (pager.loading && shown.isEmpty()) {
                ListSkeleton(Modifier.padding(top = 2.dp), rows = 2, rowHeight = 170.dp)
            }
        }
        if (!pager.loading && shown.isEmpty()) {
            item { Text("这里还没有图集，返回换个频道试试。", Modifier.padding(14.dp), color = Muted) }
        }
        if (shown.isNotEmpty()) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(ALBUM_COVER_COLUMNS) { column ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        albumCoverColumnItems(shown, column).forEachIndexed { index, book ->
                            Column(Modifier.enterFeedItem(seen, book.key, index).pressable(Tile) { onOpen(book) }) {
                                Box(Modifier.fillMaxWidth().aspectRatio(book.ratio.coerceIn(0.6f, 1.7f))) {
                                    EntryCover(book, Modifier.matchParentSize())
                                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x33141729), Ink))))
                                    // 三列后列宽只有原来的 2/3：徽标和标题都要收一档，
                                    // 否则 11.sp 徽标加 19.sp 标题会挤在 1/3 屏宽里互相顶掉。
                                    if (book.description.isNotBlank()) {
                                        Text(
                                            book.description,
                                            Modifier.align(Alignment.TopEnd).padding(6.dp)
                                                .background(Ink.copy(alpha = .72f), RoundedCornerShape(5.dp))
                                                .padding(horizontal = 5.dp, vertical = 2.dp),
                                            fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Column(Modifier.align(Alignment.BottomStart).padding(9.dp)) {
                                        Text(book.title, color = White, fontSize = ScreeningTypography.item, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { FeedEndRow(pager.loading, pager.hasMore, pager::loadMore,
            pager.loading && shown.isEmpty(), empty = shown.isEmpty()) }
    }
}

@Composable
private fun AlbumContents(book: RemoteEntry, pager: FeedPager, onBack: () -> Unit, onOpen: (Int) -> Unit) {
    val state = rememberLazyListState()
    val seen = remember { mutableSetOf<String>() }
    feedShouldLoadMore(state, pager)
    val shots = pager.items
    FeedListShell(
        pager = pager,
        state = state,
        title = book.title,
        onBack = onBack,
        detail = feedToolbarDetail(pager.loading, shots.size, "张"),
        // 搜索（用户点名）：对齐旧 ImgChannelImgListFragment——img/items/img.json 带 keyword。
        searchable = true,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 11f).clip(RoundedCornerShape(12.dp, 32.dp, 12.dp, 12.dp))) {
                EntryCover(book, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink))))
                Column(Modifier.align(Alignment.BottomStart).padding(19.dp)) {
                    Text(book.title, color = White, fontSize = ScreeningTypography.featureTitle, lineHeight = ScreeningTypography.featureTitleLineHeight, fontWeight = FontWeight.Black,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (book.description.isNotBlank()) Text(book.description, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            FeedError(pager.error, pager::reload)
            // 同列表页：首屏等待只留扫光骨架，不叠第二个转圈
            if (pager.loading && shots.isEmpty()) {
                ListSkeleton(rows = 2, rowHeight = 120.dp)
            }
        }
        // 空态按搜索态分支（用户 2026-11-26：搜 0 结果仍印列表口径，看不出是「没搜到」）。
        if (!pager.loading && shots.isEmpty()) item { Text(feedEmptyCopy(pager.keyword, "这一册还没有图片，返回换一册。"), Modifier.padding(14.dp), color = Muted) }
        items(shots.chunked(3)) { row ->
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEachIndexed { column, shot ->
                    val index = shots.indexOf(shot)
                    Box(
                        Modifier.weight(1f).aspectRatio(1f)
                            .enterFeedItem(seen, shot.key, column)
                            .pressable(RoundedCornerShape(7.dp)) { onOpen(index) },
                    ) {
                        EntryCover(shot, Modifier.matchParentSize())
                        if (shot.title.isNotBlank()) {
                            Text(
                                shot.title,
                                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3141729))))
                                    .padding(horizontal = 6.dp, vertical = 5.dp),
                                color = White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
                // AD3：图集正文列表此前无槽（旧 native 通路早已注释停用，台账 §4.2）。
                FeedGridAdSlot(shots.indexOf(row.last()))
            }
            Spacer(Modifier.height(5.dp))
        }
        item { FeedEndRow(pager.loading, pager.hasMore, pager::loadMore, empty = shots.isEmpty()) }
    }
}

/** 全屏阅览：滑动换张、双指缩放、单击开关信息、返回收起。 */
@Composable
private fun Lightbox(
    shots: List<RemoteEntry>,
    start: Int,
    onMove: (Int) -> Unit,
    onTail: (RemoteEntry) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pager = rememberPagerState(initialPage = start.coerceIn(shots.indices)) { shots.size }
    var chrome by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(pager.currentPage) {
        zoom = 1f
        pan = Offset.Zero
        if (pager.currentPage == shots.lastIndex) shots.lastOrNull()?.let(onTail)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = zoom <= 1.01f,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val shot = shots[page]
            Box(Modifier.fillMaxSize().pointerInput(shot.key) {
                detectTransformGestures { _, drag, gesture, _ ->
                    zoom = (zoom * gesture).coerceIn(1f, 4f)
                    pan = if (zoom > 1f) pan + drag else Offset.Zero
                }
            }) {
                RemoteCover(
                    lightboxImageUrl(shot),
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom; scaleY = zoom
                            translationX = pan.x; translationY = pan.y
                        },
                )
            }
        }
        Box(Modifier.matchParentSize().pointerInput(Unit) {
            detectTapGestures { chrome = !chrome }
        })
        if (chrome) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .72f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onMove(-1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "收起", tint = White) }
                Column(Modifier.weight(1f)) {
                    val shot = shots[pager.currentPage.coerceIn(shots.indices)]
                    Text(shot.title, color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${pager.currentPage + 1} / ${shots.size}", color = Muted, fontSize = 12.sp)
                }
                if (zoom > 1.01f) TextButton({ zoom = 1f; pan = Offset.Zero }) { Text("还原") }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .78f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 「收藏 / 下载」两个键以前是空实现（点了没反应），随死代码一并撤掉；
                // 做真功能时再加回来。
                Spacer(Modifier.weight(1f))
                // 分享（A40 用户 2026-11-26）：发当前这一张的身份地址；没有地址不出现按钮。
                val shareShot = shots.getOrNull(pager.currentPage)
                val shareUrl = shareShot?.let { lightboxImageUrl(it) }
                if (!shareUrl.isNullOrBlank()) {
                    IconButton(onClick = {
                        com.openapi.common.ui.share.ShareKit.shareText(
                            context, shareUrl, subject = shareShot.title,
                        )
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, "分享", tint = White)
                    }
                }
                Text("双指缩放 · 左右滑动", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        if (chrome) {
            // 滑动指示：细线在底部，比圆点更克制。图多的时候细线太密，只画前 20 段。
            val ticks = shots.size.coerceAtMost(20)
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).height(2.dp).fillMaxWidth(0.4f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(ticks) { index ->
                    val page = (index.toFloat() * shots.size / ticks).toInt().coerceIn(shots.indices)
                    Box(
                        Modifier.weight(1f).fillMaxHeight().background(
                            if (page == pager.currentPage) Mint else White.copy(alpha = .28f),
                        ),
                    )
                }
            }
        }
        val glow by animateFloatAsState(if (zoom > 1f) 1f else 0f, label = "zoomHint")
        if (glow > 0f && chrome) {
            Text(
                "${(zoom * 100).toInt()}%",
                Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 16.dp)
                    .background(Ink.copy(alpha = .7f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                color = Mint, fontSize = 11.sp,
            )
        }
    }
}
