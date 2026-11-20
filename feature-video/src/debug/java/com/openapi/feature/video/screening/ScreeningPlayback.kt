package com.openapi.feature.video.screening

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openapi.feature.video.screening.kernel.Kernel
import com.openapi.feature.video.screening.kernel.KernelHost
import com.openapi.cast.DlnaDevice
import com.openapi.cast.DlnaDiscoveryCallback
import com.openapi.cast.JavaCaster
import com.openapi.common.net.http.NetConfig
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.feature.video.subtitle.local.ScreeningCaption
import com.openapi.feature.video.subtitle.style.CaptionOverlay
import com.openapi.feature.video.subtitle.style.CaptionPosition
import com.openapi.feature.video.subtitle.style.captionPresets
import com.openapi.feature.video.subtitle.style.captionPresetIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.io.StringReader

/**
 * 一个可播放的东西。
 *
 * [key] 是它在服务端 / 本机上稳定的身份：进度、收藏、弹幕分窗都用它，不用 uri 字符串——
 * 懒播放地址每次可能不一样，拿地址当 key 会让同一片子的进度对不上。
 */
internal data class ScreeningSource(
    val title: String,
    val description: String,
    val asset: String? = null,
    val uri: Uri? = null,
    val key: String = asset ?: uri.toString(),
)

