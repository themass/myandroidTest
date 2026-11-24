package com.openapi.feature.video.screening

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.openapi.feature.video.screening.kernel.KernelHost
import com.openapi.common.net.http.NetConfig
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.feature.video.subtitle.local.ScreeningCaption
import com.openapi.feature.video.subtitle.style.CaptionOverlay
import com.openapi.feature.video.subtitle.style.CaptionPosition
import com.openapi.feature.video.subtitle.style.captionPresets
import com.openapi.feature.video.subtitle.style.captionPresetIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.StringReader
import kotlin.math.roundToInt

/**
 * 开发入口显形开关：`adb shell setprop log.tag.ScreeningDbg DEBUG`。
 * 和 FeedPagerDbg 同一套路——默认关，正常构建零影响；调试弹幕这类入口不渲染进用户面板，
 * 但代码保留，联调时一条 setprop 就回来，不需要改代码重装。
 */
internal const val TAG_SCREENING_DBG = "ScreeningDbg"

/**
 * 一个可播放的东西。
 *
 * [key] 是它在服务端 / 本机上稳定的身份：进度、收藏、弹幕分窗都用它，不用 uri 字符串——
 * 懒播放地址每次可能不一样，拿地址当 key 会让同一片子的进度对不上。
 */
internal data class ScreeningSource(
    val title: String,
    val description: String,
    val uri: Uri? = null,
    val key: String = uri.toString(),
)

internal fun documentName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
}.getOrNull() ?: "本地文件"

/**
 * 播放区横滑快进退的档位换算：右滑（正位移）快进、左滑快退，每滑过 1/4 屏宽跳 30 秒。
 * 纯函数抽出来是为了 JVM 单测钉住方向和档位（手势回调里没法直接测）。
 */
internal fun dragSeekMs(totalDragPx: Float, viewportWidthPx: Float): Long {
    if (viewportWidthPx <= 0f) return 0L
    return (totalDragPx / (viewportWidthPx / 4f)).roundToInt() * 30_000L
}

/**
 * 现在是否存在「可切换的字幕来源」：服务端有轨道、已导入过文件、或这条片源能做实时识别，任一即可。
 * serverList 为 null 表示还没成功取到列表——这时不能断言服务端没有，只按另两条判。
 * 纯函数抽出来是为了 JVM 单测钉住判定口径（A22）。
 */
internal fun hasCaptionSourceChoice(
    serverList: List<SubtitleItem>?,
    hasImportedCaption: Boolean,
    realtimeAvailable: Boolean,
): Boolean = serverList?.isNotEmpty() == true || hasImportedCaption || realtimeAvailable

/**
 * 字幕面板里「关闭字幕」这一项要不要显示成选中（勾）。
 * A22：只在「当前确实关着」且「存在可切换来源」时才打勾——
 * 一条可开来源都没有时，勾会让用户以为「有字幕被我关了」，而实情是这条片子本来就没字幕。
 */
internal fun captionOffSettingSelected(captionSource: Int, hasCaptionSourceChoice: Boolean): Boolean =
    captionSource == 0 && hasCaptionSourceChoice

/**
 * 空态（没有任何可开来源）时并进「关闭字幕」hint 的原因说明（A22）。
 * 「没有导入过字幕文件」在空态恒成立（有导入就不算空态）；服务端那条只在确实取到过列表时才敢写死。
 */
internal fun captionNoSourceHint(serverList: List<SubtitleItem>?, realtimeAvailable: Boolean): String {
    val reasons = buildList {
        if (serverList != null) add("服务端没有字幕轨")
        add("没有导入过字幕文件")
        if (!realtimeAvailable) add("这个片源不能现场识别")
    }
    return "现在没有可开的字幕：" + reasons.joinToString("，")
}

/**
 * 「关闭字幕」已经是关闭态时再点一次的可见反馈（A22：写同值零变化，用户以为按钮坏了）。
 * 有可开来源和没有来源两种措辞分开，别把「没字幕」说给有字幕轨的片子。
 */
internal fun captionAlreadyOffNotice(
    serverList: List<SubtitleItem>?,
    hasImportedCaption: Boolean,
    realtimeAvailable: Boolean,
): String = if (hasCaptionSourceChoice(serverList, hasImportedCaption, realtimeAvailable)) {
    "字幕本来就是关的；要开字幕，直接选下面列出的字幕来源。"
} else {
    "字幕本来就是关的；这个视频没有可开的字幕轨。"
}

