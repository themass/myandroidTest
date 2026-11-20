package com.openapi.feature.video.screening

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.openapi.core.config.Constants
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
    /** 懒播放地址在路上：点了卡到地址回来之间要有反馈，不能像没点上。 */
    var opening by remember { mutableStateOf(false) }
    val holder = rememberSaveableStateHolder()
    val preferences = remember { activity.getSharedPreferences("screening-preview", 0) }
    val progress = remember { mutableStateMapOf<String, Long>() }
    val openEntry: (RemoteEntry) -> Unit = { entry ->
        scope.launch {
            opening = true
            // needLazyUrl 的条目这一步才拿到真正能播的地址；样例条目原样返回
            val url = store.playUrl(entry)
            val asset = entry.assetId()
            selection = if (asset != null) {
                ScreeningSource(entry.title, entry.description, asset = asset, key = entry.key)
            } else {
                ScreeningSource(entry.title, entry.description, uri = Uri.parse(url), key = entry.key)
            }
            opening = false
        }
    }
    val openChannel: (RemoteEntry) -> Unit = { entry ->
        channelPage = ScreeningChannelPage(
            title = entry.title,
            detail = entry.description,
            coverUrl = entry.imageUrl,
            coverAsset = entry.sampleCoverAsset(),
            channelParam = entry.channelParam,
            sample = sampleChannelFor(entry),
        )
    }
    val openGallery: (RemoteEntry?) -> Unit = { entry ->
        galleryChannel = entry?.galleryChannelParam()
        gallery = true
    }
    BackHandler(channelPage != null && selection == null) { channelPage = null }
    Surface(Modifier.fillMaxSize()) {
        val selected = selection
        val current = channelPage
        when {
            selected != null -> key(selected) {
                ScreeningPlayback(activity, selected, current?.title.orEmpty(), progress[selected.key] ?: 0L,
                    onExit = { selection = null }, onSave = { position ->
                        progress[selected.key] = position
                        preferences.edit().putLong(progressKey(selected.key), position).apply()
                    })
            }
            current != null -> holder.SaveableStateProvider(current.title) {
                Library(current, store, progress, preferences, { channelPage = null }, openEntry) { source ->
                    selection = source
                }
            }
            gallery -> ScreeningGallery(activity, store, galleryChannel) { gallery = false }
            else -> holder.SaveableStateProvider("channels") {
                Channels(store, onBack = { activity.finish() }, onChannel = openChannel, onPlay = openEntry, onOpenLocal = {
                    channelPage = ScreeningChannelPage(samples.title, samples.category, null, samples.cover, null, samples)
                }, onGallery = openGallery)
            }
        }
        if (opening) Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp, color = Mint)
        }
    }
}

/** 进度存的键：所有条目（样例和真实）都按 [RemoteEntry.key] 存，一条规则。 */
private fun progressKey(entryKey: String) = "position:$entryKey"

private fun storedProgress(preferences: android.content.SharedPreferences, entryKey: String): Long =
    preferences.getLong(progressKey(entryKey), if (entryKey == sampleReelKey("ridge")) 4200L else 0L)

/** 一个推荐位在页面上怎么响应：频道/图集入口进内页，其余直接播。 */
private fun routeOf(entry: RemoteEntry): FeedRoute = when {
    entry.isImageCard -> FeedRoute.ALBUM
    entry.isGroupEntry -> FeedRoute.CHANNEL
    else -> FeedRoute.PLAY
}

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

