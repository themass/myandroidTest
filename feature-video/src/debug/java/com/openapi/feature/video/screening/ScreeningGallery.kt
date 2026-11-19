package com.openapi.feature.video.screening

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * 图集原生页，三层：封面瀑布流 → 图集网格 → 全屏阅览。
 *
 * 与设计稿一致的点：
 * - 封面用双列受控瀑布流（按各自的宽高比），不是等高卡片；
 * - 图集内是规则缩略图网格，点第 N 张就从第 N 张全屏，返回还在原来的滚动位置（列表状态由
 *   `rememberLazyListState` 持有，没有被重建）；
 * - 全屏支持左右滑动、双指缩放、位置计数；缩放后拖动查看细节。
 */
@Composable
internal fun ScreeningGallery(activity: ScreeningActivity, onExit: () -> Unit) {
    var album by rememberSaveable { mutableStateOf<String?>(null) }
    var viewing by rememberSaveable { mutableStateOf(-1) }
    val coverState = rememberLazyListState()
    val detail = album?.let(albums::getValue)

    androidx.activity.compose.BackHandler(enabled = viewing >= 0 || album != null) {
        when {
            viewing >= 0 -> viewing = -1
            album != null -> album = null
            else -> onExit()
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        if (album == null) {
            AlbumCovers(coverState, onOpen = { album = it }, onExit = onExit)
        } else if (detail != null) {
            AlbumContents(detail, onBack = { album = null }, onOpen = { viewing = it })
        }
        val shots = detail?.shots.orEmpty()
        if (viewing in shots.indices) {
            Lightbox(activity, shots, viewing) { viewing = it }
        }
    }
}

@Composable
private fun AlbumCovers(state: LazyListState, onOpen: (String) -> Unit, onExit: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("全部") }
    val shown = albumOrder.map(albums::getValue)
        .filter { filter == "全部" || it.place.startsWith(filter) || (filter == "有更新" && it.shots.size >= 4) }
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding(), state = state,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 26.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回频道") }
                Text("图集", fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text("${albumOrder.size} 本", color = Muted, fontSize = 12.sp)
            }
            Text("一册一个地方，\n翻到最后一张为止。", color = White, fontSize = 37.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(14.dp))
            FilterRow(listOf("全部", "野外放映", "夜行电台", "海边来信", "有更新"), filter) { filter = it }
            Spacer(Modifier.height(12.dp))
        }
        if (shown.isEmpty()) {
            item { Text("这个筛选下还没有图集，换一个试试。", Modifier.padding(14.dp), color = Muted) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) { column ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        shown.filterIndexed { index, _ -> index % 2 == column }.forEach { book ->
                            Column(Modifier.clip(Tile).clickable { onOpen(book.id) }) {
                                Box(Modifier.fillMaxWidth().aspectRatio(book.coverRatio)) {
                                    Cover(book.cover, Modifier.matchParentSize())
                                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x33141729), Ink))))
                                    Text(
                                        "${book.shots.size} 张",
                                        Modifier.align(Alignment.TopEnd).padding(8.dp)
                                            .background(Ink.copy(alpha = .72f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 7.dp, vertical = 3.dp),
                                        fontSize = 11.sp,
                                    )
                                    Column(Modifier.align(Alignment.BottomStart).padding(11.dp)) {
                                        Text(book.title, color = White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(book.place, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumContents(book: Album, onBack: () -> Unit, onOpen: (Int) -> Unit) {
    val state = rememberLazyListState()
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding(), state = state,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
    ) {
        item {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回图集") }
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 11f).clip(RoundedCornerShape(12.dp, 32.dp, 12.dp, 12.dp))) {
                Cover(book.cover, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink))))
                Column(Modifier.align(Alignment.BottomStart).padding(19.dp)) {
                    Text(book.place, color = Peach, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(book.title, color = White, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black)
                    Text(book.description, color = Muted, fontSize = 13.sp)
                }
            }
            Heading("全部照片", "${book.shots.size} 张")
        }
        items(book.shots.chunked(3)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEachIndexed { slot, shot ->
                    val index = book.shots.indexOf(shot)
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(7.dp)).clickable { onOpen(index) },
                    ) {
                        Cover(shot.asset, Modifier.matchParentSize())
                        Text(
                            shot.caption,
                            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3141729))))
                                .padding(horizontal = 6.dp, vertical = 5.dp),
                            color = White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                if (row.isEmpty()) Spacer(Modifier.width(0.dp))
            }
            Spacer(Modifier.height(5.dp))
        }
    }
}

/** 全屏阅览：滑动换张、双指缩放、单击开关信息、返回收起。 */
@Composable
private fun Lightbox(activity: ScreeningActivity, shots: List<Shot>, start: Int, onMove: (Int) -> Unit) {
    val pager = rememberPagerState(initialPage = start.coerceIn(shots.indices)) { shots.size }
    var chrome by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(pager.currentPage) { zoom = 1f; pan = Offset.Zero }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = zoom <= 1.01f,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val shot = shots[page]
            Box(Modifier.fillMaxSize().pointerInput(shot.id) {
                detectTransformGestures { _, drag, gesture, _ ->
                    zoom = (zoom * gesture).coerceIn(1f, 4f)
                    pan = if (zoom > 1f) pan + drag else Offset.Zero
                }
            }) {
                Cover(
                    shot.asset,
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom; scaleY = zoom
                            translationX = pan.x; translationY = pan.y
                        },
                )
            }
        }
        Box(Modifier.matchParentSize().pointerInput(Unit) {
            detectTapGestures { chrome = !chrome }
        })
        if (chrome) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .72f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onMove(-1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "收起", tint = White) }
                Column(Modifier.weight(1f)) {
                    val shot = shots[pager.currentPage.coerceIn(shots.indices)]
                    Text(shot.caption, color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${pager.currentPage + 1} / ${shots.size}", color = Muted, fontSize = 12.sp)
                }
                if (zoom > 1.01f) TextButton({ zoom = 1f; pan = Offset.Zero }) { Text("还原") }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .78f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { }) { Icon(Icons.Default.FavoriteBorder, null, Modifier.size(16.dp)); Text(" 收藏") }
                TextButton(onClick = { }) { Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Text(" 下载") }
                Spacer(Modifier.weight(1f))
                Text("双指缩放 · 左右滑动", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        if (chrome) {
            // 滑动指示：细线在底部，比圆点更克制
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).height(2.dp).fillMaxWidth(0.4f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                shots.indices.forEach { index ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().background(
                            if (index == pager.currentPage) Mint else White.copy(alpha = .28f),
                        ),
                    )
                }
            }
        }
        val glow by animateFloatAsState(if (zoom > 1f) 1f else 0f, label = "zoomHint")
        if (glow > 0f && chrome) {
            Text(
                "${(zoom * 100).toInt()}%",
                Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 16.dp)
                    .background(Ink.copy(alpha = .7f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                color = Mint, fontSize = 11.sp,
            )
        }
    }
}
