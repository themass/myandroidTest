package com.openapi.home.presentation

import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bumptech.glide.Glide
import com.openapi.core.design.theme.AppColors
import com.openapi.home.ChannelsDependencies
import com.openapi.home.domain.Channel
import com.openapi.feature.channel.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    modifier: Modifier = Modifier,
    viewModel: ChannelsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.onResume(context)
        onPauseOrDispose { }
    }

    Column(modifier.fillMaxSize()) {
        if (state.categories.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = category.id == state.selectedId,
                        onClick = { viewModel.select(category.id) },
                        label = { Text(stringResource(category.titleRes)) },
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.home_channels_error), color = AppColors.TextSecondary)
                    Button(onClick = viewModel::retry) { Text(stringResource(R.string.home_channels_retry)) }
                }
                state.items.isEmpty() -> Text(
                    stringResource(R.string.home_channels_empty),
                    color = AppColors.TextSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> {
                    val gridState = rememberLazyStaggeredGridState()
                    val nearEnd by remember {
                        derivedStateOf {
                            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= gridState.layoutInfo.totalItemsCount - 4
                        }
                    }
                    LaunchedEffect(nearEnd, state.items.size) {
                        if (nearEnd) viewModel.loadMore()
                    }
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            state = gridState,
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(state.items) { _, channel ->
                                ChannelCard(channel) {
                                    ChannelsDependencies.require().openChannel(context, channel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        RemoteImage(
            url = channel.imageUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(channel.aspectRatio)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
        )
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                channel.title,
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (channel.description.isNotEmpty()) {
                Text(
                    channel.description,
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun RemoteImage(url: String?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
        modifier = modifier,
        update = { view ->
            if (url.isNullOrEmpty()) {
                Glide.with(view).clear(view)
                view.setImageDrawable(null)
            } else {
                Glide.with(view).load(url).into(view)
            }
        },
    )
}
