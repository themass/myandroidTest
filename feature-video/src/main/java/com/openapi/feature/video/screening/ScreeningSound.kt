package com.openapi.feature.video.screening

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.openapi.core.config.Constants
import com.openapi.core.db.FavoriteVo
import com.openapi.core.favorite.FavoriteUtil
import com.openapi.common.net.http.NetUtils
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.core.model.RecommendVo
import com.openapi.speech.subtitle.api.StreamingSubtitleOverlayPresenter
import com.openapi.speech.subtitle.realtime.VideoRealtimeSubtitleSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive

/**
 * 声音馆：频道列表 → 曲目列表 + 底栏播放器（MediaSessionService）。
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
    val soundPreferences = remember(context) {
        context.getSharedPreferences("screening_sound", android.content.Context.MODE_PRIVATE)
    }
    var playbackMode by rememberSaveable {
        mutableStateOf(soundPlaybackMode(soundPreferences.getString("playback_mode", null)))
    }
    var playbackSpeed by rememberSaveable {
        mutableFloatStateOf(clampSoundPlaybackSpeed(soundPreferences.getFloat("playback_speed", 1f)))
    }
    LaunchedEffect(playbackMode) {
        soundPreferences.edit().putString("playback_mode", playbackMode.name).apply()
    }
    LaunchedEffect(playbackSpeed) {
        soundPreferences.edit().putFloat("playback_speed", playbackSpeed).apply()
    }
    var player by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, ScreeningAudioService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        var disposed = false
        future.addListener(
            {
                if (!disposed) player = runCatching { future.get() }.getOrNull()
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            disposed = true
            if (future.isDone && !future.isCancelled) {
                runCatching { future.get().release() }
            } else {
                future.cancel(true)
            }
        }
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
                val connectedPlayer = player
                if (connectedPlayer == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("正在连接播放器…", color = Muted)
                    }
                } else {
                    SoundTrackHub(
                        pager = tracksPager,
                        state = tracksState,
                        seen = trackSeen,
                        tracks = tracks,
                        player = connectedPlayer,
                        currentIndex = currentIndex,
                        playbackMode = playbackMode,
                        onPlaybackModeChange = { playbackMode = it },
                        playbackSpeed = playbackSpeed,
                        onPlaybackSpeedChange = { playbackSpeed = clampSoundPlaybackSpeed(it) },
                        onSelect = { currentIndex = it },
                        onBack = { hub = null },
                    )
                }
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

internal fun SoundTrackRef.toSoundFavorite(): FavoriteVo {
    val recommend = RecommendVo().apply {
        title = this@toSoundFavorite.title
        actionUrl = this@toSoundFavorite.fileUrl
        dataType = RecommendVo.dataType_SOUND_CHANNEL
    }
    return recommend.tofavorite(Constants.FavoriteType.SOUND)
}

internal enum class SoundPlaybackMode(val label: String) {
    SINGLE("单曲循环"),
    LIST("列表循环"),
    SEQUENTIAL("顺序播放"),
    SHUFFLE("随机播放");

    fun nextMode(): SoundPlaybackMode = entries[(ordinal + 1) % entries.size]
}

internal fun soundPlaybackMode(savedName: String?): SoundPlaybackMode =
    SoundPlaybackMode.entries.firstOrNull { it.name == savedName } ?: SoundPlaybackMode.LIST

internal fun clampSoundPlaybackSpeed(speed: Float): Float = speed.coerceIn(0.25f, 3f)

internal const val SOUND_SWITCH_DEFAULT_ENABLED = true

internal fun shouldPromptForSoundNetwork(
    isWifi: Boolean,
    soundSwitch: Boolean,
    sessionOverride: Boolean,
    isPlaying: Boolean,
): Boolean = isPlaying && !isWifi && !soundSwitch && !sessionOverride

internal fun screeningSubtitleMediaUrl(currentMediaUrl: String?, selectedTrackUrl: String?): String =
    currentMediaUrl?.takeIf { it.isNotBlank() } ?: selectedTrackUrl.orEmpty()

internal fun manualNextSoundTrackIndex(
    mode: SoundPlaybackMode,
    currentIndex: Int,
    trackCount: Int,
    chooseRandom: (List<Int>) -> Int = { it.random() },
): Int? {
    if (trackCount <= 0) return null
    val currentIsValid = currentIndex in 0 until trackCount
    if (!currentIsValid) return 0
    return when (mode) {
        SoundPlaybackMode.SINGLE, SoundPlaybackMode.SEQUENTIAL ->
            (currentIndex + 1).takeIf { it < trackCount }
        SoundPlaybackMode.LIST -> (currentIndex + 1) % trackCount
        SoundPlaybackMode.SHUFFLE -> {
            if (trackCount == 1) currentIndex
            else chooseRandom((0 until trackCount).filter { it != currentIndex })
        }
    }
}

internal fun previousSoundTrackIndex(currentIndex: Int, trackCount: Int): Int? =
    if (currentIndex in 1 until trackCount) currentIndex - 1 else null

internal fun nextSoundTrackIndex(
    mode: SoundPlaybackMode,
    currentIndex: Int,
    trackCount: Int,
    chooseRandom: (List<Int>) -> Int = { it.random() },
): Int? {
    if (trackCount <= 0) return null
    val currentIsValid = currentIndex in 0 until trackCount
    return when (mode) {
        SoundPlaybackMode.SINGLE -> if (currentIsValid) currentIndex else 0
        SoundPlaybackMode.LIST -> if (currentIsValid) (currentIndex + 1) % trackCount else 0
        SoundPlaybackMode.SEQUENTIAL -> when {
            !currentIsValid -> 0
            currentIndex < trackCount - 1 -> currentIndex + 1
            else -> null
        }
        SoundPlaybackMode.SHUFFLE -> {
            if (!currentIsValid) 0
            else if (trackCount == 1) currentIndex
            else chooseRandom((0 until trackCount).filter { it != currentIndex })
        }
    }
}

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
    player: Player,
    currentIndex: Int,
    playbackMode: SoundPlaybackMode,
    onPlaybackModeChange: (SoundPlaybackMode) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var playbackEnded by remember(player) { mutableStateOf(false) }
    var pendingManualNextFromCount by remember(player) { mutableIntStateOf(-1) }
    var speedDialogOpen by rememberSaveable { mutableStateOf(false) }
    var networkDialogOpen by rememberSaveable { mutableStateOf(false) }
    var networkOverride by remember { mutableStateOf(false) }
    var pendingNetworkTrack by remember(player) { mutableIntStateOf(-1) }
    var resumeAfterNetworkConfirmation by remember(player) { mutableStateOf(false) }
    val context = LocalContext.current
    var isWifi by remember(context) { mutableStateOf(NetUtils.isWifi(context)) }
    val current = tracks.getOrNull(currentIndex)
    val subtitleHost = remember(context) { FrameLayout(context) }
    val subtitleSession = remember(player, subtitleHost, current?.fileUrl) {
        VideoRealtimeSubtitleSession(
            context,
            subtitleHost,
            {
                screeningSubtitleMediaUrl(
                    player.currentMediaItem?.localConfiguration?.uri?.toString(),
                    current?.fileUrl,
                )
            },
            null,
            object : VideoRealtimeSubtitleSession.PlaybackProbe {
                override fun isPlaying(): Boolean = player.isPlaying
                override fun positionMs(): Long = player.currentPosition.coerceAtLeast(0L)
            },
        )
    }
    val subtitlePresenter = remember(subtitleSession) {
        StreamingSubtitleOverlayPresenter().apply { setSession(subtitleSession) }
    }
    var subtitlesVisible by remember(subtitlePresenter) { mutableStateOf(false) }
    LaunchedEffect(current?.fileUrl, subtitlePresenter) {
        if (subtitlePresenter.isVisible) subtitlePresenter.detach()
        subtitlesVisible = false
    }
    DisposableEffect(subtitlePresenter) {
        onDispose { subtitlePresenter.detach() }
    }
    fun interruptPlaybackForNetwork(wifi: Boolean) {
        isWifi = wifi
        if (networkDialogOpen) return
        val shouldPrompt = shouldPromptForSoundNetwork(
            isWifi = wifi,
            soundSwitch = PreferenceUtils.getPrefBoolean(context, Constants.SOUND_SWITCH, SOUND_SWITCH_DEFAULT_ENABLED),
            sessionOverride = networkOverride,
            isPlaying = player.isPlaying,
        )
        if (shouldPrompt) {
            player.pause()
            pendingNetworkTrack = -1
            resumeAfterNetworkConfirmation = true
            networkDialogOpen = true
        }
    }
    val latestInterruptPlaybackForNetwork by rememberUpdatedState<(Boolean) -> Unit>({ wifi ->
        interruptPlaybackForNetwork(wifi)
    })
    DisposableEffect(context, player) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainExecutor.execute { latestInterruptPlaybackForNetwork(NetUtils.isWifi(context)) }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                mainExecutor.execute { latestInterruptPlaybackForNetwork(wifi) }
            }

            override fun onLost(network: Network) {
                mainExecutor.execute { latestInterruptPlaybackForNetwork(NetUtils.isWifi(context)) }
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }
    LaunchedEffect(player, playbackSpeed) {
        player.setPlaybackParameters(PlaybackParameters(playbackSpeed, 1f))
    }
    fun playAt(index: Int) {
        val track = tracks.getOrNull(index) ?: return
        pendingNetworkTrack = -1
        playbackEnded = false
        pendingManualNextFromCount = -1
        onSelect(index)
        player.setMediaItem(screeningAudioMediaItem(track.title, track.subtitle, track.fileUrl))
        player.prepare()
        player.play()
    }
    fun playNextManually() {
        playbackEnded = false
        if (currentIndex == tracks.lastIndex && pager.hasMore) {
            pendingManualNextFromCount = tracks.size
            if (!pager.loading) pager.loadMore()
            return
        }
        manualNextSoundTrackIndex(playbackMode, currentIndex, tracks.size)?.let(::playAt)
    }
    fun playPreviousManually() {
        previousSoundTrackIndex(currentIndex, tracks.size)?.let(::playAt)
    }
    fun togglePlayback() {
        if (player.isPlaying) player.pause() else player.play()
    }
    val latestPlayAt by rememberUpdatedState<(Int) -> Unit>({ index -> playAt(index) })
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) playbackEnded = true
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) interruptPlaybackForNetwork(isWifi)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(playbackEnded, pendingManualNextFromCount, tracks, currentIndex, playbackMode, pager.hasMore, pager.loading) {
        if (pendingManualNextFromCount >= 0) {
            if (pager.loading) return@LaunchedEffect
            if (tracks.size > pendingManualNextFromCount) {
                val nextIndex = manualNextSoundTrackIndex(playbackMode, currentIndex, tracks.size)
                pendingManualNextFromCount = -1
                if (nextIndex != null) latestPlayAt(nextIndex)
            } else if (pager.hasMore) {
                pager.loadMore()
            } else {
                pendingManualNextFromCount = -1
            }
            return@LaunchedEffect
        }
        if (!playbackEnded) return@LaunchedEffect
        if (playbackMode != SoundPlaybackMode.SINGLE && currentIndex == tracks.lastIndex && pager.hasMore) {
            if (!pager.loading) pager.loadMore()
            return@LaunchedEffect
        }
        val nextIndex = nextSoundTrackIndex(playbackMode, currentIndex, tracks.size)
        if (nextIndex == null) playbackEnded = false else latestPlayAt(nextIndex)
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
    var isFavorite by remember(current?.fileUrl) { mutableStateOf(false) }
    LaunchedEffect(current?.fileUrl) {
        val fileUrl = current?.fileUrl ?: return@LaunchedEffect
        FavoriteUtil.getFavoriteAsync(context, fileUrl) { favorite -> isFavorite = favorite != null }
    }
    fun toggleFavorite() {
        val track = current ?: return
        FavoriteUtil.modLocalFavoritesAsync(context, track.toSoundFavorite()) {
            FavoriteUtil.getFavoriteAsync(context, track.fileUrl) { favorite -> isFavorite = favorite != null }
        }
    }
    // 列表外壳自带安全区；外层 Column 不再垫 insets（会双重），播放条自己贴底。
    Box(Modifier.fillMaxSize()) {
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
                    TextButton(onClick = { onPlaybackModeChange(playbackMode.nextMode()) }) {
                        Text(playbackMode.label, color = Mint, fontSize = 12.sp)
                    }
                    TextButton(onClick = { speedDialogOpen = true }) {
                        Text("${playbackSpeed}×", color = Mint, fontSize = 12.sp)
                    }
                    IconButton(onClick = { toggleFavorite() }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "取消收藏" else "收藏",
                            tint = if (isFavorite) Peach else White,
                        )
                    }
                    TextButton(onClick = {
                        subtitlePresenter.setPlaybackPositionMs(player.currentPosition.coerceAtLeast(0L))
                        subtitlesVisible = subtitlePresenter.toggle()
                    }) {
                        Text(if (subtitlesVisible) "字幕中" else "字幕", color = if (subtitlesVisible) Mint else White, fontSize = 12.sp)
                    }
                    IconButton(onClick = { playPreviousManually() }) {
                        Icon(Icons.Default.SkipPrevious, "上一首", tint = White)
                    }
                    IconButton(onClick = { togglePlayback() }) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "播放", tint = Mint, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { playNextManually() }) {
                        Icon(Icons.Default.SkipNext, "下一首", tint = White)
                    }
                }
            }
        }
        }
        AndroidView(
            factory = { subtitleHost.apply { isClickable = false; isFocusable = false } },
            modifier = Modifier.fillMaxSize(),
        )
    }
    if (speedDialogOpen) {
        AlertDialog(
            onDismissRequest = { speedDialogOpen = false },
            title = { Text("播放倍速") },
            text = {
                Column {
                    Text("${playbackSpeed}×", color = White)
                    Slider(
                        value = playbackSpeed,
                        onValueChange = onPlaybackSpeedChange,
                        valueRange = 0.25f..3f,
                        steps = 54,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { speedDialogOpen = false }) { Text("完成") }
            },
        )
    }
    if (networkDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                networkDialogOpen = false
                pendingNetworkTrack = -1
                resumeAfterNetworkConfirmation = false
            },
            title = { Text("非 Wi-Fi 网络播放音频？") },
            text = { Text("当前使用移动网络，继续播放可能消耗流量。") },
            dismissButton = {
                TextButton(onClick = {
                    networkDialogOpen = false
                    pendingNetworkTrack = -1
                    resumeAfterNetworkConfirmation = false
                }) { Text("取消") }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trackIndex = pendingNetworkTrack
                    val shouldResume = resumeAfterNetworkConfirmation
                    networkOverride = true
                    networkDialogOpen = false
                    pendingNetworkTrack = -1
                    resumeAfterNetworkConfirmation = false
                    if (shouldResume) player.play() else if (trackIndex >= 0) playAt(trackIndex)
                }) { Text("继续播放") }
            },
        )
    }
}
