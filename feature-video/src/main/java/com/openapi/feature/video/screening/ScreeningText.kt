package com.openapi.feature.video.screening

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.openapi.common.net.http.HttpUtils
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 文字馆原生页：频道列表 → 文章列表 → 正文（HTML）。
 *
 * 接口与旧 [com.openapi.feature.content.text.TextChannleBodyFragment] /
 * [com.openapi.feature.content.text.TextChannelListFragment] /
 * [com.openapi.feature.content.text.TextItemsFragment] 一致。
 *
 * 取数状态（pager、滚动位置）挂在转场之上，对齐 [ScreeningGallery]：进正文、退回目录
 * 拆掉的只是展示的那一层，pager 活着、列表原样在——回到目录不再自动重新取数
 * （用户点名「不是进入列表页都要刷新」）。数据重取只由用户下拉触发（[FeedPager.refresh]），
 * 触底续页仍是 loadMore。
 */
@Composable
internal fun ScreeningText(
    store: ScreeningFeedStore,
    /** 首页文字卡 actionUrl 里的 channel=；null 则首屏空态。 */
    hubChannel: String?,
    onExit: () -> Unit,
    /** 条目 fileUrl 以 .html 结尾时交回 WebView（旧 TextChannelListFragment 分支）。 */
    onOpenWeb: (String, String) -> Unit,
) {
    var hub by rememberSaveable { mutableStateOf<String?>(null) }
    var article by rememberSaveable { mutableStateOf<TextArticleRef?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = article != null || hub != null) {
        when {
            article != null -> article = null
            hub != null -> hub = null
            else -> onExit()
        }
    }

    // 两层列表的 pager 都建在这一层：AnimatedContent 换层拆的是展示组件，不是取数状态。
    val hubs = remember(hubChannel) {
        FeedPager(scope) { start -> store.textChannelHub(hubChannel ?: "", start) }
    }
    val hubParam = hub
    val articles = remember(hubParam) {
        FeedPager(scope) { start -> store.textArticles(hubParam ?: "", start) }
    }
    val hubState = rememberLazyListState()
    val articlesState = rememberLazyListState()
    val hubSeen = remember { mutableSetOf<String>() }
    val articleSeen = remember { mutableSetOf<String>() }
    // 首屏只在 pager 新建立刻取一次；load() 自带「已取过不再取」的闸。
    LaunchedEffect(hubs, hubChannel) { if (hubChannel != null) hubs.load() }
    LaunchedEffect(articles, hubParam) { if (hubParam != null) articles.load() }

    val motion = LocalScreeningMotion.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val slidePx = with(density) { motion.slideDistanceDp().dp.toPx() }

    Box(Modifier.fillMaxSize().background(Ink)) {
        AnimatedContent(
            targetState = when {
                article != null -> 2
                hub != null -> 1
                else -> 0
            },
            transitionSpec = { screenSlide(motion, slidePx, targetState > initialState) },
            label = "textLevel",
        ) { level ->
            when (level) {
                0 -> TextHubPicker(
                    pager = hubs,
                    state = hubState,
                    seen = hubSeen,
                    seedChannel = hubChannel,
                    onExit = onExit,
                    onOpenHub = { hub = it },
                )
                1 -> TextArticleList(
                    pager = articles,
                    state = articlesState,
                    seen = articleSeen,
                    onBack = { hub = null },
                    onOpenBody = { entry ->
                        val ref = entry.textArticleRef() ?: return@TextArticleList
                        if (ref.fileUrl.endsWith("html", ignoreCase = true)) {
                            onOpenWeb(ref.fileUrl, ref.title)
                        } else {
                            article = ref
                        }
                    },
                )
                else -> TextArticleReader(
                    store = store,
                    ref = article ?: return@AnimatedContent,
                    onBack = { article = null },
                )
            }
        }
    }
}

/** 对齐 TextChannelListFragment：列表分页 key 用 vo.param。 */
internal fun RemoteEntry.textListKey(): String? =
    param?.takeIf { it.isNotBlank() } ?: channelParam

