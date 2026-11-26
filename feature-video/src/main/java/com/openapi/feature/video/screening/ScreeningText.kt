package com.openapi.feature.video.screening

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
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
        FeedPager(scope) { start, _ -> store.textChannelHub(hubChannel ?: "", start) }
    }
    val hubParam = hub
    val articles = remember(hubParam) {
        FeedPager(scope) { start, keyword -> store.textArticles(hubParam ?: "", start, keyword) }
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
            transitionSpec = {
                if (initialState == 2) {
                    // 从阅览层退回（A40「快速进出闪退」）：AnimatedContent 的退场内容要演完
                    // 动画才走 onRelease——WebView 的 destroy 跟着拖到动效结束。连点进出时
                    // 两三个 Chromium 渲染核叠着解码大图，就是 OOM 现场。退回时旧阅览层
                    // 零时长退场（当场 release/destroy），入场照常滑入。
                    screenSlideNoExit(motion, slidePx, targetState > initialState)
                } else screenSlide(motion, slidePx, targetState > initialState)
            },
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
    feedShouldLoadMore(state, pager)
    val rows = pager.items
    FeedListShell(
        pager = pager,
        state = state,
        title = "文字馆",
        onBack = onExit,
        detail = feedToolbarDetail(pager.loading, rows.size, "个频道"),
    ) {
        item {
            // 同声音馆：单句限一行，不硬折行（台账 A29）。
            Text("一屏一故事，换一盏灯。", color = White, fontSize = ScreeningTypography.hero, lineHeight = ScreeningTypography.heroLineHeight,
                fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
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
                    Text(entry.title, color = White, fontSize = ScreeningTypography.item, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (entry.description.isNotBlank()) {
                        Text(entry.description, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item { FeedEndRow(pager.loading, pager.hasMore, pager::loadMore,
            pager.loading && rows.isEmpty(), empty = rows.isEmpty()) }
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
    feedShouldLoadMore(state, pager)
    val rows = pager.items
    FeedListShell(
        pager = pager,
        state = state,
        title = "目录",
        onBack = onBack,
        detail = feedToolbarDetail(pager.loading, rows.size, "篇"),
        // 搜索（用户点名）：对齐旧 TextChannelListFragment——keyword 直传，服务端 text mapper 按 name like。
        searchable = true,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
    ) {
        item {
            FeedError(pager.error, pager::reload)
            if (pager.loading && rows.isEmpty()) ListSkeleton(rowHeight = 56.dp)
        }
        if (!pager.loading && rows.isEmpty()) {
            // 空态按搜索态分支（用户 2026-11-26：搜 0 结果仍印列表口径，看不出是「没搜到」）。
            item { Text(feedEmptyCopy(pager.keyword, "这个频道还没有文章。"), Modifier.padding(14.dp), color = Muted) }
        }
        items(rows.size) { index ->
            val entry = rows[index]
            Column {
                Row(
                    Modifier.fillMaxWidth()
                        .enterFeedItem(seen, entry.key, index)
                        .pressable(Tile) { onOpenBody(entry) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(14.dp)) {
                        Text(entry.title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        // 上线口径不展示裸 fileUrl（旧开发期留下的调试行）：有日期就标日期。
                        if (entry.description.isNotBlank()) {
                            Text(entry.description, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                // AD3：这条就是旧 TextChannelListItemsViewAdapter.java:77-91 的 rv_ads 槽，
                // 位置表/类别不变，宿主换成 Compose（台账 §4.3）。
                FeedRowAdSlot(index)
            }
        }
        item { FeedEndRow(pager.loading, pager.hasMore, pager::loadMore, empty = rows.isEmpty()) }
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
            html != null -> TextHtmlBody(html!!, onSwipeBack = onBack)
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
        "video,iframe{max-width:100%;height:auto;}" +
        // 长截图/连图不设上限会把整页撑成巨幅滚动面（大图卡顿、解码内存峰值的根源之一），
        // 70vh 封顶后 Chromium 按 CSS 替换元素规则等比缩，不变形。
        "img{max-width:100%;max-height:70vh;height:auto;}" +
        "a{color:#0B5CAB;}" +
        "table{max-width:100%;}" +
        "</style>" +
        "</head><body>" + lazyLoadImages(normalizeBodyImages(body)) + "</body></html>"

/**
 * 正文图片地址归一化（A40「文章页图片经常没有」用户 2026-11-26，两条根因都在 HTML 里）：
 *
 * ① **懒加载**：Discuz 帖子的图把真地址藏在 `data-src`，`src` 是占位
 *   （`static/image/common/blank.gif` 一类）。旧文章页靠论坛自带的懒加载 JS 把
 *   data-src 换进 src；1312b454 把 JS 一刀切关掉后，图片就只剩占位图。
 *   这里端上先把 data-src 顶替成 src——不再依赖页面脚本才显形。
 * ② **明文**：帖子存的绝对地址是 `http://`，而 network_security_config 全局禁明文，
 *   Chromium 发起请求前就被系统掐掉（logcat 里连失败响应都没有）。非白名单域一律
 *   升级 https；白名单只有 `www.ok123find.top`（还没配 https，配置文件里单独放行明文），
 *   对它的地址升级反而会握手失败——所以那一条必须不动。
 *
 * 只动 `<img>` 标签：正文文字与普通链接里的 URL 原样保留。
 */
internal fun normalizeBodyImages(body: String): String =
    IMG_TAG.replace(body) { match ->
        var tag = match.value
        // ① data-src 顶替 src（没有 src 属性时补一个）。
        val real = DATA_SRC_ATTR.find(tag)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        if (real != null) {
            tag = if (SRC_ATTR.containsMatchIn(tag)) {
                tag.replace(SRC_ATTR) { """src="$real"""" }
            } else {
                IMG_OPEN.find(tag)?.let { m ->
                    tag.substring(0, m.range.last + 1) + """ src="$real"""" + tag.substring(m.range.last + 1)
                } ?: tag
            }
        }
        // ② 值以 http:// 开头的图片地址升级 https（白名单域除外）。
        HTTP_VALUE.replace(tag) { m ->
            val url = m.groupValues[1]
            if (cleartextAllowedHost(url)) m.value else "\"https://" + url.substring("http://".length) + "\""
        }
    }

/** 放行明文的域：与 `app/src/main/res/xml/network_security_config.xml` 的 domain-config 逐字对齐（includeSubdomains=false，所以精确匹配）。 */
private const val CLEARTTEXT_ALLOWED_HOST = "www.ok123find.top"

private fun cleartextAllowedHost(url: String): Boolean =
    url.substringAfter("://", "").substringBefore('/', "") == CLEARTTEXT_ALLOWED_HOST

/**
 * 正文的 baseUrl：帖子 HTML 里的相对地址（`/data/attachment/…`、`static/…`）没有 base
 * 就解析不出来——`loadDataWithBaseURL(null, …)` 等于告诉 Chromium「这页不属于任何站点」，
 * 相对图片请求直接被丢弃（A40 图片缺失的另一条根因；旧页面走真实 URL 加载才没这问题）。
 * 论坛站点根就是这些相对路径的家；该域在网络安全配置里单独放行明文（只有 http）。
 */
internal val TEXT_BODY_BASE_URL = com.openapi.core.config.CommonConstants.MAIN_URL

private val IMG_TAG = Regex("(?i)<img\\b[^>]*>")
private val IMG_OPEN = Regex("(?i)<img\\b")
private val LOADING_ATTR = Regex("(?i)\\bloading\\s*=")

/** 懒加载真址：`data-src="…"`。 */
private val DATA_SRC_ATTR = Regex("""(?i)\bdata-src\s*=\s*"([^"]*)"""")

/** 真正的 src——lookbehind 排除 `data-src`（`-` 也算词边界，不加就会误伤）。 */
private val SRC_ATTR = Regex("""(?i)(?<!data-)\bsrc\s*=\s*"[^"]*"""")

/** 属性值里的明文 URL。 */
private val HTTP_VALUE = Regex(""""(http://[^"]*)"""")

/**
 * 连图懒加载：帖子正文里的 `<img>` 一律补 `loading="lazy"`（WebView 即 Chromium，
 * 认这个属性），已有 `loading=` 的标签原样——「大量图片」的页面里，屏外图不再
 * 一次性下载解码，首屏和滚动都只处理视口附近的图。
 */
internal fun lazyLoadImages(body: String): String =
    IMG_TAG.replace(body) { match ->
        val tag = match.value
        if (LOADING_ATTR.containsMatchIn(tag)) tag
        else IMG_OPEN.find(tag)?.let { m ->
            // 在 `<img` 之后立刻插入属性，其余原样
            tag.substring(0, m.range.last + 1) + " loading=\"lazy\"" + tag.substring(m.range.last + 1)
        } ?: tag
    }

@Composable
private fun TextHtmlBody(html: String, onSwipeBack: () -> Unit) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    // 右滑关闭的阈值从 dp 换算成 px，判定本身是纯函数（articleSwipeCloses）。
    val minDistancePx = with(density) { ARTICLE_SWIPE_MIN_DP.dp.toPx() }
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(PAPER_RGB))
            .pointerInput(html) {
                detectSwipeBack(minDistancePx, minVelocityPx = MIN_FLING_PX_PER_S, onSwipe = onSwipeBack)
            },
        factory = {
            WebView(context).apply {
                // 纸面白而不是透明：深色屏幕上透明=黑底，而帖子正文没声明颜色（天生黑字）。
                setBackgroundColor(PAPER_RGB.toInt())
                // JS 开回来（A40「文章页图片经常没有」，修 1312b454 的一刀切）：
                // 1312b454 为去「论坛脚本噪音」把 JS 整个关了，顺带把 Discuz 懒加载脚本也关了——
                // 帖子图的真地址在 data-src，靠页面脚本换进 src，关掉后满页只剩 blank 占位图。
                // 噪音的另一半（相对地址、明文 http）已在 normalizeBodyImages/TEXT_BODY_BASE_URL
                // 端上修掉，脚本报错少了；再出声就按 console 过滤，而不是把显示功能陪葬。
                @SuppressLint("SetJavaScriptEnabled")
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                // DOM storage 开着：论坛懒加载/表情脚本会用到；正文 WebView 不进历史、
                // 不共享给别的页面，风险面仅限这一个数据目录。
                settings.domStorageEnabled = true
                // 大量图片的正文页：保留 Chromium 默认磁盘缓存（不设 LOAD_NO_CACHE），
                // 重复进同一篇不再重新下载图片。
                settings.userAgentString = settings.userAgentString + " " + HttpUtils.getUserAgentSuffix(context)
                // 正文里的链接点击一律吞掉：帖子链接指向论坛/外链，喂给外部浏览器既跳走
                // 又可能带上本机标识；锚点跳转同样不影响（页面内容是自包含的）。
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                }
            }
        },
        update = { view ->
            // base 传站点根（A40）：null base 下帖子相对地址（/data/attachment/…、static/…）
            // 解析不出来，图片请求直接被 Chromium 丢；论坛根就是这些路径的家。
            view.loadDataWithBaseURL(TEXT_BODY_BASE_URL, articleBodyHtml(html), "text/html", "utf-8", null)
        },
        // 离页时把 WebView 从窗口解绑再销毁：只靠 GC 收 WebView 会在「大量文字+图片」
        // 的页面上留驻渲染内存（反复进出文章页后崩溃的正路之一）。
        onRelease = { view ->
            view.stopLoading()
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            view.destroy()
        },
    )
}

/** 右滑关闭文章页的滑动阈值：横向位移至少这么多 dp。 */
internal const val ARTICLE_SWIPE_MIN_DP = 60f

/** 最低横向速度（px/s）：慢蹭的横移多半是误触，不给关页。 */
private const val MIN_FLING_PX_PER_S = 600f

/**
 * 右滑是否应关闭文章页（纯函数，ScreeningTextBodyTest 钉桩）：
 * 终点相对起点横移超过 [minDistancePx] 且平均速度超过 [minVelocityPx] 才算。
 * 用户口径：「只有文章页右滑是关闭文章页」——整页右滑都算，不限左缘起手。
 */
internal fun articleSwipeCloses(
    dx: Float,
    velocityPxPerS: Float,
    minDistancePx: Float,
    minVelocityPx: Float,
): Boolean = dx >= minDistancePx && velocityPxPerS >= minVelocityPx

/**
 * 手势侦测（挂在 pointerInput 的 PointerInputScope 上）：把 pointer 事件里的
 * 横移/速度喂给 [articleSwipeCloses]。WebView 自己的纵向滚动不受影响——
 * 只在「明显横向」时判定关闭一次。用 Initial pass 观察即可：
 * 不与 WebView 子 view 争消费权，判定只看不吃事件。
 */
private suspend fun PointerInputScope.detectSwipeBack(
    minDistancePx: Float,
    minVelocityPx: Float,
    onSwipe: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
        var downX = down.position.x
        var downTime = System.nanoTime()
        var fired = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.none { it.pressed }) break
            if (fired) continue
            val change = event.changes.firstOrNull() ?: break
            val dx = change.position.x - downX
            val dtSeconds = (System.nanoTime() - downTime) / 1_000_000_000f
            val velocity = if (dtSeconds > 0f) dx / dtSeconds else 0f
            // 只有明显横向（位移+速度都达标）才判关闭；纯竖滑 dx 不够，永远不会触发。
            if (articleSwipeCloses(dx, velocity, minDistancePx, minVelocityPx)) {
                fired = true
                onSwipe()
            }
        }
    }
}
