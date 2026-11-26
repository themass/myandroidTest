package com.openapi.feature.video.screening

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.openapi.common.net.http.NetError
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 放映场所有翻页列表的共用外壳（用户点名 2026-11-24：对应旧页面的列表基类）。
 *
 * 结构：置顶工具条（返回箭头 + 标题 + 右侧计数）+ 可下拉刷新的列表 + 浮动回顶按钮。
 * 工具条在滚动容器**外面**，不是列表的第一行——翻很多页之后返回箭头仍在顶上可按
 * （旧写法把返回行放进 LazyColumn 首 item，滚远了就再也退不出去）。
 * 回顶按钮只在列表离开顶部后出现（[LazyListState.canScrollBackward]），点击平滑滚回第一行。
 *
 * 各列表层只提供标题、右侧计数、列表内容和各自的 contentPadding/间距——
 * 下拉刷新、回顶、安全区这些行为从此只有一份。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedListShell(
    pager: FeedPager,
    state: LazyListState,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 工具条右侧计数文案（[feedToolbarDetail]）。 */
    detail: String = "",
    /**
     * 列表页搜索（用户点名 2026-11-25：「图片，视频，文章，音频 列表页的搜索功能不见了」）。
     * 旧列表基类（BasePullLoadbleFragment）每个列表页工具条都挂 SearchView：提交存 keyword、
     * setRefresh(true) 重取首屏、翻页继续带词。这里把这条行为收进壳里，四个馆只差开关。
     */
    searchable: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(start = 18.dp, end = 18.dp, bottom = 26.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    listContent: LazyListScope.() -> Unit,
) {
    val scrollScope = rememberCoroutineScope()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    // 搜索框草稿：回车/提交才进 pager.search()（旧 SearchView 同款——onQueryTextSubmit 才刷，
    // 边打字边打接口是旧页面都没做过的行为）。back 退出时列表页销毁，草稿自然丢弃。
    var draft by remember { mutableStateOf(pager.keyword) }
    // 外层 Box：回顶按钮直接压在列表区之上（BottomEnd），跟 ScreeningGallery 的 lightbox
    // 同一形状。Column 套 Box 里再放 AnimatedVisibility 会撞 BoxScope/ColumnScope 的接收者解析。
    Box(modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).background(Ink).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text(title, Modifier.weight(1f), fontSize = ScreeningTypography.page, fontWeight = FontWeight.Black,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotEmpty()) Text(detail, color = Muted, fontSize = 12.sp)
                if (searchable) IconButton(onClick = { searchOpen = !searchOpen }) {
                    Icon(
                        if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                        if (searchOpen) "收起搜索" else "搜索",
                        tint = if (searchOpen || pager.keyword.isNotEmpty()) Mint else Muted,
                    )
                }
            }
            if (searchOpen || pager.keyword.isNotEmpty()) {
                SearchRow(
                    draft = draft,
                    onDraftChange = { draft = it },
                    activeKeyword = pager.keyword,
                    onSubmit = {
                        val q = it.trim()
                        if (q != pager.keyword) pager.search(q)
                        // 提交任何搜索都回顶（用户 2026-11-26：搜索结果从第一屏看起；
                        // 以前只有清空词才回顶，带词提交后列表停在上一次滚到的位置）。
                        scrollScope.launch { state.animateScrollToItem(0) }
                    },
                    onClear = {
                        draft = ""
                        if (pager.keyword.isNotEmpty()) pager.search("")
                    },
                )
            }
            // 一条比底稍亮的分界线：列表滚到工具条底下时能看清「被吸顶挡住」的边界。
            Box(Modifier.fillMaxWidth().height(1.dp).background(Lift))
            PullToRefreshBox(
                isRefreshing = pager.refreshing,
                onRefresh = pager::refresh,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = state,
                    contentPadding = contentPadding,
                    verticalArrangement = verticalArrangement,
                    content = listContent,
                )
            }
        }
        AnimatedVisibility(
            visible = state.canScrollBackward,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
        ) {
            SmallFloatingActionButton(
                onClick = { scrollScope.launch { state.animateScrollToItem(0) } },
                containerColor = Lift,
                contentColor = Mint,
            ) { Icon(Icons.Default.KeyboardArrowUp, "回到顶部") }
        }
    }
}