/**
 * 影院态底部走带要不要在 3.5 秒后自动收起。
 * imeVisible 是 A23 新加的闸门：edge-to-edge 下 adjustResize 失效，键盘弹着时收走带
 * 会把正在输入的弹幕发送栏整个藏掉，所以键盘没弹出才允许自动收。
 */
internal fun shouldAutoHideChrome(
    full: Boolean,
    playing: Boolean,
    locked: Boolean,
    panelOpen: Boolean,
    chromeVisible: Boolean,
    draft: String,
    imeVisible: Boolean,
): Boolean = full && playing && !locked && !panelOpen && chromeVisible && draft.isEmpty() && !imeVisible

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreeningPlayback(activity: ComponentActivity, source: ScreeningSource, channel: String, initial: Long,
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
    // 实时字幕：引擎在后台并行重解一路静音音频喂火山流式识别，只吃能重解的地址（见 realtimeMediaUrl）。
    // 随包样片已下线，重解地址就是条目的 uri 本身（http/file 能重解，content:// 不能）。
    val realtimeUrl = remember(source.key) {
        realtimeMediaUrl(source.uri?.toString(), source.uri?.scheme, null)
    }
    var realtimeText by remember { mutableStateOf("") }
    // "" = 没有消息；否则依次为加载中/失败原因，字幕面板的实时字幕条目直接显示它
    var realtimeStatus by remember { mutableStateOf("") }
    val realtimeRunner = remember { RealtimeSubtitleRunner(context) }
    DisposableEffect(realtimeRunner) { onDispose { realtimeRunner.stop() } }
    LaunchedEffect(source) {
        host.open(
            uri = checkNotNull(source.uri),
            file = null,
            // 网络片总长度这时还不知道，先按存的进度起播，host 拿到 duration 自己会夹
            startMs = initial,
        )
    }
    var position by remember { mutableLongStateOf(initial) }
    var width by remember { mutableLongStateOf(0L) }
    val playing = host.playing
    var dragging by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var full by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf<String?>(null) }
    var captions by remember { mutableStateOf<ScreeningCaption?>(null) }
    var captionName by remember { mutableStateOf("") }
    var captionSource by remember { mutableIntStateOf(0) }
    // 服务端字幕：列表（null = 还没成功取到）、已加载的那份、正在加载谁
    var serverList by remember { mutableStateOf<List<SubtitleItem>?>(null) }
    var serverLoading by remember { mutableStateOf(false) }
    var serverCaption by remember { mutableStateOf<ScreeningCaption?>(null) }
    var serverCaptionName by remember { mutableStateOf("") }
    var serverBusy by remember { mutableStateOf<Long?>(null) }
    var offset by remember { mutableIntStateOf(0) }
    DisposableEffect(realtimeRunner) { onDispose { realtimeRunner.stop() } }
    // 字幕样式：预设序号，跟着这个播放器偏好走（和收藏同一个 prefs）
    var presetIndex by remember { mutableIntStateOf(captionPresetIndex(store.getInt("caption-style", 0))) }
    val captionStyle = captionPresets[presetIndex].style
    var info by remember { mutableStateOf("") }
    var speed by remember { mutableFloatStateOf(1f) }
    // 实时字幕会话：开/关走 captionSource==4（顶部字幕键和「关闭字幕」都能关）；跳转或重开就重启会话——
    // 识别引擎是并行重解音频，不会跟播放器的进度条跳转，跳了就按新位置重连一路。
    // 放在 speed 声明之后：判定要按倍速折算墙钟推进量。
    LaunchedEffect(captionSource, realtimeUrl) {
        realtimeRunner.stop()
        if (captionSource != 4) return@LaunchedEffect
        val url = realtimeUrl ?: return@LaunchedEffect
        realtimeRunner.onLoading = { realtimeStatus = "正在连接识别服务…" }
        realtimeRunner.onText = {
            realtimeText = it
            if (realtimeStatus.isNotEmpty()) realtimeStatus = ""
        }
        realtimeRunner.onError = { realtimeStatus = "识别失败：$it" }
        realtimeRunner.onStopped = { realtimeStatus = "识别已停止，可以再开一次" }
        realtimeText = ""
        realtimeStatus = "正在连接识别服务…"
        realtimeRunner.start(url, host.positionNow(), { host.playing }, { host.positionNow() })
        try {
            var prevPos = host.positionNow()
            var prevElapsed = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(500)
                val pos = host.positionNow()
                val now = SystemClock.elapsedRealtime()
                if (realtimeSeekJumped(prevPos, pos, prevElapsed, now, speed, host.playing)) {
                    realtimeText = ""
                    realtimeRunner.start(url, pos, { host.playing }, { host.positionNow() })
                }
                prevPos = pos
                prevElapsed = now
            }
        } finally {
            realtimeRunner.stop()
        }
    }
    var danmaku by remember { mutableStateOf(serverVideoId != null) }
    // 弹幕来源：正式默认在线——服务端条目一进来就挂真时间轴（弹幕默认开着，互联网惯例）。
    // 手机文件没有服务端轴，来源仍走在线实现（拉不到内容、发出去也没人看），弹幕默认关。
    // 样例/假服务端只留在 setprop 显形的开发入口里，不再当任何默认。
    var danmakuSource by remember { mutableStateOf(DANMAKU_ONLINE) }
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
    // 播放区横滑快进退（市场惯例：右滑快进、左滑快退）：累计滑动量每过 1/4 屏宽跳 30 秒，
    // 回包气泡 0.9 秒后自己收，连滑就刷新同一个气泡而不是叠一层。
    var swipeSeconds by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(swipeSeconds) {
        if (swipeSeconds != null) { delay(900); swipeSeconds = null }
    }
    fun refresh() {
        host.tick()
        position = host.position
        if (host.duration > 0) width = host.duration
    }
    fun sendDanmaku() {
        val text = danmakuDraft.trim()
        if (text.isEmpty()) return
        // 发送时刻取按下这一刻的播放位置（和主流播放器口径一致），发完就清空，连发不用二次编辑
        val at = host.positionNow()
        danmakuDraft = ""
        scope.launch { feed.send(at, text) }
    }
    // 续播提示：从存档位置进来时说明一句「已续播」，并可一键回从头——
    // 用户点名「开头 10+ 秒被跳」：跳是续播没错，但要让用户看得见、可反悔。
    var resumeHinted by remember { mutableStateOf(initial > 0L) }
    LaunchedEffect(resumeHinted) {
        if (resumeHinted) { delay(6000); resumeHinted = false }
    }
    fun jump(delta: Long) {
        host.seekTo((host.position + delta).coerceIn(0, width)); refresh(); interaction++
    }
    fun setFull(value: Boolean) { full = value; locked = false; chrome = true; activity.setScreeningImmersive(value) }
    BackHandler {
        when { panel != null -> panel = null; locked -> { locked = false; interaction++ }; full -> setFull(false); else -> onExit() }
    }
    DisposableEffect(host) {
        val lifecycle = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) host.pause() }
        activity.lifecycle.addObserver(lifecycle)
        onDispose {
            onSave(host.position)
            activity.lifecycle.removeObserver(lifecycle)
            host.release()
            if (full) activity.setScreeningImmersive(false)
        }
    }
    LaunchedEffect(host) { while (true) { refresh(); delay(125) } }
    // A23：edge-to-edge（HomeActivity enableEdgeToEdge）下 adjustResize 失效，键盘弹没弹只能自己读
    // ime insets。自动收走带要过这道闸：键盘弹着、输入框聚焦但还没打字时，收走带=输入框整体消失。
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(full, playing, locked, panel, interaction, danmakuDraft, imeVisible) {
        // 正在打弹幕就不收走带：发送栏挂在走带上，收了等于把输入框藏起来
        if (shouldAutoHideChrome(full, playing, locked, panel != null, chrome, danmakuDraft, imeVisible)) { delay(3500); chrome = false }
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
    // 随包演示字幕已随本地样片库下线：字幕只有导入文件、服务端轨道、实时识别三条真路
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
        2 -> captions?.at(position, offset).orEmpty()
        3 -> serverCaption?.at(position, offset).orEmpty()
        // 实时识别：partial 文本滚动刷新，样式走同一套 CaptionStyle；延时对它没有意义（没有轨可对）
        4 -> realtimeText
        else -> ""
    }
    val seekBlock: @Composable () -> Unit = {
        Column {
            // 时间在条上方两端、细进度条在下：照 astryx 设计稿的走带（3dp 轨道 + 11dp 圆钮自绘，
            // 这个 material3 版 Slider 没有粗细参数——见 ThinSeek）。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(time(dragging?.let { (it * width).toLong() } ?: position), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(time(width), color = Muted, fontSize = 12.sp)
            }
            ThinSeek(
                fraction = dragging ?: if (width > 0) (position.toFloat() / width).coerceIn(0f, 1f) else 0f,
                enabled = width > 0,
                onFraction = { dragging = it; interaction++ },
                onCommit = {
                    dragging?.let { host.seekTo((width * it).toLong()); refresh() }
                    dragging = null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    // iconOnly：影院态底部只要图标走带（设计稿），文字让给画面；信息态保留图标+文字在 Lift 卡上。
    // 两处按用户点名改动：字幕开着时再点一下=直接关掉（之前必须钻进面板，看着像没反应）；
    // 内核不再上工具台——换内核只在后台按失败自动兜底，不再暴露给用户手动切。
    val toolsRow: @Composable (Boolean) -> Unit = { iconOnly ->
        Row(
            Modifier.fillMaxWidth()
                .then(if (iconOnly) Modifier else Modifier.background(Lift, Tile)),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(
                "字幕" to Icons.Default.Subtitles,
                (if (speed == 1f) "倍速" else "${speed}×") to Icons.Default.Speed,
                "弹幕" to Icons.Default.ChatBubbleOutline,
                "更多" to Icons.Default.MoreHoriz,
            ).forEachIndexed { index, (name, picture) ->
                Column(
                    Modifier.weight(1f).heightIn(min = if (iconOnly) 40.dp else 51.dp).clip(Tile).clickable {
                        when {
                            index == 0 && captionSource != 0 -> { captionSource = 0; info = "字幕已关闭" }
                            // 弹幕键按互联网惯例直接开/关（和字幕键同款手感）；来源与重试收进「更多 → 弹幕设置」。
                            index == 2 -> { info = ""; danmaku = !danmaku }
                            else -> { info = ""; panel = if (index == 1) "倍速" else name }
                        }
                    },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(picture, name, Modifier.size(18.dp), tint = if (index == 0 && captionSource != 0 || index == 2 && danmaku) Mint else White)
                    if (!iconOnly) Text(name, fontSize = 11.sp, maxLines = 1)
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
            // DLNA 投屏入口正式口径下线（没有可验证的真电视设备；旧 :cast 模块和旧页面原样保留）。
        }
    }
    // 画面内发送栏（互联网播放器惯例）：弹幕开着时贴在走带下方，胶囊输入框 + 发送键，回车即发。
    val danmakuSendRow: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .14f))) {
                if (danmakuDraft.isEmpty()) {
                    Text("发个弹幕见证当下", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
                BasicTextField(
                    value = danmakuDraft,
                    onValueChange = { danmakuDraft = it.take(40) },
                    singleLine = true,
                    cursorBrush = SolidColor(Mint),
                    textStyle = TextStyle(color = White, fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendDanmaku() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = { sendDanmaku() },
                enabled = danmakuDraft.isNotBlank(),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "发送弹幕", Modifier.size(15.dp))
                Text("发送", fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
    val panelBody: @Composable (String) -> Unit = { current ->
        Text(current, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        when (current) {
            "字幕" -> {
                // A22：没有任何可开来源时「关闭字幕」不再显示成选中勾——勾会让用户以为
                // 「有字幕被我关了」，实情是这条片子本来就没字幕。serverList == null 是列表还没取到，
                // 这时不能断言服务端没有轨道，只按导入/实时两条真路判。
                val captionChoice = hasCaptionSourceChoice(serverList, captions != null, realtimeUrl != null)
                Setting(
                    "关闭字幕",
                    if (captionChoice) "不影响播放和弹幕" else captionNoSourceHint(serverList, realtimeUrl != null),
                    captionOffSettingSelected(captionSource, captionChoice),
                ) {
                    if (captionSource == 0) {
                        // A22：关闭态再点写入同值、界面上零变化（勾不动），用户以为按钮坏了——
                        // 走现成的 info 通道给可见反馈（面板底部那行 Mint 文案）
                        info = captionAlreadyOffNotice(serverList, captions != null, realtimeUrl != null)
                    } else {
                        captionSource = 0; info = "字幕已关闭"
                    }
                }
                        // 样式放最前：面板在弹窗里能滚的范围有限，样式是主功能不该藏在延时/导入后面
                        Text("字幕样式", Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
                        captionPresets.forEachIndexed { index, preset ->
                            Setting(preset.title, preset.hint, presetIndex == index) {
                                presetIndex = index
                                store.edit().putInt("caption-style", index).apply()
                            }
                        }
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
                        // 实时识别字幕：没有现成字幕轨的片子现场认。只支持能重新解码音频的地址；
                        // 手机里挑的文件（content://）拿不到路径就不能重解，如实写清楚而不是隐藏。
                        if (realtimeUrl != null) {
                            Setting(
                                "实时识别字幕",
                                if (captionSource == 4 && realtimeStatus.isNotEmpty()) realtimeStatus
                                else "边播边认，不用字幕文件（识别用内置密钥，会耗流量）",
                                captionSource == 4,
                            ) {
                                if (captionSource == 4) { captionSource = 0 } else { captionSource = 4; offset = 0 }
                            }
                        } else {
                            Text("实时识别字幕：这个片源在手机上（没有可读地址），暂时不能现场识别；在线地址可以用。", color = Muted, fontSize = 12.sp)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("字幕延时", Modifier.weight(1f))
                            TextButton({ offset = (offset - 500).coerceAtLeast(-60_000) }) { Text("−500 ms") }
                            Text("${if (offset > 0) "+" else ""}$offset ms", color = Mint)
                            TextButton({ offset = (offset + 500).coerceAtMost(60_000) }) { Text("+500 ms") }
                        }
                        Text("正值让字幕更晚出现。", color = Muted, fontSize = 12.sp)
                    }
                    // 倍速：2.5×/3.0× 是用户点名的两档（追剧快进常用位）
                    "倍速" -> listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f).forEach { value ->
                        Setting("${value}×", if (value == 1f) "正常速度" else "", speed == value) {
                            speed = value; host.setSpeed(value); panel = null
                        }
                    }
                    "弹幕设置" -> {
                        // 开/关走播放页的弹幕键或下面这一条；这里只调来源。正式默认在线（服务端条目）。
                        Setting("关闭弹幕", "立即清屏，不影响字幕和播放", !danmaku) { danmaku = false }
                        // 来源切换：两个来源实现同一个 TimedCommentSource，渲染和车道调度完全共用
                        Setting("在线弹幕", if (serverVideoId != null) "默认：登录后发送，所有人共用这条时间轴" else "这条媒体没有服务端时间轴，这里看不到弹幕", danmaku && danmakuSource == DANMAKU_ONLINE) {
                            danmakuSource = DANMAKU_ONLINE; danmaku = true
                        }
                        // 样例弹幕/链路自测是开发入口，用户面板里不出现（正式化整改拍板）。
                        // 联调时 `adb shell setprop log.tag.ScreeningDbg DEBUG` 显形，默认零影响；
                        // 两套来源实现和切换逻辑原样保留，只是面板上不渲染。
                        if (Log.isLoggable(TAG_SCREENING_DBG, Log.DEBUG)) {
                            Setting("本地样例弹幕", "离线演示用：随包内置，无需网络", danmaku && danmakuSource == DANMAKU_SAMPLE) {
                                danmakuSource = DANMAKU_SAMPLE; danmaku = true
                            }
                            Setting("链路自测弹幕", "开发用：内置假服务端，验证拉取/发送/重试/撤回链路", danmaku && danmakuSource == DANMAKU_SERVER) {
                                danmakuSource = DANMAKU_SERVER; danmaku = true
                            }
                        }
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
                        Text(
                            "弹幕跟着媒体时钟走：暂停就停住，倍速会一起变快变慢，拖进度条立刻换成那个位置的弹幕。" +
                                "固定在上半区，底部留给字幕。",
                            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    "更多" -> {
                        Setting("适应画面", "保留原始比例", !fill) { fill = false }
                        Setting("填满画面", "可能裁掉画面边缘", fill) { fill = true }
                        Setting("弹幕设置", if (danmaku) "弹幕来源与发送状态" else "当前已关闭", panel == "弹幕设置") { panel = "弹幕设置" }
                    }
        }
        if (info.isNotEmpty()) Text(info, Modifier.padding(top = 9.dp), color = Mint)
    }
    Column(Modifier.fillMaxSize().background(Ink).then(
        // A23：信息态把键盘高度并进滚动视口（imePadding 在 verticalScroll 之外），
        // 配合输入框聚焦自带的 bring-into-view，弹幕发送行不再被键盘压在视口外
        if (!full) Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()) else Modifier
    )) {
        // 竖屏（信息态，设计稿的主要改动）：返回+片名不再压在画面上，走页面自己的顶栏；
        // 画面下移成一张圆角卡，和瀑布流同一套 13dp 语言。
        if (!full) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScreenIcon(Icons.AutoMirrored.Filled.ArrowBack, "返回列表", { onExit() })
                Text(
                    source.title,
                    Modifier.weight(1f).padding(start = 10.dp),
                    color = White, fontSize = 17.sp, lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                ScreenIcon(Icons.Default.Fullscreen, "全屏", { setFull(true) })
            }
        }
        Box(
            Modifier.fillMaxWidth()
                .then(
                    if (full) Modifier.weight(1f)
                    else Modifier.padding(horizontal = 6.dp).clip(Tile).aspectRatio(16f / 10)
                )
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { host.view },
                modifier = Modifier.matchParentSize(),
            )
            Box(
                Modifier.matchParentSize()
                    .pointerInput(locked, full, panel) {
                        detectTapGestures(
                            // 影院态菜单打开时，点画面先收菜单（菜单就在底部走带上，点黑处=关掉它）
                            onTap = { if (!locked) { if (panel != null) panel = null else { chrome = !chrome }; interaction++ } },
                            onDoubleTap = { tap ->
                                if (!locked) { jump(if (tap.x < size.width / 2) -10_000 else 10_000); chrome = true }
                            },
                        )
                    }
                    // 横滑快进退（市场惯例：右滑快进、左滑快退）：每滑过 1/4 屏宽跳一档，一档 30 秒。
                    // 滑过阈值就消费事件，上面的点击检测会收到取消，不会滑完又开关控件。
                    .pointerInput(locked, full) {
                        var acc = 0f
                        var steppedMs = 0L
                        detectHorizontalDragGestures(onDragStart = { acc = 0f; steppedMs = 0L }) { _, dragAmount ->
                            if (locked) return@detectHorizontalDragGestures
                            acc += dragAmount
                            val ms = dragSeekMs(acc, size.width.toFloat())
                            if (ms != steppedMs) {
                                steppedMs = ms
                                host.seekTo((host.position + ms).coerceIn(0, width))
                                refresh()
                                swipeSeconds = (ms / 1000).toInt()
                            }
                        }
                    },
            )
            // 续播说明（用户点名「开头 10+ 秒被跳」）：从存档位置进来就说清「已续播」并给一键从头，
            // 6 秒自收；位置太短的存档根本不会进来（ScreeningActivity 的 RESUME_MIN_MS 阈值）。
            if (resumeHinted && !locked) {
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = if (full) 96.dp else 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = .72f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("已从 ${time(initial)} 续播", color = White, fontSize = 12.sp)
                    TextButton(
                        onClick = { resumeHinted = false; host.seekTo(0); refresh() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) { Text("从头播放", color = Mint, fontSize = 12.sp) }
                }
            }
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
            swipeSeconds?.let { delta ->
                // 横滑快进退的回包：+45s / -30s 带落点时间，滑完 0.9 秒自收。
                // 位置偏上避开中间那排控件——气泡和控件同在中线会被整个盖住（装机实测过）。
                Box(
                    Modifier.align(Alignment.Center).offset(y = if (full) (-140).dp else (-110).dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = .72f))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text((if (delta > 0) "+" else "") + "${delta}s", color = White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text(time(position), color = Muted, fontSize = 12.sp)
                    }
                }
            }
            if ((chrome || !playing) && !locked) {
                // 压屏头部只在影院态有：半屏的返回+片名已经上了页面顶栏（设计稿），不再压在画面上
                if (full) Row(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Transparent)))
                        .windowInsetsPadding(if (full) WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal) else WindowInsets(0, 0, 0, 0))
                        // 头部和画面顶边拉开距离：以前返回键几乎贴着状态栏压在上面
                        .padding(start = 8.dp, end = 8.dp, top = if (full) 10.dp else 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ScreenIcon(Icons.AutoMirrored.Filled.ArrowBack, if (full) "退出全屏" else "返回列表", {
                        if (full) setFull(false) else onExit()
                    })
                    // 片名上头部：全屏和非全屏都显示，滑到中间时也知道在看哪部
                    Text(
                        source.title,
                        Modifier.weight(1f).padding(start = 10.dp),
                        color = White, fontSize = if (full) 16.sp else 15.sp, lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (full) ScreenIcon(Icons.Default.Lock, "锁定控件", { locked = true })
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
                        // A23：键盘不再靠 resize 顶起窗口，会把挂在走带底部的弹幕发送栏整个盖住——
                        // imePadding 把底部这条（走带+工具+发送栏）抬到键盘上方（先例：ChatScreen/CompanionScreen）
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .88f))))
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        seekBlock()
                        // 影院态底部只要走带+图标工具台（设计稿）：收藏留在信息态
                        toolsRow(true)
                        // 弹幕开着就把发送栏挂在最底下（互联网播放器惯例：进度条→工具→输入行）
                        if (danmaku) danmakuSendRow()
                    }
                }
            }
            if (full) panel?.let { current ->
                // 影院态菜单（用户点名「底部弹窗一点不好看」）：不再是全宽大抽屉，
                // 半透 scrim + 一张浮在走带上方的圆角卡；点 scrim 或画面都能收。
                Box(
                    Modifier.matchParentSize()
                        .pointerInput(current) { detectTapGestures { panel = null; interaction++ } }
                        .background(Color.Black.copy(alpha = .42f)),
                ) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).width(340.dp)
                            .padding(bottom = 116.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Ink.copy(alpha = .96f))
                            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
                            .heightIn(max = 170.dp)
                            .verticalScroll(rememberScrollState()),
                    ) { panelBody(current) }
                }
            }
            if (locked) {
                // 锁定/解锁成对都在右上角同一个位置、同一套锁头（用户点名）：
                // 锁定后其他控件全收，只留解锁锁头在这个角，样式和锁定键一模一样。
                Row(
                    Modifier.align(Alignment.TopEnd).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Transparent)))
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    ScreenIcon(Icons.Default.LockOpen, "解锁控件", { locked = false; chrome = true; interaction++ })
                }
            }
        }
        if (!full) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 9.dp)) {
                seekBlock()
                Text(channel.ifEmpty { "本周放映" }, color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(source.title, color = White, fontSize = 35.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black)
                Text(source.description, color = Muted, modifier = Modifier.padding(top = 5.dp, bottom = 12.dp))
                // 设计稿：简介和工具台之间一条分隔线，下半页分成「讲什么」和「能干什么」两段
                HorizontalDivider(color = Lift)
                Spacer(Modifier.height(10.dp))
                toolsRow(false)
                if (danmaku) danmakuSendRow()
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.CenterEnd) { favorRow() }
                error?.let { message -> Text(message, color = Peach, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
    }
    // 面板内容只写一份；影院态浮在走带上方（用户点名底部大弹窗难看），信息态还是底部抽屉。
    // 信息态：底部抽屉；影院态的面板浮在画面 Box 里（见播放区），不再是横屏大抽屉。
    if (!full) panel?.let { current ->
        ModalBottomSheet(onDismissRequest = { panel = null; interaction++ }, containerColor = Ink) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
            ) { panelBody(current) }
        }
    }
}

