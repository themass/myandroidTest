package com.openapi.feature.content.common

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
import com.openapi.common.util.lang.StringUtils
import com.openapi.feature.content.R
import com.openapi.core.model.RecommendVo
import com.openapi.core.config.Constants
import com.openapi.core.image.ImagePhotoLoad
import kotlinx.coroutines.flow.distinctUntilChanged
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

@Composable
internal fun RetryPlaceholder(textRes: Int, onClick: () -> Unit) {
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
internal fun StaggeredList(
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
internal fun IrregularGridList(
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
internal fun LoadMoreWatcher(
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
internal fun LoadMoreIndicator() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = dimensionResource(R.dimen.margin_5)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(dimensionResource(R.dimen.margin_22)))
    }
}
