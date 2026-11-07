package com.openapi.chatkit.input

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
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
 * 「按住说话」条，就是输入栏的胶囊本身：背景、圆角、高度都在外层 Row 上；
 * 手势只挂在中间的 weight 区域，所以点右侧的 [trailing]（键盘按钮）不会触发录音。
 *
 * 松手去向由 [intentFor] 决定：
 * - 豆包式（[VoiceStyle.Ripple]）：上滑超过 80dp 取消，否则发送。
 * - 微信式（[VoiceStyle.Floating]）：左滑超过 80dp 取消，右滑超过 80dp 转文字，居中松手发送。
 * - 录音条（[VoiceStyle.Bar]）：左滑超过 80dp 取消。
 *
 * 按下时给一次长按触感，去向每次变化给一次轻震。浮层（光晕 / 卡片）由 [VoiceOverlay] 另外叠加。
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
    trailing: @Composable () -> Unit = {},
) {
    val colors = ChatKit.colors
    val haptic = LocalHapticFeedback.current
    val thresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    // 手势协程以 style 为 key 只启动一次，里面要读最新的回调，所以用 rememberUpdatedState
    val currentStart by rememberUpdatedState(onStart)
    val currentRelease by rememberUpdatedState(onRelease)
    val currentIntentChange by rememberUpdatedState(onIntentChange)
    val background = when {
        !recording -> colors.surfaceVariant
        intent == VoiceIntent.Cancel -> colors.error.copy(alpha = 0.10f)
        style == VoiceStyle.Floating && intent == VoiceIntent.Send -> colors.surfaceVariant
        else -> colors.accent.copy(alpha = 0.10f)
    }

    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
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
                                // 进入、离开某个去向都轻震一下，不用看屏幕也知道状态变了
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentIntentChange(next)
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
                    color = colors.bubbleText,
                )
            }
        }
        trailing()
    }
}

/** 按住过程中的位移 → 松手去向。 */
private fun intentFor(style: VoiceStyle, dx: Float, dy: Float, threshold: Float): VoiceIntent = when (style) {
    VoiceStyle.Floating -> when {
        dx < -threshold -> VoiceIntent.Cancel
        dx > threshold -> VoiceIntent.ToText
        else -> VoiceIntent.Send
    }
    VoiceStyle.Ripple -> if (dy < -threshold) VoiceIntent.Cancel else VoiceIntent.Send
    VoiceStyle.Bar -> if (dx < -threshold) VoiceIntent.Cancel else VoiceIntent.Send
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
 * - 豆包式：底部一圈椭圆光晕（正常蓝、取消红），亮度和轻微缩放随音量变化；光晕上方是实时文字、提示，
 *   再上方是一排点状波形（离底部约 44dp）。
 * - 微信式：屏幕中央一张 192dp 的半透明黑卡片，顶部状态行，中间话筒加音量柱（取消时换成撤销图标），
 *   底部两个去向提示，当前去向加粗变白。
 * - 录音条样式不需要浮层。
 */
@Composable
internal fun VoiceOverlay(
    style: VoiceStyle,
    intent: VoiceIntent,
    history: List<Float>,
    liveTranscript: String,
    modifier: Modifier = Modifier,
) {
    when (style) {
        VoiceStyle.Ripple -> GlowOverlay(intent, history, liveTranscript, modifier)
        VoiceStyle.Floating -> CardOverlay(intent, history, modifier)
        VoiceStyle.Bar -> Unit
    }
}

private val GlowBlue = Color(0xFF1E88FF)
private val GlowRed = Color(0xFFFF3B30)

/** 豆包式光晕：高 340dp，向上画出父级边界，不影响布局。 */
@Composable
private fun GlowOverlay(intent: VoiceIntent, history: List<Float>, liveTranscript: String, modifier: Modifier) {
    val colors = ChatKit.colors
    val canceling = intent == VoiceIntent.Cancel
    val level = (history.lastOrNull() ?: 0f).coerceIn(0f, 1f)
    val tint = if (canceling) GlowRed else GlowBlue
    val centerAlpha = (if (canceling) 0.55f else 0.45f) + 0.40f * level
    val pulse = 1f + 0.06f * level
    Box(
        modifier = modifier
            .fillMaxWidth()
            .overflowUpwards(340.dp)
            .drawBehind {
                // 椭圆：水平半径 0.75 屏宽（左右都超出屏幕），竖直半径约 0.9 个高度，圆心压在底边上，
                // 只保留上半部分，所以顶边是拱形、底边贴着输入栏。
                val w = size.width
                val h = size.height
                val radius = w * 0.75f
                val squash = h * 0.9f / radius * pulse
                clipRect(0f, 0f, w, h) {
                    withTransform({
                        translate(w / 2f, h)
                        scale(1f, squash, pivot = Offset.Zero)
                    }) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0f to tint.copy(alpha = centerAlpha),
                                    0.6f to tint.copy(alpha = centerAlpha * 0.35f),
                                    1f to Color.Transparent,
                                ),
                                center = Offset.Zero,
                                radius = radius,
                            ),
                            radius = radius,
                            center = Offset.Zero,
                        )
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (liveTranscript.isNotBlank() && !canceling) {
                Text(
                    liveTranscript,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    fontSize = 16.sp,
                    color = colors.bubbleText,
                    maxLines = 2,
                )
            }
            Text(
                stringResource(if (canceling) R.string.chatkit_release_to_cancel_short else R.string.chatkit_release_to_send_hint),
                fontSize = 13.sp,
                color = if (canceling) GlowRed else colors.secondaryText,
            )
            Spacer(Modifier.height(12.dp))
            DotWave(history = history, level = level, tint = if (canceling) GlowRed else Color.White)
        }
    }
}