internal fun documentName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
}.getOrNull() ?: "本地文件"

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreeningPlayback(activity: ScreeningActivity, source: ScreeningSource, channel: String, initial: Long,
                               onExit: () -> Unit, onSave: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { context.getSharedPreferences("screening-preview", 0) }
    val feedStore = remember { ScreeningFeedStore(context) }
    // 服务端字幕只对服务端条目有意义：随包样片和手机上的文件没有对应的服务端记录
    val serverVideoId = remember(source.key) { serverIdOf(source.key) }
    val id = source.key
    // 两套内核各占一格画面槽，换内核在播放中途完成，见 KernelHost
    val host = remember { KernelHost(context) }
    LaunchedEffect(source) {
        // IJK / 系统内核吃不了 asset://，随包的样片先落到 cache 再交给内核
        val file = withContext(Dispatchers.IO) {
            source.asset?.let { id ->
                runCatching {
                    File(context.cacheDir, "screening-$id.mp4").takeIf { it.length() > 0 } ?: run {
                        File(context.cacheDir, "screening-$id.mp4").also { out ->
                            context.assets.open("$id.mp4").use { input -> out.outputStream().use(input::copyTo) }
                        }
                    }
                }.getOrNull()
            }
        }
        host.open(
            uri = source.uri ?: Uri.parse("asset:///${source.asset}.mp4"),
            file = file,
            // 随包样片长度已知（SAMPLE_MS），越界的旧进度直接丢掉；
            // 网络片总长度这时还不知道，先按存的进度起播，host 拿到 duration 自己会夹
            startMs = if (initial <= 0L) 0L else if (source.asset != null) initial.coerceAtMost(SAMPLE_MS) else initial,
        )
    }
    var position by remember { mutableLongStateOf(initial) }
    var width by remember { mutableLongStateOf(if (source.asset != null) SAMPLE_MS else 0L) }
    val playing = host.playing
    var dragging by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var full by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf<String?>(null) }
    var captions by remember { mutableStateOf<ScreeningCaption?>(null) }
    var demo by remember { mutableStateOf<ScreeningCaption?>(null) }
    var captionName by remember { mutableStateOf("") }
    var captionSource by remember { mutableIntStateOf(0) }
    // 服务端字幕：列表（null = 还没成功取到）、已加载的那份、正在加载谁
    var serverList by remember { mutableStateOf<List<SubtitleItem>?>(null) }
    var serverLoading by remember { mutableStateOf(false) }
    var serverCaption by remember { mutableStateOf<ScreeningCaption?>(null) }
    var serverCaptionName by remember { mutableStateOf("") }
    var serverBusy by remember { mutableStateOf<Long?>(null) }
    var offset by remember { mutableIntStateOf(0) }
    // 字幕样式：预设序号，跟着这个播放器偏好走（和收藏同一个 prefs）
    var presetIndex by remember { mutableIntStateOf(captionPresetIndex(store.getInt("caption-style", 0))) }
    val captionStyle = captionPresets[presetIndex].style
    var info by remember { mutableStateOf("") }
    var speed by remember { mutableFloatStateOf(1f) }
    var danmaku by remember { mutableStateOf(false) }
    // 弹幕来源：没有服务端时默认本地样例；假服务端做离线链路演示；在线弹幕走真 HTTP 收发
    var danmakuSource by remember { mutableStateOf(DANMAKU_SAMPLE) }
    val danmakuToken = remember { { PreferenceUtils.getPrefString(context, NetConfig.tokenHeader(), null) } }
    val feed = remember(danmakuSource, id) {
        DanmakuFeed(
            when (danmakuSource) {
                DANMAKU_SERVER -> InMemoryDanmakuSource(TimelineKey.local(id))
                DANMAKU_ONLINE -> HttpDanmakuSource(
                    // 分窗 key 用页面暴露的稳定 id 字符串，不写死样例 id
                    TimelineKey.local(id),
                    OkHttpDanmakuTransport(),
                    accessToken = danmakuToken,
                )
                else -> SampleDanmakuSource(TimelineKey.local(id))
            },
        )
    }
    var danmakuState by remember { mutableStateOf(feed.state) }
    var danmakuDraft by remember { mutableStateOf("") }
    DisposableEffect(feed) {
        feed.onChange = { danmakuState = feed.state }
        danmakuState = feed.state
        onDispose { feed.onChange = null }
    }
    LaunchedEffect(feed, danmaku) {
        if (!danmaku) return@LaunchedEffect
        // 打开或换来源就当一次 seek：清掉上一份缓存，从当前位置重新排
        var last = host.positionNow()
        feed.seek(last)
        while (danmaku) {
            // 进度条和快进快退都走 host.seekTo，这里按「一次跳太远」认 seek：
            // 换代数作废在途回包，从新位置重新排队，旧位置的弹幕不会冒出来
            val now = host.positionNow()
            if (kotlin.math.abs(now - last) > feed.windowMs) feed.seek(now)
            last = now
            feed.sync(now)
            delay(400)
        }
    }
    var fill by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(store.getBoolean("favorite:$id", false)) }
    val discovery = remember { DlnaDiscoveryCallback() }
    val caster = remember { JavaCaster(context) }
    var devices by remember { mutableStateOf<List<DlnaDevice>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    fun refresh() {
        host.tick()
        position = host.position
        if (host.duration > 0) width = host.duration
    }
    fun jump(delta: Long) {
        host.seekTo((host.position + delta).coerceIn(0, width)); refresh(); interaction++
    }
    fun setFull(value: Boolean) { full = value; locked = false; chrome = true; activity.immersive(value) }
    BackHandler {
        when { panel != null -> panel = null; locked -> { locked = false; interaction++ }; full -> setFull(false); else -> onExit() }
    }
    DisposableEffect(host) {
        val lifecycle = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) host.pause() }
        activity.lifecycle.addObserver(lifecycle)
        onDispose {
            onSave(host.position)
            activity.lifecycle.removeObserver(lifecycle)
            host.release(); discovery.close(); caster.close()
            if (full) activity.immersive(false)
        }
    }
    LaunchedEffect(host) { while (true) { refresh(); delay(125) } }
    LaunchedEffect(full, playing, locked, panel, interaction) {
        if (full && playing && !locked && panel == null && chrome) { delay(3500); chrome = false }
    }
    val picker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val name = documentName(context, it)
                        val input = checkNotNull(context.contentResolver.openInputStream(it))
                        val track = InputStreamReader(input, Charsets.UTF_8.newDecoder()).use(::ScreeningCaption)
                        name to track
                    }
                }
                result.onSuccess { pair ->
                    captions = pair.second; captionName = pair.first; captionSource = 2; offset = 0; info = "已导入：${pair.first}"
                }.onFailure { info = "读取失败：请选择 UTF-8 的 SRT 或 WebVTT。" }
            }
        }
    }
    // 演示字幕是一份真实的 WebVTT，走和导入文件完全相同的解析路径，不是写死的字符串
    LaunchedEffect(source.asset) {
        if (source.asset == null) return@LaunchedEffect
        demo = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("screening.vtt").use { stream ->
                    InputStreamReader(stream, Charsets.UTF_8.newDecoder()).use(::ScreeningCaption)
                }
            }.getOrNull()
        }
    }
    // 服务端字幕列表：打开「字幕」面板才取，一次会话取一遍就够；失败保持 null，面板里给重试
    LaunchedEffect(panel, serverVideoId) {
        if (panel != "字幕" || serverVideoId == null || serverList != null || serverLoading) return@LaunchedEffect
        serverLoading = true
        serverList = runCatching { withContext(Dispatchers.IO) { feedStore.subtitles(serverVideoId) } }.getOrNull()
        serverLoading = false
    }
    LaunchedEffect(host.failure) { host.failure?.let { error = it } }
    LaunchedEffect(fill) { host.setFill(fill) }
    val cue = when (captionSource) {
        1 -> demo?.at(position, offset).orEmpty()
        2 -> captions?.at(position, offset).orEmpty()
        3 -> serverCaption?.at(position, offset).orEmpty()
        else -> ""
    }
    val seekBlock: @Composable () -> Unit = {
        Column {
            Slider(
                value = dragging ?: if (width > 0) (position.toFloat() / width).coerceIn(0f, 1f) else 0f,
                onValueChange = { dragging = it; interaction++ },
                onValueChangeFinished = {
                    dragging?.let { host.seekTo((width * it).toLong()); refresh() }
                    dragging = null
                },
                enabled = width > 0,
                modifier = Modifier.fillMaxWidth().height(34.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${time(dragging?.let { (it * width).toLong() } ?: position)} / ${time(width)}", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { setFull(!full) }) {
                    Icon(if (full) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, if (full) "退出全屏" else "全屏")
                }
            }
        }
    }
    val toolsRow: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().background(Lift, Tile), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(
                "字幕" to Icons.Default.Subtitles,
                (if (speed == 1f) "倍速" else "${speed}×") to Icons.Default.Speed,
                (if (host.switching) "换内核…" else "内核 · ${host.kernel.title}") to Icons.Default.Memory,
                "弹幕" to Icons.Default.ChatBubbleOutline,
                "更多" to Icons.Default.MoreHoriz,
            ).forEachIndexed { index, (name, picture) ->
                Column(
                    Modifier.weight(1f).heightIn(min = 51.dp).clip(Tile).clickable {
                        info = ""; panel = if (index == 1) "倍速" else name.substringBefore(" ·")
                    },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(picture, null, Modifier.size(17.dp), tint = if (index == 0 && captionSource != 0 || index == 3 && danmaku || index == 2) Mint else White)
                    Text(name, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
    val favorRow: @Composable () -> Unit = {
        Row(Modifier.background(Color(0xFF243A3F), Tile).padding(horizontal = 5.dp)) {
            TextButton(onClick = { liked = !liked; store.edit().putBoolean("favorite:$id", liked).apply() }) {
                Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, Modifier.size(16.dp))
                Text(if (liked) " 已收藏" else " 收藏")
            }
            TextButton(onClick = {
                panel = "电视投屏"; info = ""; devices = emptyList(); scanning = true
                discovery.discover { devices = it; scanning = false }
            }) {
                Icon(Icons.Default.Cast, null, Modifier.size(17.dp)); Text(" 电视投屏")
            }
        }
    }
    Column(Modifier.fillMaxSize().background(Ink).then(
        if (!full) Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()) else Modifier
    )) {
        Box(Modifier.fillMaxWidth().then(if (full) Modifier.weight(1f) else Modifier.aspectRatio(16f / 10)).background(Color.Black)) {
            AndroidView(
                factory = { host.view },
                modifier = Modifier.matchParentSize(),
            )
            Box(Modifier.matchParentSize().pointerInput(locked, full) {
                detectTapGestures(
                    onTap = { if (!locked) { chrome = !chrome; interaction++ } },
                    onDoubleTap = { tap ->
                        if (!locked) { jump(if (tap.x < size.width / 2) -10_000 else 10_000); chrome = true }
                    },
                )
            })
            // 弹幕占上半区：底部永远留给字幕和控件，二者不互相压
            if (danmaku && !locked) {
                DanmakuOverlay(
                    clock = { host.positionNow() },
                    state = danmakuState,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(.56f).align(Alignment.TopCenter),
                )
            }
            if (cue.isNotEmpty()) {
                // 通用字幕渲染层：样式（颜色/底衬/字号/位置/滚动）全走 CaptionStyle，放映场只决定贴边距
                CaptionOverlay(
                    cue, captionStyle,
                    Modifier.align(
                        when (captionStyle.position) {
                            CaptionPosition.BOTTOM -> Alignment.BottomCenter
                            CaptionPosition.MIDDLE -> Alignment.Center
                            CaptionPosition.TOP -> Alignment.TopCenter
                        }
                    )
                        .padding(
                            bottom = if (captionStyle.position == CaptionPosition.BOTTOM && full && chrome && !locked) 137.dp else 14.dp,
                            top = if (captionStyle.position == CaptionPosition.TOP) (if (full) 44.dp else 14.dp) else 0.dp,
                            start = 12.dp, end = 12.dp,
                        ),
                )
            }
            if ((chrome || !playing) && !locked) {
                Row(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Transparent)))
                        .windowInsetsPadding(if (full) WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal) else WindowInsets(0, 0, 0, 0))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ScreenIcon(Icons.AutoMirrored.Filled.ArrowBack, if (full) "退出全屏" else "返回列表", {
                        if (full) setFull(false) else onExit()
                    })
                    if (full) {
                        Text(
                            source.title,
                            Modifier.weight(1f).padding(start = 10.dp),
                            color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        ScreenIcon(Icons.Default.Lock, "锁定控件", { locked = true })
                    }
                }
                Row(
                    Modifier.align(Alignment.Center).offset(y = if (full) (-28).dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ScreenIcon(Icons.Default.Replay10, "快退 10 秒", { jump(-10_000) })
                    ScreenIcon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playing) "暂停" else "播放",
                        { host.toggle(); refresh(); interaction++ },
                        primary = true,
                    )
                    ScreenIcon(Icons.Default.Forward10, "快进 10 秒", { jump(10_000) })
                }
                if (full) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .88f))))
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        seekBlock()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.weight(1f)) { toolsRow() }
                            favorRow()
                        }
                    }
                }
            }
            if (locked) {
                TextButton(
                    onClick = { locked = false; chrome = true; interaction++ },
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                ) { Text("解锁") }
            }
        }
        if (!full) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 9.dp)) {
                seekBlock()
                Text(channel.ifEmpty { "本周放映" }, color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(source.title, color = White, fontSize = 35.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black)
                Text(source.description, color = Muted, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
                toolsRow()
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.CenterEnd) { favorRow() }
                error?.let { message -> Text(message, color = Peach, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
    }
    panel?.let { current ->
        ModalBottomSheet(onDismissRequest = { panel = null; interaction++ }, containerColor = Ink) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = if (full) 235.dp else 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
            ) {
                Text(current, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                when (current) {
                    "字幕" -> {
                        Setting("关闭字幕", "不影响播放和弹幕", captionSource == 0) { captionSource = 0 }
                        // 服务端字幕：只有服务端条目才有对应记录，随包样片和本地文件不显示这一段
                        serverVideoId?.let { videoId ->
                            when {
                                serverLoading -> Text("正在取字幕列表…", color = Muted, fontSize = 13.sp)
                                serverList == null -> {
                                    Text("服务端字幕还没取到。", color = Peach, fontSize = 13.sp)
                                    TextButton(onClick = {
                                        serverLoading = true
                                        scope.launch {
                                            serverList = runCatching { withContext(Dispatchers.IO) { feedStore.subtitles(videoId) } }.getOrNull()
                                            serverLoading = false
                                        }
                                    }) { Text("重试") }
                                }
                                serverList!!.isEmpty() -> Text("这个视频服务端没有字幕。", color = Muted, fontSize = 13.sp)
                                else -> serverList!!.forEach { item ->
                                    val chosen = captionSource == 3 && serverCaptionName == item.title
                                    Setting(
                                        item.title,
                                        (if (item.label.isNotEmpty()) item.label else "") +
                                            if (serverBusy == item.id) "  加载中…" else "",
                                        selected = chosen,
                                    ) {
                                        if (chosen) { captionSource = 3; return@Setting }
                                        if (serverCaption != null && serverCaptionName == item.title) { captionSource = 3; return@Setting }
                                        if (serverBusy != null) return@Setting
                                        serverBusy = item.id
                                        scope.launch {
                                            val loaded = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val text = feedStore.subtitleContent(item.id)
                                                    check(looksLikeSubtitle(text)) { "内容不像字幕" }
                                                    ScreeningCaption(StringReader(text!!))
                                                }
                                            }
                                            serverBusy = null
                                            loaded.onSuccess {
                                                serverCaption = it; serverCaptionName = item.title
                                                captionSource = 3; offset = 0; info = "已加载：${item.title}"
                                            }.onFailure {
                                                info = "字幕加载失败：${it.message ?: "网络或服务端问题"}"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Setting("导入本地字幕", "打开系统文件选择器，支持 SRT / WebVTT") {
                            // 同时允许 octet-stream：很多 srt 在系统库里没有注册成文本类型，只给 text/*
                            // 会让选择器把它们过滤掉；给 */* 又会让华为选择器停在「图片」分区
                            picker.launch(
                                arrayOf(
                                    "text/*",
                                    "application/x-subrip",
                                    "application/x-subtitle-vtt",
                                    "application/octet-stream",
                                ),
                            )
                        }
                        if (captions != null) Setting("已导入的字幕", captionName, captionSource == 2) { captionSource = 2 }
                        if (source.asset != null) Setting("使用演示字幕", "随包的 WebVTT，第 1 秒开始逐句出现", captionSource == 1) { captionSource = 1 }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("字幕延时", Modifier.weight(1f))
                            TextButton({ offset = (offset - 500).coerceAtLeast(-60_000) }) { Text("−500 ms") }
                            Text("${if (offset > 0) "+" else ""}$offset ms", color = Mint)
                            TextButton({ offset = (offset + 500).coerceAtMost(60_000) }) { Text("+500 ms") }
                        }
                        Text("正值让字幕更晚出现。", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("字幕样式", Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
                        captionPresets.forEachIndexed { index, preset ->
                            Setting(preset.title, preset.hint, presetIndex == index) {
                                presetIndex = index
                                store.edit().putInt("caption-style", index).apply()
                            }
                        }
                    }
                    "倍速" -> listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                        Setting("${value}×", if (value == 1f) "正常速度" else "", speed == value) {
                            speed = value; host.setSpeed(value); panel = null
                        }
                    }
                    "弹幕" -> {
                        Setting("关闭弹幕", "立即清屏，不影响字幕和播放", !danmaku) { danmaku = false }
                        // 来源切换：两个来源实现同一个 TimedCommentSource，渲染和车道调度完全共用
                        Setting("本地样例弹幕", "随包内置，无需网络", danmaku && danmakuSource == DANMAKU_SAMPLE) {
                            danmakuSource = DANMAKU_SAMPLE; danmaku = true
                        }
                        Setting("服务端弹幕", "链路演示：分窗拉取、发送回显、重试、撤回（假服务端，接口与真服务端一致）", danmaku && danmakuSource == DANMAKU_SERVER) {
                            danmakuSource = DANMAKU_SERVER; danmaku = true
                        }
                        Setting("在线弹幕", "真 HTTP 收发：分窗分页拉取、幂等发送、撤回（服务端未就绪时显示错误，可切回本地）", danmaku && danmakuSource == DANMAKU_ONLINE) {
                            danmakuSource = DANMAKU_ONLINE; danmaku = true
                        }
                        if (danmaku) {
                            OutlinedTextField(
                                value = danmakuDraft,
                                onValueChange = { danmakuDraft = it.take(40) },
                                placeholder = { Text("发一条弹幕，贴在 ${time(host.positionNow())}") },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                singleLine = true,
                            )
                            danmakuState.outgoing.lastOrNull()?.let { mine ->
                                val label = when (mine.state) {
                                    SendState.Sending -> "发送中"
                                    SendState.Sent -> "已发送"
                                    SendState.Failed -> "发送失败：${mine.error ?: "未知原因"}"
                                    SendState.Retracted -> "已撤回"
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("「${mine.text}」 $label", color = if (mine.state == SendState.Failed) Peach else Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    if (mine.state == SendState.Failed) TextButton(onClick = { scope.launch { feed.retry(mine.clientMessageId) } }) { Text("重试") }
                                    if (mine.state == SendState.Sent) TextButton(onClick = { scope.launch { feed.retract(mine.clientMessageId) } }) { Text("撤回") }
                                }
                            }
                            TextButton(
                                enabled = danmakuDraft.isNotBlank(),
                                onClick = {
                                    // 发送时刻固定为打开输入时的播放位置，避免打字时视频在走贴错场景
                                    val at = host.positionNow()
                                    val text = danmakuDraft
                                    danmakuDraft = ""
                                    scope.launch { feed.send(at, text) }
                                },
                            ) { Text("发送，贴在 ${time(host.positionNow())}") }
                        }
                        Text(
                            "弹幕跟着媒体时钟走：暂停就停住，倍速会一起变快变慢，拖进度条立刻换成那个位置的弹幕。" +
                                "固定在上半区，底部留给字幕。",
                            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    "更多" -> {
                        Setting("适应画面", "保留原始比例", !fill) { fill = false }
                        Setting("填满画面", "可能裁掉画面边缘", fill) { fill = true }
                    }
                    "内核" -> {
                        Text("换内核不重启播放：新内核静音准备好，第一帧上屏时才切过去。位置、倍速、字幕都不受影响；新内核播不了会自动退回原来的，并保留你的偏好设置。", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Kernel.entries.forEach { kind ->
                            Setting(
                                "${kind.title} 内核",
                                if (host.switching && host.kernel != kind) kind.note else kind.note,
                                host.kernel == kind && !host.switching,
                            ) {
                                if (host.kernel == kind) return@Setting
                                info = "正在切换到 ${kind.title}…"
                                host.select(kind)
                                panel = null
                            }
                        }
                        Text("选择会写回设置里的播放内核，别的页面打开视频也用同一个。", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    "电视投屏" -> {
                        Text("手机和电视须连接同一局域网。字幕和弹幕仅在手机显示。", color = Muted)
                        if (scanning) Text("正在搜索电视…", Modifier.padding(top = 12.dp))
                        else if (devices.isEmpty()) Text("未发现可投屏的电视。", Modifier.padding(top = 12.dp))
                        devices.forEach { device ->
                            Setting(device.friendlyName, "DLNA") {
                                info = "正在准备投屏…"
                                scope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val input = source.asset?.let { context.assets.open("$it.mp4") }
                                                ?: source.uri?.let(context.contentResolver::openInputStream)
                                            checkNotNull(input).use { stream ->
                                                File.createTempFile("screening-", ".mp4", context.cacheDir).also { out ->
                                                    try { out.outputStream().use { stream.copyTo(it) } }
                                                    catch (failure: Exception) { out.delete(); throw failure }
                                                }
                                            }
                                        }.getOrNull()
                                    }
                                    if (file == null) {
                                        info = "无法读取视频文件。"
                                    } else {
                                        caster.castLocal(device, file.path, source.title) { failure ->
                                            info = failure?.let { "投屏失败：$it" } ?: "已发送到 ${device.friendlyName}"
                                            if (failure == null) host.pause()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (info.isNotEmpty()) Text(info, Modifier.padding(top = 9.dp), color = Mint)
            }
        }
    }
}

@Composable
private fun Setting(title: String, hint: String, selected: Boolean = false, onClick: () -> Unit) {
    FilledTonalButton(
        onClick, Modifier.fillMaxWidth().padding(vertical = 2.dp), shape = Tile,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (selected) Color(0xFF29483E) else Lift),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = if (selected) Mint else White)
            if (hint.isNotEmpty()) Text(hint, color = Muted, fontSize = 11.sp)
        }
        if (selected) Icon(Icons.Default.Check, "已选中", tint = Mint)
    }
}

@Composable
private fun ScreenIcon(picture: ImageVector, label: String, onClick: () -> Unit, primary: Boolean = false) {
    IconButton(
        onClick,
        Modifier.size(if (primary) 56.dp else 48.dp)
            .background(if (primary) Mint else Ink.copy(alpha = .66f), CircleShape),
    ) {
        Icon(picture, label, tint = if (primary) Ink else White)
    }
}
