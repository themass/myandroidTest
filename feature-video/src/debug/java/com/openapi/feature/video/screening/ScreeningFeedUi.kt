package com.openapi.feature.video.screening

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch

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
    /** 取一页；返回的 [ScreeningFeedStore.Page.nextStart] 决定还能不能继续翻。 */
    private val fetch: suspend (Int) -> ScreeningFeedStore.Page<RemoteEntry>,
) {
    val items = mutableStateListOf<RemoteEntry>()
    var loading by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    /** 上次取数失败的原因；null = 没失败过。页面据此显示错误 + 重试，而不是假数据。 */
    var error by mutableStateOf<String?>(null)
        private set

    private var nextStart: Int? = 0
    private var requested = false

    /** 首屏。已经取过就不重复取，重组不该反复打接口。 */
    fun load() {
        if (requested) return
        requested = true
        request(nextStart ?: 0, append = false)
    }

    /** 重新从第一页取；重试按钮走这里。 */
    fun reload() {
        nextStart = 0
        requested = true
        request(0, append = false)
    }

    /** 翻下一页；滚到底时调用。 */
    fun loadMore() {
        val start = nextStart ?: return
        if (loading) return
        // 首屏失败清场后列表是空的：这时「触底」不代表还有下一页，别再替首屏重复打接口。
        // 少了这道闸，失败 → 空列表 → 触底判定恒真 → 再取 → loading 转圈改变布局 → 又触发判定，
        // 会自己转起来无限拉。首屏只归 load()/reload() 管。
        if (items.isEmpty() && start == 0) return
        request(start, append = true)
    }

    private fun request(start: Int, append: Boolean) {
        loading = true
        scope.launch(Dispatchers.Main) {
            // 目视验证钩子：`adb shell setprop log.tag.FeedPagerDbg DEBUG` 后每次取数慢 5 秒，
            // 让加载骨架/扫光动画停在屏上可截图。默认关闭，对正常构建零影响。
            if (Log.isLoggable(TAG_DBG, Log.DEBUG)) delay(SLOW_FETCH_MS)
            val page = runCatching { fetch(start) }.recoverCatching { cause ->
                // 首屏失败自动补一次（短退避）：一次网络抖动不该逼用户去找「重试」按钮；
                // 只补这一次，再失败就落到 error，不会无限打服务端。
                // 翻页失败不补——屏上已有列表，保留它、说清失败就够了。
                if (append) throw cause
                Log.i(TAG, "首屏取数失败，${RETRY_BACKOFF_MS}ms 后自动重试: ${cause.message}")
                delay(RETRY_BACKOFF_MS)
                fetch(start)
            }
            loading = false
            page.fold(
                onSuccess = { data ->
                    if (!append) items.clear()
                    items.addAll(data.items)
                    nextStart = data.nextStart
                    hasMore = data.hasMore
                    error = null
                },
                onFailure = { cause ->
                    // 首屏失败清干净，别让半截列表冒充数据；翻页失败保留已加载的部分
                    if (!append) { items.clear(); nextStart = 0; hasMore = false }
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
 * 取数失败的文案：先分「是我们没连上」还是「服务端自己错了」，再给人话。
 *
 * 原来一律拼「没取到服务端数据：<异常 toString>」，把服务端 500 也报成看着像网络问题，
 * 用户就会去查明明正常的网络（真机就撞上了）。错误类型是 NetError 的四个子类，分支直白。
 *
 * 注意网络层的形状：`awaitInfoList`/`awaitGet` 为了兼容旧同步调用，把 NetError 又包了一层
 * `ExecutionException`（BaseServiceAwait.kt）。不先剥掉这层，下面的 when 永远落 else，
 * 新文案形同虚设（真机截图里旧文案复活就是这个原因）。
 */
internal fun friendlyFeedError(cause: Throwable): String {
    var c = cause
    while ((c is java.util.concurrent.ExecutionException || c is java.util.concurrent.CancellationException) &&
        c.cause != null && c.cause !== c
    ) {
        c = c.cause!!
    }
    return when (c) {
        is NetError.BusinessError ->
            if (c.errno > 0) "服务端返回错误（errno ${c.errno}）：${c.msg}"
            else "服务端返回错误：${c.msg}"
        is NetError.ServerError -> "服务端没接住这个请求（HTTP ${c.statusCode}）"
        is NetError.TimeoutError -> "等服务端回应超时，网络可能很慢"
        is NetError.NetworkError -> "没连上服务端，检查下网络再重试"
        else -> "没取到服务端数据：${c.message ?: "未知原因"}"
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
 * 服务端封面：Glide 加载远程图，没有地址时退回纯色底，不留空洞。
 *
 * 用 [AndroidView] 包 Glide，而不是另找一个 Compose 图片库：项目里远程图一律走
 * `Glide.with(view).load(url)`（见 `ChannelCardLoaders`），这样共用同一份磁盘缓存和
 * 已配好代理/超时的 OkHttp loader，不用再配一套。
 */
@Composable
internal fun RemoteCover(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (url.isNullOrBlank()) {
        Box(modifier.background(Lift))
        return
    }
    AndroidView(
        modifier = modifier,
        factory = {
            ShapeableImageView(context).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                // 短交叉淡入而不是硬切：远程图落地时不闪白块
                Glide.with(this).load(url).centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade(180)).into(this)
            }
        },
        onRelease = { view -> runCatching { Glide.with(view).clear(view) } },
    )
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
@Composable
internal fun FeedEndRow(
    loading: Boolean, hasMore: Boolean, onLoadMore: () -> Unit,
    /** 首屏加载中（列表还空着）：底部不再转圈，等待信号由列表位的扫光骨架统一表达。 */
    firstPageLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (loading && firstPageLoading) return
    Box(modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            hasMore -> Text("继续下滑加载更多", color = Muted, fontSize = 12.sp)
            else -> Text("到底了", color = Muted, fontSize = 12.sp)
        }
    }
}
