package com.openapi.feature.video.screening

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 一页可分页的服务端列表状态：首屏、翻页、失败重试。
 *
 * 频道列表、频道里的片单、图集列表、图集内的图片都用这一个形状，页面只认它。
 * 这里没有「随包样例」这条路：服务端没通就说没通（[error]，页面给重试），
 * 通了但没数据就是空（items 为空）。页面绝不拿假数据填屏幕。
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
        request(start, append = true)
    }

    private fun request(start: Int, append: Boolean) {
        loading = true
        scope.launch(Dispatchers.Main) {
            val page = runCatching { fetch(start) }
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
                    error = "没取到服务端数据：${cause.message ?: "网络问题"}"
                },
            )
        }
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
                Glide.with(this).load(url).centerCrop().dontAnimate().into(this)
            }
        },
        onRelease = { view -> runCatching { Glide.with(view).clear(view) } },
    )
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

/** 列表底部：转圈 + 到底提示。 */
@Composable
internal fun FeedEndRow(loading: Boolean, hasMore: Boolean, onLoadMore: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            hasMore -> Text("继续下滑加载更多", color = Muted, fontSize = 12.sp)
            else -> Text("到底了", color = Muted, fontSize = 12.sp)
        }
    }
}
