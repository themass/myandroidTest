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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.openapi.chatkit.model.Sender
import com.openapi.chatkit.theme.ChatKit
import java.util.Locale

/** 气泡形状：靠头像一侧的顶角是小圆角，形成「尾巴」。 */
@Composable
fun bubbleShape(sender: Sender): Shape {
    val r = ChatKit.dimens.bubbleRadius
    val tail = ChatKit.dimens.bubbleTailRadius
    return if (sender == Sender.User) {
        RoundedCornerShape(topStart = r, topEnd = tail, bottomEnd = r, bottomStart = r)
    } else {
        RoundedCornerShape(topStart = tail, topEnd = r, bottomEnd = r, bottomStart = r)
    }
}

/** 文字气泡：用户绿色、助手灰色。具体文字由 [content] 渲染（助手可用 Markdown）。 */
@Composable
fun TextBubble(
    sender: Sender,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = ChatKit.colors
    val dimens = ChatKit.dimens
    Surface(
        color = if (sender == Sender.User) colors.userBubble else colors.assistantBubble,
        shape = bubbleShape(sender),
        modifier = modifier.combinedClickable(onClick = {}, onLongClick = onLongPress),
    ) {
        Box(Modifier.padding(horizontal = dimens.bubblePaddingHorizontal, vertical = dimens.bubblePaddingVertical)) {
            content()
        }
    }
}

/** 默认的文字渲染：纯文本，组件库统一字号和行高。 */
@Composable
fun DefaultMessageText(text: String) {
    Text(
        text = text,
        color = ChatKit.colors.bubbleText,
        fontSize = ChatKit.typography.body,
        lineHeight = ChatKit.typography.bodyLineHeight,
    )
}

/** 助手思考中：气泡里三个依次跳动的点。 */
@Composable
fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(
        modifier = Modifier.height(ChatKit.typography.bodyLineHeight.value.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, delayMillis = index * 160, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot-$index",
            )
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(ChatKit.colors.secondaryText.copy(alpha = alpha)),
            )
        }
    }
}

/**
 * 图片块。单张按原图比例显示；多张排成方格（2、4 张两列，其余三列），最多 9 张，
 * 超出的数量盖在第 9 张上。点击任意一张打开全屏查看器（组件自己管理查看器状态）。
 */
