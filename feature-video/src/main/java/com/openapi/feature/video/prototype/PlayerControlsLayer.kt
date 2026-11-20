package com.openapi.feature.video.prototype

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.feature.video.R
import kotlin.math.roundToInt

/**
 * 新播放页控件层，逐项对齐 `docs/design/player-tools-preview.svg` 的 01 分区：
 * 顶部「‹ 标题 …… 锁屏 ⋯」，中部「↶ ▶ ↷」，底部「细进度条 / 时间 · 全屏 / 四个工具芯片 / 右下 Favor」。
 *
 * 所有位置由窗口 inset 推算，横竖屏是同一份组合，不存在“退出全屏后标题错位”。
 */
internal object PlayerPalette {
    val Mint = Color(0xFFA5E7CB)
    val MintSoft = Color(0xFFC3F3DC)
    val OnMint = Color(0xFF132A21)
    val Surface = Color(0xFF222A30)
    val SurfaceActive = Color(0xFF2C3A3F)
    val Track = Color(0xFF657C82)
    val Scrim = Color(0xFF0B1013)
    val TextPrimary = Color(0xFFF0F4F2)
    val TextSecondary = Color(0xFFAAB9B7)
}

internal data class PlayerControlsState(
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val buffering: Boolean,
    val controlsVisible: Boolean,
    val landscape: Boolean,
    val locked: Boolean,
    val fullscreen: Boolean,
    val subtitleText: String,
    val subtitleEnabled: Boolean,
    val subtitleDelayMs: Int,
    val speed: Float,
    val danmakuOn: Boolean,
    val speedLabel: String,
)

internal data class PlayerControlsActions(
    val onBack: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onRewind: () -> Unit,
    val onForward: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onSeekCommit: (Long) -> Unit,
    val onToggleFullscreen: () -> Unit,
    val onToggleControls: () -> Unit,
    val onLockToggle: () -> Unit,
    val onSubtitles: () -> Unit,
    val onSpeed: () -> Unit,
    val onDanmaku: () -> Unit,
    val onMore: () -> Unit,
    val onFavorite: () -> Unit,
    val onCast: () -> Unit,
)

/** state 为 null 表示播放器尚未就绪，只画一层黑底，避免闪出 GSY 的默认界面。 */
@Composable
internal fun PlayerControlsHost(
    state: PlayerControlsState?,
    actions: PlayerControlsActions,
) {
    if (state == null) {
        Box(Modifier.fillMaxSize().background(PlayerPalette.Scrim))
        return
    }
    PlayerControlsLayer(state, actions)
}

@Composable
internal fun PlayerControlsLayer(
    state: PlayerControlsState,
    actions: PlayerControlsActions,
) {
    val bars = WindowInsets.statusBars.asPaddingValues()
    val nav = WindowInsets.navigationBars.asPaddingValues()
    val topInset = bars.calculateTopPadding()
    val bottomInset = nav.calculateBottomPadding()
    // 横屏刘海和手势区在左右两侧，交给系统栏内边距，标题与芯片才不会被挖孔切掉
    val leftInset = bars.calculateLeftPadding(LayoutDirection.Ltr)
    val rightInset = bars.calculateRightPadding(LayoutDirection.Ltr)
    val showChrome = state.controlsVisible && !state.locked

    Box(Modifier.fillMaxSize()) {
        if (state.controlsVisible || !state.playing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to PlayerPalette.Scrim.copy(alpha = 0.82f),
                            0.28f to Color.Transparent,
                            0.60f to Color.Transparent,
                            1f to PlayerPalette.Scrim.copy(alpha = 0.92f),
                        )
                    )
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = actions.onToggleControls,
                    )
            )
        }

        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            TopBar(
                title = state.title,
                landscape = state.landscape,
                leftInset = leftInset,
                rightInset = rightInset,
                topInset = topInset,
                onBack = actions.onBack,
                onLock = actions.onLockToggle,
                onMore = actions.onMore,
            )
        }

        if (state.locked) {
            LockedPill(
                topInset = topInset,
                leftInset = leftInset,
                onUnlock = actions.onLockToggle,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        if (showChrome) {
            Transport(
                playing = state.playing,
                onRewind = actions.onRewind,
                onTogglePlay = actions.onTogglePlay,
                onForward = actions.onForward,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            BottomSection(
                state = state,
                actions = actions,
                bottomInset = bottomInset,
                leftInset = leftInset,
                rightInset = rightInset,
            )
        }

        // 字幕占底部、弹幕避让字幕：字幕始终在控件条之上、底部安全区之下
        if (state.subtitleText.isNotEmpty()) {
            Text(
                text = state.subtitleText,
                color = PlayerPalette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 24.dp + leftInset,
                        end = 24.dp + rightInset,
                        bottom = bottomInset + if (showChrome) 176.dp else 48.dp,
                    ),
            )
        }

        if (state.buffering) {
            CircularProgressIndicator(
                color = PlayerPalette.Mint,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )
        }
    }
}

