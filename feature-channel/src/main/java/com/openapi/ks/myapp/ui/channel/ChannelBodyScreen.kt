package com.openapi.ks.myapp.ui.channel

import android.graphics.Color as AndroidColor
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.openapi.core.util.StringUtils
import com.openapi.feature.channel.R
import com.openapi.ks.myapp.bean.vo.RecommendVo
import com.openapi.ks.myapp.constant.Constants
import com.openapi.ks.myapp.data.ImagePhotoLoad
import kotlinx.coroutines.flow.distinctUntilChanged
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Compose version of the legacy channel body (LoadableFragment + MyPullView + IndexRecommendAdapter).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelBodyScreen(
    controller: ChannelBodyController,
    spanCount: Int,
    irregularGrid: Boolean,
    onItemClick: (RecommendVo) -> Unit,
) {
    val scope = rememberCoroutineScope()
    when (controller.uiState) {
        ChannelUiState.Loading -> Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(dimensionResource(R.dimen.margin_250)).padding(top = dimensionResource(R.dimen.margin_55)), contentAlignment = Alignment.TopCenter) {
                CircularProgressIndicator(modifier = Modifier.size(dimensionResource(R.dimen.margin_48)))
            }
        }

        ChannelUiState.Retry -> RetryPlaceholder(
            textRes = com.openapi.core.util.R.string.error_network_retry,
            onClick = { controller.retry(scope) },
        )

        ChannelUiState.NoData -> RetryPlaceholder(
            textRes = com.openapi.core.util.R.string.error_network_nodata_retry,
            onClick = { controller.retry(scope) },
        )

        ChannelUiState.Content -> PullToRefreshBox(
            isRefreshing = controller.refreshing,
            onRefresh = { controller.pullToRefresh(scope) },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (irregularGrid) {
                IrregularGridList(controller, scope, onItemClick)
            } else {
                StaggeredList(controller, scope, spanCount, onItemClick)
            }
        }
    }
}

@Composable
private fun RetryPlaceholder(textRes: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.margin_250))
            .padding(top = dimensionResource(R.dimen.margin_55))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = stringResource(textRes),
            color = Color(0xFFA8B0BE),
            fontSize = dimenToSp(R.dimen.textsize_18),
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun StaggeredList(
    controller: ChannelBodyController,
    scope: kotlinx.coroutines.CoroutineScope,
    spanCount: Int,
    onItemClick: (RecommendVo) -> Unit,
) {
    val state = rememberLazyStaggeredGridState()
    LoadMoreWatcher(
        listenFlow = { snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 } },
        threshold = 5,
        controller = controller,
        scope = scope,
        count = { controller.items.size },
    )
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(spanCount),
        state = state,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(count = controller.items.size) { index ->
            val vo = controller.items[index]
            ChannelItem(vo = vo, onClick = { onItemClick(vo) })
        }
        if (controller.loadingMore) {
            item(span = StaggeredGridItemSpan.FullLine) {
                LoadMoreIndicator()
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun IrregularGridList(
    controller: ChannelBodyController,
    scope: kotlinx.coroutines.CoroutineScope,
    onItemClick: (RecommendVo) -> Unit,
) {
    val state = rememberLazyGridState()
    LoadMoreWatcher(
        listenFlow = { snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 } },
        threshold = 3,
        controller = controller,
        scope = scope,
        count = { controller.items.size },
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        state = state,
        modifier = Modifier.fillMaxSize(),
    ) {
        // Legacy createIrregularWidthImageGridLayout: span 2,2,2 then 3,3 alternating.
        items(
            count = controller.items.size,
            span = { index -> GridItemSpan(if (index % 5 < 3) 2 else 3) },
        ) { index ->
            val vo = controller.items[index]
            ChannelItem(vo = vo, onClick = { onItemClick(vo) })
        }
        if (controller.loadingMore) {
            item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                LoadMoreIndicator()
            }
        }
    }
}

/** Legacy MyPullView scroll listener: load more when the last visible item is near the end. */
@Composable
private fun LoadMoreWatcher(
    listenFlow: () -> kotlinx.coroutines.flow.Flow<Int>,
    threshold: Int,
    controller: ChannelBodyController,
    scope: kotlinx.coroutines.CoroutineScope,
    count: () -> Int,
) {
    LaunchedEffect(controller) {
        listenFlow().distinctUntilChanged().collect { last ->
            if (last >= 0 && last > count() - threshold) {
                controller.loadMore(scope)
            }
        }
    }
}

@Composable
private fun LoadMoreIndicator() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = dimensionResource(R.dimen.margin_5)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(dimensionResource(R.dimen.margin_22)))
    }
}

/**
 * One recommend card. Layout and show-type rules mirror tab_index_item_recommend.xml plus
 * the ShowType handlers (Normal / Blur / Text / Text_Below) and IndexRecommendAdapter.
 */
