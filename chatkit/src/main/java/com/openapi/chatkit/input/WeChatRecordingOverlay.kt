package com.openapi.chatkit.input

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.openapi.chatkit.R
import kotlin.math.cos
import kotlin.math.sin

/*
 * 微信式录音浮层（SPEC2 第 6 项，数值来自微信 APK 的 c7u.xml 等布局）。
 *
 * 结构（自上而下）：
 * - 全屏遮罩 #655862（半透明）。
 * - 上方气泡：176×80dp，圆角 16dp，绿色 #95EC69（取消时 #FA5151），底部 24×10dp 小尖角；
 *   里面是声波；滑到「转文字」时变宽变高，显示实时识别的文字。
 * - 中部环：直径 1376dp 的大圆环上两段弧，「取消」在 260°，「滑到这里 转文字」在 280.5°，
 *   沿圆周排列的文字，环宽 68dp（高亮 78dp）。
 * - 底部圆：直径 1200dp，只露出顶部 96dp，竖直渐变 #999999 → #D5D5D5，外圈 4dp #26FFFFFF 描边，
 *   上方 24dp 处是「松开 发送」等状态文字。
 *
 * 手势（左滑取消、右滑转文字、居中松手发送）由 [VoicePressBar] 处理，这里只负责画。
 * 浮层用 [Popup] 盖住整个屏幕，不占输入栏的布局高度。
 */

private val WeChatMask = Color(0xCC655862)
private val WeChatGreen = Color(0xFF95EC69)
private val WeChatRed = Color(0xFFFA5151)
private val WeChatInk = Color(0xE6000000)
private val WeChatRingIdle = Color(0x33FFFFFF)
private val WeChatRingActive = Color(0xCCFFFFFF)
private val WeChatLabel = Color(0xCCFFFFFF)

private const val RING_DIAMETER_DP = 1376f
private const val RING_WIDTH_DP = 68f
private const val RING_WIDTH_ACTIVE_DP = 78f
private const val RING_LABEL_RADIUS_DP = 657f
private const val RING_CANCEL_CENTER_DEG = 260f
private const val RING_TEXT_CENTER_DEG = 280.5f
private const val RING_SEGMENT_SWEEP_DEG = 18f
private const val CIRCLE_DIAMETER_DP = 1200f

/** 微信式录音浮层。 */
@Composable
internal fun WeChatRecordingOverlay(
    intent: VoiceIntent,
    history: List<Float>,
    liveTranscript: String,
) {
    // 画面交给独立的 WeChatRecordingView（已单独验证过效果），这里只负责弹出层和把录音状态映射过去。
    // 弹出层固定在窗口左上角（默认会跟着输入栏的位置，导致上半部分画到屏幕外，只剩输入栏一块变灰）。
    Popup(
        popupPositionProvider = WindowOriginPosition,
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        WeChatRecordingView(
            intent = when (intent) {
                VoiceIntent.Send -> WeChatIntent.Send
                VoiceIntent.Cancel -> WeChatIntent.Cancel
                VoiceIntent.ToText -> WeChatIntent.ToText
            },
            levels = history,
            liveText = liveTranscript.takeIf { it.isNotBlank() },
        )
    }
}

/** 上方气泡：声波，或转文字时的实时文字。 */
@Composable
private fun WeChatBubble(
    intent: VoiceIntent,
    history: List<Float>,
    liveTranscript: String,
    modifier: Modifier,
) {
    val cancel = intent == VoiceIntent.Cancel
    val showText = intent == VoiceIntent.ToText && liveTranscript.isNotBlank()
    val tint = if (cancel) WeChatRed else WeChatGreen
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = (if (showText) Modifier.widthIn(min = 176.dp, max = 320.dp).heightIn(min = 80.dp, max = 380.dp) else Modifier.size(176.dp, 80.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(tint)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (showText) {
                Text(liveTranscript, fontSize = 24.sp, color = WeChatInk)
            } else {
                LevelBars(history = history, tint = WeChatInk.copy(alpha = 0.7f), modifier = Modifier.height(24.dp))
            }
        }
        Canvas(Modifier.size(24.dp, 10.dp)) {
            val tail = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(tail, color = tint)
        }
    }
}

