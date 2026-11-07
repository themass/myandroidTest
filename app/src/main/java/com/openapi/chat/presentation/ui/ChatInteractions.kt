package com.openapi.chat.presentation.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import com.bumptech.glide.Glide
import com.openapi.core.design.theme.AppColors
import com.openapi.core.design.theme.AppTypography
import com.openapi.core.media.AudioPlayer
import com.openapi.core.media.VoiceRecorder
import com.openapi.ks.chat.R
import java.io.File

/**
 * 语音输入的三种样式，可在真机上切换对比。长按输入栏左侧的语音按钮循环切换，选择保存在本地。
 * - [FLOATING]（A）微信式：按住时在输入栏上方弹出浮层，里面是随音量跳动的竖条。
 * - [RIPPLE]（B）豆包式：按住时麦克风向外扩散波纹，波纹随音量变大；输入栏上方实时显示识别文字。
 * - [BAR]（C）录音条：整条按钮变成录音条，显示计时和声波，左滑取消。
 */
internal object VoiceStyle {
    const val FLOATING = 0
    const val RIPPLE = 1
    const val BAR = 2

    private const val PREFS = "chat_voice_style"
    private const val KEY = "style"

    fun load(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, RIPPLE)

    fun cycle(context: Context): Int {
        val next = (load(context) + 1) % 3
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, next).apply()
        return next
    }

    fun labelRes(style: Int): Int = when (style) {
        FLOATING -> R.string.chat_voice_style_floating
        BAR -> R.string.chat_voice_style_bar
        else -> R.string.chat_voice_style_ripple
    }
}

/** 录音时的音量历史，最多保留 [MAX] 条，给声波用。 */
@Composable
internal fun rememberVoiceLevelHistory(recording: Boolean): SnapshotStateList<Float> {
    val history = remember { androidx.compose.runtime.mutableStateListOf<Float>() }
    val level by VoiceRecorder.level.collectAsState()
    LaunchedEffect(level, recording) {
        if (recording) {
            history.add(level)
            if (history.size > VoiceLevelConfig.MAX) history.removeAt(0)
        } else {
            history.clear()
        }
    }
    return history
}

/**
 * 按住说话条。按下开始录音，松开发送；取消方向随样式不同：
 * - 样式 A、B：上滑超过 80dp 取消；
 * - 样式 C：左滑超过 80dp 取消。
 * [canceling] 与 [onCancelingChange] 由输入栏持有，用于切换提示文案。
 */
@Composable
internal fun VoicePressBar(
    modifier: Modifier,
    style: Int,
    recording: Boolean,
    canceling: Boolean,
    onCancelingChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    history: List<Float>,
) {
    val cancelThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    val horizontalCancel = style == VoiceStyle.BAR
    val level by VoiceRecorder.level.collectAsState()
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                when {
                    recording && canceling -> AppColors.Error.copy(alpha = 0.12f)
                    recording -> AppColors.Primary.copy(alpha = 0.12f)
                    else -> AppColors.Surface
                },
            )
            .pointerInput(style) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onStart()
                    var nowCanceling = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        val cancelNow = if (horizontalCancel) dx < -cancelThresholdPx else dy < -cancelThresholdPx
                        if (cancelNow != nowCanceling) {
                            nowCanceling = cancelNow
                            onCancelingChange(nowCanceling)
                        }
                        if (change.changedToUpIgnoreConsumed()) break
                    }
                    onCancelingChange(false)
                    if (nowCanceling) onCancel() else onStop()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            recording && style == VoiceStyle.BAR -> RecordingBarContent(canceling = canceling, history = history)
            recording -> {
                if (style == VoiceStyle.RIPPLE) RippleWaves(level = level)
                Text(
                    stringResource(if (canceling) R.string.chat_release_to_cancel else R.string.chat_release_to_send),
                    fontSize = AppTypography.bodyLarge,
                    color = if (canceling) AppColors.Error else AppColors.Primary,
                )
            }
            else -> Text(stringResource(R.string.chat_hold_to_talk), fontSize = AppTypography.bodyLarge, color = Color.Black)
        }
    }
}

/** 样式 C：录音条内容，左边计时，中间声波，右边提示左滑取消。 */
@Composable
private fun RecordingBarContent(canceling: Boolean, history: List<Float>) {
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        seconds = 0
        while (true) {
            kotlinx.coroutines.delay(1000)
            seconds += 1
        }
    }
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "0:%02d".format(seconds),
            fontSize = AppTypography.caption,
            color = if (canceling) AppColors.Error else AppColors.Primary,
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            VoiceLevelBars(history = history, tint = if (canceling) AppColors.Error else AppColors.Primary)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(if (canceling) R.string.chat_release_to_cancel else R.string.chat_slide_left_cancel),
            fontSize = AppTypography.caption,
            color = AppColors.TextSecondary,
        )
    }
}

/** 样式 A：按住时输入栏上方的浮层，里面是声波和提示文字。 */
@Composable
internal fun VoiceFloatingPanel(canceling: Boolean, history: List<Float>) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AppColors.Surface,
        shadowElevation = 6.dp,
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VoiceLevelBars(
                history = history,
                tint = if (canceling) AppColors.Error else AppColors.Primary,
                modifier = Modifier.height(36.dp).width(160.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (canceling) R.string.chat_release_to_cancel else R.string.chat_release_to_send),
                fontSize = AppTypography.caption,
                color = if (canceling) AppColors.Error else AppColors.TextSecondary,
            )
        }
    }
}

/** 音量竖条：历史越新越靠右，高度随音量变化。 */
@Composable
internal fun VoiceLevelBars(
    history: List<Float>,
    tint: Color,
    modifier: Modifier = Modifier.height(20.dp),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val padded = List(VoiceLevelConfig.MAX - history.size) { 0f } + history
        padded.forEach { level ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((3 + 30 * level).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tint.copy(alpha = if (level > 0.02f) 1f else 0.25f)),
            )
            Spacer(Modifier.width(2.dp))
        }
    }
}

private object VoiceLevelConfig {
    const val MAX = 28
}

/** 样式 B：麦克风外围的扩散波纹，音量越大扩得越远。 */
@Composable
private fun RippleWaves(level: Float) {
    val transition = rememberInfiniteTransition(label = "voice-ripple")
    val boost = 0.8f + 0.9f * level
    listOf(0, 600, 1200).forEachIndexed { index, delay ->
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 2.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, delayMillis = delay, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "ripple-scale-$index",
        )
        val rippleAlpha by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, delayMillis = delay, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "ripple-alpha-$index",
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer {
                    scaleX = scale * boost
                    scaleY = scale * boost
                    alpha = rippleAlpha
                }
                .clip(CircleShape)
                .background(AppColors.Primary),
        )
    }
}

/** 语音气泡里的音量柱：播放时起伏，停止时是静态波形。 */
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
    Column {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoiceWaveBars(playing = playing, tint = Color.Black)
            Spacer(Modifier.width(10.dp))
            Text("${durationSeconds}″", fontSize = AppTypography.bodyLarge, color = Color.Black)
        }
        if (transcript != null) {
            Text(transcript, fontSize = AppTypography.caption, color = AppColors.TextSecondary)
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
        val uri = FileProvider.getUriForFile(context, "com.openapi.ks.chat.fileprovider", File(path))
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