@Composable
private fun ChannelItem(vo: RecommendVo, onClick: () -> Unit) {
    val m2 = dimensionResource(R.dimen.margin_2)
    val m3 = dimensionResource(R.dimen.margin_3)
    val m5 = dimensionResource(R.dimen.margin_5)
    val shape = RoundedCornerShape(10.dp)
    val cardBg = colorResource(R.color.music_row_bg_alt)
    val divider = colorResource(R.color.base_white)
    val type = vo.showType
    val hasImg = StringUtils.hasText(vo.img)
    val photoVisible = !(type == Constants.ShowType.Text && hasImg)
    val loadsImage = hasImg && type != Constants.ShowType.Text
    var imageReady by remember(vo.img) { mutableStateOf(false) }

    val titleVisible = when (type) {
        Constants.ShowType.Normal -> !imageReady
        Constants.ShowType.Blur, Constants.ShowType.Text -> true
        else -> false
    }
    val titleBelowVisible = type == Constants.ShowType.Text_Below
    val newBadge: Int? = when {
        vo.newShow == true -> R.drawable.ic_new
        vo.type == 1 -> R.drawable.ic_vip1
        vo.type == 2 -> R.drawable.ic_vip2
        vo.type == 3 -> R.drawable.ic_vip3
        vo.type == 4 -> R.drawable.ic_vip4
        else -> null
    }
    val adsText: String? = when {
        vo.dataType == RecommendVo.dataType_ADS -> stringResource(R.string.ads_logo)
        StringUtils.hasText(vo.showLogo) -> vo.showLogo
        else -> null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = m2, end = m2, top = m3, bottom = m3)
            .shadow(2.dp, shape)
            .clip(shape)
            .background(cardBg)
            .clickable(onClick = onClick),
    ) {
        // Card width = cell width minus the card margins; photo height = width * rate (legacy).
        val cardWidth: Dp = maxWidth
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = m2)) {
            if (photoVisible) {
                val photoHeight: Dp = cardWidth * vo.rate
                Box(modifier = Modifier.fillMaxWidth().height(photoHeight)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                        update = { iv ->
                            iv.setBackgroundColor(parseColorOrTransparent(vo.color))
                            if (iv.tag != vo.img) {
                                iv.tag = vo.img
                                if (loadsImage) {
                                    loadPhoto(iv, vo.img) { imageReady = true }
                                } else {
                                    Glide.with(iv).clear(iv)
                                    iv.setImageDrawable(null)
                                }
                            }
                        },
                    )
                    SharedOverlays(
                        newBadge = newBadge,
                        titleText = if (titleVisible) vo.title else null,
                        adsText = adsText,
                        m5 = m5,
                        m3 = m3,
                    )
                }
            } else {
                // Text show-type with an image: the photo view is GONE, so the frame only
                // holds the overlays (legacy FrameLayout wraps to the title).
                Box(modifier = Modifier.fillMaxWidth()) {
                    SharedOverlays(
                        newBadge = newBadge,
                        titleText = if (titleVisible) vo.title else null,
                        adsText = adsText,
                        m5 = m5,
                        m3 = m3,
                    )
                }
            }
            if (titleBelowVisible) {
                Text(
                    text = vo.title ?: "",
                    color = Color(0xFF3A3A3C),
                    fontSize = dimenToSp(R.dimen.textsize_12),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = m2, end = m2, top = m3, bottom = m2),
                )
            }
        }
        // Legacy DividerItemDecoration: 1dp white line under each item.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(divider),
        )
    }
}

/** Bottom scrim, optional VIP/new badge, title overlay and ads label, drawn over the photo. */
@Composable
private fun BoxScope.SharedOverlays(newBadge: Int?, titleText: String?, adsText: String?, m5: Dp, m3: Dp) {
    // 底部渐变遮罩：顶部透明、底部 #CC000000（与原 bg_gallery_bottom_scrim 的方向一致）。
    // 原来用 painterResource 加载 <shape> XML，Compose 不接受，会崩溃。
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(36.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))),
    )
    if (newBadge != null) {
        Image(
            painter = painterResource(newBadge),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize(),
        )
    }
    if (titleText != null) {
        Text(
            text = titleText,
            color = Color.White,
            fontSize = dimenToSp(R.dimen.recommend_hit_size),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = Shadow(color = Color(0x80000000), offset = Offset(0f, 1f), blurRadius = 3f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = m5, end = m5, bottom = m5),
        )
    }
    if (adsText != null) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = m5, top = m3)
                // 原 bg_gallery_page_badge 是 <shape>（圆角 20dp、#66000000、1dp #33FFFFFF 描边），用 Compose 修饰符画
                .background(Color(0x66000000), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp)),
        ) {
            Text(
                text = adsText,
                color = Color.White,
                fontSize = dimenToSp(R.dimen.textsize_12),
                modifier = Modifier.padding(horizontal = m5, vertical = dimensionResource(R.dimen.margin_2)),
            )
        }
    }
}

/** Glide load used by NormalShowTypeHandle / BlurShowTypeHandle / TextBelowShowTypeHandle. */
private fun loadPhoto(iv: ImageView, url: String, onReady: () -> Unit) {
    if (url.endsWith(".txt")) {
        fetchBase64(url) { base64 ->
            iv.post {
                if (iv.tag == url) {
                    Glide.with(iv)
                        .load(base64)
                        .apply(ImagePhotoLoad.options)
                        .transition(DrawableTransitionOptions.withCrossFade(400))
                        .into(iv)
                }
            }
        }
        return
    }
    Glide.with(iv)
        .load(url)
        .apply(ImagePhotoLoad.options)
        .transition(DrawableTransitionOptions.withCrossFade(500))
        .listener(object : RequestListener<Drawable> {
            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean = false

            override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                onReady()
                return false
            }
        })
        .into(iv)
}

/** Legacy ShowTypeHandle.fetchBase64ImageFromUrl. */
private val base64Client by lazy { OkHttpClient() }

private fun fetchBase64(url: String, onBody: (String) -> Unit) {
    val request = Request.Builder().url(url).build()
    base64Client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            com.openapi.core.util.LogUtil.e("onFailure", e)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { r ->
                val body = r.body
                if (r.isSuccessful && body != null) {
                    onBody(body.string())
                }
            }
        }
    })
}

private fun parseColorOrTransparent(value: String?): Int {
    return try {
        AndroidColor.parseColor(value)
    } catch (e: Exception) {
        AndroidColor.TRANSPARENT
    }
}

@Composable
private fun dimenToSp(id: Int): TextUnit {
    val density = LocalDensity.current
    val px = LocalContext.current.resources.getDimension(id)
    return with(density) { px.toSp() }
}
