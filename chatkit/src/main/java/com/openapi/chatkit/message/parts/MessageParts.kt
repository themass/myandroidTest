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

/** 气泡形状：四角同一圆角（豆包式，不做「尾巴」）。 */
@Composable
fun bubbleShape(sender: Sender): Shape {
    val r = if (sender == Sender.User) ChatKit.dimens.userBubbleRadius else ChatKit.dimens.assistantBubbleRadius
    return RoundedCornerShape(r)
}

/** 文字气泡：用户蓝底白字，助手浅灰底深灰字。具体文字由 [content] 渲染（助手可用 Markdown）。 */
@Composable
fun TextBubble(
    sender: Sender,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    /** 气泡卡片内部、正文下面的 footer（助手最新回复）。 */
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = ChatKit.colors
    val dimens = ChatKit.dimens
    val isUser = sender == Sender.User
    Surface(
        color = if (isUser) colors.userBubble else colors.assistantBubble,
        contentColor = if (isUser) colors.userBubbleText else colors.assistantBubbleText,
        shape = bubbleShape(sender),
        modifier = modifier.combinedClickable(onClick = {}, onLongClick = onLongPress),
    ) {
        Box(Modifier.padding(horizontal = dimens.bubblePaddingHorizontal, vertical = dimens.bubblePaddingVertical)) {
            if (footer == null) {
                content()
            } else {
                Column {
                    content()
                    footer()
                }
            }
        }
    }
}

/** 默认的文字渲染：纯文本，颜色取自所在气泡（Surface 的 contentColor），字号和行高统一。 */
@Composable
fun DefaultMessageText(text: String) {
    Text(
        text = text,
        color = Color.Unspecified,
        fontSize = ChatKit.typography.body,
        lineHeight = ChatKit.typography.bodyLineHeight,
    )
}

/**
 * 助手思考中：气泡里的「正在思考…」文字，透明度缓慢呼吸（约 1.6 秒一个来回）。
 * 比三个跳动的点更接近常见对话产品的等待态；流式输出第一段文字到达后即被替换。
 */
@Composable
fun ThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "thinking-alpha",
    )
    Text(
        text = "正在思考…",
        color = ChatKit.colors.secondaryText.copy(alpha = alpha),
        fontSize = ChatKit.typography.body,
        lineHeight = ChatKit.typography.bodyLineHeight,
    )
}

/**
 * 图片块。单张按原图比例显示；多张排成方格（2、4 张两列，其余三列），最多 9 张，
 * 超出的数量盖在第 9 张上。点击任意一张打开全屏查看器（组件自己管理查看器状态）。
 */
@Composable
fun ImageGrid(
    sources: List<String>,
    maxWidth: Dp,
    /** 长按某一张图：传它的来源（本地路径或地址）。 */
    onLongPress: (String) -> Unit,
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
                .combinedClickable(onClick = { viewerStart = 0 }, onLongClick = { onLongPress(sources[0]) }),
        )
    } else {
        // 多图：2–4 张用堆叠卡片，5 张及以上用横向轮播（通用媒体组件 :mediaview）
        val items = sources.map { MediaItem(model = imageModel(it)) }
        val onTap: (Int) -> Unit = { index -> viewerStart = index }
        val onLong: (Int) -> Unit = { index -> onLongPress(sources[index]) }
        val theme = if (androidx.compose.foundation.isSystemInDarkTheme()) MediaTheme.Dark else MediaTheme.Light
        Box(modifier) {
            if (sources.size <= 4) {
                MediaStack(items = items, theme = theme, onClick = onTap, onLongClick = onLong)
            } else {
                MediaCarousel(items = items, theme = theme, onClick = onTap, onLongClick = onLong)
            }
        }
    }

    viewerStart?.let { start ->
        ImageViewer(
            sources = sources,
            startIndex = start,
            onDismiss = { viewerStart = null },
            onLongPress = { source ->
                // 先关掉查看器（它是独立窗口，会挡住菜单），再弹出同一张图的菜单
                viewerStart = null
                onLongPress(source)
            },
        )
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

internal fun fileColor(extension: String): Color = when (extension.lowercase(Locale.US)) {
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