/**
 * 工具条下方的搜索行：输入 + 提交（键盘「搜索」键 / 回车）+ 清除。
 *
 * 行为对齐旧 SearchView：打字不打接口，提交才刷（`onQueryTextSubmit`）；
 * 搜索生效期间这行常驻（即使把输入面板收起来），并显示当前生效的词——
 * 用户必须能看出「现在屏上是搜索结果」而不是全量列表（旧页面缺这一点，
 * 搜完过一会儿就忘了自己在搜索态）。
 */
@Composable
private fun SearchRow(
    draft: String,
    onDraftChange: (String) -> Unit,
    activeKeyword: String,
    onSubmit: (String) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Ink).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("搜标题", color = Muted, fontSize = 13.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 14.sp),
            singleLine = true,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Mint, unfocusedBorderColor = Lift,
                cursorColor = Mint,
                focusedTextColor = White, unfocusedTextColor = White,
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { onSubmit(draft) },
            ),
        )
        if (activeKeyword.isNotEmpty()) {
            Text("「$activeKeyword」", color = Mint, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp))
        }
        TextButton(onClick = { onSubmit(draft) }) { Text("搜索", color = Mint, fontSize = 13.sp) }
        if (activeKeyword.isNotEmpty() || draft.isNotEmpty()) {
            TextButton(onClick = onClear) { Text("清除", color = Muted, fontSize = 13.sp) }
        }
    }
}

/**
 * 触底续页的判定（用户 2026-11-26 点名：所有子页「上滑加载更多」失效）。
 *
 * 旧写法八个调用点各写一遍 `index >= pager.items.size - k`——拿 LazyColumn 的
 * **item 下标**去比**数据条数**：网格页把 N 条 chunked 成 N/3 行、外面还有头卡
 * 和页脚 item，下标永远够不到条数，loadMore() 一次都不发。正确的信号只有一个：
 * 列表已经不能继续向前滚（到底）且服务端说还有下一页且不在取数中。
 * 收成一个纯函数，八个调用点统一走 feedShouldLoadMore(state, pager)。
 */
internal fun feedShouldLoadMore(
    lastVisibleIndex: Int,
    totalItemsCount: Int,
    canScrollForward: Boolean,
    hasMore: Boolean,
    loading: Boolean,
): Boolean =
    !canScrollForward && hasMore && !loading && lastVisibleIndex >= 0 && totalItemsCount > 0

/**
 * 触底续页的唯一触发器：把 (最后可见 item 下标, 总 item 数, 能否继续向前滚) 收成
 * 一个 Triple，变化时才判 [feedShouldLoadMore]（用户 2026-11-26：所有翻页列表共用）。
 * 注意判的是**列表还能不能滚**——不是数据条数算术，网格页 chunked 成行同样成立；
 * 重复页空转由 [FeedPager.loadMore] 的 append 闸挡住。
 */
@Composable
internal fun feedShouldLoadMore(state: LazyListState, pager: FeedPager) {
    LaunchedEffect(state, pager) {
        snapshotFlow {
            Triple(
                state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                state.layoutInfo.totalItemsCount,
                state.canScrollForward,
            )
        }.distinctUntilChanged().collect { (last, total, canForward) ->
            if (feedShouldLoadMore(last, total, canForward, pager.hasMore, pager.loading)) pager.loadMore()
        }
    }
}

/**
 * 空列表文案按搜索态分支（用户 2026-11-26：搜索 0 结果时空态还是列表口径，
 * 用户以为频道本身没内容）。未搜索保留各页自己的列表口径；搜索中必须说出
 * 「是没搜到」并给下一步——换词。四个可搜索列表共用，文案不再在页面各写各的。
 */
internal fun feedEmptyCopy(keyword: String, fallback: String): String =
    if (keyword.isBlank()) fallback else "没有找到「${keyword.trim()}」，换个词试试。"

/**
 * 图集册内取数的路由判定（用户 2026-11-26：走到尾续后新搜索被静默丢词——
 * fetch 闭包只看 tailItemUrl，置位后 keyword 再也进不了请求）。规则：只有
 * 「没有搜索词」时才走 item/page.json 尾续；一旦有词，走回 albumImages 带词取。
 */
internal fun albumImagesUseTail(tailItemUrl: String?, keyword: String): Boolean =
    tailItemUrl != null && keyword.isBlank()

