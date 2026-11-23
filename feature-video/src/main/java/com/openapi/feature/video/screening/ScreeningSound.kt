package com.openapi.feature.video.screening

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
                SoundHubPicker(store, hubChannel, onExit) { hub = it }
            } else {
                SoundTrackHub(
                    store = store,
                    hubParam = hub ?: return@AnimatedContent,
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
    store: ScreeningFeedStore,
    seedChannel: String?,
    onExit: () -> Unit,
    onOpenHub: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pager = remember(seedChannel) {
        FeedPager(scope) { start -> store.soundChannelHub(seedChannel ?: "", start) }
    }
    val state = rememberLazyListState()
    val seen = remember { mutableSetOf<String>() }
    LaunchedEffect(pager, seedChannel) { if (seedChannel != null) pager.load() }
    LaunchedEffect(state, pager) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { index -> if (index >= pager.items.size - 6) pager.loadMore() }
    }
    val rows = pager.items
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding(),
        state = state,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 26.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("声音馆", fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text(
                    if (pager.loading) "正在取数据…" else "${rows.size} 个频道",
                    color = Muted, fontSize = 12.sp,
                )
            }
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

@Composable
private fun SoundTrackHub(
    store: ScreeningFeedStore,
    hubParam: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = remember(hubParam) {
        FeedPager(scope) { start -> store.soundTracks(hubParam, start) }
    }
    val state = rememberLazyListState()
    val seen = remember { mutableSetOf<String>() }
    var currentIndex by rememberSaveable { mutableIntStateOf(-1) }
    val tracks = remember(pager.items) {
        pager.items.mapIndexedNotNull { i, e -> e.soundTrackRef(i) }
    }
    val player = remember {
        ExoPlayer.Builder(context).build()
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    fun playAt(index: Int) {
        val track = tracks.getOrNull(index) ?: return
        currentIndex = index
        player.setMediaItem(MediaItem.fromUri(Uri.parse(track.fileUrl)))
        player.prepare()
        player.play()
    }
    LaunchedEffect(pager) { pager.load() }
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
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        LazyColumn(
            Modifier.weight(1f),
            state = state,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
        ) {
            item {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回频道") }
                Heading("曲目", if (pager.loading) "正在取数据…" else "${tracks.size} 首")
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
            Column(Modifier.fillMaxWidth().background(Lift).padding(14.dp)) {
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
