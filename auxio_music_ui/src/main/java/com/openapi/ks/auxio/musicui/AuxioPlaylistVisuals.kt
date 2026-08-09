package com.openapi.ks.auxio.musicui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.util.Locale

private fun formatTrackMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}

private fun MusicTrack.rowTitle(): String = title.ifBlank { mediaUrl }

private fun auxioPlayModeIcon(mode: Int): ImageVector = when (mode) {
    1 -> Icons.Filled.RepeatOne
    2 -> Icons.Filled.Repeat
    4 -> Icons.Filled.Shuffle
    else -> Icons.Filled.QueueMusic
}

@Composable
private fun auxioPlayModeContentDescription(mode: Int): String = when (mode) {
    1 -> stringResource(R.string.auxio_play_mode_one)
    2 -> stringResource(R.string.auxio_play_mode_all)
    4 -> stringResource(R.string.auxio_play_mode_shuffle)
    else -> stringResource(R.string.auxio_play_mode_list)
}

/** Auxio 列表「正在播」：黑条、底对齐、左高中低 + 正弦起伏（对齐官方列表缩略图）。 */
@Composable
private fun AuxioPlayingEqualizer(
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    val t = rememberInfiniteTransition(label = "auxio_eq")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = (2 * kotlin.math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = LinearEasing),
        ),
        label = "phase",
    )
    val baseRatio = floatArrayOf(1f, 0.52f, 0.26f)
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        repeat(3) { i ->
            val wave = (0.5f + 0.5f * kotlin.math.sin(phase + i * 1.25f)).coerceIn(0f, 1f)
            val innerH = 28f
            val barH = (innerH * baseRatio[i] * (0.38f + 0.62f * wave)).coerceIn(4f, innerH)
            Box(
                Modifier
                    .width(3.5.dp)
                    .height(barH.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor),
            )
        }
    }
}

/**
 * 列表行左侧：封面 / 静态音符；**当前曲且正在播放**时显示 Auxio 式三竖条频谱（与原版一致）。
 */
@Composable
internal fun AuxioTrackLeadingThumb(
    artworkUrl: String?,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
) {
    val shape = RoundedCornerShape(8.dp)
    val containerColor = when {
        isCurrentTrack && isPlaying -> MaterialTheme.colorScheme.primaryContainer
        isCurrentTrack -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val barColor = Color.Black
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isCurrentTrack && isPlaying -> {
                AuxioPlayingEqualizer(barColor = barColor, modifier = Modifier.fillMaxSize())
            }
            !artworkUrl.isNullOrEmpty() -> {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

/**
 * 与宿主 [layout_sound_status_auxio] 一致的结构：主行 → 细进度条 → 时间行；含上一首 / 下一首。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AuxioMiniPlayerStatusBar(
    track: MusicTrack?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    playMode: Int,
    onBarClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onCyclePlayMode: () -> Unit,
    onSliderDragChange: (Float) -> Unit,
    onSliderDragFinished: () -> Unit,
    sliderValue: Float,
) {
    val topShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = topShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBarClick,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AuxioTrackLeadingThumb(
                        artworkUrl = track?.artworkUrl,
                        isCurrentTrack = true,
                        isPlaying = isPlaying,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track?.rowTitle().orEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 600,
                                    repeatDelayMillis = 1200,
                                ),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = track?.lineSubtitle().orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCyclePlayMode, modifier = Modifier.size(40.dp)) {
                        Icon(
                            auxioPlayModeIcon(playMode),
                            contentDescription = stringResource(
                                R.string.auxio_play_mode_button_cd,
                                auxioPlayModeContentDescription(playMode),
                            ),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = onSkipPrev, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_auxio_skip_previous),
                            contentDescription = stringResource(R.string.auxio_prev),
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) {
                                stringResource(R.string.auxio_pause)
                            } else {
                                stringResource(R.string.auxio_play)
                            },
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    IconButton(onClick = onSkipNext, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_auxio_skip_next),
                            contentDescription = stringResource(R.string.auxio_next),
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Slider(
                value = sliderValue,
                onValueChange = onSliderDragChange,
                onValueChangeFinished = onSliderDragFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTrackMs(positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatTrackMs(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun AuxioSongRowOverflowMenu(
    onPlay: () -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.auxio_row_menu_cd),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.auxio_row_play)) },
                onClick = {
                    expanded = false
                    onPlay()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.auxio_row_now_playing)) },
                onClick = {
                    expanded = false
                    onOpenNowPlaying()
                },
            )
        }
    }
}

private fun MusicTrack.lineSubtitle(): String {
    val host = try {
        android.net.Uri.parse(mediaUrl).host
    } catch (_: Throwable) {
        null
    }
    return when {
        !host.isNullOrBlank() -> host
        mediaUrl.length > 52 -> mediaUrl.take(49) + "…"
        else -> mediaUrl
    }
}