@Composable
private fun Channels(
    store: ScreeningFeedStore,
    onBack: () -> Unit,
    onChannel: (RemoteEntry) -> Unit,
    onPlay: (RemoteEntry) -> Unit,
    onOpenLocal: () -> Unit,
    onGallery: (RemoteEntry?) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(FeedCategory.ALL.label) }
    val category = FeedCategory.entries.firstOrNull { it.label == filter } ?: FeedCategory.ALL
    val pagerScope = rememberCoroutineScope()
    // 每个分类一个 pager 并留档：切回看过的分类直接展示已加载内容（秒回、不闪、不重复打接口），
    // 没看过的分类从占位加载开始。原来 pager 随分类整个重建，切 tab 必先整屏清空再填——那就是闪。
    val pagerCache = remember { mutableStateMapOf<FeedCategory, FeedPager>() }
    val pager = pagerCache.getOrPut(category) {
        FeedPager(pagerScope, fetch = { start -> store.channels(category, start) })
    }
    val listState = rememberLazyListState()
    LaunchedEffect(pager) { pager.load() }
    LaunchedEffect(listState, pager) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged().collect { index -> if (index >= pager.items.size - 4) pager.loadMore() }
    }
    val cards = pager.items.filter { routeOf(it) != FeedRoute.PLAY }
    val hero = pager.items.firstOrNull { !it.isImageCard }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), state = listState,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                // 放映场是独立 Activity，系统返回之外得有个看得见的退出口
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Icon(Icons.Default.PlayArrow, null, tint = Mint)
                Text("放映场", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        pager.loading && pager.items.isEmpty() -> "正在取数据…"
                        pager.error != null -> "未连上服务端"
                        else -> "服务端数据"
                    },
                    color = Muted, fontSize = 12.sp,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("频道", color = White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(24.dp).height(3.dp).background(Mint, Tile))
                }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = onOpenLocal) { Text("本地视频", color = Muted, fontSize = 18.sp) }
                TextButton(onClick = { onGallery(null) }) { Text("图集", color = Muted, fontSize = 18.sp) }
            }
            FeedError(pager.error, pager::reload)
            // 推荐位的高度是恒定的：没数据时占同一块高度，而不是整块不存在。
            // 原来 hero 为 null 时这里高 0，数据一回来凭空插入 352dp，下面所有内容由
            // 「紧贴 tab」被顶到「隔一整屏」——这就是切 tab / 首屏加载时的跳动闪屏。
            Spacer(Modifier.height(13.dp))
            Box(Modifier.fillMaxWidth().height(352.dp).clip(RoundedCornerShape(13.dp, 38.dp, 13.dp, 13.dp))) {
                val entry = hero
                if (entry == null) {
                    // 空占位不再是一块死色板：加载中扫一层缓慢移动的光，
                    // 表示「这块位置留着，内容在路上」；失败就停成静态底，不空带动画
                    if (pager.loading) ShimmerPlaceholder(Modifier.matchParentSize())
                    else Box(Modifier.matchParentSize().background(Lift))
                    if (pager.loading) {
                        CircularProgressIndicator(
                            Modifier.align(Alignment.Center).size(26.dp), strokeWidth = 2.dp, color = Mint,
                        )
                    }
                } else {
                    Box(Modifier.matchParentSize()
                        .clickable {
                            when (routeOf(entry)) {
                                FeedRoute.PLAY -> onPlay(entry)
                                FeedRoute.CHANNEL -> onChannel(entry)
                                FeedRoute.ALBUM -> onGallery(entry)
                            }
                        }) {
                        EntryCover(entry, Modifier.matchParentSize())
                        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x45202340), Ink))))
                        Column(Modifier.align(Alignment.BottomStart).padding(23.dp)) {
                            Text(if (entry.isGroupEntry) "频道推荐" else "正在热映", Modifier.background(Peach, RoundedCornerShape(5.dp))
                                .padding(6.dp), color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            Text(entry.title, color = White, fontSize = 44.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (entry.description.isNotBlank()) {
                                Spacer(Modifier.height(9.dp))
                                Text(entry.description, color = White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(13.dp))
                            Button(onClick = {
                                when (routeOf(entry)) {
                                    FeedRoute.PLAY -> onPlay(entry)
                                FeedRoute.CHANNEL -> onChannel(entry)
                                FeedRoute.ALBUM -> onGallery(entry)
                            }
                        }, shape = RoundedCornerShape(9.dp)) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(7.dp))
                            Text(if (entry.isGroupEntry) "看这个频道" else "看预告")
                        }
                    }
                }
                }
            }
            Heading("逛频道", if (pager.loading && pager.items.isEmpty()) "正在取数据…" else "${cards.size} 个频道")
            FilterRow(FeedCategory.entries.map { it.label }, filter) { filter = it }
            Spacer(Modifier.height(9.dp))
            // 加载态只在上面的推荐位占位里转圈：这里再放一块 190dp 的转圈区，
            // 数据一到它整块消失、网格顶上，等于第二次布局跳动。
        }
        if (cards.isNotEmpty()) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) { column ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        cards.filterIndexed { index, _ -> index % 2 == column }.forEach { entry ->
                            Box(Modifier.fillMaxWidth().aspectRatio(entry.ratio.coerceIn(0.6f, 1.6f)).clip(Tile)
                                .clickable {
                                    when (routeOf(entry)) {
                                        FeedRoute.PLAY -> onPlay(entry)
                                        FeedRoute.CHANNEL -> onChannel(entry)
                                        FeedRoute.ALBUM -> onGallery(entry)
                                    }
                                }) {
                                EntryCover(entry, Modifier.matchParentSize())
                                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Ink))))
                                Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                                    Text(entry.title, color = White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (entry.description.isNotBlank()) {
                                        Text(entry.description, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { FeedEndRow(pager.loading, pager.hasMore, pager::loadMore) }
    }
}

@Composable
private fun Library(
    page: ScreeningChannelPage,
    store: ScreeningFeedStore,
    progress: MutableMap<String, Long>,
    preferences: android.content.SharedPreferences,
    back: () -> Unit,
    openEntry: (RemoteEntry) -> Unit,
    onFile: (ScreeningSource) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(OpenDocument()) { uri -> uri?.let {
        onFile(ScreeningSource(documentName(context, it), "在此设备上播放", uri = it, key = "file:${it}"))
    } }
    var filter by rememberSaveable { mutableStateOf("全部") }
    var poster by rememberSaveable { mutableStateOf(false) }
    val channelParam = page.channelParam
    // 真实频道按 channel= 取服务端片单；「本地视频」页放的是随包样片，本来就不走网络
    val pager = remember(page) {
        FeedPager(scope,
            fetch = { start ->
                if (channelParam != null) store.videos(channelParam, start)
                else ScreeningFeedStore.Page(sampleReels(page.sample), null, false)
            })
    }
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
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp)) {
        item {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回频道") }
            Box(Modifier.fillMaxWidth().height(215.dp).clip(RoundedCornerShape(12.dp, 32.dp, 12.dp, 12.dp))) {
                if (page.coverAsset != null) Cover(page.coverAsset, Modifier.matchParentSize())
                else RemoteCover(page.coverUrl, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink))))
                Column(Modifier.align(Alignment.BottomStart).padding(19.dp)) {
                    Text(if (page.channelParam != null) Constants.BASE_IP else "离线样片库", color = Peach, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(page.title, color = White, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 42.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (pager.loading) "正在取数据…" else "${pager.items.size} 条", color = Muted)
                }
            }
            FeedError(pager.error, pager::reload)
            if (page.sample != null) FilledTonalButton(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("打开手机里的视频")
            }
            resume?.let { entry ->
                Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(Tile).background(Lift).clickable { openEntry(entry) }.padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, tint = Mint)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text("继续看《${entry.title}》", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("从 ${time(progress.getValue(entry.key))} 接着播", color = Muted, fontSize = 12.sp)
                    }
                    Text("续播", color = Mint)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("全部视频", Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Black)
                IconButton(onClick = { poster = !poster }) { Icon(if (poster) Icons.Default.ViewList else Icons.Default.GridView, if (poster) "切换列表视图" else "切换海报视图") }
            }
            FilterRow(listOf("全部", "未观看"), filter) { filter = it }
            if (pager.loading && pager.items.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                }
            }
        }
        if (!pager.loading && shown.isEmpty()) item { Text("这一页没有内容。切回「全部」或者返回上一个频道。", Modifier.padding(14.dp), color = Muted) }
        if (poster) items(shown.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { entry -> EntryCard(entry, progress[entry.key] ?: 0L, true, Modifier.weight(1f), openEntry) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        } else items(shown, key = { it.key }) { entry -> EntryCard(entry, progress[entry.key] ?: 0L, false, Modifier.fillMaxWidth(), openEntry) }
        item { FeedEndRow(pager.loading, pager.hasMore, pager::loadMore) }
    }
}

