package com.openapi.ks.auxio.musicui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import java.util.Locale

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}

private fun MusicTrack.lineTitle(): String =
    title.ifBlank { mediaUrl }

private fun playModeIcon(mode: Int): ImageVector = when (mode) {
    1 -> Icons.Filled.RepeatOne
    2 -> Icons.Filled.Repeat
    4 -> Icons.Filled.Shuffle
    else -> Icons.Filled.QueueMusic
}

@Composable
private fun playModeContentDescription(mode: Int): String = when (mode) {
    1 -> stringResource(R.string.auxio_play_mode_one)
    2 -> stringResource(R.string.auxio_play_mode_all)
    4 -> stringResource(R.string.auxio_play_mode_shuffle)
    else -> stringResource(R.string.auxio_play_mode_list)
}

private fun MusicTrack.lineSubtitle(): String {
    val host = try {
        Uri.parse(mediaUrl).host
    } catch (_: Throwable) {
        null
    }
    return when {
        !host.isNullOrBlank() -> host
        mediaUrl.length > 52 -> mediaUrl.take(49) + "…"
        else -> mediaUrl
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuxioMusicNavHost(
    playlistTitle: String,
    playerVm: PlayerViewModel,
    onFinish: () -> Unit,
    remoteListExtras: AuxioRemoteListExtras? = null,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "list",
    ) {
        composable("list") {
            AuxioListScreen(
                title = playlistTitle,
                vm = playerVm,
                onOpenDetail = { index -> navController.navigate("detail/$index") },
                remoteListExtras = remoteListExtras,
            )
        }
        composable(
            route = "detail/{idx}",
            arguments = listOf(
                navArgument("idx") { type = NavType.IntType },
            ),
        ) { entry ->
            val idx = entry.arguments?.getInt("idx") ?: 0
            AuxioDetailScreen(
                vm = playerVm,
                index = idx,
                onBack = {
                    if (!navController.popBackStack()) {
                        onFinish()
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuxioListScreen(
    title: String,
    vm: PlayerViewModel,
    onOpenDetail: (Int) -> Unit,
    remoteListExtras: AuxioRemoteListExtras? = null,
) {
    val tracks by vm.playlist.collectAsState()
    val currentIndex by vm.currentIndex.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val playMode by vm.playMode.collectAsState()
    val currentTrack = vm.trackAt(currentIndex)
    val listState = rememberLazyListState()
    val positionMs by vm.positionMs.collectAsState()
    val durationMs by vm.durationMs.collectAsState()
    var miniSliderDragging by remember { mutableStateOf(false) }
    var miniSliderValue by remember { mutableFloatStateOf(0f) }
    val miniProgress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    if (!miniSliderDragging) {
        miniSliderValue = miniProgress
    }

    LaunchedEffect(listState, tracks.size, remoteListExtras?.hasMore, remoteListExtras?.loadingMore) {
        val extras = remoteListExtras ?: return@LaunchedEffect
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= tracks.size - 4 && tracks.isNotEmpty()
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && extras.hasMore && !extras.loadingMore) {
                extras.onLoadMore()
            }
        }
    }

    Scaffold(
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            if (tracks.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { vm.skipNext() },
                    modifier = Modifier.padding(end = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_auxio_skip_next),
                        contentDescription = stringResource(R.string.auxio_fab_next_cd),
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (remoteListExtras != null) {
                        if (remoteListExtras.refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(
                                onClick = remoteListExtras.onRefresh,
                                enabled = !remoteListExtras.refreshing,
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.auxio_list_refresh_cd),
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider()
                AuxioMiniPlayerStatusBar(
                    track = currentTrack,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    playMode = playMode,
                    onBarClick = { onOpenDetail(currentIndex.coerceIn(0, tracks.lastIndex.coerceAtLeast(0))) },
                    onPlayPause = { vm.playPause() },
                    onSkipPrev = { vm.skipPrev() },
                    onSkipNext = { vm.skipNext() },
                    onCyclePlayMode = { vm.cyclePlayMode() },
                    onSliderDragChange = {
                        miniSliderDragging = true
                        miniSliderValue = it
                    },
                    onSliderDragFinished = {
                        miniSliderDragging = false
                        if (durationMs > 0) {
                            vm.seekToMs((miniSliderValue * durationMs).toLong())
                        }
                    },
                    sliderValue = miniSliderValue,
                )
                Text(
                    text = stringResource(R.string.auxio_gpl_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            itemsIndexed(
                items = tracks,
                key = { index, item -> "${item.id}-$index" },
            ) { index, item ->
                val rowCurrent = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AuxioTrackLeadingThumb(
                        artworkUrl = item.artworkUrl,
                        isCurrentTrack = rowCurrent,
                        isPlaying = isPlaying && rowCurrent,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                            .clickable { onOpenDetail(index) },
                    ) {
                        Text(
                            text = item.lineTitle(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = item.lineSubtitle(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AuxioSongRowOverflowMenu(
                        onPlay = { vm.playAt(index) },
                        onOpenNowPlaying = { onOpenDetail(index) },
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 76.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            if (remoteListExtras?.loadingMore == true) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AuxioDetailScreen(
    vm: PlayerViewModel,
    index: Int,
    onBack: () -> Unit,
) {
    LaunchedEffect(index) {
        vm.playAt(index)
    }
    val track = vm.trackAt(index)
    val position by vm.positionMs.collectAsState()
    val duration by vm.durationMs.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val playMode by vm.playMode.collectAsState()

    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    val progress = if (duration > 0L) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    if (!sliderDragging) {
        sliderValue = progress
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auxio_now_playing)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.auxio_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val art = track?.artworkUrl
                if (!art.isNullOrEmpty()) {
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = "♪",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = track?.title.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 600,
                        repeatDelayMillis = 1200,
                    ),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatMs(position), style = MaterialTheme.typography.bodyMedium)
                Text(formatMs(duration), style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = sliderValue,
                onValueChange = {
                    sliderDragging = true
                    sliderValue = it
                },
                onValueChangeFinished = {
                    sliderDragging = false
                    if (duration > 0) {
                        vm.seekToMs((sliderValue * duration).toLong())
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.skipPrev() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_auxio_skip_previous),
                        contentDescription = stringResource(R.string.auxio_prev),
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = { vm.playPause() }) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) {
                            stringResource(R.string.auxio_pause)
                        } else {
                            stringResource(R.string.auxio_play)
                        },
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { vm.skipNext() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_auxio_skip_next),
                        contentDescription = stringResource(R.string.auxio_next),
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = { vm.cyclePlayMode() }) {
                    Icon(
                        playModeIcon(playMode),
                        contentDescription = stringResource(
                            R.string.auxio_play_mode_button_cd,
                            playModeContentDescription(playMode),
                        ),
                    )
                }
            }
        }
    }
}
