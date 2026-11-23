package com.openapi.chatkit.input

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.chatkit.R
import com.openapi.chatkit.model.VoiceStyle
import com.openapi.chatkit.theme.ChatKit
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** 松手时的去向：发送、取消、转文字（转文字只在微信式里有：右滑）。 */
internal enum class VoiceIntent { Send, Cancel, ToText }

/** 录音期间的音量历史（最新在右），给声波用。停止录音时清空。 */
@Composable
internal fun rememberLevelHistory(recording: Boolean, level: Float): SnapshotStateList<Float> {
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(level, recording) {
        if (recording) {
            history.add(level)
            if (history.size > DOT_COUNT) history.removeAt(0)
        } else {
            history.clear()
        }
    }
    return history
}

/**
 * 「按住说话」区域。只负责中间的文字 / 录音条和手势；外层的卡片、左右图标由输入栏提供。
 *
 * 手势只挂在这块区域上，所以点右侧的图标按钮不会触发录音。
 * 松手去向由 [intentFor] 决定：
 * - 豆包式（[VoiceStyle.Ripple]）：上滑超过 80dp 取消，否则发送。
 * - 微信式（[VoiceStyle.Floating]）：左滑取消，右滑转文字，居中松手发送。
 * - 录音条（[VoiceStyle.Bar]）：左滑超过 80dp 取消。
 *
 * 按下给一次长按触感，去向每次变化给一次轻震。
 */
@Composable
internal fun VoicePressBar(
    style: VoiceStyle,
    recording: Boolean,
    intent: VoiceIntent,
    history: List<Float>,
    onIntentChange: (VoiceIntent) -> Unit,
    onStart: () -> Unit,
    onRelease: (VoiceIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChatKit.colors
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val thresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    // 手势协程以 style 为 key 只启动一次，里面要读最新的回调，所以用 rememberUpdatedState
    val currentStart by rememberUpdatedState(onStart)
    val currentRelease by rememberUpdatedState(onRelease)
    val currentIntentChange by rememberUpdatedState(onIntentChange)

    Box(
        modifier = modifier
            .pointerInput(style) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentStart()
                    var current = VoiceIntent.Send
                    while (true) {
                        val change = awaitPointerEvent().changes.first()
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        val next = intentFor(style, dx, dy, thresholdPx)
                        if (next != current) {
                            current = next
                            // 进入、离开某个去向都震一下。先通知外层（录音会把接下来一小段静音，
                            // 马达声不会录进语音），再震动。
                            currentIntentChange(next)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vibrateTick(context)
                        }
                        if (change.changedToUpIgnoreConsumed()) break
                    }
                    currentRelease(current)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            recording && style == VoiceStyle.Bar -> RecordingBar(canceling = intent == VoiceIntent.Cancel, history = history)
            recording && style == VoiceStyle.Floating -> Text(
                stringResource(R.string.chatkit_release_to_end),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.bubbleText,
            )
            recording -> Text(
                stringResource(
                    if (intent == VoiceIntent.Cancel) R.string.chatkit_release_to_cancel_short
                    else R.string.chatkit_release_to_send_short,
                ),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (intent == VoiceIntent.Cancel) colors.error else colors.accent,
            )
            else -> Text(
                stringResource(R.string.chatkit_hold_to_talk),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
        }
    }
}

/** 按住过程中的位移 → 松手去向。 */
internal fun intentFor(style: VoiceStyle, dx: Float, dy: Float, threshold: Float): VoiceIntent = when (style) {
    VoiceStyle.Floating -> when {
        dx < -threshold -> VoiceIntent.Cancel
        dx > threshold -> VoiceIntent.ToText
        else -> VoiceIntent.Send
    }
    VoiceStyle.Ripple -> if (dy < -threshold) VoiceIntent.Cancel else VoiceIntent.Send
    VoiceStyle.Bar -> if (dx < -threshold) VoiceIntent.Cancel else VoiceIntent.Send
}
