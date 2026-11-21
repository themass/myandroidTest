package com.openapi.mediaview

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 一个媒体条目：图片或视频（视频在缩略图上显示播放标记）。[model] 可以是本地路径、URI 或网络地址。
 *
 * [failed] / [retrying] / [attempt] 由父组件的 [MediaLoadState] 驱动（整改清单 P1-10）：
 * 加载失败时卡片不再是一块黑底，而是可辨认的失败占位；[attempt] 拼进 Coil 缓存 key，
 * 点一次重试换一个 key，强制重新解码本机文件 / 重新请求远程地址。全部默认值下行为与旧版一致。
 */
data class MediaItem(
    val model: Any,
    val isVideo: Boolean = false,
    val contentDescription: String? = null,
    val failed: Boolean = false,
    val retrying: Boolean = false,
    val attempt: Int = 0,
)

/** 浅色 / 深色：决定卡片边框、圆点和角标的颜色。 */
enum class MediaTheme { Light, Dark }

/** 牌堆最多同时露出的层数；再多也只叠三层，其余靠「滑走一张、后面补位」轮转。 */
private const val STACK_VISIBLE = 3

/** 拖过卡片宽度的这个比例就算「翻页」，否则回弹。 */
private const val STACK_SWIPE_FRACTION = 0.22f

/**
 * 堆叠卡片（Stack）：多张图按对角线错开叠放，第一张在最上面，右上角显示当前位置/总数。
 * 设计稿：卡片 190×140dp，每张错开 14dp，白色 2dp 边框，阴影，角标半透明黑底。
 *
 * 可横向拖动：顶卡跟手平移并轻微倾斜，后面的卡片按拖动进度补位；松手超过阈值顶卡滑出、
 * 牌堆前进一张，否则回弹。只处理水平方向，纵向滑动仍然交给外层消息列表。
 */