/**
 * 点状波形：一排细圆点，中间高两边低，高度随音量变化。
 * 没声音时各点仍按相位轻轻起伏（[rememberInfiniteTransition]），不会是死的。
 */
@Composable
private fun DotWave(history: List<Float>, level: Float, tint: Color) {
    val transition = rememberInfiniteTransition(label = "dot-sway")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1800, easing = LinearEasing)),
        label = "dot-phase",
    )
    val padded = List((DOT_COUNT - history.size).coerceAtLeast(0)) { 0f } + history.takeLast(DOT_COUNT)
    val calm = 1f - level
    Row(
        modifier = Modifier.height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
    ) {
        padded.forEachIndexed { index, value ->
            // 两边收窄的包络，让整排看起来像一条中间鼓起的声波
            val envelope = sin(PI * (index + 0.5) / DOT_COUNT).toFloat().coerceIn(0.15f, 1f)
            // 每个点相位不同，安静时形成流动的起伏；有声音时起伏让位给音量
            val wave = (sin((phase + index * 0.45f).toDouble()).toFloat() + 1f) / 2f
            val height = 3f + 4f * wave * calm + 22f * value * envelope
            Box(
                Modifier
                    .width(2.5.dp)
                    .height(height.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = if (value > 0.02f) 1f else 0.55f)),
            )
        }
    }
}

/** 微信式卡片：192dp 见方，半透明黑，圆角 14dp。 */
@Composable
private fun CardOverlay(intent: VoiceIntent, history: List<Float>, modifier: Modifier) {
    val canceling = intent == VoiceIntent.Cancel
    Box(modifier.fillMaxWidth().overflowUpwards(300.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(192.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x8F000000)),
        ) {
            Text(
                stringResource(
                    when (intent) {
                        VoiceIntent.Send -> R.string.chatkit_release_to_send_short
                        VoiceIntent.Cancel -> R.string.chatkit_release_to_cancel_short
                        VoiceIntent.ToText -> R.string.chatkit_release_to_text
                    },
                ),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (canceling) Icons.AutoMirrored.Filled.Undo else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
                if (!canceling) {
                    Spacer(Modifier.width(10.dp))
                    VolumeColumns(history = history)
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CardHint(
                    text = "‹ " + stringResource(R.string.chatkit_cancel_short),
                    active = intent == VoiceIntent.Cancel,
                )
                CardHint(
                    text = stringResource(R.string.chatkit_to_text) + " ›",
                    active = intent == VoiceIntent.ToText,
                )
            }
        }
    }
}

/** 卡片底部的去向提示：当前去向加粗变白，其余半透明。 */
@Composable
private fun CardHint(text: String, active: Boolean) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (active) Color.White else Color.White.copy(alpha = 0.55f),
    )
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
 *
 * 写在它之后的修饰符（drawBehind 等）作用在被放大的内容上，所以画的是完整的 [height] 区域。
 */
private fun Modifier.overflowUpwards(height: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val h = height.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
    layout(placeable.width, 0) { placeable.place(0, -h) }
}