/** 顶部：返回 + 标题（设计稿 `‹   云层之上 …… 锁屏 ⋯`）。 */
@Composable
private fun TopBar(
    title: String,
    landscape: Boolean,
    leftInset: androidx.compose.ui.unit.Dp,
    rightInset: androidx.compose.ui.unit.Dp,
    topInset: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onLock: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp + leftInset, end = 12.dp + rightInset, top = topInset + 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.np_back), onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", color = PlayerPalette.TextPrimary, fontSize = 28.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = PlayerPalette.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (landscape) {
            Text(
                text = stringResource(R.string.np_lock_short),
                color = PlayerPalette.TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onLock)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        Text(
            text = "⋯",
            color = PlayerPalette.TextPrimary,
            fontSize = 16.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onMore)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LockedPill(
    topInset: androidx.compose.ui.unit.Dp,
    leftInset: androidx.compose.ui.unit.Dp,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.np_unlock),
        color = PlayerPalette.OnMint,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .padding(top = topInset + 10.dp, start = 16.dp + leftInset)
            .clip(RoundedCornerShape(20.dp))
            .background(PlayerPalette.MintSoft)
            .clickable(onClick = onUnlock)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** 中部：快退 / 播放暂停 / 快进（设计稿 `↶   ▶   ↷`）。 */
@Composable
private fun Transport(
    playing: Boolean,
    onRewind: () -> Unit,
    onTogglePlay: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(label = "↶", description = stringResource(R.string.np_rewind), onClick = onRewind)
        PlayButton(playing = playing, onClick = onTogglePlay)
        TransportButton(label = "↷", description = stringResource(R.string.np_forward), onClick = onForward)
    }
}

@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(PlayerPalette.Surface.copy(alpha = 0.72f))
            .clickable(
                onClickLabel = stringResource(if (playing) R.string.np_pause else R.string.np_play),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (playing) "❚❚" else "▶",
            color = PlayerPalette.TextPrimary,
            fontSize = if (playing) 20.sp else 26.sp,
        )
    }
}

@Composable
private fun TransportButton(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = PlayerPalette.TextPrimary, fontSize = 26.sp)
    }
}

@Composable
private fun BottomSection(
    state: PlayerControlsState,
    actions: PlayerControlsActions,
    bottomInset: androidx.compose.ui.unit.Dp,
    leftInset: androidx.compose.ui.unit.Dp,
    rightInset: androidx.compose.ui.unit.Dp,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp + leftInset, end = 16.dp + rightInset, bottom = bottomInset + 12.dp),
    ) {
        ProgressBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            onSeek = actions.onSeek,
            onSeekCommit = actions.onSeekCommit,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}",
                color = PlayerPalette.TextPrimary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(
                    if (state.fullscreen) R.string.np_exit_fullscreen else R.string.np_fullscreen
                ),
                color = PlayerPalette.TextPrimary,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = actions.onToggleFullscreen)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolChip(
                label = stringResource(R.string.np_subtitles),
                active = state.subtitleEnabled,
                onClick = actions.onSubtitles,
                modifier = Modifier.weight(1f),
            )
            ToolChip(
                label = stringResource(R.string.np_speed, state.speedLabel),
                active = false,
                onClick = actions.onSpeed,
                modifier = Modifier.weight(1f),
            )
            ToolChip(
                label = stringResource(R.string.np_danmaku),
                active = state.danmakuOn,
                onClick = actions.onDanmaku,
                modifier = Modifier.weight(1f),
            )
            ToolChip(
                label = stringResource(R.string.np_more),
                active = false,
                onClick = actions.onMore,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FavorChip(
                favoriteLabel = stringResource(R.string.np_favorite),
                castLabel = stringResource(R.string.np_cast),
                onFavorite = actions.onFavorite,
                onCast = actions.onCast,
            )
        }
    }
}

/**
 * 细进度条：设计稿是 4 单位高的圆角线 + 圆点，Material Slider 的拇指和水波纹都过重，这里自绘。
 * 拖动期间由本地值驱动，松手才 seek；播放器 250 ms 才推一次位置，直连 state 会拖不动。
 */
@Composable
private fun ProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onSeekCommit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    var trackWidthPx by remember { mutableStateOf(1f) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = (dragFraction ?: (positionMs.toFloat() / safeDuration)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(safeDuration) {
                detectTapGestures { offset ->
                    val target = (offset.x / trackWidthPx).coerceIn(0f, 1f) * safeDuration
                    onSeek(target.toLong())
                    onSeekCommit(target.toLong())
                }
            }
            .pointerInput(safeDuration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragFraction?.let { onSeekCommit((it * safeDuration).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                )
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(2.dp))
                .background(PlayerPalette.Track)
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(2.dp))
                .background(PlayerPalette.Mint)
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((trackWidthPx * fraction).roundToInt() - 6, 0) }
                .size(12.dp)
                .clip(CircleShape)
                .background(PlayerPalette.Mint)
        )
    }
}

@Composable
private fun FavorChip(
    favoriteLabel: String,
    castLabel: String,
    onFavorite: () -> Unit,
    onCast: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PlayerPalette.MintSoft)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FavorItem(label = favoriteLabel, onClick = onFavorite)
        Box(
            Modifier
                .width(1.dp)
                .height(20.dp)
                .background(PlayerPalette.OnMint.copy(alpha = 0.18f))
        )
        FavorItem(label = castLabel, onClick = onCast)
    }
}

@Composable
private fun FavorItem(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = PlayerPalette.OnMint,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun ToolChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) PlayerPalette.SurfaceActive else PlayerPalette.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) PlayerPalette.Mint else PlayerPalette.TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000
    val hours = total / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, (total % 3600) / 60, total % 60)
    } else {
        "%02d:%02d".format(total / 60, total % 60)
    }
}