@Composable
fun MediaStack(
    items: List<MediaItem>,
    modifier: Modifier = Modifier,
    theme: MediaTheme = MediaTheme.Light,
    cardWidth: Dp = 190.dp,
    cardHeight: Dp = 140.dp,
    offsetStep: Dp = 14.dp,
    onClick: (index: Int) -> Unit = {},
    onLongClick: ((index: Int) -> Unit)? = null,
    /**
     * 点了一张**已标记失败**的卡片：交给调用方去重新取地址（整改清单 P1-10）。
     * 不传时失败的卡片照常走 [onClick]。
     */
    onRetry: ((index: Int) -> Unit)? = null,
    /** 某张卡片自然加载失败：报给调用方合并进状态，让失败的卡片显示失败占位。 */
    onItemError: ((index: Int) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val count = items.size
    val border = if (theme == MediaTheme.Light) Color.White else Color(0xFF2C2C2E)
    val visible = minOf(count, STACK_VISIBLE)
    val totalWidth = cardWidth + offsetStep * (visible - 1)
    val totalHeight = cardHeight + offsetStep * (visible - 1)
    // 失败卡点击的语义：有 onRetry 就重试，否则照旧打开查看器
    val frontClick: (MediaItem, Int) -> Unit = { item, index ->
        if (onRetry != null && item.failed) onRetry(index) else onClick(index)
    }

    // 牌堆最上面那张在 items 里的下标；换一条消息（items 变化）时回到第一张
    var front by remember(items) { mutableIntStateOf(0) }
    // 顶卡的水平位移（px）：拖动时跟手，松手后动画收敛
    var dragX by remember(items) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val cardWidthPx = with(density) { cardWidth.toPx() }
    val swipeThreshold = cardWidthPx * STACK_SWIPE_FRACTION

    // 后面的卡片按拖动进度往前补一档：拖到阈值时正好完成一次位移
    val progress = if (swipeThreshold > 0f) (abs(dragX) / swipeThreshold).coerceIn(0f, 1f) else 0f
    val settle: (Float) -> Unit = { target ->
        scope.launch {
            animate(dragX, target, animationSpec = tween(durationMillis = 180)) { value, _ -> dragX = value }
        }
    }

    Box(
        modifier = modifier
            .width(totalWidth)
            .height(totalHeight)
            .then(
                if (count <= 1) {
                    Modifier
                } else {
                    Modifier.pointerInput(count) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragX = 0f },
                            onDragCancel = { settle(0f) },
                            onDragEnd = {
                                if (abs(dragX) > swipeThreshold) {
                                    val direction = if (dragX > 0) 1f else -1f
                                    scope.launch {
                                        // 滑出的同时把牌堆推进一格；动画结束再归零，避免闪一下
                                        animate(dragX, direction * cardWidthPx * 1.3f,
                                            animationSpec = tween(durationMillis = 180)) { value, _ -> dragX = value }
                                        front = (front + 1) % count
                                        dragX = 0f
                                    }
                                } else {
                                    settle(0f)
                                }
                            },
                            onHorizontalDrag = { change, delta ->
                                change.consume()
                                dragX += delta
                            },
                        )
                    }
                },
            ),
    ) {
        // 倒序绘制：最上面那张（depth 0）最后画，位于最前
        (0 until visible).reversed().forEach { depth ->
            val index = (front + depth) % count
            val item = items[index]
            // 拖动时后面各层朝 depth-1 的位置靠拢，形成「牌堆前进」的连续视觉
            val effectiveDepth = (depth - progress).coerceAtLeast(0f)
            val isFront = depth == 0
            Box(
                modifier = Modifier
                    .offset(
                        x = offsetStep * effectiveDepth + (if (isFront) with(density) { dragX.toDp() } else 0.dp),
                        y = offsetStep * effectiveDepth,
                    )
                    .size(width = cardWidth, height = cardHeight)
                    .graphicsLayer {
                        // 顶卡跟手倾斜，最大约 6 度，滑动有手感
                        rotationZ = if (isFront && cardWidthPx > 0f) (dragX / cardWidthPx) * 6f else 0f
                    }
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), clip = false)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, border, RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .combinedClickable(onClick = { frontClick(item, index) }, onLongClick = onLongClick?.let { cb -> { cb(index) } }),
            ) {
                MediaImage(item, onError = onItemError?.let { cb -> { cb(index) } })
                if (item.isVideo) VideoBadge(Modifier.align(Alignment.TopStart).padding(8.dp))
            }
        }
        // 位置角标：右上角。>1 张时显示「当前/总数」，滑动时有进度感
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = if (count > 1) "${front + 1}/$count" else count.toString(),
                color = Color.White,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * 横向轮播（Carousel）：横向滑动的卡片流，底部圆点指示当前位置；视频在左上角有播放标记。
 * 设计稿：卡片 162×122dp，间距 8dp，圆角 16dp；圆点 6dp，当前页拉长为 18dp。
 */
@Composable
fun MediaCarousel(
    items: List<MediaItem>,
    modifier: Modifier = Modifier,
    theme: MediaTheme = MediaTheme.Light,
    cardWidth: Dp = 162.dp,
    cardHeight: Dp = 122.dp,
    gap: Dp = 8.dp,
    onClick: (index: Int) -> Unit = {},
    onLongClick: ((index: Int) -> Unit)? = null,
    /** 点了一张**已标记失败**的卡片：交给调用方去重新取地址（整改清单 P1-10）。 */
    onRetry: ((index: Int) -> Unit)? = null,
    /** 某张卡片自然加载失败：报给调用方合并进状态。 */
    onItemError: ((index: Int) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val listState = rememberLazyListState()
    val current by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, items.lastIndex) } }
    Column(modifier = modifier.width(cardWidth + gap * 1.6f), horizontalAlignment = Alignment.CenterHorizontally) {
        LazyRow(
            state = listState,
            modifier = Modifier.height(cardHeight),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(items.size) { index ->
                val item = items[index]
                Box(
                    modifier = Modifier
                        .size(width = cardWidth, height = cardHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .combinedClickable(
                            onClick = { if (onRetry != null && item.failed) onRetry(index) else onClick(index) },
                            onLongClick = onLongClick?.let { cb -> { cb(index) } },
                        ),
                ) {
                    MediaImage(item, onError = onItemError?.let { cb -> { cb(index) } })
                    if (item.isVideo) VideoBadge(Modifier.align(Alignment.TopStart).padding(9.dp))
                }
            }
        }
        if (items.size > 1) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.indices.forEach { index ->
                    val active = index == current
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (active) 18.dp else 6.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(dotColor(theme, active)),
                    )
                }
            }
        }
    }
}