/**
 * 细进度条：3dp 轨道 + 11dp 圆钮，自绘。
 * 这个 material3 版本的 Slider 没有粗细参数，默认轨道在窄屏视频上太粗（用户点名）；
 * 自己画还顺带把触点做成整行 28dp 高——好看和好按不用二选一。
 */
@Composable
private fun ThinSeek(
    fraction: Float,
    enabled: Boolean,
    onFraction: (Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = if (enabled) Mint else Mint.copy(alpha = .45f)
    val inactive = if (enabled) Lift else Lift.copy(alpha = .45f)
    Box(
        modifier
            .height(28.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures {
                    // 点按即跳转：写完 fraction 立刻 commit，不用等抬手
                    val at = (it.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onFraction(at); onCommit()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                fun at(x: Float) = (x / size.width.toFloat()).coerceIn(0f, 1f)
                detectDragGestures(
                    onDragStart = { onFraction(at(it.x)) },
                    onDrag = { change, _ -> change.consume(); onFraction(at(change.position.x)) },
                    onDragEnd = onCommit,
                    onDragCancel = onCommit,
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = 5.5.dp.toPx()
            val cy = size.height / 2f
            val cx = r + fraction.coerceIn(0f, 1f) * (size.width - r * 2f).coerceAtLeast(0f)
            drawLine(inactive, Offset(r, cy), Offset(size.width - r, cy), 3.dp.toPx(), StrokeCap.Round)
            drawLine(active, Offset(r, cy), Offset(cx, cy), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(active, r, Offset(cx, cy))
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
