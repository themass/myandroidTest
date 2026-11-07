package com.openapi.chatkit.media

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.openapi.chatkit.R
import java.io.File

/** 把本地路径或地址转成 Coil 可加载的模型。 */
internal fun imageModel(source: String): Any =
    if (source.startsWith("http") || source.startsWith("content:") || source.startsWith("file:")) source else File(source)

/**
 * 全屏图片查看器：左右滑动翻页，双指缩放，双击放大 / 还原，单击关闭。
 * 多张图时顶部显示「当前 / 总数」。
 */
@Composable
fun ImageViewer(sources: List<String>, startIndex: Int, onDismiss: () -> Unit) {
    if (sources.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, sources.lastIndex)) { sources.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableImage(source = sources[page], onTap = onDismiss)
            }
            if (sources.size > 1) {
                Text(
                    stringResource(R.string.chatkit_image_count, pager.currentPage + 1, sources.size),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(source: String, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 4f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    AsyncImage(
        model = imageModel(source),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            // 未放大时不拦截单指拖动，让外层翻页器能左右滑
            .transformable(transformState, canPan = { scale > 1f })
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