/**
 * 一页可分页的服务端列表状态：首屏、翻页、失败重试。
 *
 * 频道列表、频道里的片单、图集列表、图集内的图片都用这一个形状，页面只认它。
 * 这里没有「随包样例」这条路：服务端没通就说没通（[error]，页面给重试），
 * 通了但没数据就是空（items 为空）。页面绝不拿假数据填屏幕。
 *
 * 首屏失败先自动重试一次（短退避），还不行才落到 [error] —— 一次网络抖动
 * 不该逼用户去找重试按钮；只重试一次，不会无限打服务端。
 */
internal class FeedPager(
    private val scope: CoroutineScope,
    /**
     * 取一页（起始行 + 当前搜索词）；返回的 [ScreeningFeedStore.Page.nextStart] 决定还能不能继续翻。
     * keyword 由 [search] 维护、每次取数都带上——对齐旧列表基类（`LoadableFragment.keyword`：
     * 提交后存下来，翻页取数继续用它，直到清空）。
     */
    private val fetch: suspend (Int, String) -> ScreeningFeedStore.Page<RemoteEntry>,
) {
    val items = mutableStateListOf<RemoteEntry>()
    var loading by mutableStateOf(false)
        private set
    /** 下拉刷新在路上（[refresh] 置真、取回即假）。[loading] 还盖着翻页，刷新指示只认这个。 */
    var refreshing by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    /** 上次取数失败的原因；null = 没失败过。页面据此显示错误 + 重试，而不是假数据。 */
    var error by mutableStateOf<String?>(null)
        private set
    /** 当前搜索词；空串 = 未搜索。列表页的搜索框/关键词提示都读它。 */
    var keyword by mutableStateOf("")
        private set

    private var nextStart: Int? = 0
    private var requested = false
    /**
     * 防空转：上一次 append 取回后 [items] 条数没涨（服务端回重复页等），
     * 「到底」判定会原地反复重发同一枪——标记置位后 [loadMore] 直接返回，
     * 直到 [load]/[reload]/[search] 重新起头才清。
     */
    private var appendStalled = false

    /** 首屏。已经取过就不重复取，重组不该反复打接口。 */
    fun load() {
        if (requested) return
        requested = true
        appendStalled = false
        request(nextStart ?: 0, append = false)
    }

    /** 重新从第一页取；重试按钮走这里。 */
    fun reload() {
        nextStart = 0
        requested = true
        appendStalled = false
        request(0, append = false)
    }

    /**
     * 搜索：记住关键词、立刻从第一页重取（旧列表基类 `onQueryTextSubmit` 就是这个行为——
     * 存 keyword、`setRefresh(true)` 刷回首屏；清空后翻页取数也仍带着该词直到再次清空）。
     * 传空串 = 取消搜索，回到普通列表。搜索词不 URL 编码——和旧页面同一拼法，
     * 服务端（含中文参数）本来就按原样收。
     */
    fun search(newKeyword: String) {
        keyword = newKeyword.trim()
        reload()
    }
    /** 翻下一页；列表滚到底时调用（判定见 [feedShouldLoadMore]）。 */
    fun loadMore() {
        val start = nextStart ?: return
        if (loading) return
        // 首屏失败清场后列表是空的：这时「触底」不代表还有下一页，别再替首屏重复打接口。
        // 少了这道闸，失败 → 空列表 → 触底判定恒真 → 再取 → loading 转圈改变布局 → 又触发判定，
        // 会自己转起来无限拉。首屏只归 load()/reload() 管。
        if (items.isEmpty() && start == 0) return
        // 上一枪 append 取回后条数没涨（服务端回重复页）：同一位置重发没有意义，
        // 用户 2026-11-26 点名「加载更多」族问题的防空转闸；重新起头（load/reload/search）才放开。
        if (appendStalled) return
        request(start, append = true)
    }

    /**
     * 下拉刷新：重取第一页，但不先清屏。
     *
     * 用户定的规矩：数据只在他自己下拉时动。刷新路上旧列表原样留在屏上，
     * 新数据到了才换；失败也保留旧列表、只报错误——用户自己拉的就自动补一次不补，
     * 那和 [reload]（失败界面上的重试）是两回事。
     */
    fun refresh() {
        if (loading) return
        requested = true
        refreshing = true
        request(0, append = false, retryOnFail = false, keepOnFail = true)
    }

    private fun request(
        start: Int,
        append: Boolean,
        /** 首屏失败补一枪；翻页、下拉刷新不补（屏上有内容，失败说清楚就够）。 */
        retryOnFail: Boolean = !append,
        /** 失败时是否保留屏上已有列表（首屏清场，翻页/刷新保留）。 */
        keepOnFail: Boolean = append,
    ) {
        loading = true
        // 取数那一刻的关键词快照：搜索框在请求路上又改了词，这一枪仍按发起时的词回，
        // 回词后的那一枪由 search() 自己发——不这样，路上两枪会拿不同关键词往同一个列表里拼。
        val at = keyword
        scope.launch(Dispatchers.Main) {
            // 目视验证钩子：`adb shell setprop log.tag.FeedPagerDbg DEBUG` 后每次取数慢 5 秒，
            // 让加载骨架/扫光动画停在屏上可截图。默认关闭，对正常构建零影响。
            if (Log.isLoggable(TAG_DBG, Log.DEBUG)) delay(SLOW_FETCH_MS)
            val page = runCatching { fetch(start, at) }.recoverCatching { cause ->
                // 首屏失败自动补一次（短退避）：一次网络抖动不该逼用户去找「重试」按钮；
                // 只补这一次，再失败就落到 error，不会无限打服务端。
                // 翻页失败不补——屏上已有列表，保留它、说清失败就够了。
                if (!retryOnFail) throw cause
                Log.i(TAG, "首屏取数失败，${RETRY_BACKOFF_MS}ms 后自动重试: ${cause.message}")
                delay(RETRY_BACKOFF_MS)
                fetch(start, at)
            }
            loading = false
            refreshing = false
            page.fold(
                onSuccess = { data ->
                    val before = items.size
                    if (!append) items.clear()
                    items.addAll(data.items)
                    // append 取回后条数没涨＝服务端回的是重复页：封住 loadMore，
                    // 别再对同一位置空转（见 [appendStalled]）。真涨了就照常放行。
                    appendStalled = append && items.size == before
                    nextStart = data.nextStart
                    hasMore = data.hasMore
                    error = null
                },
                onFailure = { cause ->
                    // 首屏失败清干净，别让半截列表冒充数据；翻页失败保留已加载的部分。
                    // nextStart 保持 0：空列表时触底判定恒真会反复调 loadMore()，
                    // 由 loadMore 的「空列表且 start==0 直接返回」挡住，首屏只归 load()/reload() 管。
                    if (!append && !keepOnFail) { items.clear(); nextStart = 0; hasMore = false }
                    Log.w(TAG, "取数最终失败(append=$append): ${cause.message}")
                    error = friendlyFeedError(cause)
                },
            )
        }
    }

    private companion object {
        const val TAG = "FeedPager"
        const val TAG_DBG = "FeedPagerDbg"
        const val SLOW_FETCH_MS = 5_000L
        const val RETRY_BACKOFF_MS = 800L
    }
}

