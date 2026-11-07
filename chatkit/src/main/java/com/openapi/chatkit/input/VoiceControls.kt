package com.openapi.chatkit.input

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.openapi.chatkit.R
import com.openapi.chatkit.model.VoiceStyle
import com.openapi.chatkit.theme.ChatKit
import kotlinx.coroutines.delay

private const val LEVEL_HISTORY_SIZE = 28

/** 录音期间的音量历史（最新在右），给声波用。停止录音时清空。 */
@Composable
internal fun rememberLevelHistory(recording: Boolean, level: Float): SnapshotStateList<Float> {
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(level, recording) {
        if (recording) {
            history.add(level)
            if (history.size > LEVEL_HISTORY_SIZE) history.removeAt(0)
        } else {
            history.clear()
        }
    }
    return history
}

/**
 * 「按住说话」条。按下开始录音，松开发送；取消方向随样式不同：
 * 样式 A、B 上滑超过 80dp 取消，样式 C 左滑超过 80dp 取消。
 */
@Composable
internal fun VoicePressBar(
    style: VoiceStyle,
    recording: Boolean,
    canceling: Boolean,
    level: Float,
    history: List<Float>,
    onCancelingChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChatKit.colors
    val thresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    val horizontalCancel = style == VoiceStyle.Bar
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(
                when {
                    recording && canceling -> colors.error.copy(alpha = 0.12f)
                    recording -> colors.accent.copy(alpha = 0.12f)
                    else -> colors.surfaceVariant
                },
            )
            .pointerInput(style) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onStart()
                    var cancelNow = false
                    while (true) {
                        val change = awaitPointerEvent().changes.first()
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        val next = if (horizontalCancel) dx < -thresholdPx else dy < -thresholdPx
                        if (next != cancelNow) {
                            cancelNow = next
                            onCancelingChange(next)
                        }
                        if (change.changedToUpIgnoreConsumed()) break
                    }
                    onCancelingChange(false)
                    if (cancelNow) onCancel() else onStop()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            recording && style == VoiceStyle.Bar -> RecordingBar(canceling = canceling, history = history)
            recording -> {
                if (style == VoiceStyle.Ripple) Ripples(level = level)
                Text(
                    stringResource(if (canceling) R.string.chatkit_release_to_cancel else R.string.chatkit_release_to_send),
                    fontSize = ChatKit.typography.body,
                    color = if (canceling) colors.error else colors.accent,
                )
            }
            else -> Text(
                stringResource(R.string.chatkit_hold_to_talk),
                fontSize = ChatKit.typography.body,
                color = colors.bubbleText,
            )
        }
    }
}

/** 样式 C：录音条，左计时、中声波、右提示。 */
@Composable
private fun RecordingBar(canceling: Boolean, history: List<Float>) {
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

/** 样式 A：按住时输入栏上方的浮层。 */
@Composable
internal fun VoiceFloatingPanel(canceling: Boolean, history: List<Float>) {
    val colors = ChatKit.colors
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surface, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LevelBars(
                history = history,
                tint = if (canceling) colors.error else colors.accent,
                modifier = Modifier.height(40.dp).width(170.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(if (canceling) R.string.chatkit_release_to_cancel else R.string.chatkit_release_to_send),
                fontSize = ChatKit.typography.caption,
                color = if (canceling) colors.error else colors.secondaryText,
            )
        }
    }
}

/** 音量竖条：历史越新越靠右，高度随音量变化。 */
@Composable
internal fun LevelBars(
    history: List<Float>,
    tint: Color,
    modifier: Modifier = Modifier.height(22.dp),
) {
    val padded = List((LEVEL_HISTORY_SIZE - history.size).coerceAtLeast(0)) { 0f } + history
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

/** 样式 B：从中心向外扩散的波纹，音量越大扩得越远。 */
@Composable
private fun Ripples(level: Float) {
    val transition = rememberInfiniteTransition(label = "ripples")
    val boost = 0.8f + 0.9f * level
    val accent = ChatKit.colors.accent
    listOf(0, 600, 1200).forEachIndexed { index, delayMs ->
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 2.2f,
            animationSpec = infiniteRepeatable(tween(1800, delayMs, LinearEasing), RepeatMode.Restart),
            label = "ripple-scale-$index",
        )
        val alpha by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(1800, delayMs, LinearEasing), RepeatMode.Restart),
            label = "ripple-alpha-$index",
        )
        Box(
            Modifier
                .size(28.dp)
                .graphicsLayer {
                    scaleX = scale * boost
                    scaleY = scale * boost
                    this.alpha = alpha
                }
                .clip(CircleShape)
                .background(accent),
        )
    }
}
