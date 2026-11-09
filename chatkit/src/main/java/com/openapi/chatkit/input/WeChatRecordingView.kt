package com.openapi.chatkit.input

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 微信式录音的三种松手去向。 */
enum class WeChatIntent { Send, Cancel, ToText }

/**
 * 微信式录音浮层（独立 view，只负责画面，不带录音逻辑）。
 * - 屏幕变暗；上方一个绿色语音气泡（带向下小尖角），中间是声波。
 * - 上滑到「取消」：气泡变浅红、声波变红，左侧按钮高亮。
 * - 滑到「转文字」：气泡变白，显示实时文字。
 * - 底部「松手 发送」。
 */
@Composable
fun WeChatRecordingView(
    intent: WeChatIntent,
    levels: List<Float>,
    liveText: String?,
    modifier: Modifier = Modifier,
) {
    val cancel = intent == WeChatIntent.Cancel
    val toText = intent == WeChatIntent.ToText
    val bubble = when {
        cancel -> Color(0xFFFA9D8B)
        else -> Color(0xFF95EC69)
    }
    val bars = when {
        cancel -> Color(0xFFFA5151)
        toText -> Color(0xFF07C160)
        else -> Color(0xFF2E3A2E)
    }

    Box(modifier.fillMaxSize().background(Color(0xB0000000))) {
        // 上方语音气泡
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 410.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Bubble(bubble = bubble, bars = bars, levels = levels, liveText = if (toText) liveText else null)
        }

        // 底部：圆弧底座 + 沿弧线的文字；上方两个按钮沿同一段弧线倾斜摆放
        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(240.dp)) {
            ArcPill(
                label = "取消",
                active = cancel,
                rotation = -12f,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 58.dp),
            )
            ArcPill(
                label = "转文字",
                active = toText,
                rotation = 12f,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 58.dp),
            )
            ArcBase(
                label = when {
                    cancel -> "松手 取消"
                    toText -> "松手 转文字"
                    else -> "松手 发送"
                },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(136.dp),
            )
        }
    }
}

/** 底部圆弧底座：一个很大的圆露出顶部一段；文字沿弧线排布。 */
@Composable
private fun ArcBase(label: String, modifier: Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    Canvas(modifier) {
        val radius = size.width * 0.72f
        val cx = size.width / 2f
        val cy = radius + 24.dp.toPx()          // 圆顶在画布顶部往下 24dp
        drawCircle(Color(0xFF3A3A3A), radius = radius, center = Offset(cx, cy))
        // 文字沿着比圆顶略低的同心弧排布
        val textRadius = radius - 84.dp.toPx()
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = with(density) { 16.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val path = android.graphics.Path().apply {
            addArc(cx - textRadius, cy - textRadius, cx + textRadius, cy + textRadius, 230f, 80f)
        }
        drawContext.canvas.nativeCanvas.drawTextOnPath(label, path, 0f, 0f, paint)
    }
}

/** 沿弧线倾斜的胶囊按钮：左边向左下倾、右边向右下倾。 */
@Composable
private fun ArcPill(label: String, active: Boolean, rotation: Float, modifier: Modifier) {
    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = rotation }
            .size(width = 140.dp, height = 58.dp)
            .background(if (active) Color.White else Color(0x55FFFFFF), RoundedCornerShape(29.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = if (active) Color(0xFF191919) else Color.White, fontSize = 17.sp)
    }
}

@Composable
private fun Bubble(bubble: Color, bars: Color, levels: List<Float>, liveText: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .widthIn(min = 200.dp, max = 300.dp)
                .background(bubble, RoundedCornerShape(10.dp))
                .padding(horizontal = 20.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (liveText.isNullOrBlank()) {
                Waveform(levels = levels, color = bars, modifier = Modifier.width(160.dp).height(18.dp))
            } else {
                Text(text = liveText, color = Color(0xFF191919), fontSize = 17.sp)
            }
        }
        // 向下的小尖角
        Canvas(modifier = Modifier.size(width = 16.dp, height = 8.dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(path, color = bubble)
        }
    }
}

@Composable
private fun Waveform(levels: List<Float>, color: Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val count = 32
        val gap = size.width / count
        val barWidth = gap * 0.5f
        val recent = if (levels.size >= count) levels.takeLast(count) else List(count - levels.size) { 0f } + levels
        recent.forEachIndexed { i, level ->
            val h = (size.height * (0.15f + 0.85f * level.coerceIn(0f, 1f)))
            val x = i * gap + (gap - barWidth) / 2f
            val y = (size.height - h) / 2f
            drawRoundRect(color, topLeft = Offset(x, y), size = Size(barWidth, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f))
        }
    }
}
