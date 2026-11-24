package com.openapi.feature.video.screening

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    FeedListShell(
        pager = pager,
        state = state,
        title = "文字馆",
        onBack = onExit,
        detail = feedToolbarDetail(pager.loading, rows.size, "个频道"),
    ) {
        item {
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
    FeedListShell(
        pager = pager,
        state = state,
        title = "目录",
        onBack = onBack,
        detail = feedToolbarDetail(pager.loading, rows.size, "篇"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
    ) {
        item {
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

/**
 * 正文纸面色：旧文章页是浅色背景图（`boob_bg2`）+ 黑字，不是纯白——深色放映场里
 * 纯白会刺眼，这里取同一档柔和纸白。CSS 与 Compose 兜底共用这一个值，别漂移。
 */
private const val PAPER_RGB = 0xFFFFFDF8

/**
 * 正文 HTML 的纸面包装——用户点名「背景是黑的，文字也是黑的，全都看不见」。
 *
 * 根因：服务端 `item.json` 回的 `file` 是论坛帖子片段（Discuz `t_msgfontfix` 一类的 `<div>`），
 * 它没带任何颜色声明，天生按白底黑字写。旧文章页
 * （[com.openapi.feature.content.text.TextItemsFragment] + [com.openapi.common.ui.widget.MyWebView]）
 * 之所以读得出来：`layout_textitems.xml` 整页是浅色背景图（`boob_bg2`），WebView 透明、
 * `textColor=base_black`，黑字压在浅底上。放映场整屏深色（Ink），WebView 再透明就是黑字压黑底。
 *
 * 修法照旧页的观感来：正文区自带浅色纸面 + 显式字色（不再靠底下那层颜色碰巧是对的），
 * 顺带把旧 `loadHtmlBody` 的 viewport 与图片自适应一起补齐——帖子内容原样，不加工不删减。
 */
internal fun articleBodyHtml(body: String): String =
    "<html><head>" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1," +
        "minimum-scale=1,maximum-scale=1,user-scalable=no\" />" +
        "<style>" +
        "html,body{margin:0;padding:0;background:#FFFDF8;color:#1A1A1A;}" +
        "body{padding:16px 14px;word-wrap:break-word;word-break:break-word;" +
        "font-size:17px;line-height:1.7;}" +
        "img,video,iframe{max-width:100%;height:auto;}" +
        "a{color:#0B5CAB;}" +
        "table{max-width:100%;}" +
        "</style>" +
        "</head><body>" + body + "</body></html>"

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TextHtmlBody(html: String) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color(PAPER_RGB)),
        factory = {
            WebView(context).apply {
                // 纸面白而不是透明：深色屏幕上透明=黑底，而帖子正文没声明颜色（天生黑字）。
                setBackgroundColor(PAPER_RGB.toInt())
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.domStorageEnabled = true
                settings.userAgentString = settings.userAgentString + " " + HttpUtils.getUserAgentSuffix(context)
                webViewClient = WebViewClient()
            }
        },
        update = { it.loadDataWithBaseURL(null, articleBodyHtml(html), "text/html", "utf-8", null) },
    )
}