@Composable
fun ImageGrid(
    sources: List<String>,
    maxWidth: Dp,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sources.isEmpty()) return
    var viewerStart by remember { mutableStateOf<Int?>(null) }
    val placeholder = ChatKit.colors.surfaceVariant

    if (sources.size == 1) {
        AsyncImage(
            model = imageModel(sources[0]),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .sizeIn(minWidth = 96.dp, minHeight = 96.dp, maxWidth = minOf(maxWidth, 220.dp), maxHeight = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(placeholder)
                .combinedClickable(onClick = { viewerStart = 0 }, onLongClick = onLongPress),
        )
    } else {
        val columns = if (sources.size == 2 || sources.size == 4) 2 else 3
        val gap = 4.dp
        val gridWidth = minOf(maxWidth, if (columns == 2) 200.dp else 246.dp)
        val cell = (gridWidth - gap * (columns - 1)) / columns
        val shown = sources.take(9)
        Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
            shown.chunked(columns).forEachIndexed { row, rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowItems.forEachIndexed { col, source ->
                        val index = row * columns + col
                        Box(
                            modifier = Modifier
                                .size(cell)
                                .clip(RoundedCornerShape(8.dp))
                                .background(placeholder)
                                .combinedClickable(onClick = { viewerStart = index }, onLongClick = onLongPress),
                        ) {
                            AsyncImage(
                                model = imageModel(source),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (index == 8 && sources.size > 9) {
                                Box(
                                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("+${sources.size - 9}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    viewerStart?.let { start ->
        ImageViewer(sources = sources, startIndex = start, onDismiss = { viewerStart = null })
    }
}

/** 文件卡片：类型色块 + 扩展名、文件名、大小。点击交给调用方打开。 */
@Composable
fun FileCard(
    file: MessagePart.File,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChatKit.colors
    val extension = file.name.substringAfterLast('.', "").uppercase(Locale.US).take(4)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.divider),
        modifier = modifier
            .widthIn(min = 200.dp, max = 260.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = colors.bubbleText,
                    fontSize = ChatKit.typography.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(formatFileSize(file.sizeBytes), color = colors.secondaryText, fontSize = ChatKit.typography.caption)
            }
            Spacer(Modifier.width(10.dp))
            FileBadge(extension = extension)
        }
    }
}

/** 文件类型色块：不同类型不同颜色，上面写扩展名。草稿托盘也复用它。 */
@Composable
fun FileBadge(extension: String, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(fileColor(extension)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            extension.ifBlank { "FILE" },
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private fun fileColor(extension: String): Color = when (extension.lowercase(Locale.US)) {
    "pdf" -> Color(0xFFE53935)
    "doc", "docx", "wps" -> Color(0xFF1E88E5)
    "xls", "xlsx", "csv" -> Color(0xFF43A047)
    "ppt", "pptx", "key" -> Color(0xFFFB8C00)
    "zip", "rar", "7z", "tar", "gz" -> Color(0xFF8E24AA)
    "txt", "md", "log", "json", "xml" -> Color(0xFF757575)
    "mp3", "wav", "m4a", "aac", "flac" -> Color(0xFF00897B)
    "mp4", "mov", "mkv", "avi" -> Color(0xFF5E35B1)
    "jpg", "jpeg", "png", "gif", "webp", "heic" -> Color(0xFFD81B60)
    "apk" -> Color(0xFF7CB342)
    else -> Color(0xFF90A4AE)
}

internal fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

/**
 * 语音气泡（微信式）：宽度随时长增加；用户消息时长在左、声波在右，助手相反。
 * 播放中声波跳动。有转写时在气泡下方显示浅色「转文字」块。
 */
@Composable
fun VoiceBubble(
    sender: Sender,
    voice: MessagePart.Voice,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChatKit.colors
    val isUser = sender == Sender.User
    val width = (72 + voice.durationSeconds.coerceAtLeast(1) * 5).coerceAtMost(220).dp
    Column(modifier, horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(
            color = if (isUser) colors.userBubble else colors.assistantBubble,
            shape = bubbleShape(sender),
            modifier = Modifier
                .width(width)
                .heightIn(min = 42.dp)
                .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            ) {
                if (isUser) {
                    Text("${voice.durationSeconds}″", color = colors.bubbleText, fontSize = ChatKit.typography.body)
                    Spacer(Modifier.width(8.dp))
                    VoiceWaveBars(playing = voice.playing, tint = colors.bubbleText)
                } else {
                    VoiceWaveBars(playing = voice.playing, tint = colors.bubbleText)
                    Spacer(Modifier.width(8.dp))
                    Text("${voice.durationSeconds}″", color = colors.bubbleText, fontSize = ChatKit.typography.body)
                }
            }
        }
        val transcript = voice.transcript
        if (!transcript.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = colors.surfaceVariant) {
                Text(
                    transcript,
                    color = colors.bubbleText,
                    fontSize = ChatKit.typography.secondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 语音声波：四根竖条，播放时起伏，停止时静止。 */
@Composable
fun VoiceWaveBars(playing: Boolean, tint: Color) {
    val baseHeights = listOf(0.45f, 0.8f, 1f, 0.6f)
    val transition = rememberInfiniteTransition(label = "voice-bars")
    val animated = baseHeights.mapIndexed { index, _ ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 360 + index * 130, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar-$index",
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        baseHeights.forEachIndexed { index, base ->
            val factor = if (playing) animated[index].value else 1f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((4 + 14 * base * factor).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tint),
            )
        }
    }
}
