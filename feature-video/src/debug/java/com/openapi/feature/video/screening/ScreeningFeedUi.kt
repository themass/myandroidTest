package com.openapi.feature.video.screening

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 一页可分页的服务端列表状态：首屏、翻页、失败回落样例。
 *
 * 频道列表、频道里的片单、图集列表、图集内的图片都用这一个形状，页面只认它。
 * 失败和空结果都落到 [fallback]，并标记 [fromSample]，所以断网时页面还是能逛的。
 */
internal class FeedPager(
    private val scope: CoroutineScope,
    /** 取一页；返回的 [ScreeningFeedStore.Page.nextStart] 决定还能不能继续翻。 */
    private val fetch: suspend (Int) -> ScreeningFeedStore.Page<RemoteEntry>,
    /** 取不到真实数据时用的样例。 */
    private val fallback: () -> List<RemoteEntry>,
) {
    val items = mutableStateListOf<RemoteEntry>()
    var loading by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    /** true = 现在这批数据来自随包样例（服务端没通，或者返回空）。 */
    var fromSample by mutableStateOf(false)
        private set
    /** 给用户看的一句话：只在回落到样例时出现，说明为什么不是真实数据。 */
    var notice by mutableStateOf<String?>(null)
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
            val page = runCatching { fetch(start) }.getOrElse {
                ScreeningFeedStore.Page(fallback(), nextStart = null, hasMore = false, fromSample = true)
            }
            if (!append) items.clear()
            items.addAll(page.items)
            nextStart = page.nextStart
            hasMore = page.hasMore
            fromSample = page.fromSample
            // 提示要说实话：真发过请求才算「没连上」；本地样片库压根不发请求，说没连上是骗人
            notice = if (page.fromSample && page.requested) FALLBACK_NOTICE else null
            loading = false
        }
    }

    private companion object {
        const val FALLBACK_NOTICE = "没取到服务端数据，现在是随包样例。"
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

/** 内容区上方的数据源说明：让「现在是样例」这件事在页面上看得见。 */
@Composable
internal fun FeedNotice(notice: String?, modifier: Modifier = Modifier) {
    if (notice == null) return
    Box(modifier.fillMaxWidth().background(Lift).padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(notice, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
