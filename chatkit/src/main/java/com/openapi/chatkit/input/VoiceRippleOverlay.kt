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

/**
 * 录音浮层。叠在输入栏上方、不占布局高度（否则一按下列表就被顶起来抖动）。
 *
 * - 豆包式（[VoiceStyle.Ripple]）：底部的蓝色光晕 + 白色提示 + 点阵波形；取消时切成粉色底 + 红色。
 *   [modifier] 应当对齐到输入栏的底边。
 * - 微信式（[VoiceStyle.Floating]）：屏幕中央一张半透明黑色圆角卡片（第二阶段再重做），[modifier] 对齐到输入栏顶边。
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
        VoiceStyle.Ripple -> RippleOverlay(intent, history, liveTranscript, modifier)
        VoiceStyle.Floating -> WeChatRecordingOverlay(intent, history, liveTranscript)
        VoiceStyle.Bar -> Unit
    }
}

/** 豆包式浮层的高度：光晕（160dp 矩形 + 拱顶圆）加上留给模糊的余量。 */
internal val RippleOverlayHeight = 340.dp
internal val RippleGlowRectHeight = 160.dp
internal val RippleGlowHighlightRadius = 120.dp
internal val RippleBlurRadius = 48.dp
internal val GlowCore = Color(0xFF2D66C8)
internal val GlowMid = Color(0xFF4FA3D6)
internal val GlowHighlight = Color(0xFF9FE3FF)
internal val CancelWash = Color(0xFFF6C9D2)
internal val CancelRed = Color(0xFFE5484D)

/**
 * 豆包式浮层。
 * - 光晕：底部 160dp 矩形 ∪ 圆心在矩形上沿、半径 0.8×半屏宽的圆，整体 48dp 模糊（API 31+），
 *   中心一个 120dp 高光。音量越大整体越大；没声音时缓慢呼吸（1.00 ↔ 1.03，约 1.6 秒一个来回）。
 * - 取消：蓝色光晕淡出、粉色底淡入（200ms），文字和点阵变红。
 */
@Composable
internal fun RippleOverlay(intent: VoiceIntent, history: List<Float>, liveTranscript: String, modifier: Modifier) {
    val canceling = intent == VoiceIntent.Cancel
    val rawLevel = (history.lastOrNull() ?: 0f).coerceIn(0f, 1f)
    val level by animateFloatAsState(rawLevel, animationSpec = tween(durationMillis = 120), label = "ripple-level")
    val cancelMix by animateFloatAsState(if (canceling) 1f else 0f, animationSpec = tween(200), label = "ripple-cancel-mix")
    val textColor by animateColorAsState(if (canceling) CancelRed else Color.White, animationSpec = tween(200), label = "ripple-text")
    val breath = rememberBreath()
    val scale = breath + 0.2f * level
    val softFallback = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    Box(modifier.fillMaxWidth().overflowUpwards(RippleOverlayHeight)) {
        // 蓝色光晕（取消时淡出）
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 1f - cancelMix
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .then(
                    if (softFallback) Modifier
                    else Modifier.blur(radius = RippleBlurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                ),
        ) {
            if (softFallback) drawGlowSoft(level) else drawGlowSolid(level)
        }
        // 取消态：从下往上的浅粉渐变
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(RippleOverlayHeight)
                .graphicsLayer { alpha = cancelMix }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.7f to CancelWash.copy(alpha = 0.7f),
                        1f to CancelWash,
                    ),
                ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (liveTranscript.isNotBlank() && !canceling) {
                Text(
                    liveTranscript,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 3,
                )
            }
            Text(
                stringResource(if (canceling) R.string.chatkit_release_to_cancel_short else R.string.chatkit_release_to_send_hint),
                fontSize = 15.sp,
                color = textColor,
            )
            Spacer(Modifier.height(20.dp))
            DotWave(history = history, level = level, color = textColor)
        }
    }
}

/** 呼吸系数：1.00 ↔ 1.03，约 1.6 秒一个来回。 */
@Composable
internal fun rememberBreath(): Float {
    val transition = rememberInfiniteTransition(label = "ripple-breath")
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ripple-breath-value",
    )
    return breath
}

/** 光晕（API 31+，配合模糊）：实心的矩形和圆，颜色从深蓝到浅蓝，中间加高光。 */
internal fun DrawScope.drawGlowSolid(level: Float) {
    val w = size.width
    val h = size.height
    val rectH = RippleGlowRectHeight.toPx()
    val rectTop = h - rectH
    val cx = w / 2f
    // 拱形：半圆顶（半径 = 半屏宽）与两侧竖边相切，没有尖角
    val domeRadius = w / 2f
    val arch = Path().apply {
        moveTo(0f, h)
        lineTo(0f, rectTop)
        arcTo(Rect(0f, rectTop - domeRadius, w, rectTop + domeRadius), 180f, 180f, false)
        lineTo(w, h)
        close()
    }
    drawPath(
        path = arch,
        brush = Brush.verticalGradient(
            0f to GlowMid,
            0.5f to GlowMid,
            1f to GlowCore,
            startY = rectTop - domeRadius,
            endY = h,
        ),
    )
    drawCircle(
        color = GlowHighlight.copy(alpha = 0.35f + 0.4f * level),
        radius = RippleGlowHighlightRadius.toPx(),
        center = Offset(cx, rectTop),
    )
}

/** 光晕（API 30 及以下，无模糊）：用径向渐变和纵向渐变把边缘淡出，近似同样的形状。 */
internal fun DrawScope.drawGlowSoft(level: Float) {
    val w = size.width
    val h = size.height
    val rectH = RippleGlowRectHeight.toPx()
    val rectTop = h - rectH
    val radius = 0.65f * (w / 2f)
    val cx = w / 2f
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.45f to GlowMid.copy(alpha = 0.6f),
            1f to GlowCore,
            startY = rectTop - rectH * 0.2f,
            endY = h,
        ),
        topLeft = Offset(0f, rectTop - rectH * 0.2f),
        size = Size(w, h - (rectTop - rectH * 0.2f)),
    )
    drawCircle(
        brush = Brush.radialGradient(
            0f to GlowCore,
            0.6f to GlowMid.copy(alpha = 0.6f),
            1f to Color.Transparent,
            center = Offset(cx, rectTop),
            radius = radius,
        ),
        radius = radius,
        center = Offset(cx, rectTop),
    )
    drawCircle(
        brush = Brush.radialGradient(
            0f to GlowHighlight.copy(alpha = 0.35f + 0.4f * level),
            1f to Color.Transparent,
            center = Offset(cx, rectTop),
            radius = RippleGlowHighlightRadius.toPx(),
        ),
        radius = RippleGlowHighlightRadius.toPx(),
        center = Offset(cx, rectTop),
    )
}

/**
 * 点阵波形：56 个细点，宽 2.5dp、间距 2.5dp。两端渐隐；高度随音量变化、中间高两边低；
 * 没有声音时每个点相位错开地轻轻起伏，不会静止。
 */
@Composable
internal fun DotWave(history: List<Float>, level: Float, color: Color) {
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
        modifier = Modifier.height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
    ) {
        padded.forEachIndexed { index, value ->
            val edge = sin(PI * (index + 0.5) / DOT_COUNT).toFloat().coerceIn(0f, 1f)
            val wave = (sin((phase + index * 0.5f).toDouble()).toFloat() + 1f) / 2f
            val heightDp = 3f + 5f * wave * calm + 26f * value * edge
            Box(
                Modifier
                    .width(2.5.dp)
                    .height(heightDp.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = sqrt(edge).coerceIn(0f, 1f))),
            )
        }
    }
}
