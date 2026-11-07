package com.openapi.chatkit.input

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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.chatkit.R
import com.openapi.chatkit.model.VoiceStyle
import com.openapi.chatkit.theme.ChatKit
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private const val LEVEL_HISTORY_SIZE = 28
private const val DOT_COUNT = 56

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
 * 「按住说话」条。按下开始录音，松开发送；取消方向随样式不同：
 * 豆包式（[VoiceStyle.Ripple]）、微信式（[VoiceStyle.Floating]）上滑超过 80dp 取消，
 * 录音条（[VoiceStyle.Bar]）左滑超过 80dp 取消。
 *
 * 条本身只显示文字，录音时的光晕、卡片、波形都在 [VoiceOverlay] 里，由输入栏统一叠在上方。
 * 开始录音、进入取消区、离开取消区各给一次触感反馈。
 */
@Composable
internal fun VoicePressBar(
    style: VoiceStyle,
    recording: Boolean,
    canceling: Boolean,
    history: List<Float>,
    onCancelingChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChatKit.colors
    val haptic = LocalHapticFeedback.current
    val thresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    val horizontalCancel = style == VoiceStyle.Bar
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                when {
                    recording && style == VoiceStyle.Floating -> colors.surfaceVariant
                    recording && canceling -> colors.error.copy(alpha = 0.10f)
                    recording -> colors.accent.copy(alpha = 0.10f)
                    else -> colors.surfaceVariant
                },
            )
            .pointerInput(style) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStart()
                    var cancelNow = false
                    while (true) {
                        val change = awaitPointerEvent().changes.first()
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        val next = if (horizontalCancel) dx < -thresholdPx else dy < -thresholdPx
                        if (next != cancelNow) {
                            cancelNow = next
                            // 进入、离开取消区都轻震一下，让人不用看屏幕也知道状态变了
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
            recording && style == VoiceStyle.Floating -> Text(
                stringResource(R.string.chatkit_release_to_end),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.bubbleText,
            )
            recording -> Text(
                stringResource(if (canceling) R.string.chatkit_release_to_cancel_short else R.string.chatkit_release_to_send_short),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (canceling) colors.error else colors.accent,
            )
            else -> Text(
                stringResource(R.string.chatkit_hold_to_talk),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.bubbleText,
            )
        }
    }
}

/** 录音条样式：左计时、中声波、右提示。 */
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

/**
 * 录音浮层。叠在输入栏上方、不占布局高度（否则一按下列表就被顶起来抖动）。
 *
 * - 豆包式：底部泛起一层渐变光晕（正常偏蓝，取消变红），中间一行提示，下面是一排点状波形。
 * - 微信式：屏幕中央一张半透明黑色圆角卡片，话筒加音量柱，取消时换成取消图标。
 * - 录音条样式不需要浮层。
 */
@Composable
internal fun VoiceOverlay(
    style: VoiceStyle,
    canceling: Boolean,
    history: List<Float>,
    liveTranscript: String,
    modifier: Modifier = Modifier,
) {
    when (style) {
        VoiceStyle.Ripple -> GlowOverlay(canceling, history, liveTranscript, modifier)
        VoiceStyle.Floating -> CardOverlay(canceling, history, modifier)
        VoiceStyle.Bar -> Unit
    }
}

private val GlowBlue = Color(0xFF1E88FF)
private val GlowRed = Color(0xFFFF3B30)

/** 豆包式光晕：高度 340dp，向上画出父级边界，不影响布局。 */
@Composable
private fun GlowOverlay(canceling: Boolean, history: List<Float>, liveTranscript: String, modifier: Modifier) {
    val tint = if (canceling) GlowRed else GlowBlue
    Box(
        modifier = modifier
            .fillMaxWidth()
            .overflowUpwards(340.dp)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to tint.copy(alpha = 0.10f),
                    1f to tint.copy(alpha = if (canceling) 0.30f else 0.55f),
                ),
            ),
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (liveTranscript.isNotBlank() && !canceling) {
                Text(
                    liveTranscript,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    fontSize = 16.sp,
                    color = Color.Black,
                    maxLines = 2,
                )
            }
            Text(
                stringResource(if (canceling) R.string.chatkit_release_to_cancel_short else R.string.chatkit_release_to_send_hint),
                fontSize = 13.sp,
                color = if (canceling) GlowRed else Color(0xFF6B7280),
            )
            Spacer(Modifier.height(14.dp))
            DotWave(history = history, tint = if (canceling) GlowRed else Color.White)
        }
    }
}

/** 点状波形：一排细圆点，中间高两边低，高度随音量变化。 */
@Composable
private fun DotWave(history: List<Float>, tint: Color) {
    val padded = List((DOT_COUNT - history.size).coerceAtLeast(0)) { 0f } + history.takeLast(DOT_COUNT)
    Row(
        modifier = Modifier.height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
    ) {
        padded.forEachIndexed { index, level ->
            // 两边收窄的包络，让整排看起来像一条中间鼓起的声波
            val envelope = sin(PI * (index + 0.5) / DOT_COUNT).toFloat().coerceIn(0.15f, 1f)
            val height = (3f + 24f * level * envelope).coerceAtLeast(3f)
            Box(
                Modifier
                    .width(2.5.dp)
                    .height(height.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = if (level > 0.02f) 1f else 0.55f)),
            )
        }
    }
}

/** 微信式卡片：192dp 见方，半透明黑，圆角 14dp。 */
@Composable
private fun CardOverlay(canceling: Boolean, history: List<Float>, modifier: Modifier) {
    Box(modifier.fillMaxWidth().overflowUpwards(300.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(192.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x8F000000)),
        ) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Icon(
                    if (canceling) Icons.AutoMirrored.Filled.Undo else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(72.dp),
                )
                if (!canceling) {
                    Spacer(Modifier.width(8.dp))
                    VolumeColumns(history = history)
                }
            }
            Text(
                stringResource(if (canceling) R.string.chatkit_release_to_cancel else R.string.chatkit_slide_up_cancel),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 35.dp),
                fontSize = 15.sp,
                color = Color.White,
            )
        }
    }
}

/** 微信卡片里话筒右侧的音量柱：5 根，按最近的音量从低到高点亮。 */
@Composable
private fun VolumeColumns(history: List<Float>) {
    val level = history.lastOrNull() ?: 0f
    val lit = (level * 5f).toInt().coerceIn(0, 5)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.Start) {
        for (i in 5 downTo 1) {
            Box(
                Modifier
                    .width((8 + i * 5).dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = if (i <= lit || i == 1) 0.95f else 0.28f)),
            )
        }
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
 */
private fun Modifier.overflowUpwards(height: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val h = height.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
    layout(placeable.width, 0) { placeable.place(0, -h) }
}