/**
 * 工具条右侧计数文案：取数中显示「正在取数据…」，取回后显示 `N 个词`。
 * 各列表层（频道/篇/首/本/张/条）只差量词，行为一致——放纯函数里钉住，别在八处各写各的。
 */
internal fun feedToolbarDetail(loading: Boolean, count: Int, unit: String): String =
    if (loading) "正在取数据…" else "$count $unit"

/**
 * 取数失败的文案：只说「发生了什么、能做什么」，不印接口细节。
 *
 * 两代教训：① 拼异常 toString 让用户看到 `NetError$BusinessError: System error…`；
 * ② 改成「服务端返回错误（errno 10000）：…」后用户点名叫这是把接口和错误直接
 * 打印到端上——errno、HTTP 码、服务端英文原文对用户的行动没有任何价值。
 * 界面上只留人话 + 一个重试；errno 和原始报文进 logcat（FeedPager.onFailure 已 Log.w），
 * 排查走日志，不拿用户当调试面板。
 *
 * 注意网络层的形状：`awaitInfoList`/`awaitGet` 为了兼容旧同步调用，把 NetError 又包了一层
 * `ExecutionException`（BaseServiceAwait.kt）。不先剥掉这层，下面的 when 永远落 else。
 */
internal fun friendlyFeedError(cause: Throwable): String {
    var c = cause
    while ((c is java.util.concurrent.ExecutionException || c is java.util.concurrent.CancellationException) &&
        c.cause != null && c.cause !== c
    ) {
        c = c.cause!!
    }
    return when (c) {
        is NetError.BusinessError -> "服务端出了点小问题，点重试再试一次"
        is NetError.ServerError -> "服务端没接住这个请求，稍后再试"
        is NetError.TimeoutError -> "等了很久，服务端没回应，点重试再试一次"
        is NetError.NetworkError -> "没连上网，检查一下网络再重试"
        else -> "内容没加载出来，点重试再试一次"
    }
}

