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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 一个媒体条目：图片或视频（视频在缩略图上显示播放标记）。[model] 可以是本地路径、URI 或网络地址。 */
data class MediaItem(
    val model: Any,
    val isVideo: Boolean = false,
    val contentDescription: String? = null,
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
) {
    if (items.isEmpty()) return
    val count = items.size
    val border = if (theme == MediaTheme.Light) Color.White else Color(0xFF2C2C2E)
    val visible = minOf(count, STACK_VISIBLE)
    val totalWidth = cardWidth + offsetStep * (visible - 1)
    val totalHeight = cardHeight + offsetStep * (visible - 1)

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
                    .combinedClickable(onClick = { onClick(index) }, onLongClick = onLongClick?.let { cb -> { cb(index) } }),
            ) {
                MediaImage(item)
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
                        .combinedClickable(onClick = { onClick(index) }, onLongClick = onLongClick?.let { cb -> { cb(index) } }),
                ) {
                    MediaImage(item)
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

@Composable
private fun MediaImage(item: MediaItem) {
    AsyncImage(
        model = item.model,
        contentDescription = item.contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
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

