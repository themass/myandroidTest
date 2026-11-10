package com.openapi.core.view.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

/**
 * 重叠排列（设计稿第 2 张）：卡片横向错开叠放，后面的压在前面的上面，白色边框，右上角显示总数。
 * 与 [MediaStack] 不同：这里是横向排开，不是对角线堆叠，也没有圆点。
 */
@Composable
fun MediaOverlap(
    items: List<MediaItem>,
    modifier: Modifier = Modifier,
    theme: MediaTheme = MediaTheme.Light,
    cardWidth: Dp = 150.dp,
    cardHeight: Dp = 200.dp,
    offsetStep: Dp = 46.dp,
    onClick: (index: Int) -> Unit = {},
    onLongClick: ((index: Int) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val border = if (theme == MediaTheme.Light) Color.White else Color(0xFF2C2C2E)
    val total = cardWidth + offsetStep * (items.size - 1)
    Box(modifier = modifier.width(total).height(cardHeight)) {
        items.indices.forEach { index ->
            val item = items[index]
            Box(
                modifier = Modifier
                    .offset(x = offsetStep * index)
                    .size(width = cardWidth, height = cardHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .border(2.dp, border, RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .clickable { onClick(index) },
            ) {
                AsyncImage(
                    model = item.model,
                    contentDescription = item.contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (item.isVideo) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "▶", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(text = items.size.toString(), color = Color.White, fontSize = 12.sp)
        }
    }
}

/**
 * 视频播放对话框：全屏黑底，用 Media3 播放 [url]（本地路径、URI 或网址），点击外部或返回键关闭。
 * 播放器随对话框创建和释放，不会在后台残留。
 */
@Composable
fun MediaVideoDialog(url: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val context = LocalContext.current
        val player = remember(url) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(ExoMediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
        }
        DisposableEffect(player) {
            onDispose { player.release() }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
