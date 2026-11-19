package com.openapi.feature.video.screening

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal val Ink = Color(0xFF141729)
internal val Lift = Color(0xFF252A43)
internal val Muted = Color(0xFFBCC2D7)
internal val Mint = Color(0xFFADF7D9)
internal val Peach = Color(0xFFFFC1A4)
internal val White = Color(0xFFF7F5F0)
internal val Tile = RoundedCornerShape(13.dp)

class ScreeningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Mint, onPrimary = Ink, background = Ink,
                onBackground = White, surface = Ink, onSurface = White, surfaceVariant = Lift, onSurfaceVariant = Muted)) {
                ScreeningHome(this)
            }
        }
    }

    internal fun immersive(on: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            if (on) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

@Composable
private fun ScreeningHome(activity: ScreeningActivity) {
    var channel by remember { mutableStateOf<Channel?>(null) }
    var selection by remember { mutableStateOf<ScreeningSource?>(null) }
    val holder = rememberSaveableStateHolder()
    val preferences = remember { activity.getSharedPreferences("screening-preview", 0) }
    val progress = remember { mutableStateMapOf<String, Long>().apply {
        reels.keys.forEach { put(it, preferences.getLong("position:$it", if (it == "ridge") 4200L else 0L)) }
    } }
    val onReel: (Reel) -> Unit = { selection = ScreeningSource(it.title, it.description, it.id) }
    BackHandler(channel != null && selection == null) { channel = null }
    Surface(Modifier.fillMaxSize()) {
        val selected = selection
        val current = channel
        when {
            selected != null -> key(selected) {
                ScreeningPlayback(activity, selected, current?.title.orEmpty(), progress[selected.asset] ?: 0L,
                    onExit = { selection = null }, onSave = { position -> selected.asset?.let {
                        progress[it] = position
                        preferences.edit().putLong("position:$it", position).apply()
                    } })
            }
            current != null -> holder.SaveableStateProvider(current.title) {
                Library(current, progress, { channel = null }, onReel, { selection = it })
            }
            else -> holder.SaveableStateProvider("channels") {
                Channels(onChannel = { channel = it }, onPreview = { onReel(reels.getValue("ridge")) })
            }
        }
    }
}

@Composable
internal fun Cover(id: String, modifier: Modifier = Modifier) {
    val bitmap = coverBitmap(LocalContext.current, id)
    Box(modifier.background(Lift)) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun Channels(onChannel: (Channel) -> Unit, onPreview: () -> Unit) {
    var filter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("全部") }
    val shown = channels.filter { filter == "全部" || it.category == filter }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), state = rememberLazyListState(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, tint = Mint)
                Text("放映场", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text("原生预览", color = Muted, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("频道", color = White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(24.dp).height(3.dp).background(Mint, Tile))
                }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = { onChannel(samples) }) { Text("本地视频", color = Muted, fontSize = 18.sp) }
            }
            Spacer(Modifier.height(13.dp))
            Box(Modifier.fillMaxWidth().height(352.dp).clip(RoundedCornerShape(13.dp, 38.dp, 13.dp, 13.dp))) {
                Cover("ridge", Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x45202340), Ink))))
                Column(Modifier.align(Alignment.BottomStart).padding(23.dp)) {
                    Text("本周放映", Modifier.background(Peach, RoundedCornerShape(5.dp)).padding(6.dp), color = Ink,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("云层\n之上。", color = White, fontSize = 59.sp, lineHeight = 58.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(9.dp))
                    Text(reels.getValue("ridge").description, color = White, fontSize = 13.sp)
                    Spacer(Modifier.height(13.dp))
                    Button(onClick = onPreview, shape = RoundedCornerShape(9.dp)) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(7.dp)); Text("看预告")
                    }
                }
            }
            Heading("逛频道", "${shown.size} 个频道")
            FilterRow(listOf("全部", "电影", "夜色", "自定义"), filter) { filter = it }
            Spacer(Modifier.height(9.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) { column ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        shown.filterIndexed { index, _ -> index % 2 == column }.forEach { group ->
                            Box(Modifier.fillMaxWidth().aspectRatio(group.ratio).clip(Tile).clickable { onChannel(group) }) {
                                Cover(group.cover, Modifier.matchParentSize())
                                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Ink))))
                                Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                                    Text(group.title, color = White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                                    Text("${group.reels.size} 个片段", color = Muted, fontSize = 12.sp)
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
private fun Library(channel: Channel, progress: Map<String, Long>, back: () -> Unit, onReel: (Reel) -> Unit,
                    onFile: (ScreeningSource) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(OpenDocument()) { uri -> uri?.let {
        onFile(ScreeningSource(documentName(context, it), "在此设备上播放", uri = it))
    } }
    var filter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("全部") }
    var poster by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val items = channel.reels.map(reels::getValue).filter { filter != "未观看" || (progress[it.id] ?: 0) == 0L }
    val resume = channel.reels.firstOrNull { (progress[it] ?: 0L) in 1 until SAMPLE_MS }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), state = rememberLazyListState(),
        verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp)) {
        item {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回频道") }
            Box(Modifier.fillMaxWidth().height(215.dp).clip(RoundedCornerShape(12.dp, 32.dp, 12.dp, 12.dp))) {
                Cover(channel.cover, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink))))
                Column(Modifier.align(Alignment.BottomStart).padding(19.dp)) {
                    Text(channel.category, color = Peach, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(channel.title, color = White, fontWeight = FontWeight.Black, fontSize = 42.sp, lineHeight = 48.sp)
                    Text("${channel.reels.size} 个样片", color = Muted)
                }
            }
            if (channel == samples) FilledTonalButton(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("打开手机里的视频")
            }
            resume?.let { id ->
                Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(Tile).background(Lift).clickable { onReel(reels.getValue(id)) }.padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, tint = Mint)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text("继续看《${reels.getValue(id).title}》", fontWeight = FontWeight.Bold)
                        Text("从 ${time(progress.getValue(id))} 接着播", color = Muted, fontSize = 12.sp)
                    }
                    Text("续播", color = Mint)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("全部视频", Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Black)
                IconButton(onClick = { poster = !poster }) { Icon(if (poster) Icons.Default.ViewList else Icons.Default.GridView, if (poster) "切换列表视图" else "切换海报视图") }
            }
            FilterRow(listOf("全部", "未观看"), filter) { filter = it }
        }
        if (items.isEmpty()) item { Text("没有未观看的样片。切回「全部」继续浏览。", Modifier.padding(14.dp), color = Muted) }
        if (poster) items(items.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { reel -> ReelCard(reel, progress[reel.id] ?: 0, true, Modifier.weight(1f), onReel) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        } else items(items, key = { it.id }) { reel -> ReelCard(reel, progress[reel.id] ?: 0, false, Modifier.fillMaxWidth(), onReel) }
    }
}