@Composable
private fun EntryCard(entry: RemoteEntry, position: Long, poster: Boolean, modifier: Modifier, open: (RemoteEntry) -> Unit) {
    val duration = entry.sampleDurationMs()
    val art: @Composable (Modifier) -> Unit = { m ->
        Box(m.aspectRatio(if (poster) entry.ratio.coerceIn(0.6f, 1.6f) else 16f / 9).clip(Tile)) {
            EntryCover(entry, Modifier.matchParentSize())
            if (duration > 0L) Text(time(duration), Modifier.align(Alignment.BottomEnd).padding(6.dp)
                .background(Ink, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp), fontSize = 11.sp)
            if (position > 0 && duration > 0L) Box(Modifier.align(Alignment.BottomStart)
                .fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).height(3.dp).background(Mint))
        }
    }
    val title: @Composable () -> Unit = {
        Text(entry.title, color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(when {
            duration > 0L && position >= duration -> "已看完"
            position > 0 -> "已看到 ${time(position)}"
            else -> "未观看"
        }, color = Muted, fontSize = 12.sp)
    }
    val clickable = modifier.clip(Tile).clickable { open(entry) }
    if (poster) Column(clickable) { art(Modifier.fillMaxWidth()); Spacer(Modifier.height(5.dp)); title() }
    else Row(clickable, verticalAlignment = Alignment.CenterVertically) {
        art(Modifier.width(130.dp)); Column(Modifier.padding(horizontal = 13.dp)) { title() }
    }
}

@Composable
internal fun Heading(title: String, detail: String = "") {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = White, fontSize = 24.sp, fontWeight = FontWeight.Black)
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
