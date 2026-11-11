package com.openapi.ks.myapp.content.sound.compose

import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.openapi.feature.content.R
import kotlin.math.roundToInt

/** User actions from the detail screen; implemented by SoundDetailActivity. */
interface SoundDetailActions {
    fun onBackClicked()
    fun onPreviousClicked()
    fun onPlayPauseClicked()
    fun onNextClicked()
    /** Called when the user releases the seek bar; [positionMs] is the target progress. */
    fun onSeekFinished(positionMs: Int)
}

private val TextPrimary = Color(0xFFECEFF4)
private val TextSecondary = Color(0xFF9AA3B2)
private val SeekInactive = Color(0x33FFFFFF)
private val BarShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/**
 * Hosts [SoundDetailScreen] in [this] ComposeView. [favoriteBar] is the existing MyFavoriteView:
 * it stays a View because its translation overlay is attached to the activity's ViewGroup.
 */
fun ComposeView.setSoundDetailContent(
    state: SoundDetailUiState,
    favoriteBar: View,
    actions: SoundDetailActions,
) {
    setContent {
        SoundDetailScreen(state = state, favoriteBar = favoriteBar, actions = actions)
    }
}

@Composable
fun SoundDetailScreen(
    state: SoundDetailUiState,
    favoriteBar: View,
    actions: SoundDetailActions,
) {
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(onBack = { actions.onBackClicked() })

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_music_play_filled),
                contentDescription = stringResource(R.string.sound_detail_art_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(200.dp),
            )
            Text(
                text = state.trackTitle,
                color = TextPrimary,
                fontSize = 18.sp,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .basicMarquee(),
            )
            Text(
                text = stringResource(R.string.music_lyrics_translation_hint),
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            )
        }

        // Legacy: favorite bar sits above the status bar, aligned to the end.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(end = 22.dp, bottom = 10.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            AndroidView(factory = { favoriteBar })
        }

        StatusBar(state = state, actions = actions)
    }
}

@Composable
private fun DetailTopBar(onBack: () -> Unit) {
    val backDescription = stringResource(R.string.sound_detail_back)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .semantics { contentDescription = backDescription },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.sound_detail_back_symbol),
                color = TextPrimary,
                fontSize = 22.sp,
            )
        }
        Text(
            text = stringResource(R.string.sound_detail_title),
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
    }
}

@Composable
private fun StatusBar(state: SoundDetailUiState, actions: SoundDetailActions) {
    // While dragging, the thumb follows the finger; the service position is applied on release.
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val sliderMax = state.progressMax.coerceAtLeast(1).toFloat()
    val primary = colorResource(R.color.style_color_primary)

    Column(
        Modifier
            .fillMaxWidth()
            .shadow(12.dp, BarShape)
            .background(colorResource(R.color.sound_bar_surface), BarShape)
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Legacy behavior: the status title is never updated on this screen and stays "no selected".
            Text(
                text = stringResource(R.string.no_select),
                color = TextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
                    .basicMarquee(),
            )
            // Legacy behavior: play-mode button is visible but has no click handler on this screen.
            Image(
                painter = painterResource(R.drawable.ic_play_mode_order),
                contentDescription = stringResource(R.string.sound_play_mode_cycle),
                modifier = Modifier
                    .padding(end = 2.dp)
                    .size(40.dp)
                    .padding(8.dp),
            )
            TransportIcon(
                resId = R.drawable.ic_music_skip_previous,
                description = stringResource(R.string.sound_prev),
                size = 44.dp,
                padding = 10.dp,
                onClick = { actions.onPreviousClicked() },
            )
            TransportIcon(
                resId = if (state.playing) R.drawable.ic_music_pause_filled else R.drawable.ic_music_play_filled,
                description = stringResource(R.string.sound_play_pause),
                size = 48.dp,
                padding = 12.dp,
                modifier = Modifier.padding(horizontal = 4.dp),
                onClick = { actions.onPlayPauseClicked() },
            )
            TransportIcon(
                resId = R.drawable.ic_music_skip_next,
                description = stringResource(R.string.sound_next),
                size = 44.dp,
                padding = 10.dp,
                onClick = { actions.onNextClicked() },
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.widthIn(min = 42.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.preparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = primary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(text = state.timeText, color = TextSecondary, fontSize = 11.sp)
                }
            }
            Slider(
                value = (dragValue ?: state.progress.toFloat()).coerceIn(0f, sliderMax),
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    actions.onSeekFinished((dragValue ?: state.progress.toFloat()).roundToInt())
                    dragValue = null
                },
                valueRange = 0f..sliderMax,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = primary,
                    activeTrackColor = primary,
                    inactiveTrackColor = SeekInactive,
                ),
            )
            Box(
                Modifier.widthIn(min = 42.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = state.durationText,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun TransportIcon(
    resId: Int,
    description: String,
    size: Dp,
    padding: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Image(
        painter = painterResource(resId),
        contentDescription = description,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(padding),
    )
}
