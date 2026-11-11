package com.openapi.feature.content.sound.compose

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

internal val TextPrimary = Color(0xFFECEFF4)
internal val TextSecondary = Color(0xFF9AA3B2)
internal val SeekInactive = Color(0x33FFFFFF)
internal val BarShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

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