/** 中部环：两段弧 + 沿圆周的文字。顶边距离输入栏上方 42dp。 */
@Composable
private fun WeChatRing(intent: VoiceIntent, modifier: Modifier) {
    val cancelLabel = stringResource(R.string.chatkit_cancel_short)
    val textLabel = stringResource(R.string.chatkit_slide_here_to_text)
    val cancelActive = intent == VoiceIntent.Cancel
    val textActive = intent == VoiceIntent.ToText
    Canvas(modifier) {
        val diameter = RING_DIAMETER_DP.dp.toPx()
        val cx = size.width / 2f
        val cy = 42.dp.toPx() + diameter / 2f
        val radius = diameter / 2f - RING_WIDTH_DP.dp.toPx() / 2f
        drawRingSegment(cx, cy, radius, RING_CANCEL_CENTER_DEG, cancelActive)
        drawRingSegment(cx, cy, radius, RING_TEXT_CENTER_DEG, textActive)
        val labelRadius = RING_LABEL_RADIUS_DP.dp.toPx()
        val labelSize = 16.sp.toPx()
        drawCurvedLabel(cx, cy, labelRadius, RING_CANCEL_CENTER_DEG, cancelLabel, labelSize)
        drawCurvedLabel(cx, cy, labelRadius, RING_TEXT_CENTER_DEG, textLabel, labelSize)
    }
}

/** 一段弧：以 [centerDeg] 为中心，高亮时环变宽、变亮。 */
private fun DrawScope.drawRingSegment(cx: Float, cy: Float, radius: Float, centerDeg: Float, active: Boolean) {
    val width = (if (active) RING_WIDTH_ACTIVE_DP else RING_WIDTH_DP).dp.toPx()
    drawArc(
        color = if (active) WeChatRingActive else WeChatRingIdle,
        startAngle = centerDeg - RING_SEGMENT_SWEEP_DEG / 2f,
        sweepAngle = RING_SEGMENT_SWEEP_DEG,
        useCenter = false,
        topLeft = Offset(cx - radius, cy - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = width),
    )
}

/** 沿圆周排列的文字：每个字的基线与圆相切，文字中心对准 [angleDeg]。 */
private fun DrawScope.drawCurvedLabel(cx: Float, cy: Float, radius: Float, angleDeg: Float, text: String, sizePx: Float) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val x = cx + radius * cos(rad).toFloat()
    val y = cy + radius * sin(rad).toFloat()
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WeChatLabel.toArgb()
            textSize = sizePx
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        native.save()
        native.translate(x, y)
        native.rotate(angleDeg + 90f)
        native.drawText(text, 0f, sizePx / 3f, paint)
        native.restore()
    }
}

/** 底部大圆：只露出顶部 96dp；上方 24dp 处是状态文字。 */
@Composable
private fun WeChatSendCircle(intent: VoiceIntent, modifier: Modifier) {
    val label = stringResource(
        when (intent) {
            VoiceIntent.Send -> R.string.chatkit_wechat_release_send
            VoiceIntent.Cancel -> R.string.chatkit_wechat_release_cancel
            VoiceIntent.ToText -> R.string.chatkit_wechat_release_edit
        },
    )
    Box(modifier.clipToBounds()) {
        Canvas(Modifier.fillMaxSize()) {
            val r = CIRCLE_DIAMETER_DP.dp.toPx() / 2f
            val cx = size.width / 2f
            val cy = r // 圆顶贴着容器顶边
            drawCircle(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF999999), Color(0xFFD5D5D5)),
                    startY = 0f,
                    endY = 2f * r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = Color(0x26FFFFFF),
                radius = r + 2.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 4.dp.toPx()),
            )
        }
        Text(
            label,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = WeChatInk,
        )
    }
}

/** 弹出层始终放在窗口的 (0, 0)，配合 fillMaxSize 铺满整个屏幕。 */
private object WindowOriginPosition : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): androidx.compose.ui.unit.IntOffset = androidx.compose.ui.unit.IntOffset.Zero
}
