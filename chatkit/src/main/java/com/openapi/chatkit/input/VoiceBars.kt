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

internal const val LEVEL_HISTORY_SIZE = 28
internal const val DOT_COUNT = 56

/** 录音条样式：左计时、中声波、右提示。 */
@Composable
internal fun RecordingBar(canceling: Boolean, history: List<Float>) {
    val colors = ChatKit.colors
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds += 1
        }
    }
    val tint = if (canceling) colors.error else colors.accent
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(6.dp))
        Text("%d:%02d".format(seconds / 60, seconds % 60), fontSize = ChatKit.typography.caption, color = tint)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) { LevelBars(history = history, tint = tint) }
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(if (canceling) R.string.chatkit_release_to_cancel else R.string.chatkit_slide_left_cancel),
            fontSize = ChatKit.typography.caption,
            color = colors.secondaryText,
        )
    }
}

/** 音量竖条：历史越新越靠右，高度随音量变化（录音条样式使用）。 */
@Composable
internal fun LevelBars(
    history: List<Float>,
    tint: Color,
    modifier: Modifier = Modifier.height(22.dp),
) {
    val padded = List((LEVEL_HISTORY_SIZE - history.size).coerceAtLeast(0)) { 0f } + history.takeLast(LEVEL_HISTORY_SIZE)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
    ) {
        padded.forEach { value ->
            Box(
                Modifier
                    .width(3.dp)
                    .height((3 + 30 * value).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tint.copy(alpha = if (value > 0.02f) 1f else 0.25f)),
            )
        }
    }
}

/**
 * 让这个元素向上画出 [height]，但自身测量高度为 0：不参与布局，所以浮层出现和消失时
 * 下面的输入栏、上面的消息列表都不会被挤动。
 *
 * 写在它之后的修饰符作用在被放大的内容上，所以画的是完整的 [height] 区域。
 */
internal fun Modifier.overflowUpwards(height: Dp): Modifier = layout { measurable, constraints ->
    val h = height.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
    layout(placeable.width, 0) { placeable.place(0, -h) }
}

/** 切换状态时的一次明确震动（比 HapticFeedbackType.LongPress 更明显，部分设备上后者不震）。 */
internal fun vibrateTick(context: android.content.Context) {
    val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(android.os.VibratorManager::class.java))?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(android.os.Vibrator::class.java)
    }
    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
}
