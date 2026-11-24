package com.openapi.feature.video.screening

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive

/**
 * 声音馆：频道列表 → 曲目列表 + 底栏播放器（ExoPlayer）。
 * 接口对齐 SoundChannleBodyFragment / SoundItemsMusicFragment。
 *
 * 取数状态挂在转场之上（对齐 [ScreeningGallery]、[ScreeningText]）：退回频道列表
 * 不重建曲目 pager、不重新取数；重取只由用户下拉触发。播放器也提到这一层——
 * 从曲目列表退回频道列表不该把正在播的歌掐断（旧页面就是这个行为）。
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun ScreeningSound(
    store: ScreeningFeedStore,
    hubChannel: String?,
    onExit: () -> Unit,
) {
    var hub by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = hub != null) {
        if (hub != null) hub = null else onExit()
    }

    val context = LocalContext.current
    val hubs = remember(hubChannel) {
        FeedPager(scope) { start -> store.soundChannelHub(hubChannel ?: "", start) }
    }
    val hubParam = hub
    val tracksPager = remember(hubParam) {
        FeedPager(scope) { start -> store.soundTracks(hubParam ?: "", start) }
    }
    val hubState = rememberLazyListState()
    val tracksState = rememberLazyListState()
    val hubSeen = remember { mutableSetOf<String>() }
    val trackSeen = remember { mutableSetOf<String>() }
    LaunchedEffect(hubs, hubChannel) { if (hubChannel != null) hubs.load() }
    LaunchedEffect(tracksPager, hubParam) { if (hubParam != null) tracksPager.load() }

    val tracks = remember(tracksPager.items) {
        tracksPager.items.mapIndexedNotNull { i, e -> e.soundTrackRef(i) }
    }
    var currentIndex by rememberSaveable { mutableIntStateOf(-1) }
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    val motion = LocalScreeningMotion.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val slidePx = with(density) { motion.slideDistanceDp().dp.toPx() }

    Box(Modifier.fillMaxSize().background(Ink)) {
        AnimatedContent(
            targetState = if (hub == null) 0 else 1,
            transitionSpec = { screenSlide(motion, slidePx, targetState > initialState) },
            label = "soundLevel",
        ) { level ->
            if (level == 0) {
                SoundHubPicker(
                    pager = hubs,
                    state = hubState,
                    seen = hubSeen,
                    seedChannel = hubChannel,
                    onExit = onExit,
                    onOpenHub = { hub = it },
                )
            } else {
                SoundTrackHub(
                    pager = tracksPager,
                    state = tracksState,
                    seen = trackSeen,
                    tracks = tracks,
                    player = player,
                    currentIndex = currentIndex,
                    onSelect = { currentIndex = it },
                    onBack = { hub = null },
                )
            }
        }
    }
}

internal fun RemoteEntry.soundListKey(): String? =
    param?.takeIf { it.isNotBlank() } ?: channelParam

internal data class SoundTrackRef(
    val index: Int,
    val title: String,
    val fileUrl: String,
    val subtitle: String,
)

internal fun RemoteEntry.soundTrackRef(listIndex: Int): SoundTrackRef? {
    if (actionUrl.isBlank()) return null
    return SoundTrackRef(listIndex, title, actionUrl, description)
}

@Composable
private fun SoundHubPicker(
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
        title = "声音馆",
        onBack = onExit,
        detail = feedToolbarDetail(pager.loading, rows.size, "个频道"),
    ) {
        item {
            Text("戴上耳机，\n慢慢来。", color = White, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black)
            FeedError(pager.error, pager::reload)
            if (seedChannel == null) {
                Text("没有频道参数，返回换一张声音卡试试。", Modifier.padding(14.dp), color = Muted)
            } else if (pager.loading && rows.isEmpty()) {
                ListSkeleton(Modifier.padding(top = 2.dp), rows = 3, rowHeight = 72.dp)
            }
        }
        items(rows.size) { index ->
            val entry = rows[index]
            val key = entry.soundListKey()
            Row(
                Modifier.fillMaxWidth()
                    .enterFeedItem(seen, entry.key, index)
                    .pressable(Tile) { if (key != null) onOpenHub(key) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.GraphicEq, null, tint = Peach, modifier = Modifier.padding(horizontal = 12.dp))
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun SoundTrackHub(
    pager: FeedPager,
    state: LazyListState,
    seen: MutableSet<String>,
    tracks: List<SoundTrackRef>,
    player: ExoPlayer,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
) {
    fun playAt(index: Int) {
        val track = tracks.getOrNull(index) ?: return
        onSelect(index)
        player.setMediaItem(MediaItem.fromUri(Uri.parse(track.fileUrl)))
        player.prepare()
        player.play()
    }
    LaunchedEffect(state, pager) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { index -> if (index >= pager.items.size - 6) pager.loadMore() }
    }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    val playing = player.isPlaying
    LaunchedEffect(player) {
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(1L)
            delay(200)
        }
    }
    val current = tracks.getOrNull(currentIndex)
    // 列表外壳自带安全区；外层 Column 不再垫 insets（会双重），播放条自己贴底。
    Column(Modifier.fillMaxSize()) {
        FeedListShell(
            pager = pager,
            state = state,
            title = "曲目",
            onBack = onBack,
            detail = feedToolbarDetail(pager.loading, tracks.size, "首"),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            item {
                FeedError(pager.error, pager::reload)
                if (pager.loading && tracks.isEmpty()) ListSkeleton(rowHeight = 56.dp)
            }
            if (!pager.loading && tracks.isEmpty()) {
                item { Text("这个频道还没有音频。", Modifier.padding(14.dp), color = Muted) }
            }
            items(tracks.size) { index ->
                val track = tracks[index]
                val selected = index == currentIndex
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (selected) Lift else Ink)
                        .enterFeedItem(seen, "sound:$index", index)
                        .pressable(Tile) { playAt(index) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(14.dp)) {
                        Text(track.title, color = if (selected) Mint else White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (track.subtitle.isNotBlank()) {
                            Text(track.subtitle, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item { FeedEndRow(pager.loading, pager.hasMore, pager::loadMore) }
        }
        if (current != null) {
            // 播放条在壳之外（壳的安全区够不到它）：自己垫导航条，同旧写法——
            // 以前整页 Column 带 safeDrawingPadding，撤掉包装时这份底部安全区不能丢。
            Column(Modifier.fillMaxWidth().safeDrawingPadding().background(Lift).padding(14.dp)) {
                Text(current.title, color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Slider(
                    value = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                    onValueChange = { fraction -> player.seekTo((durationMs * fraction).toLong()) },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (tracks.isEmpty()) return@IconButton
                        playAt((currentIndex - 1).coerceAtLeast(0))
                    }) { Icon(Icons.Default.SkipPrevious, "上一首", tint = White) }
                    IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "播放", tint = Mint, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = {
                        if (tracks.isEmpty()) return@IconButton
                        playAt((currentIndex + 1).coerceAtMost(tracks.lastIndex))
                    }) { Icon(Icons.Default.SkipNext, "下一首", tint = White) }
                }
            }
        }
    }
}