/**
 * 加载中的骨架占位：一块带缓慢扫光的圆角面。
 *
 * 比转圈更安静——转圈是把焦虑画在屏幕正中间，扫光说的是「位置已经给你留好了，
 * 内容马上来」。颜色只在 Lift 底上提一档亮，尊重 prefers-reduced-motion 之外
 * 也天然低干扰；数据到位直接换内容，骨架本身不制造第二次跳动。
 */
@Composable
internal fun ShimmerPlaceholder(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = Tile) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    // -0.4..1.4：光带从框左缘外扫到右缘外，往返 2.4s 一次，慢到不抢注意力
    val slide by transition.animateFloat(
        initialValue = -0.4f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "slide",
    )
    val base = Color(0xFF2B3050)
    val sheen = Color(0xFF3A4168)
    BoxWithConstraints(modifier.clip(shape)) {
        // 渐变坐标是像素：按比例把扫光起点/终点摊到实际宽高上
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        Box(Modifier.matchParentSize().background(
            Brush.linearGradient(
                listOf(base, sheen, base),
                start = Offset((slide - 0.35f) * wPx, 0f), end = Offset((slide + 0.35f) * wPx, hPx),
            ),
        ))
    }
}

/**
 * 全 App 统一的图片占位/失败默认图，资源在 `:core`（`core/src/main/res/drawable-xxhdpi/img_bg.png`）。
 * 引用方式跟模块内既有写法一致（`feature-content` 里跨模块取 core 资源也是 `com.openapi.core.R.…`）。
 */
internal val ImagePlaceholderRes = com.openapi.core.R.drawable.img_bg

/**
 * 一张封面的加载状态：加载中 → 成功 / 失败。
 *
 * 收成纯类是因为「扫光什么时候收」是这条链上唯一会骗用户的地方：
 * 收早了露出空白，收晚了失败也一直被读成「还在加载」。Glide 的
 * [com.bumptech.glide.request.RequestListener] 直接喂 [onStart]/[onReady]/[onFailed]，
 * 状态本身不需要真机就能验。字段是快照状态：Glide 的回调在 UI 线程改它，
 * 必须有快照才能把扫光收掉（纯 `var` 改完屏幕不会重绘）。
 */
internal class CoverLoadState {
    var showShimmer by androidx.compose.runtime.mutableStateOf(true)
        private set
    var failed by androidx.compose.runtime.mutableStateOf(false)
        private set

    /** 新请求开始（首次进入或 view 复用重试）。 */
    fun onStart() {
        showShimmer = true
        failed = false
    }

    fun onReady() {
        showShimmer = false
        failed = false
    }

    /** 取不到图：不再扫光，露出静态 [ImagePlaceholderRes]——这才是「真没图」。 */
    fun onFailed() {
        showShimmer = false
        failed = true
    }
}

/**
 * 服务端封面：Glide 加载远程图，加载中盖扫光，失败/没有地址落到 `img_bg`，不留空洞。
 *
 * 用 [AndroidView] 包 Glide，而不是另找一个 Compose 图片库：项目里远程图一律走
 * `Glide.with(view).load(url)`（见 `ChannelCardLoaders`），这样共用同一份磁盘缓存和
 * 已配好代理/超时的 OkHttp loader，不用再配一套。
 *
 * 【A40】加载中必须有等待信号。以前只挂 `.placeholder(img_bg)`：图慢的时候是一块
 * 静底，用户读成「这块位置本来就没图」，跟「服务端就是没图」再也分不出来
 * （台账 A28 是另一半：少了 error 底）。现在由 RequestListener 把状态交给
 * [CoverLoadState]，加载中在图上叠 [ShimmerPlaceholder]（全站唯一的等待语言），
 * 落地/失败都把扫光收掉，失败留 img_bg 静态底。
 *
 * 【规矩·A28】`img_bg`（`:core` 的 `drawable-xxhdpi/img_bg.png`）是全 App 统一的
 * 图片占位图兼加载失败默认图——旧代码每条 Glide 链都挂它
 * （`core` 的 `ImagePhotoLoad.options/optionsHigh`、`feature-content` 的
 * `FullScreenImagePagerAdapter`）。新代码接远程图必须同时挂
 * `.placeholder(ImagePlaceholderRes)` 和 `.error(ImagePlaceholderRes)`；
 * 这条以前在 Compose 迁移时漏掉了（台账 A28），现在由 [ImagePlaceholderRes] 一处收口。
 */
