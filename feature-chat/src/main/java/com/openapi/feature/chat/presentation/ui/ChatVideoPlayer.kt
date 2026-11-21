package com.openapi.feature.chat.presentation.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.openapi.feature.chat.R
import kotlinx.coroutines.launch

/**
 * 视频消息的全屏播放宿主（P2-13）。
 *
 * 放在 feature-chat 而不是 chatkit：组件库只画卡片、交出点击回调，
 * 播放器（media3）只在这一层出现——chatkit 不依赖 media3 是刻意分层。
 *
 * 来源规则在这里定死：本机文件在就播本机文件（零流量、零延迟）；
 * 只有服务端对象时用 [ChatRemoteVideoPlayer]，在**点开这一刻**才换签名 URL——
 * URL 只活 300 秒，存进缓存或提前换好都会在用户真正要看时变成黑屏。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ChatVideoPlayer(
    video: com.openapi.chatkit.model.MessagePart.Video,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember { newChatVideoPlayer(context) }
    VideoPlayerHost(player = player, onDismiss = onDismiss, modifier = modifier) {
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(java.io.File(video.path))))
        true
    }
}

/**
 * 历史视频（本机文件已丢）的播放宿主：现取签名 URL 再播（[refreshUrl] 由调用方给，
 * 走 `repository.refreshImageUrl` 同一条 `GET /uploads/{id}` 通道）。
 * 与本机版分开而不是合一个入口，是为了让「点开才换 URL」这条规则在调用点可见。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ChatRemoteVideoPlayer(
    video: com.openapi.chatkit.model.MessagePart.Video,
    refreshUrl: suspend (String) -> String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember { newChatVideoPlayer(context) }
    VideoPlayerHost(player = player, onDismiss = onDismiss, modifier = modifier) {
        val uploadId = video.uploadId ?: return@VideoPlayerHost false
        val url = runCatching { refreshUrl(uploadId) }.getOrNull() ?: return@VideoPlayerHost false
        player.setMediaItem(MediaItem.fromUri(url))
        true
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun newChatVideoPlayer(context: android.content.Context): ExoPlayer =
    ExoPlayer.Builder(context.applicationContext).build().apply { playWhenReady = true }

/**
 * 两个入口共用的播放器生命周期：黑底全屏、ExoPlayer 随组合释放、加载中转圈、失败给文案。
 * [start] 在组合后的协程里喂给播放器（可能挂起换 URL）；返回 false = 没有可播的来源，
 * 直接显示失败提示。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerHost(
    player: ExoPlayer,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    start: suspend () -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loadError by remember { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(true) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY || state == Player.STATE_ENDED) preparing = false
            }

            override fun onPlayerError(error: PlaybackException) {
                preparing = false
                loadError = context.getString(R.string.chat_video_play_failed)
            }
        }
        player.addListener(listener)
        scope.launch {
            val started = runCatching { start() }.getOrDefault(false)
            if (started) player.prepare()
            else {
                preparing = false
                loadError = context.getString(R.string.chat_video_play_failed)
            }
        }
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    // 竖屏看横视频留黑边，不拉伸不变形
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        if (preparing && loadError == null) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )
        }
        loadError?.let { message ->
            Text(
                message,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
        }
    }
}