@Composable
private fun ReelCard(reel: Reel, position: Long, poster: Boolean, modifier: Modifier, open: (Reel) -> Unit) {
    val art: @Composable (Modifier) -> Unit = { m ->
        Box(m.aspectRatio(if (poster) reel.ratio else 16f / 9).clip(Tile)) {
            Cover(reel.id, Modifier.matchParentSize())
            Text("0:12", Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Ink, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp), fontSize = 11.sp)
            if (position > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth((position.toFloat() / SAMPLE_MS).coerceIn(0f, 1f)).height(3.dp).background(Mint))
        }
    }
    val title: @Composable () -> Unit = {
        Text(reel.title, color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(when { position >= SAMPLE_MS -> "已看完"; position > 0 -> "已看到 ${time(position)}"; else -> "未观看" }, color = Muted, fontSize = 12.sp)
    }
    val clickable = modifier.clip(Tile).clickable { open(reel) }
    if (poster) Column(clickable) { art(Modifier.fillMaxWidth()); Spacer(Modifier.height(5.dp)); title() }
    else Row(clickable, verticalAlignment = Alignment.CenterVertically) {
        art(Modifier.width(130.dp)); Column(Modifier.padding(horizontal = 13.dp)) { title() }
    }
}

@Composable
internal fun Heading(title: String, detail: String = "") {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        if (detail.isNotEmpty()) Text(detail, color = Muted, fontSize = 12.sp)
    }
}

@Composable
internal fun FilterRow(labels: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(labels) { name -> FilterChip(selected = name == selected, onClick = { onSelect(name) }, label = { Text(name) },
            shape = RoundedCornerShape(8.dp), colors = FilterChipDefaults.filterChipColors(containerColor = Lift,
                selectedContainerColor = Mint, labelColor = Muted, selectedLabelColor = Ink), border = null) }
    }
}
