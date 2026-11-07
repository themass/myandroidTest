package com.openapi.chat.presentation.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import android.webkit.MimeTypeMap
import com.bumptech.glide.Glide
import com.openapi.core.design.theme.AppColors
import com.openapi.core.media.AudioPlayer
import com.openapi.ks.chat.R
import java.io.File

/**
 * 按住说话按钮。
 * - 按下开始录音，按钮变为主色并向外扩散波纹；
 * - 松开发送；手指上滑超过阈值后变为「取消」状态，松开则丢弃；
 * - 状态通过 [onCancelingChange] 回传，由输入栏显示提示文字。
 */
@Composable
internal fun VoiceHoldButton(
    recording: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onCancelingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cancelThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    Box(
        modifier = modifier
            .size(48.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onStart()
                    var canceling = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        val nowCanceling = change.position.y - down.position.y < -cancelThresholdPx
                        if (nowCanceling != canceling) {
                            canceling = nowCanceling
                            onCancelingChange(canceling)
                        }
                        if (change.changedToUpIgnoreConsumed()) break
                    }
                    onCancelingChange(false)
                    if (canceling) onCancel() else onStop()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (recording) RippleWaves()
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (recording) AppColors.Primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = stringResource(R.string.chat_hold_to_talk_desc),
                tint = if (recording) Color.White else IconTintForVoice,
            )
        }
    }
}

private val IconTintForVoice = AppColors.TextSecondary

/** 三圈错开的扩散波纹，从麦克风按钮向外扩散并淡出。 */
@Composable
private fun RippleWaves() {
    val transition = rememberInfiniteTransition(label = "voice-ripple")
    listOf(0, 600, 1200).forEachIndexed { index, delay ->
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 2.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, delayMillis = delay, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "ripple-scale-$index",
        )
        val rippleAlpha by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, delayMillis = delay, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "ripple-alpha-$index",
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = rippleAlpha
                }
                .clip(CircleShape)
                .background(AppColors.Primary),
        )
    }
}

/** 语音条里的音量柱：播放时起伏，停止时是静态的波形。 */
@Composable
internal fun VoiceWaveBars(playing: Boolean, tint: Color) {
    val baseHeights = listOf(0.45f, 0.8f, 1f, 0.6f)
    val transition = rememberInfiniteTransition(label = "voice-bars")
    val animated = baseHeights.mapIndexed { index, _ ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 360 + index * 130, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar-$index",
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        baseHeights.forEachIndexed { index, base ->
            val factor = if (playing) animated[index].value else 1f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((4 + 14 * base * factor).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tint),
            )
            if (index != baseHeights.lastIndex) Spacer(Modifier.width(3.dp))
        }
    }
}

/** 语音条：点击播放或停止，播放中音量柱起伏。 */
@Composable
internal fun VoiceBubbleRow(
    messageId: String,
    durationSeconds: Int,
    transcript: String?,
    onClick: () -> Unit,
) {
    val playback by AudioPlayer.state.collectAsState()
    val playing = playback.playing && playback.mediaId == messageId
    androidx.compose.foundation.layout.Column {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoiceWaveBars(playing = playing, tint = Color.Black)
            Spacer(Modifier.width(10.dp))
            androidx.compose.material3.Text(
                "${durationSeconds}″",
                fontSize = com.openapi.core.design.theme.AppTypography.bodyLarge,
                color = Color.Black,
            )
        }
        if (transcript != null) {
            androidx.compose.material3.Text(
                transcript,
                fontSize = com.openapi.core.design.theme.AppTypography.caption,
                color = AppColors.TextSecondary,
            )
        }
    }
}

/** 图片点击后全屏查看，点任意位置关闭。 */
@Composable
internal fun FullScreenImageDialog(path: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                },
                update = { view -> Glide.with(view.context).load(File(path)).into(view) },
            )
        }
    }
}

/** 用系统应用打开附件（附件保存在应用私有目录，通过 FileProvider 授权）。 */
internal fun openAttachment(context: Context, path: String, name: String) {
    try {
        val file = File(path)
        val uri = FileProvider.getUriForFile(context, "com.openapi.ks.chat.fileprovider", file)
        val extension = name.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.chat_open_file_failed), Toast.LENGTH_SHORT).show()
    } catch (e: IllegalArgumentException) {
        Toast.makeText(context, context.getString(R.string.chat_open_file_failed), Toast.LENGTH_SHORT).show()
    }
}

/** 语音模式下的「按住说话」条：全宽，按下时变色并向外扩散波纹，上滑超过 80dp 变为取消。 */
@Composable
internal fun VoicePressBar(
    modifier: Modifier,
    recording: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onCancelingChange: (Boolean) -> Unit,
) {
    val cancelThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (recording) AppColors.Primary.copy(alpha = 0.12f) else AppColors.Surface)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onStart()
                    var canceling = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        val nowCanceling = change.position.y - down.position.y < -cancelThresholdPx
                        if (nowCanceling != canceling) {
                            canceling = nowCanceling
                            onCancelingChange(canceling)
                        }
                        if (change.changedToUpIgnoreConsumed()) break
                    }
                    onCancelingChange(false)
                    if (canceling) onCancel() else onStop()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (recording) RippleWaves()
        androidx.compose.material3.Text(
            stringResource(if (recording) R.string.chat_release_to_send else R.string.chat_hold_to_talk),
            fontSize = com.openapi.core.design.theme.AppTypography.bodyLarge,
            color = if (recording) AppColors.Primary else Color.Black,
        )
    }
}
