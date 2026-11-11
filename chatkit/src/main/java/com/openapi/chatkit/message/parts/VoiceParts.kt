@file:OptIn(ExperimentalFoundationApi::class)

package com.openapi.chatkit.message.parts

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.openapi.chatkit.media.ImageViewer
import com.openapi.chatkit.media.imageModel
import com.openapi.chatkit.model.MessagePart
import com.openapi.mediaview.MediaCarousel
import com.openapi.mediaview.MediaItem
import com.openapi.mediaview.MediaStack
import com.openapi.mediaview.MediaTheme
import com.openapi.chatkit.model.Sender
import com.openapi.chatkit.theme.ChatKit
import java.util.Locale

/**
 * 语音气泡（微信式）：宽度随时长增加；用户消息时长在左、声波在右，助手相反。
 * 播放中声波跳动。同一个气泡里，上面是语音行，下面是转写文字（转写中 / 未识别到文字 两种状态）。
 */
@Composable
internal fun VoiceBubble(
    sender: Sender,
    voice: MessagePart.Voice,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChatKit.colors
    val isUser = sender == Sender.User
    val textColor = if (isUser) colors.userBubbleText else colors.assistantBubbleText
    val hintColor = textColor.copy(alpha = 0.6f)
    val dividerColor = textColor.copy(alpha = 0.15f)
    val transcript = voice.transcript?.takeIf { it.isNotBlank() }
    // 语音气泡的最小宽度随时长变化（88–200dp）；有文字时气泡随文字变宽，但不超过外层限宽
    val minWidth = (88 + voice.durationSeconds.coerceAtLeast(1) * 5).coerceIn(88, 200).dp
    Surface(
        color = if (isUser) colors.userBubble else colors.assistantBubble,
        contentColor = textColor,
        shape = bubbleShape(sender),
        modifier = modifier
            .widthIn(min = minWidth)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        // 宽度取内容的最大固有宽度：分割线 fillMaxWidth 不再把气泡撑满限宽
        Column(modifier = Modifier.width(IntrinsicSize.Max).padding(horizontal = 12.dp, vertical = 10.dp)) {
            // 语音行：播放 / 停止图标 + 声波 + 时长，整行可点
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (voice.playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                VoiceWaveBars(playing = voice.playing, tint = textColor.copy(alpha = if (isUser) 0.85f else 0.7f))
                Spacer(Modifier.width(8.dp))
                Text("${voice.durationSeconds}″", color = textColor, fontSize = ChatKit.typography.body)
            }
            // 转写区：和语音行同一个气泡，中间一条细分割线
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(dividerColor))
            Spacer(Modifier.height(8.dp))
            when {
                voice.transcribing -> Text("转文字中…", color = hintColor, fontSize = ChatKit.typography.secondary)
                transcript != null -> Text(transcript, color = textColor, fontSize = ChatKit.typography.body)
                else -> Text("未识别到文字", color = hintColor, fontSize = ChatKit.typography.secondary)
            }
        }
    }
}

/** 语音声波：四根竖条，播放时起伏，停止时静止。 */
@Composable
internal fun VoiceWaveBars(playing: Boolean, tint: Color) {
    // 5 根细条：静止高度 6 / 10 / 14 / 10 / 6 dp；播放时在 4dp 与各自高度之间错相起伏
    val maxHeights = listOf(6f, 10f, 14f, 10f, 6f)
    val transition = rememberInfiniteTransition(label = "voice-bars")
    val animated = maxHeights.mapIndexed { index, _ ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 360 + index * 70, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar-$index",
        )
    }
    Row(
        modifier = Modifier.height(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        maxHeights.forEachIndexed { index, max ->
            val height = if (playing) 4f + (max - 4f) * animated[index].value else max
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint),
            )
        }
    }
}