@Composable
internal fun RemoteCover(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (url.isNullOrBlank()) {
        // 没有地址：同样画 img_bg，和「有地址但还没加载出来」保持同一种底。
        // 注意别退回纯色 Box——那正是 A28 的现场：占位图整个不见了。
        Image(
            painter = painterResource(ImagePlaceholderRes),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
        return
    }
    val state = remember(url) { CoverLoadState() }
    Box(modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = {
                ShapeableImageView(context).apply {
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    // 短交叉淡入而不是硬切：远程图落地时不闪白块
                    Glide.with(this).load(url).centerCrop()
                        .placeholder(ImagePlaceholderRes).error(ImagePlaceholderRes)
                        .transition(DrawableTransitionOptions.withCrossFade(180))
                        .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                            override fun onLoadFailed(
                                e: com.bumptech.glide.load.engine.GlideException?,
                                model: Any?,
                                target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                                isFirstResource: Boolean,
                            ): Boolean {
                                state.onFailed()
                                // 不拦截：Glide 自己的 .error(img_bg) 继续落静态底。
                                return false
                            }

                            override fun onResourceReady(
                                resource: android.graphics.drawable.Drawable,
                                model: Any,
                                target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                                dataSource: com.bumptech.glide.load.DataSource,
                                isFirstResource: Boolean,
                            ): Boolean {
                                state.onReady()
                                return false
                            }
                        })
                        .into(this)
                }
            },
            // 列表复用/离开组合：clear 掉请求，防止已废弃的 state 被后续回调命中
            // （扫光收了又复现、或失败态盖住新图）。与 A28 的泄漏钉子同源。
            onRelease = { view -> runCatching { Glide.with(view).clear(view) } },
        )
        if (state.showShimmer) {
            ShimmerPlaceholder(Modifier.matchParentSize())
        }
    }
}

/**
 * 列表加载骨架：几条扫光占位行，替代独立转圈。
 *
 * 用户反馈「2 个加载 loading，只要一个」：首屏转圈和列表骨架同屏各转各的，
 * 屏幕上出现两个互相不认识的等待信号。全站加载只留扫光这一种语言
 * （和 [ShimmerPlaceholder] 同一块光），行高比例接近真实条目，数据落地不跳版。
 */
@Composable
internal fun ListSkeleton(modifier: Modifier = Modifier, rows: Int = 3, rowHeight: androidx.compose.ui.unit.Dp = 84.dp) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(rows) {
            ShimmerPlaceholder(Modifier.fillMaxWidth().height(rowHeight))
        }
    }
}

/** 取数失败的横条：说清没取到，并给一个重试。绝不拿样例填这块位置。 */
@Composable
internal fun FeedError(error: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (error == null) return
    Box(modifier.fillMaxWidth().background(Lift).padding(horizontal = 18.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(error, color = Peach, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

/** 列表底部：到底提示；翻页中的转圈只在屏上已有内容时出现。 */
/**
 * 页脚该不该画（纯函数，台账 A29/抽象点 18）：
 * ① 首屏还在取数且列表空着——等待由扫光骨架统一表达，页脚不转圈；
 * ② 取完了但列表是空的——没有下一页可翻，「继续下滑加载更多」「到底了」都是没用的文字。
 */
internal fun feedFooterShown(loading: Boolean, firstPageLoading: Boolean, empty: Boolean): Boolean {
    if (loading && firstPageLoading) return false
    if (empty && !loading) return false
    return true
}

@Composable
internal fun FeedEndRow(
    loading: Boolean, hasMore: Boolean, onLoadMore: () -> Unit,
    /** 首屏加载中（列表还空着）：底部不再转圈，等待信号由列表位的扫光骨架统一表达。 */
    firstPageLoading: Boolean = false,
    /**
     * 列表当前是空的。空列表没有「下一页」可翻，页脚一行（「继续下滑加载更多」
     * 「到底了」）就成了没用的文字——空态文案由列表层自己给，页脚整个不画
     * （台账 A29，抽象点 18：空数据守卫统一收在这一层，不在八个调用点各写各的）。
     */
    empty: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (!feedFooterShown(loading, firstPageLoading, empty)) return
    Box(modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            hasMore -> Text("继续下滑加载更多", color = Muted, fontSize = 12.sp)
            else -> Text("到底了", color = Muted, fontSize = 12.sp)
        }
    }
}
