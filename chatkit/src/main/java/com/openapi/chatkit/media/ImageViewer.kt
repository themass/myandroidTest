package com.openapi.chatkit.media

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.openapi.chatkit.model.ImageLoadState
import com.openapi.chatkit.model.ImageVisualStatus
import com.openapi.mediaview.MediaLoadFailedOverlay
import com.openapi.mediaview.rememberStatefulImageModel
import java.io.File

/** 把本地路径或地址转成 Coil 可加载的模型。 */
internal fun imageModel(source: String): Any =
    if (source.startsWith("http") || source.startsWith("content:") || source.startsWith("file:")) source else File(source)

/**
 * 全屏图片查看器：左右滑动翻页，双指缩放，双击放大 / 还原，单击关闭。
 * 多张图时顶部显示「当前 / 总数」。
 *
 * 某一张加载失败时（整改清单 P1-10）显示失败占位而不是黑屏；失败时单击 = 重试而不是关闭，
 * [onRetry] 交回调用方去重新换取远程地址，组件自己会带新的缓存 key 强制重新加载。
 *
 * [onEdit] 非空时在底部居中浮一个「编辑」按钮（对**当前页**那一张生效）；为 null 时查看器
 * 与从前完全一致——陪伴页这类没有编辑管线的宿主不该看到一个点了没反应的按钮。
 */
@Composable
fun ImageViewer(
    sources: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit,
    /** 长按某一张（传它的来源）。调用方应先关闭查看器再弹菜单，否则菜单会被查看器挡住。 */
    onLongPress: (String) -> Unit = {},
    /** 点了失败的那张图（下标）：交调用方换源；组件同步做本地强制重载。 */
    onRetry: (Int) -> Unit = {},
    /** 点「编辑」（传当前页的来源）。宿主若会打开编辑器，记得先关闭查看器再进编辑。 */
    onEdit: ((String) -> Unit)? = null,
) {
    if (sources.isEmpty()) return
    // 与气泡同一套语义：来源一换整组回到 Loading，旧回调落在被换掉的状态上不会污染新图
    val states = remember(sources) { sources.map { mutableStateOf(ImageLoadState()) } }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, sources.lastIndex)) { sources.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableImage(
                    source = sources[page],
                    state = states[page].value,
                    onLoaded = { states[page].value = states[page].value.onLoaded() },
                    onFailed = { states[page].value = states[page].value.onFailed() },
                    onTap = {
                        // 失败的图：单击是重试，不是关掉查看器
                        if (states[page].value.status == ImageVisualStatus.Failed) {
                            states[page].value = states[page].value.onRetryClick()
                            onRetry(page)
                        } else {
                            onDismiss()
                        }
                    },
                    onLongPress = { onLongPress(sources[page]) },
                )
            }
            if (sources.size > 1) {
                Text(
                    stringResource(R.string.chatkit_image_count, pager.currentPage + 1, sources.size),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp),
                )
            }
            if (onEdit != null) {
                // 底部居中的「编辑」：半透明底压在小图上，图片主体（Fit 居中）不被遮挡。
                // 动作对当前页生效；宿主打开编辑器前应自己关掉查看器（它盖在最上层）。
                Button(
                    onClick = { onEdit(sources[pager.currentPage]) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black.copy(alpha = 0.55f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
                ) {
                    Text(stringResource(R.string.chatkit_edit))
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    source: String,
    state: ImageLoadState,
    onLoaded: () -> Unit,
    onFailed: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 4f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    val failed = state.status == ImageVisualStatus.Failed || state.status == ImageVisualStatus.Retrying
    val statefulModel = rememberStatefulImageModel(imageModel(source), state.attempt)
    // 失败且未点重试：不发请求（model=null），避免每次重组都重烧一次必失败的加载
    val model: Any? = if (failed && state.status != ImageVisualStatus.Retrying) null else statefulModel
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = model,
            // 全屏查看器里的图是内容本体，不是装饰（整改清单 P2-16）
            contentDescription = stringResource(R.string.chatkit_image),
            contentScale = ContentScale.Fit,
            onSuccess = { onLoaded() },
            onError = { onFailed() },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onLongPress = { onLongPress() },
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
        if (failed) {
            MediaLoadFailedOverlay(retrying = state.status == ImageVisualStatus.Retrying, compact = false)
        }
    }
}