internal data class TextArticleRef(
    val id: Long,
    val title: String,
    val fileUrl: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextHubPicker(
    pager: FeedPager,
    state: LazyListState,
    seen: MutableSet<String>,
    seedChannel: String?,
    onExit: () -> Unit,
    onOpenHub: (String) -> Unit,
) {
    LaunchedEffect(state, pager) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { index -> if (index >= pager.items.size - 6) pager.loadMore() }
    }
    val rows = pager.items
    PullToRefreshBox(
        isRefreshing = pager.refreshing,
        onRefresh = pager::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize().safeDrawingPadding(),
            state = state,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 26.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    Text("文字馆", fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (pager.loading) "正在取数据…" else "${rows.size} 个频道",
                        color = Muted, fontSize = 12.sp,
                    )
                }
                Text("一个故事换一屏。", color = White, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black)
                FeedError(pager.error, pager::reload)
                if (seedChannel == null) {
                    Text("没有频道参数，返回换一张文字卡试试。", Modifier.padding(14.dp), color = Muted)
                } else if (pager.loading && rows.isEmpty()) {
                    ListSkeleton(Modifier.padding(top = 2.dp), rows = 3, rowHeight = 72.dp)
                }
            }
            items(rows.size) { index ->
                val entry = rows[index]
                val key = entry.textListKey()
                Row(
                    Modifier.fillMaxWidth()
                        .enterFeedItem(seen, entry.key, index)
                        .pressable(Tile) { if (key != null) onOpenHub(key) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.MenuBook, null, tint = Mint, modifier = Modifier.padding(horizontal = 12.dp))
                    Column(Modifier.weight(1f).padding(vertical = 14.dp)) {
                        Text(entry.title, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (entry.description.isNotBlank()) {
                            Text(entry.description, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item { FeedEndRow(pager.loading, pager.hasMore, pager::loadMore, pager.loading && rows.isEmpty()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextArticleList(
    pager: FeedPager,
    state: LazyListState,
    seen: MutableSet<String>,
    onBack: () -> Unit,
    onOpenBody: (RemoteEntry) -> Unit,
) {
    LaunchedEffect(state, pager) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { index -> if (index >= pager.items.size - 6) pager.loadMore() }
    }
    val rows = pager.items
    PullToRefreshBox(
        isRefreshing = pager.refreshing,
        onRefresh = pager::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), state = state,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp)) {
            item {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回频道") }
                Heading("目录", if (pager.loading) "正在取数据…" else "${rows.size} 篇")
                FeedError(pager.error, pager::reload)
                if (pager.loading && rows.isEmpty()) ListSkeleton(rowHeight = 56.dp)
            }
            if (!pager.loading && rows.isEmpty()) {
                item { Text("这个频道还没有文章。", Modifier.padding(14.dp), color = Muted) }
            }
            items(rows.size) { index ->
                val entry = rows[index]
                Row(
                    Modifier.fillMaxWidth()
                        .enterFeedItem(seen, entry.key, index)
                        .pressable(Tile) { onOpenBody(entry) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(14.dp)) {
                        Text(entry.title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(entry.actionUrl, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item { FeedEndRow(pager.loading, pager.hasMore, pager::loadMore) }
        }
    }
}

/**
 * 正文页。取数失败必须留下日志（id + 原因）再给文案：这一层把异常吞成一句话是排查
 * 「正文没加载出来」最大的黑洞——服务端到底回了什么，只有 logcat 知道。
 */
@Composable
private fun TextArticleReader(
    store: ScreeningFeedStore,
    ref: TextArticleRef,
    onBack: () -> Unit,
) {
    val retryScope = rememberCoroutineScope()
    var html by remember(ref.id) { mutableStateOf<String?>(null) }
    var err by remember(ref.id) { mutableStateOf<String?>(null) }
    var loading by remember(ref.id) { mutableStateOf(true) }
    suspend fun fetch() {
        loading = true
        err = null
        val result = runCatching { store.textArticleBody(ref.id) }
        loading = false
        val failure = result.exceptionOrNull()
        when {
            failure != null -> {
                Log.w(TAG_BODY, "正文取数失败 id=${ref.id} url=${ref.fileUrl}: ${failure.message}", failure)
                err = friendlyFeedError(failure)
            }
            result.getOrNull().isNullOrBlank() -> {
                Log.w(TAG_BODY, "正文取回为空 id=${ref.id}（服务端回了解析成功的空 file）")
                err = "这篇服务端没给正文，点重试再试一次"
            }
            else -> {
                html = result.getOrNull()
                Log.i(TAG_BODY, "正文取回 id=${ref.id} len=${html?.length}")
            }
        }
    }
    LaunchedEffect(ref.id) { fetch() }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回目录") }
            Text(ref.title, Modifier.weight(1f), color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            loading -> ListSkeleton(rows = 4, rowHeight = 120.dp)
            err != null -> FeedError(err, { retryScope.launch { fetch() } })
            html != null -> TextHtmlBody(html!!)
        }
    }
}

private const val TAG_BODY = "ScreeningTextBody"

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TextHtmlBody(html: String) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            WebView(context).apply {
                setBackgroundColor(0)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.domStorageEnabled = true
                settings.userAgentString = settings.userAgentString + " " + HttpUtils.getUserAgentSuffix(context)
                webViewClient = WebViewClient()
            }
        },
        update = { it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) },
    )
}
