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
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(0.34f))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Bubble(bubble = bubble, bars = bars, levels = levels, liveText = if (toText) liveText else null)
            }
            Spacer(Modifier.weight(0.26f))
            // 左右两个按钮：取消 / 转文字（高亮的那个是白色）
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                SideButton(label = "取消", active = cancel)
                SideButton(label = "转文字", active = toText)
            }
            Spacer(Modifier.height(16.dp))
            // 底部：松手 发送 / 取消 / 编辑文字
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Color(0xFF2B2B2B)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        cancel -> "松手 取消"
                        toText -> "松手 编辑文字"
                        else -> "松手 发送"
                    },
                    color = Color.White,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun Bubble(bubble: Color, bars: Color, levels: List<Float>, liveText: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .widthIn(min = 120.dp, max = 280.dp)
                .background(bubble, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (liveText.isNullOrBlank()) {
                Waveform(levels = levels, color = bars, modifier = Modifier.width(96.dp).height(24.dp))
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
        val count = 20
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

@Composable
private fun SideButton(label: String, active: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 132.dp, height = 52.dp)
            .background(if (active) Color.White else Color(0x66FFFFFF), RoundedCornerShape(26.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = if (active) Color(0xFF191919) else Color.White, fontSize = 16.sp)
    }
}
