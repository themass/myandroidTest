package com.openapi.feature.video.screening

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.URI
import android.os.SystemClock
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
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.openapi.common.util.android.ToastUtil
import com.openapi.speech.asr.VolcengineAsrConfig
import com.openapi.feature.video.subtitle.local.ScreeningCaption
import com.openapi.feature.video.subtitle.style.CaptionOverlay
import com.openapi.feature.video.subtitle.style.CaptionPosition
import com.openapi.feature.video.subtitle.style.captionPresets
import java.io.StringReader
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
        FeedPager(scope) { start, _ -> store.soundChannelHub(hubChannel ?: "", start) }
    }
    val hubParam = hub
    val tracksPager = remember(hubParam) {
        FeedPager(scope) { start, keyword -> store.soundTracks(hubParam ?: "", start, keyword) }
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
    var playerConnectionFailed by remember { mutableStateOf(false) }
    var playerConnectionAttempt by remember { mutableIntStateOf(0) }
    DisposableEffect(context, playerConnectionAttempt) {
        player = null
        playerConnectionFailed = false
        val token = SessionToken(context, ComponentName(context, ScreeningAudioService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        var disposed = false
        future.addListener(
            {
                if (!disposed) {
                    val connectedPlayer = runCatching { future.get() }.getOrNull()
                    if (connectedPlayer == null) {
                        playerConnectionFailed = true
                    } else {
                        player = connectedPlayer
                    }
                }
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
                // 曲目列表不再被播放器连接挡住（用户点名 2026-11-25：「进入具体的声音馆的音乐列表
                // 没有加载出来」——2026-11-25 取证：items.json 请求与参数逐一对、服务端 30 行真数据，
                // 但 player==null 时整屏被换成「正在连接播放器…」，列表请求就算成功也看不见）。
                // 浏览与播放解耦：列表照常渲染、照常搜索；播放器状态只在顶部一条状态带里说明，
                // 连接失败给重试钮；点行播放时还没连上就按状态带提示（不静默）。
                SoundTrackHub(
                    store = store,
                    pager = tracksPager,
                    state = tracksState,
                    seen = trackSeen,
                    tracks = tracks,
                    player = player,
                    playerConnecting = soundPlayerConnectionState(hasPlayer = player != null, failed = playerConnectionFailed) == SoundPlayerConnectionState.CONNECTING,
                    playerFailed = playerConnectionFailed,
                    onRetryPlayer = { playerConnectionAttempt++ },
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

internal fun RemoteEntry.soundListKey(): String? =
    param?.takeIf { it.isNotBlank() } ?: channelParam

internal data class SoundTrackRef(
    val index: Int,
    val title: String,
    val fileUrl: String,
    val subtitle: String,
    /** 服务端条目 id：歌词按它挂轨；老服务端没透出 id 的条目为 null。 */
    val serverId: Long? = null,
)

/**
 * 声音馆字幕数据源三级阶梯（契约 docs/design/sound-lyrics-contract-20261124.md §3.3）：
 * 服务端歌词 SRT → 实时 ASR（片⑥兜底）→ 无字幕。
 */
internal enum class SoundCaptionSource { SERVER, REALTIME, NONE }

internal fun soundCaptionSource(
    serverTrackLoaded: Boolean,
    realtimeConfigured: Boolean,
    urlUsable: Boolean,
): SoundCaptionSource = when {
    serverTrackLoaded -> SoundCaptionSource.SERVER
    realtimeConfigured && urlUsable -> SoundCaptionSource.REALTIME
    else -> SoundCaptionSource.NONE
}

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

internal enum class SoundPlayerConnectionState { CONNECTING, CONNECTED, FAILED }

internal fun soundPlayerConnectionState(hasPlayer: Boolean, failed: Boolean): SoundPlayerConnectionState = when {
    hasPlayer -> SoundPlayerConnectionState.CONNECTED
    failed -> SoundPlayerConnectionState.FAILED
    else -> SoundPlayerConnectionState.CONNECTING
}

internal fun shouldPromptForSoundNetwork(
    isWifi: Boolean,
    soundSwitch: Boolean,
    sessionOverride: Boolean,
    isPlaying: Boolean,
): Boolean = isPlaying && !isWifi && !soundSwitch && !sessionOverride

internal fun screeningSubtitleMediaUrl(currentMediaUrl: String?, selectedTrackUrl: String?): String =
    currentMediaUrl?.takeIf { it.isNotBlank() } ?: selectedTrackUrl.orEmpty()

internal fun screeningSoundRealtimeUrl(currentMediaUrl: String?, selectedTrackUrl: String?): String? {
    val candidate = currentMediaUrl?.takeIf { it.isNotBlank() } ?: selectedTrackUrl?.takeIf { it.isNotBlank() }
        ?: return null
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    return when (uri.scheme?.lowercase()) {
        "http", "https" -> candidate
        "file" -> candidate.takeIf { uri.path?.startsWith("/") == true }
        null -> candidate.takeIf { candidate.startsWith("/") }
        else -> null
    }
}

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
    return SoundTrackRef(listIndex, title, actionUrl, description, serverId)
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
            // 用户 20261125：首行大字不许硬折行、不许没用的文字。单句、限一行（台账 A29）。
            Text("戴上耳机，世界慢下来。", color = White, fontSize = 24.sp, lineHeight = 32.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
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
        item { FeedEndRow(pager.loading, pager.hasMore, pager::loadMore,
            pager.loading && rows.isEmpty(), empty = rows.isEmpty()) }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun SoundTrackHub(
    store: ScreeningFeedStore,
    pager: FeedPager,
    state: LazyListState,
    seen: MutableSet<String>,
    tracks: List<SoundTrackRef>,
    /**
     * 播放器控制器；null = 还没连上（连接中或失败）。列表渲染不认它，只有播放动作认它——
     * 所有播控走 [withPlayer]。见调用点注释（用户点名「音乐列表没有加载出来」）。
     */
    player: Player?,
    /** 连接仍在路上：顶部状态带显示「正在连接播放器…」。 */
    playerConnecting: Boolean,
    /** 连接失败过：状态带给「重试」。 */
    playerFailed: Boolean,
    onRetryPlayer: () -> Unit,
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
    // 播控统一闸：player 没连上时不静默吞掉点击——状态带就在顶上，点行会把它指过去。
    fun withPlayer(action: (Player) -> Unit) {
        val active = player ?: return
        action(active)
    }
    val subtitleUrl = screeningSoundRealtimeUrl(
        player?.currentMediaItem?.localConfiguration?.uri?.toString(),
        current?.fileUrl,
    )
    val subtitleRunner = remember(context) { RealtimeSubtitleRunner(context) }
    var subtitlesVisible by remember(subtitleRunner) { mutableStateOf(false) }
    var subtitleText by remember(subtitleRunner) { mutableStateOf("") }
    var subtitleStatus by remember(subtitleRunner) { mutableStateOf("") }
    // 服务端歌词（契约 sound-lyrics-contract §2/§3）：条目有 id 才探轨；无轨 fire-and-forget
    // 触发按需生成，本次照常走实时 ASR——下次再进就有歌词。探轨失败≠无轨，不触发。
    val serverId = current?.serverId
    var lyricsTrack by remember(current?.fileUrl) { mutableStateOf<ScreeningCaption?>(null) }
    DisposableEffect(subtitleRunner) { onDispose { subtitleRunner.stop() } }
    LaunchedEffect(serverId) {
        lyricsTrack = null
        val id = serverId ?: return@LaunchedEffect
        val items = runCatching { store.soundSubtitles(id) }.getOrNull() ?: return@LaunchedEffect
        val first = items.firstOrNull()
        if (first == null) {
            runCatching { store.requestSoundSubtitleGeneration(id) }
            return@LaunchedEffect
        }
        val content = runCatching { store.soundSubtitleContent(first.id) }.getOrNull()
        if (content != null && looksLikeSubtitle(content)) {
            lyricsTrack = runCatching { ScreeningCaption(StringReader(content)) }.getOrNull()
        }
    }
    // 歌词轨在场：字幕开关打开后按播放位置取 cue（随进度高亮、seek 秒跟，无需重连）。
    val lyrics = lyricsTrack
    LaunchedEffect(subtitlesVisible, lyrics, player, current?.fileUrl) {
        if (!subtitlesVisible || lyrics == null) return@LaunchedEffect
        while (isActive) {
            val active = player ?: break
            subtitleText = lyrics.at(active.currentPosition.coerceAtLeast(0L), 0)
            delay(200)
        }
    }
    LaunchedEffect(current?.fileUrl, subtitleRunner) {
        subtitleRunner.stop()
        subtitlesVisible = false
        subtitleText = ""
        subtitleStatus = ""
    }
    LaunchedEffect(subtitlesVisible, subtitleUrl, player, playbackSpeed, lyrics) {
        subtitleRunner.stop()
        if (!subtitlesVisible) return@LaunchedEffect
        if (lyrics != null) return@LaunchedEffect // 服务端歌词优先——三级阶梯 SERVER > REALTIME
        val url = subtitleUrl ?: run {
            subtitleStatus = "当前片源无法用于实时识别。"
            return@LaunchedEffect
        }
        if (!VolcengineAsrConfig.load(context).isConfigured()) {
            subtitlesVisible = false
            ToastUtil.showShort(com.openapi.speech.R.string.volcano_asr_not_configured)
            return@LaunchedEffect
        }
        subtitleRunner.onLoading = { subtitleStatus = "正在连接识别服务…" }
        subtitleRunner.onText = { subtitleText = it; subtitleStatus = "" }
        subtitleRunner.onError = { subtitleStatus = "识别失败：$it" }
        subtitleRunner.onStopped = { if (subtitlesVisible) subtitleStatus = "识别已停止，可以重试。" }
        subtitleText = ""
        subtitleStatus = "正在连接识别服务…"
        val active = player ?: run {
            // 列表照常可浏览，字幕要真播放器才能取位置：状态带就在顶上，这里只说清原因。
            if (playerConnecting) subtitleStatus = "正在连接播放器，连上后可用字幕。"
            else if (playerFailed) subtitleStatus = "播放器没连上，先在上方重试。"
            return@LaunchedEffect
        }
        subtitleRunner.start(url, active.currentPosition.coerceAtLeast(0L), { active.isPlaying }, { active.currentPosition.coerceAtLeast(0L) })
        var previousPosition = active.currentPosition.coerceAtLeast(0L)
        var previousElapsed = SystemClock.elapsedRealtime()
        try {
            while (isActive) {
                delay(500)
                val position = active.currentPosition.coerceAtLeast(0L)
                val elapsed = SystemClock.elapsedRealtime()
                if (realtimeSeekJumped(previousPosition, position, previousElapsed, elapsed, playbackSpeed, active.isPlaying)) {
                    subtitleText = ""
                    subtitleRunner.start(url, position, { active.isPlaying }, { active.currentPosition.coerceAtLeast(0L) })
                }
                previousPosition = position
                previousElapsed = elapsed
            }
        } finally {
            subtitleRunner.stop()
        }
    }
    fun interruptPlaybackForNetwork(wifi: Boolean) {
        isWifi = wifi
        if (networkDialogOpen) return
        val active = player ?: return
        val shouldPrompt = shouldPromptForSoundNetwork(
            isWifi = wifi,
            soundSwitch = PreferenceUtils.getPrefBoolean(context, Constants.SOUND_SWITCH, SOUND_SWITCH_DEFAULT_ENABLED),
            sessionOverride = networkOverride,
            isPlaying = active.isPlaying,
        )
        if (shouldPrompt) {
            active.pause()
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
        player?.setPlaybackParameters(PlaybackParameters(playbackSpeed, 1f))
    }
    fun playAt(index: Int) {
        val track = tracks.getOrNull(index) ?: return
        // 没连上播放器：不改选中、不静默——状态带就在列表顶上，用户点行时看得见它。
        val active = player ?: return
        pendingNetworkTrack = -1
        playbackEnded = false
        pendingManualNextFromCount = -1
        onSelect(index)
        active.setMediaItem(screeningAudioMediaItem(track.title, track.subtitle, track.fileUrl))
        active.prepare()
        active.play()
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
        val active = player ?: return
        if (active.isPlaying) active.pause() else active.play()
    }
    val latestPlayAt by rememberUpdatedState<(Int) -> Unit>({ index -> playAt(index) })
    DisposableEffect(player) {
        val active = player ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) playbackEnded = true
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) interruptPlaybackForNetwork(isWifi)
            }
        }
        active.addListener(listener)
        onDispose { active.removeListener(listener) }
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
    val playing = player?.isPlaying == true
    LaunchedEffect(player) {
        while (isActive) {
            val active = player ?: break
            positionMs = active.currentPosition.coerceAtLeast(0L)
            durationMs = active.duration.coerceAtLeast(1L)
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
            // 搜索（用户点名）：对齐旧 SoundItemsMusicFragment——keyword 直传 sound/items.json。
            searchable = true,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            item {
                // 播放器状态带（解耦后唯一交代播放器的地方）：列表照常浏览，
                // 连不上时顶上一条说清「能看不能播」，失败给重试——不再整屏挡列表。
                if (player == null) {
                    Row(
                        Modifier.fillMaxWidth().clip(Tile).background(Lift)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (playerFailed) "播放器没连上，可以点列表浏览，播放暂不可用。"
                            else "正在连接播放器…列表可以先看。",
                            color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f),
                        )
                        if (playerFailed) TextButton(onClick = onRetryPlayer) { Text("重试") }
                    }
                    Spacer(Modifier.height(6.dp))
                }
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
            item { FeedEndRow(pager.loading, pager.hasMore, pager::loadMore, empty = tracks.isEmpty()) }
        }
        if (current != null) {
            // 播放条在壳之外（壳的安全区够不到它）：自己垫导航条，同旧写法——
            // 以前整页 Column 带 safeDrawingPadding，撤掉包装时这份底部安全区不能丢。
            Column(Modifier.fillMaxWidth().safeDrawingPadding().background(Lift).padding(14.dp)) {
                Text(current.title, color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Slider(
                    value = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                    onValueChange = { fraction -> withPlayer { it.seekTo((durationMs * fraction).toLong()) } },
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
                    TextButton(onClick = { subtitlesVisible = !subtitlesVisible }) {
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
        if (subtitleText.isNotEmpty()) {
            val subtitleStyle = captionPresets.first().style
            CaptionOverlay(
                subtitleText,
                subtitleStyle,
                Modifier.align(
                    when (subtitleStyle.position) {
                        CaptionPosition.BOTTOM -> Alignment.BottomCenter
                        CaptionPosition.MIDDLE -> Alignment.Center
                        CaptionPosition.TOP -> Alignment.TopCenter
                    },
                ).padding(horizontal = 12.dp, vertical = 20.dp),
            )
        }
        if (subtitlesVisible && subtitleStatus.isNotEmpty() && subtitleText.isEmpty()) {
            Text(subtitleStatus, Modifier.align(Alignment.BottomCenter).padding(16.dp), color = White, fontSize = 12.sp)
        }
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
                    if (shouldResume) withPlayer { it.play() } else if (trackIndex >= 0) playAt(trackIndex)
                }) { Text("继续播放") }
            },
        )
    }
}