/** 气泡外壳：统一的圆角、内边距和浅色/深色底色；内容由调用方放入。 */
@Composable
fun MediaBubble(
    modifier: Modifier = Modifier,
    theme: MediaTheme = MediaTheme.Light,
    content: @Composable () -> Unit,
) {
    val background = if (theme == MediaTheme.Light) Color.White else Color(0xFF1C1C1E)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(background)
            .padding(12.dp),
    ) {
        content()
    }
}

// ---------- 内部组件 ----------

/**
 * 图片本体（整改清单 P1-10）：
 * - 自然加载失败 → [onError] 报给调用方合并进状态，卡片显示失败占位（灰底 + 破损图标 + 文案）；
 * - [MediaItem.failed] 时不重新请求，直接显示失败占位；[MediaItem.retrying] 时占位上叠一个进度圈；
 * - [MediaItem.attempt] 拼进 Coil 缓存 key，点一次重试换一次 key → 强制重新解码本机文件 / 重新请求远程地址。
 */
@Composable
private fun MediaImage(item: MediaItem, onError: (() -> Unit)? = null) {
    val failed = item.failed
    val stateful = statefulModel(item)
    // 失败且未点重试：不发请求（model=null），避免每次重组都重新烧一次必失败的加载
    val model: Any? = if (failed && !item.retrying) null else stateful
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = model,
            contentDescription = item.contentDescription,
            contentScale = ContentScale.Crop,
            onError = { onError?.invoke() },
            modifier = Modifier.fillMaxSize(),
        )
        if (failed) {
            MediaLoadFailedOverlay(retrying = item.retrying, compact = true)
        }
    }
}

/**
 * 给带重试次数的条目构造 Coil 模型。attempt>0 时把次数拼进 memoryCacheKey（diskCacheKey
 * 默认跟随它）：每次重试换一个新 key，内存 / 磁盘缓存必然未命中，本机文件被强制重新解码、
 * 远程地址被强制重新请求——否则内存缓存里的失败结果会被直接回放，「重试」就变成
 * 永远在重放同一个错误。
 *
 * 结果必须 remember：ImageRequest 不实现 equals，每次重组 new 一个会让 Coil 把每次重组
 * 都当成「换了图」重新加载一遍（列表滚动 / 流式刷新时就是无限重载）。
 */
@Composable
fun rememberStatefulImageModel(raw: Any, attempt: Int): Any {
    val context = LocalContext.current
    return remember(raw, attempt) {
        if (attempt <= 0) raw
        else ImageRequest.Builder(context).data(raw).memoryCacheKey("retry:$attempt:$raw").build()
    }
}

/** 卡片里图片本体用的包装（见 [rememberStatefulImageModel]）。 */
@Composable
private fun statefulModel(item: MediaItem): Any = rememberStatefulImageModel(item.model, item.attempt)

/**
 * 失败占位：灰底 + 破损图片图标 + 「图片加载失败 / 点击重试」。
 * [compact] 用于缩略卡片（小图标小字）；全屏查看器用大一号。
 */
@Composable
fun MediaLoadFailedOverlay(retrying: Boolean, compact: Boolean, modifier: Modifier = Modifier) {
    val iconSize = if (compact) 26.dp else 48.dp
    val textSize = if (compact) 11.sp else 15.sp
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF3A3F44)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (retrying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    color = Color.White.copy(alpha = 0.9f),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.BrokenImage,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(iconSize),
                )
            }
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
            Text(
                text = stringResource(if (retrying) R.string.mediaview_image_retrying else R.string.mediaview_image_load_failed),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = textSize,
            )
        }
    }
}

/** 视频标记：左上角半透明圆底 + 播放三角。 */
@Composable
private fun VideoBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "▶", color = Color.White, fontSize = 12.sp)
    }
}

private fun dotColor(theme: MediaTheme, active: Boolean): Color = when (theme) {
    MediaTheme.Light -> if (active) Color(0xFF5B6470) else Color(0xFFC7CCD1)
    MediaTheme.Dark -> if (active) Color(0xFFE6E7E9) else Color(0xFF3A3F44)
}

