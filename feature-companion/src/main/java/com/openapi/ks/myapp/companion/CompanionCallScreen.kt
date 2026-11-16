package com.openapi.ks.myapp.companion

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================
// 全屏通话页（豆包「电话」那种形态）
//
// 这个文件是自包含的：不引用 CompanionUiState / CompanionMessage / MessageRole
// 之类的既有类型（那几个类型正在被并行修改），需要的数据全部由下面的
// CompanionCallState 承载。接线方只需要把传输层的信号映射进这个 state。
//
// 调用方需要负责的两件事（UI 内部做不到）：
//   1. 真正的边到边：Activity 用 enableEdgeToEdge()。本页用
//      WindowInsets.systemBars 给内容留避让，背景是全屏铺满的；不开启边到边时
//      系统会把整页压在状态栏下面，背景色块看起来会"缺一块"。
//   2. 音频电平：LiveKit 房间的 audioLevel 由传输层上报，本页只消费不论证。
//      电平走 0f..1f 的线性刻度（不是 dB），映射规则见 CompanionCallState.audioLevel。
// ============================================================================

/** 通话连接状态。与 companion 页的 CompanionConnection 刻意分开，避免跨文件耦合。 */
enum class CallConnection {
    /** 正在建房间 / 等第一条音轨。此时不显示计时，显示「正在接通…」。 */
    CONNECTING,

    /** 已进房。计时器开始走。 */
    CONNECTED,

    /** 掉线重连中。仍然保留当前画面，避免用户以为通话已经断了。 */
    RECONNECTING,

    /** 已挂断 / 对端结束。页面通常随之退出，但保留末态以防退出动画期间白屏。 */
    ENDED,
}

/**
 * 通话页需要的全部数据。
 *
 * 字段含义：
 * - [agentName] 她的名字，显示在顶部，也是头像占位字的来源。
 * - [captions] 她说的话。**按时间正序**（最后一条最新）；界面渲染全部历史（区域有高度上限，
 *   可滚动，新消息自动跟随到底），调用方不必裁剪。流式输出时请直接覆盖最后一条的内容，
 *   不要每条分片都 append 成新元素，否则会出现「半句话一行」的碎片。
 * - [elapsedSeconds] 通话时长（秒），由调用方计时；本页不自己计时，
 *   因为重连、保持前台等状态的权威在传输层，界面各算一份必然和它对不上。
 * - [isMuted] 本地麦克风是否静音。注意：**不影响 [audioLevel]**——
 *   那个是她的音量，静音是自己不说话，不该把她那边也冻住。
 * - [cameraActive] 是否处于摄像头形态，决定整页是语音形态还是视频形态。
 * - [audioLevel] 音频电平，**0f..1f 线性刻度**，0 = 静音，1 = 满幅。
 *   不是 dB，也不是 [0,1] 之外的原始值；调用方负责先归一化再进来，
 *   因为不同采集端给出的量纲不一样（LiveKit 给的是 0..1 的近似 RMS）。
 *   本页内部还会再做两级缓动，见 [CompanionCallScreen] 里的注释。
 * - [isAgentSpeaking] 她此刻是否在说话（VAD / 音频轨道 active）。
 *   用来在电平恰好很低的那几十毫秒里保住字幕光标和呼吸环的下限，
 *   否则断句处会出现「人还在说话但界面完全静止」的割裂感。
 * - [connection] 连接状态。
 * - [avatarEmoji] 头像占位的 emoji（她的人设形象）。给了就显示 emoji，
 *   没给则退化成 [agentName] 首字的圆形占位。真正接图片时走
 *   [CompanionCallScreen] 的 avatarContent 插槽，别塞进这里。
 * - [statusHint] 覆盖默认状态文案（例如「对方正在听…」「麦克风被占用」）。
 *   非空时优先于 [connection] 推导出来的文本。
 */
@Immutable
data class CompanionCallState(
    val agentName: String = "",
    val captions: List<String> = emptyList(),
    val elapsedSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val cameraActive: Boolean = false,
    val audioLevel: Float = 0f,
    /**
     * **用户自己**的音量 0..1，驱动底部那条波形。
     *
     * 与 [audioLevel] 分开：[audioLevel] 是她的声音（呼吸环、嘴型），这个是你的声音。
     * 以前波形接的是 [audioLevel]，所以「你说话时波形不动」——它画错了对象。
     */
    val selfLevel: Float = 0f,
    val isAgentSpeaking: Boolean = false,
    val connection: CallConnection = CallConnection.CONNECTING,
    val avatarEmoji: String? = null,
    val statusHint: String? = null,
)

/**
 * 深色沉浸背景的调色板。
 *
 * 为什么不用纯黑：纯黑（#000000）在 OLED 上会让「呼吸环」失去参照——环和背景都是
 * 黑的，光晕没有落点，整页像一块死屏。这里走「深夜靛蓝 → 近黑」的竖直渐变，
 * 顶部保留一点蓝紫信息量，底部压到 #05060C，视觉上仍有深度但不发灰。
 * 这组值取自夜间类产品（Night indigo + dream violet on dark）的配色思路，
 * 主色 #8B7CF6 是紫罗兰：暖色（橙/粉）在长时间通话里会显得躁，冷紫更耐看。
 */
private object CallColors {
    /** 渐变顶部：能看出是蓝紫，而不是灰。 */
    val BackdropTop = Color(0xFF161F3D)

    /** 渐变中段：大部分内容落在这层，压深以保证白字对比度。 */
    val BackdropMid = Color(0xFF0B1122)

    /** 渐变底部：近黑但不纯黑。 */
    val BackdropBottom = Color(0xFF05060C)

    /** 呼吸环、波形条、强调色。 */
    val Violet = Color(0xFF8B7CF6)

    /** 光晕内核用的深紫，比 [Violet] 沉，避免中心过曝。 */
    val VioletDeep = Color(0xFF5B4BC4)

    /** 静音态的环与条：去饱和的青灰，语义是「冷下来了」，而不是变红报警。 */
    val Slate = Color(0xFF8590B0)

    /** 主文字（她的名字、字幕）。在 #0B1122 上对比度约 15:1，远超 4.5:1。 */
    val TextPrimary = Color(0xFFF3F5FF)

    /** 次级文字（时长、状态）。对比度约 8:1，够用但仍明显弱于主文字。 */
    val TextSecondary = Color(0xFFA3ADCB)

    /** 挂断红。整页唯一的高饱和暖色，只给挂断一个出口用。 */
    val HangupRed = Color(0xFFE5484D)

    /** 重连提示的琥珀色——「可恢复的异常」，不是错误红。 */
    val Amber = Color(0xFFF0B67F)

    /** 玻璃质感按钮底。 */
    val Glass = Color(0x1FFFFFFF)

    /** 玻璃质感描边，把按钮从深背景上"托"出来。 */
    val GlassBorder = Color(0x33FFFFFF)

    /** 字幕底色：半透明黑，保证任何画面（含视频形态）上都能读。 */
    val CaptionScrim = Color(0x59000000)
}

// 动画时间常数。集中在这里是为了让「手感」可调，而不是散落在各处的魔法数字。
/** 波形跟随电平的平滑时长。短，因为波形要"跟手"。 */
private const val LEVEL_FOLLOW_MS = 110

/** 呼吸环跟随电平的平滑时长。长，因为环要"绵"，不能跟着音节的起伏抽搐。 */
private const val RING_FOLLOW_MS = 280

/** 波形各条独立相位的循环时长。太快像噪点，太慢像卡住；1.6s 接近说话的语气节奏。 */
private const val PHASE_CYCLE_MS = 1600

/** 字幕淡入淡出时长。 */
private const val CAPTION_FADE_MS = 260

/** 字幕区域的最大高度。给了上限才能滚动——无限高的话会把头像和操作条挤出屏幕。 */
private const val CAPTION_MAX_HEIGHT_DP = 200

/** 波形条数。奇数是为了让中间一根正好落在中心线上。 */
private const val WAVE_BAR_COUNT = 23

/**
 * 全屏通话界面。
 *
 * 三种形态由 [CompanionCallState.cameraActive] / [isMuted] / [isAgentSpeaking] 组合决定：
 * - **说话态**（语音形态 + 她在说话）：呼吸环随电平扩张、波形起伏、字幕末尾带光标。
 * - **静音态**：环与波形降到去饱和的青灰，顶部出现「已静音」药丸。
 *   波形**不冻结**——它画的是她的声音，不是你的麦克风；把自己静音画成她也不出声
 *   会与耳朵听到的矛盾。
 * - **摄像头态**：上半屏远端画面（插槽注入，无插槽时是占位框），右下角本地小窗，
 *   字幕压在画面底部；此时不画波形，一屏只保留一组运动元素。
 *
 * @param state 见 [CompanionCallState]。
 * @param onHangUp 挂断。
 * @param onToggleMute 切换静音。
 * @param onToggleCamera 切换摄像头。切到开时会同时让调用方去开本地视频轨。
 * @param onSendText 通话中发出的文字。本页负责清空输入框和关闭浮层，不负责别的。
 * @param cameraAvailable 摄像头能力是否可用。false 时按钮画成置灰态（文案「即将支持」），
 *   但**仍然可点**——点了由调用方给一句提示，比一个完全没反应的灰按钮好。
 * @param modifier 外部布局约束。
 * @param avatarContent 头像插槽。**优先于** [CompanionCallState.avatarEmoji]，
 *   给了就整块渲染在圆形裁剪里（真实形象渲染器从这儿接）。
 * @param remoteVideoContent 远端画面插槽。**真实画面由调用方通过插槽注入**；
 *   为 null 时渲染一个带她名字/emoji 的占位框，并标注「等待画面」。
 * @param localVideoContent 本地小窗插槽。为 null 时渲染一个占位框（自拍预览的位置）。
 */
@Composable
fun CompanionCallScreen(
    state: CompanionCallState,
    onHangUp: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onSendText: (String) -> Unit,
    modifier: Modifier = Modifier,
    cameraAvailable: Boolean = true,
    avatarContent: (@Composable () -> Unit)? = null,
    remoteVideoContent: (@Composable () -> Unit)? = null,
    localVideoContent: (@Composable () -> Unit)? = null,
    /** 波形样式与粒子开关。由调用方持有（要持久化），本页只消费。 */
    waveOptions: WaveOptions = WaveOptions(),
    /** 切换波形样式 / 粒子。null 时不显示切换入口。 */
    onWaveOptionsChange: ((WaveOptions) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()

    // 两级缓动，喂给同一路电平，但时间常数差 2.5 倍。
    //
    // 为什么必须缓动：传输层给的电平是每秒几十次采样的瞬时值，相邻两帧差 0.3 很常见。
    // 直接拿它画半径/条高，视觉上就是每一帧都在跳，像信号故障而不是「声音」。缓动在这里
    // 等价于一个低通滤波器：起音快、衰减拖尾，波形才像呼吸而不是抽搐。
    //
    // 为什么分两级：波形在底部，离视线远，要跟手（110ms，几乎实时）；
    // 呼吸环在头像外围，离视线近且面积大，跟得太紧会显得「抖」（280ms）。
    // 用一个值同时驱动两者，只能二选一，所以拆开。
    // 波形 = 你的声音；呼吸环 = 她的声音。两者数据源不同，缓动参数也不同：
    // 波形在底部、离视线远，要跟手（110ms）；呼吸环在头像外围、面积大，跟得太紧会抖（280ms）。
    val waveLevel = animateFloatAsState(
        targetValue = state.selfLevel.toLevelOrZero(),
        animationSpec = tween(LEVEL_FOLLOW_MS, easing = LinearOutSlowInEasing),
        label = "callWaveLevel",
    )
    val ringLevel = animateFloatAsState(
        targetValue = state.audioLevel.toLevelOrZero(),
        animationSpec = tween(RING_FOLLOW_MS, easing = FastOutSlowInEasing),
        label = "callRingLevel",
    )

    // 一条持续循环的相位，让波形相邻条之间产生相位差。
    // 只有相位在走、条高由电平决定，两者相乘才有「声音在流动」的错觉；
    // 只用电平的话，所有条会同步涨落，像柱状图而不是波形。
    //
    // 注意：这个 State 只在 Canvas 的 draw lambda 里读，不在 composable 里读，
    // 所以每帧只触发重绘（invalidate），不触发重组。60fps 下这是必要的，
    // 否则每帧重建整棵子树，长通话会明显发烫。
    val phase = rememberInfiniteTransition(label = "callMotion").animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(PHASE_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "callPhase",
    )

    var composerOpen by rememberSaveable { mutableStateOf(false) }

    CallBackdrop(level = ringLevel, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 背景铺满全屏，但内容要让开状态栏/导航栏。用 padding 而不是
                // consumeWindowInsets，因为本页下面还有一层浮层输入框要自己处理 IME。
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CallHeader(
                agentName = state.agentName,
                connection = state.connection,
                elapsedSeconds = state.elapsedSeconds,
                isMuted = state.isMuted,
                statusHint = state.statusHint,
                waveOptions = waveOptions,
                onWaveOptionsChange = onWaveOptionsChange,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (state.cameraActive) {
                    VideoStage(
                        state = state,
                        remoteVideoContent = remoteVideoContent,
                        localVideoContent = localVideoContent,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    VoiceStage(
                        state = state,
                        ringLevel = ringLevel,
                        phase = phase,
                        avatarContent = avatarContent,
                        reducedMotion = reducedMotion,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // 视频形态下不画底部波形：一屏同时有远端画面、本地小窗、字幕、
            // 心跳环四组运动元素会互相抢注意力（也更容易让人晕）。语音形态才需要它。
            if (!state.cameraActive) {
                WaveformBars(
                    level = waveLevel,
                    phase = phase,
                    isMuted = state.isMuted,
                    isSpeaking = state.isAgentSpeaking,
                    reducedMotion = reducedMotion,
                    modifier = Modifier
                        .width(200.dp)
                        .height(56.dp),
                )
                Spacer(Modifier.height(14.dp))
            }

            CallControlBar(
                isMuted = state.isMuted,
                cameraActive = state.cameraActive,
                cameraAvailable = cameraAvailable,
                composerOpen = composerOpen,
                onToggleMute = onToggleMute,
                onToggleCamera = onToggleCamera,
                onOpenComposer = { composerOpen = true },
                onHangUp = onHangUp,
            )
        }

        // 文字输入浮层：盖在整页之上，带自己的 IME 避让。
        // 放在 call screen 内部而不是让调用方去弹 Dialog，是因为它和「通话中」这个
        // 上下文强绑定（挂断时要一起消失），交给调用方管会多一处状态要同步。
        AnimatedVisibility(
            visible = composerOpen,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120)),
        ) {
            TextComposerOverlay(
                agentName = state.agentName,
                onDismiss = { composerOpen = false },
                onSend = { text ->
                    onSendText(text)
                    composerOpen = false
                },
            )
        }
    }
}

// ============================================================================
// 背景
// ============================================================================

/**
 * 沉浸式深色背景：一层竖直渐变 + 两团径向光晕。
 *
 * 为什么用 drawBehind 而不是叠三个 Box：光晕的中心要跟着屏幕高度走，
 * 用 Box 得靠 padding/offset 猜位置，改一版屏幕就错位；在 DrawScope 里直接用
 * size 算，任意长宽比都能落在对的地方。
 *
 * 光晕半径会随电平轻微扩张，这是整页唯一"全身参与"的反馈：说话时整块背景亮一点，
 * 不说话时暗回去。幅度刻意做小（0.55 → 0.72），因为背景大面积提亮容易糊掉对比度。
 */
@Composable
private fun CallBackdrop(
    level: State<Float>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.drawBehind {
            drawRect(
                Brush.verticalGradient(
                    0f to CallColors.BackdropTop,
                    0.45f to CallColors.BackdropMid,
                    1f to CallColors.BackdropBottom,
                ),
            )

            // 头像位置的主光晕。0.34 是视觉重心——比几何中心略高，人眼会把上半屏当主体。
            val cx = size.width / 2f
            val cy = size.height * 0.34f
            val glowRadius = size.minDimension * (0.55f + 0.17f * level.value)
            drawRect(
                Brush.radialGradient(
                    colors = listOf(
                        CallColors.VioletDeep.copy(alpha = 0.34f),
                        CallColors.VioletDeep.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = glowRadius,
                ),
            )

            // 底部一层很淡的冷光，把操作条从背景里托起来，避免下面三分之一发闷。
            drawRect(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2A3A6B).copy(alpha = 0.28f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, size.height * 0.96f),
                    radius = size.width * 0.85f,
                ),
            )
        },
        content = content,
    )
}

// ============================================================================
// 顶部：名字 / 状态 / 时长
// ============================================================================

@Composable
private fun CallHeader(
    agentName: String,
    connection: CallConnection,
    elapsedSeconds: Long,
    isMuted: Boolean,
    statusHint: String?,
    waveOptions: WaveOptions = WaveOptions(),
    onWaveOptionsChange: ((WaveOptions) -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 名字一行 + 右侧样式菜单：菜单只在调用方允许切换时出现，
        // 否则通话页顶部会多一个点了没反应的图标。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(40.dp))
            Text(
                text = agentName.ifBlank { "她" },
                color = CallColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            if (onWaveOptionsChange != null) {
                WaveStyleMenu(options = waveOptions, onChange = onWaveOptionsChange)
            } else {
                Spacer(Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 状态点：已连接是主色，重连是琥珀，接通中是灰。用点而不是纯文字，
            // 是因为时长在变化时文字本身就在跳，再叠一个变色的词读不出来。
            val dotColor = when {
                connection == CallConnection.CONNECTED && !isMuted -> CallColors.Violet
                connection == CallConnection.RECONNECTING -> CallColors.Amber
                isMuted -> CallColors.Slate
                else -> CallColors.TextSecondary
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusHint ?: statusText(connection, elapsedSeconds),
                color = if (connection == CallConnection.RECONNECTING) CallColors.Amber else CallColors.TextSecondary,
                fontSize = 13.sp,
                letterSpacing = 0.4.sp,
            )
            if (isMuted) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "已静音",
                    color = CallColors.Slate,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CallColors.Glass)
                        .border(1.dp, CallColors.GlassBorder, RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/**
 * 波形样式切换。三套样式各有用处，所以给用户选，而不是替他们定一个：
 * - 竖条最克制、信息最清楚；
 * - 连续曲线（默认）最像"声音"；
 * - 圆环配头像的圆形构图。
 * 粒子可以单独关掉——它是最耗电的一项，低端机建议关。
 */
@Composable
private fun WaveStyleMenu(options: WaveOptions, onChange: (WaveOptions) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "波形样式",
                tint = CallColors.TextSecondary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val labels = mapOf(
                WaveStyle.Ribbon to "柔和曲线",
                WaveStyle.Bars to "竖条",
                WaveStyle.Orbit to "环形放射",
            )
            labels.forEach { (style, label) ->
                DropdownMenuItem(
                    text = { Text(if (options.style == style) "✓ $label" else "　 $label") },
                    onClick = {
                        open = false
                        onChange(options.copy(style = style))
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(if (options.particles) "✓ 飘散粒子" else "　 飘散粒子") },
                onClick = {
                    open = false
                    onChange(options.copy(particles = !options.particles))
                },
            )
        }
    }
}

private fun statusText(connection: CallConnection, elapsedSeconds: Long): String =
    when (connection) {
        CallConnection.CONNECTING -> "正在接通…"
        CallConnection.CONNECTED -> formatDuration(elapsedSeconds)
        CallConnection.RECONNECTING -> "连接不稳定，正在重连…"
        CallConnection.ENDED -> "通话已结束"
    }

private fun formatDuration(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val minutes = safe / 60
    val seconds = safe % 60
    // 超过一小时也只滚分钟（通话场景极少），简单优先。
    return "%02d:%02d".format(minutes, seconds)
}

// ============================================================================
// 语音形态：呼吸环 + 头像 + 字幕
// ============================================================================

@Composable
private fun VoiceStage(
    state: CompanionCallState,
    ringLevel: State<Float>,
    phase: State<Float>,
    avatarContent: (@Composable () -> Unit)?,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(268.dp),
            contentAlignment = Alignment.Center,
        ) {
            BreathingRing(
                level = ringLevel,
                phase = phase,
                isMuted = state.isMuted,
                isSpeaking = state.isAgentSpeaking,
                reducedMotion = reducedMotion,
                modifier = Modifier.fillMaxSize(),
            )
            AvatarCircle(
                name = state.agentName,
                emoji = state.avatarEmoji,
                level = ringLevel,
                isMuted = state.isMuted,
                slot = avatarContent,
            )
        }

        Spacer(Modifier.height(20.dp))

        CaptionStack(
            captions = state.captions,
            streaming = state.isAgentSpeaking,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = CAPTION_MAX_HEIGHT_DP.dp)
                .padding(horizontal = 8.dp),
        )
    }
}

/**
 * 呼吸环：三圈同心描边 + 一层径向填充。
 *
 * 三圈的半径和透明度都随 [level] 变化，但**变化幅度不同**（内圈最灵敏，外圈最迟钝），
 * 这样电平上去的时候环会"一层层往外推"，而不是整组等比例放大——后者看起来像一个
 * 缩放动画，没有"声音顶出来"的物理感。
 *
 * 静音时换成青灰，并且压低不透明度：静音是"我关了麦"，不是错误，不该用红色。
 */
@Composable
private fun BreathingRing(
    level: State<Float>,
    phase: State<Float>,
    isMuted: Boolean,
    isSpeaking: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    // 空场呼吸：即使她完全不说话，环也要缓慢起伏一点，否则整页像挂了一张静态图，
    // 用户会怀疑是不是卡了。幅度上限 0.16，远小于说话时的 1.0，不抢戏。
    val idleBreath = rememberInfiniteTransition(label = "callBreath").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "callBreathValue",
    )

    val ringColor = if (isMuted) CallColors.Slate else CallColors.Violet

    Canvas(modifier = modifier) {
        // 减少动效时把呼吸压到极小、相位固定：不追求"活着"，只保证有边界。
        val idle = if (reducedMotion) 0.5f else idleBreath.value
        // 说话下限：VAD 判定在说话、但电平恰好过零时，保住 0.12，避免环瞬间塌回去。
        val speakingFloor = if (isSpeaking) 0.12f else 0f
        val lv = maxOf(level.value, speakingFloor)
        val breath = 0.16f * idle

        val center = Offset(size.width / 2f, size.height / 2f)
        val unit = size.minDimension / 2f

        // 填充光晕：比描边更先被看见，负责"整块亮起来"。
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = 0.20f + 0.14f * lv),
                    Color.Transparent,
                ),
                center = center,
                radius = unit * (0.60f + 0.10f * lv + breath),
            ),
            radius = unit * (0.60f + 0.10f * lv + breath),
            center = center,
        )

        // 三圈描边。[sensitivity] 递减，制造"由内向外推"的层次。
        val rings = listOf(
            Triple(0.300f, 0.22f, 0.55f),
            Triple(0.350f, 0.14f, 0.34f),
            Triple(0.412f, 0.07f, 0.18f),
        )
        rings.forEachIndexed { index, (baseFraction, sensitivity, baseAlpha) ->
            val radius = unit * (baseFraction + sensitivity * lv + breath * (1f + index * 0.4f))
            drawCircle(
                color = ringColor.copy(alpha = (baseAlpha * (0.55f + 0.45f * lv)).coerceIn(0f, 1f)),
                radius = radius,
                center = center,
                style = Stroke(width = if (index == 0) 1.6.dp.toPx() else 1f.dp.toPx()),
            )
        }

        // 最外圈加一条"掠过的亮弧"：用一个短弧沿圆周慢速旋转，暗示持续在听。
        // 只在非减少动效时画；它比让整圈闪烁安静得多。
        if (!reducedMotion) {
            val sweepRadius = unit * (0.462f + 0.05f * lv)
            drawArc(
                color = ringColor.copy(alpha = 0.38f),
                startAngle = (phase.value * 180f / PI.toFloat()) % 360f,
                sweepAngle = 54f,
                useCenter = false,
                topLeft = Offset(center.x - sweepRadius, center.y - sweepRadius),
                size = Size(sweepRadius * 2f, sweepRadius * 2f),
                style = Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
    }
}

/**
 * 圆形头像。
 *
 * 占位优先级：插槽 > emoji > 名字首字。
 * 首字占位用渐变底 + 白色大字，比灰色实心圆更像"一个人"而不是"加载中"。
 *
 * 头像本身随电平做 1.0→1.035 的微缩放，写在 graphicsLayer 里而不是 Modifier.scale：
 * graphicsLayer 的 lambda 里读 State 只更新图层属性，不触发重组，和波形用的是同一套
 * "每帧只重绘"的约定。
 */
@Composable
private fun AvatarCircle(
    name: String,
    emoji: String?,
    level: State<Float>,
    isMuted: Boolean,
    slot: (@Composable () -> Unit)?,
) {
    val diameter = 136.dp
    val rimColor = if (isMuted) CallColors.Slate else CallColors.Violet

    Box(
        modifier = Modifier
            .size(diameter)
            .graphicsLayer {
                val scale = 1f + 0.035f * level.value
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        CallColors.VioletDeep.copy(alpha = 0.55f),
                        Color(0xFF1A2140),
                    ),
                ),
            )
            .border(1.5.dp, rimColor.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            slot != null -> slot()
            !emoji.isNullOrBlank() -> Text(text = emoji, fontSize = 54.sp)
            else -> Text(
                text = name.trim().take(1).ifBlank { "她" },
                color = CallColors.TextPrimary,
                fontSize = 52.sp,
                fontWeight = FontWeight.Light,
            )
        }
    }
}

/**
 * 字幕栈：可滚动的历史，最新一句在最下面，越旧越淡。
 *
 * 为什么不再只显示三句：语音模式下她说的话是长句流水，三句会被 `maxLines` 反复截断，
 * 用户看到的是「半句话换半句话」，想回头确认一句已经没了。现在整段对话都可滚动回看。
 *
 * 为什么用「位置决定透明度」而不是逐条进出场动画：字幕每来一句，整栈都要往上顶一格，
 * 如果同时给每一条做位移动画，三行会在同一时间往同一方向动，看起来像抖动。
 * 让透明度随位置变化即可，旧的本来就已经很淡。
 *
 * 跟随策略：默认贴住底部（新的在心里，用户看的是最新一句）；用户自己往上翻时暂停跟随，
 * 翻回底部再恢复。只用 [LazyListState.isScrollInProgress] 判断，不需要额外的拖拽手势。
 */
@Composable
private fun CaptionStack(
    captions: List<String>,
    streaming: Boolean,
    modifier: Modifier = Modifier,
) {
    // 带原始下标的非空句：下标当 key，追加新句时已有项的 key 不变。
    val visible = remember(captions) {
        captions.mapIndexedNotNull { index, line -> if (line.isNotBlank()) index to line else null }
    }
    if (visible.isEmpty()) {
        // 她还没开口时不留空白塌陷：占一行高度的占位文案，避免整个布局跳动。
        Text(
            text = "…",
            color = CallColors.TextSecondary.copy(alpha = 0.5f),
            fontSize = 15.sp,
            modifier = modifier,
            textAlign = TextAlign.Center,
        )
        return
    }

    val listState = rememberLazyListState()
    var followTail by remember { mutableStateOf(true) }
    // 我们自己触发的滚动不算「用户往上翻」，否则每次跟随都会被自己关掉。
    var autoScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && !autoScrolling) {
                // 拖动结束时判断停在哪儿：还能往下滚说明用户停在历史里，就暂停跟随。
                followTail = !listState.canScrollForward
            }
        }
    }

    LaunchedEffect(visible.size) {
        if (!followTail) return@LaunchedEffect
        autoScrolling = true
        try {
            listState.animateScrollToItem(visible.lastIndex)
        } finally {
            autoScrolling = false
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
    ) {
        itemsIndexed(visible, key = { _, item -> item.first }) { index, (_, line) ->
            val fromNewest = visible.lastIndex - index
            CaptionLine(
                text = line,
                targetAlpha = when {
                    fromNewest <= 0 -> 1f
                    fromNewest == 1 -> 0.62f
                    else -> 0.38f
                },
                // 只有最新一句带流式光标，且必须真的在说话。
                showCursor = streaming && fromNewest <= 0,
            )
        }
    }
}

@Composable
private fun CaptionLine(
    text: String,
    targetAlpha: Float,
    showCursor: Boolean,
) {
    // 位置固定、只动透明度：新句从 0.38 升到 1.0，旧句从 1.0 降到 0.62，
    // 同一时刻只有透明度在变，没有位移，所以不会抖。
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(CAPTION_FADE_MS, easing = LinearOutSlowInEasing),
        label = "captionAlpha",
    )

    Text(
        text = if (showCursor) "$text▍" else text,
        color = CallColors.TextPrimary.copy(alpha = alpha),
        fontSize = 15.sp,
        lineHeight = 23.sp,
        textAlign = TextAlign.Center,
        // 不再截断：一句说完的完整回复要能整段读完，太长由外层滚动承接。
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CallColors.CaptionScrim)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

// ============================================================================
// 底部波形
// ============================================================================

/**
 * 波形样式。用户可以在通话页的 ⋯ 菜单里切换，选择记在本地。
 *
 * 三套都只用 Canvas 画，不引入第三方波形库：
 * - [Bars]：竖条，最克制，信息量最清楚；
 * - [Ribbon]：连续曲线（类似 WaveInApp 的柔和长波），更像"声音"而不是"柱状图"；
 * - [Orbit]：圆环放射条，配合头像的环形构图。
 */
enum class WaveStyle { Bars, Ribbon, Orbit }

/**
 * 波形风格 + 粒子开关。调用方持有，界面只读。
 */
@Immutable
data class WaveOptions(
    val style: WaveStyle = WaveStyle.Ribbon,
    /** 是否从波形里飘出粒子。关掉可以省电，低端机建议关。 */
    val particles: Boolean = true,
)

/**
 * 一排随电平起伏的竖条。
 *
 * 形状由三个因子相乘，缺一个都不像：
 * 1. 电平 lv —— 整体高度；
 * 2. 包络 envelope —— 中间高两侧低（cos，两端约 0.1），让能量看起来从中间发出；
 * 3. 相位 wobble —— 每条独立相位的正弦，制造相邻条的高低差。
 *
 * 只用电平：所有条等高，像柱状图。
 * 只用电平 + 包络：像固定形状整体伸缩，死板。
 * 三者相乘才有"声波"的感觉。
 *
 * 全部在 Canvas 里画，不引入第三方波形库；电平与相位都是 State，只在 draw 阶段读，
 * 因此每帧只重绘不重组。
 */
@Composable
private fun WaveformBars(
    level: State<Float>,
    phase: State<Float>,
    isMuted: Boolean,
    isSpeaking: Boolean,
    reducedMotion: Boolean,
    style: WaveStyle = WaveStyle.Bars,
    particles: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val barColor = if (isMuted) CallColors.Slate else CallColors.Violet

    Canvas(modifier = modifier) {
        val speakingFloor = if (isSpeaking) 0.10f else 0f
        val lv = maxOf(level.value, speakingFloor)
        val ph = if (reducedMotion) 0.40f else phase.value

        when (style) {
            WaveStyle.Bars -> drawBars(lv, ph, barColor, isMuted)
            WaveStyle.Ribbon -> drawRibbon(lv, ph, barColor, isMuted)
            WaveStyle.Orbit -> drawOrbit(lv, ph, barColor, isMuted)
        }
        // 粒子只在有声音时出现：静音还飘粒子会让人以为有声音。
        if (particles && !isMuted && lv > 0.08f) {
            drawParticles(lv, ph, barColor)
        }
    }
}

/**
 * 竖条：最克制的一版。三条因子相乘（电平 × 包络 × 相位），缺一个都不像声波。
 * 相邻条相位差 0.55 弧度：不够则同涨同落像柱状图，太大像随机噪点。
 */
private fun DrawScope.drawBars(lv: Float, ph: Float, color: Color, isMuted: Boolean) {
    val gap = size.width / WAVE_BAR_COUNT
    val barWidth = (gap * 0.34f).coerceAtLeast(2.dp.toPx())
    val centerY = size.height / 2f
    val minBar = 3.dp.toPx()
    val maxBar = size.height
    val half = (WAVE_BAR_COUNT - 1) / 2f

    for (i in 0 until WAVE_BAR_COUNT) {
        val t = (i - half) / half
        val envelope = cos(t * (PI / 2) * 0.92).toFloat().coerceIn(0.10f, 1f)
        val wobble = 0.55f + 0.45f * sin(ph + i * 0.55f)
        val barHeight = minBar + (maxBar - minBar) * lv * envelope * wobble

        val left = i * gap + (gap - barWidth) / 2f
        drawRoundRect(
            color = color.copy(alpha = (0.34f + 0.66f * envelope) * if (isMuted) 0.62f else 1f),
            topLeft = Offset(left, centerY - barHeight / 2f),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
        )
    }
}

/**
 * 连续曲线（参考 WaveInApp 的柔和长波）：两条上下对称的正弦叠加，幅度分层。
 *
 * 为什么比竖条更像"声音"：人眼把连续曲线读成一条振动的弦，而竖条读成柱状统计。
 * 三层不同频率/相位叠加，避免一眼看出是纯正弦（规整感就是"假"的来源）。
 */
private fun DrawScope.drawRibbon(lv: Float, ph: Float, color: Color, isMuted: Boolean) {
    val centerY = size.height / 2f
    val amp = size.height * 0.46f * (0.15f + lv)
    val steps = 96
    val alpha = if (isMuted) 0.45f else 1f

    // 三层：主波 + 高频细节 + 慢速起伏。频率比不是整数，避免波形周期性重复。
    fun waveAt(x: Float): Float {
        val t = x / size.width
        val a = sin((t * 6.2f + ph) * PI.toFloat())
        val b = sin((t * 13.7f - ph * 1.7f) * PI.toFloat()) * 0.32f
        val c = sin((t * 2.1f + ph * 0.6f) * PI.toFloat()) * 0.22f
        // 两端收窄，避免曲线在边界被硬切
        val edge = sin(t * PI.toFloat()).coerceIn(0f, 1f)
        return (a + b + c) * edge
    }

    // 上下两条对称路径，中间用渐变填充，像一条发光的带子
    val path = Path()
    val upper = ArrayList<Offset>(steps + 1)
    val lower = ArrayList<Offset>(steps + 1)
    for (i in 0..steps) {
        val x = size.width * i / steps
        val y = waveAt(x) * amp
        upper += Offset(x, centerY - y)
        lower += Offset(x, centerY + y)
    }
    path.moveTo(upper[0].x, upper[0].y)
    for (i in 1..steps) path.lineTo(upper[i].x, upper[i].y)
    for (i in steps downTo 0) path.lineTo(lower[i].x, lower[i].y)
    path.close()

    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = 0.08f * alpha),
                color.copy(alpha = 0.55f * alpha),
                color.copy(alpha = 0.08f * alpha),
            ),
        ),
    )
    // 中心亮线：让带子有"芯"，不然只是一团色块
    val core = Path()
    core.moveTo(upper[0].x, centerY - waveAt(0f) * amp)
    for (i in 1..steps) {
        val x = size.width * i / steps
        core.lineTo(x, centerY - waveAt(x) * amp)
    }
    drawPath(core, color.copy(alpha = 0.55f * alpha), style = Stroke(width = 1.5.dp.toPx()))
}

/**
 * 环形放射条：围绕中心向外发散，配合头像的圆形构图。
 * 比竖条更有"能量从她那里发出来"的方向感。
 */
private fun DrawScope.drawOrbit(lv: Float, ph: Float, color: Color, isMuted: Boolean) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val baseR = size.minDimension * 0.18f
    val maxLen = size.minDimension * 0.32f
    val count = 72
    val alpha = if (isMuted) 0.5f else 1f

    for (i in 0 until count) {
        val angle = (i.toFloat() / count) * 2f * PI.toFloat()
        // 每根条独立相位；频率不一致，环上不会出现整齐的对称
        val wobble = 0.35f + 0.65f * sin(ph * 1.4f + i * 0.37f).toFloat()
        val len = maxLen * lv * wobble * 0.9f
        if (len < 1f) continue
        val dirX = cos(angle)
        val dirY = sin(angle)
        drawLine(
            color = color.copy(alpha = 0.75f * alpha * (0.4f + 0.6f * wobble)),
            start = Offset(cx + dirX * baseR, cy + dirY * baseR),
            end = Offset(cx + dirX * (baseR + len), cy + dirY * (baseR + len)),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 粒子：从波形中心向两侧飘散，越远越淡。
 *
 * 位置是相位 + 索引的纯函数（不用随机数），所以不需要持有粒子对象、也不会每帧分配；
 * 同一条波形上粒子的初始位置分布均匀，看起来像从声波里"抖"出来的。
 */
private fun DrawScope.drawParticles(lv: Float, ph: Float, color: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val count = 18

    for (i in 0 until count) {
        // 每颗粒子有自己的周期与相位；用质数步长让相邻粒子错开
        val seed = (i * 37 % 100) / 100f
        val cycle = (ph * 0.9f + seed * 6.28f)
        val progress = (cycle % 6.28f) / 6.28f          // 0..1 生命周期
        val dir = if (i % 2 == 0) -1f else 1f
        val drift = (seed - 0.5f) * 2f                   // -1..1 横向散布
        val rise = progress * progress                    // 加速上升
        val x = cx + dir * rise * size.width * 0.42f + drift * size.width * 0.06f
        val y = cy - rise * size.height * 0.55f + drift * size.height * 0.1f
        val radius = (1.2f + (1f - progress) * 2.2f).dp.toPx() * lv * 2.2f
        if (radius <= 0.3f) continue
        // 生命两端透明：出生和消失都不突兀
        val fade = sin(progress * PI.toFloat()).coerceIn(0f, 1f)
        drawCircle(
            color = color.copy(alpha = 0.55f * fade * lv),
            radius = radius,
            center = Offset(x, y),
        )
    }
}

// ============================================================================
// 摄像头形态
// ============================================================================

/**
 * 视频形态：远端画面占满，本地小窗挂在画面右下角，字幕压在最下面。
 *
 * 本地小窗为什么不放屏幕右下角：那里是底部操作条（静音/挂断/摄像头/文字）的位置，
 * 小窗压上去会挡住挂断按钮——这是视频通话里最不能挡的东西。
 * 所以小窗贴在**画面区域**的右下角，与操作条之间留出画面内边距。
 */
@Composable
private fun VideoStage(
    state: CompanionCallState,
    remoteVideoContent: (@Composable () -> Unit)?,
    localVideoContent: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(vertical = 8.dp),
    ) {
        // 远端画面容器。圆角 + 细描边，让它在深色背景上有一个明确的"画框"，
        // 否则黑画面和深背景糊成一片，用户不知道摄像头到底开没开。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF141B33), Color(0xFF0A0F1E)),
                    ),
                )
                .border(1.dp, CallColors.GlassBorder, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (remoteVideoContent != null) {
                // 真实画面由调用方注入（SurfaceView / TextureView / 图像帧都能塞）。
                // 用 fillMaxSize 交给插槽自己决定是 letterbox 还是 crop。
                Box(Modifier.fillMaxSize()) { remoteVideoContent() }
            } else {
                RemoteVideoPlaceholder(state = state)
            }

            // 字幕压在画面底部：视频形态下用户的视线在画面上，字幕必须贴在同一区域，
            // 不能挪到画面外——那会迫使人反复上下游移。
            CaptionStack(
                captions = state.captions,
                streaming = state.isAgentSpeaking,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 视频形态字幕上限更低：画面本身要留出来，字幕只占底部一条。
                    .heightIn(max = 140.dp)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }

        // 本地小窗：必须在远端画面容器之外，否则会被它的 clip 裁掉。
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp)
                .width(92.dp)
                .height(124.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1B2340))
                .border(1.dp, CallColors.GlassBorder, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (localVideoContent != null) {
                Box(Modifier.fillMaxSize()) { localVideoContent() }
            } else {
                Text(
                    text = "我",
                    color = CallColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/**
 * 远端画面占位。
 *
 * 明确写「等待她的画面」，而不是留一块纯黑：摄像头刚打开到第一帧到达之间有一两秒，
 * 纯黑分不清是"没开"还是"卡了"。有文案 + 她的名字，用户能判断这是正常过程。
 */
@Composable
private fun RemoteVideoPlaceholder(state: CompanionCallState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(CallColors.VioletDeep.copy(alpha = 0.35f))
                .border(1.dp, CallColors.Violet.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val emoji = state.avatarEmoji
            if (!emoji.isNullOrBlank()) {
                Text(text = emoji, fontSize = 34.sp)
            } else {
                Text(
                    text = state.agentName.trim().take(1).ifBlank { "她" },
                    color = CallColors.TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (state.connection == CallConnection.CONNECTED) "等待她的画面…" else "正在接通…",
            color = CallColors.TextSecondary,
            fontSize = 13.sp,
        )
    }
}

// ============================================================================
// 操作条
// ============================================================================

@Composable
private fun CallControlBar(
    isMuted: Boolean,
    cameraActive: Boolean,
    cameraAvailable: Boolean,
    composerOpen: Boolean,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onOpenComposer: () -> Unit,
    onHangUp: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 22.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(
            icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
            label = if (isMuted) "取消静音" else "静音",
            active = isMuted,
            onClick = onToggleMute,
        )
        ControlButton(
            icon = if (cameraActive) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
            label = if (cameraAvailable) {
                if (cameraActive) "关摄像头" else "摄像头"
            } else {
                "即将支持"
            },
            active = cameraActive,
            enabled = cameraAvailable,
            onClick = onToggleCamera,
        )
        ControlButton(
            icon = Icons.AutoMirrored.Filled.Send,
            label = "文字",
            active = composerOpen,
            onClick = onOpenComposer,
        )
        HangUpButton(onClick = onHangUp)
    }
}

/**
 * 普通操作按钮：56dp 圆 + 下方文字标签。
 *
 * 标签不是装饰：只有图标的静音/摄像头在纯图标状态下有歧义（"点了是开还是关"），
 * 中文字标签直接写当前**动作**（"静音" = 点了会静音），配上图标本身的形态
 * （MicOff 表示当前已静音）双向确认。
 *
 * 点击区域是整列（圆 + 标签），高度 ≈ 78dp，远大于 44dp 的可点下限。
 */
@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    /** false = 能力还没有，画成置灰态。仍然响应点击：调用方需要给出「即将支持」的提示。 */
    enabled: Boolean = true,
) {
    val iconColor = when {
        !enabled -> CallColors.TextSecondary.copy(alpha = 0.55f)
        active -> CallColors.Violet
        else -> CallColors.TextPrimary
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Transparent)
            .pointerInput(enabled) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !enabled -> CallColors.Glass.copy(alpha = 0.10f)
                        active -> CallColors.Violet.copy(alpha = 0.24f)
                        else -> CallColors.Glass
                    },
                )
                .border(
                    1.dp,
                    when {
                        !enabled -> CallColors.GlassBorder.copy(alpha = 0.10f)
                        active -> CallColors.Violet.copy(alpha = 0.55f)
                        else -> CallColors.GlassBorder
                    },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = when {
                !enabled -> CallColors.TextSecondary.copy(alpha = 0.55f)
                active -> CallColors.Violet
                else -> CallColors.TextSecondary
            },
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

/** 挂断的二次确认窗口。3 秒足够看清提示，又不至于让按钮长时间处于「待确认」。 */
private const val HANGUP_CONFIRM_WINDOW_MS = 3_000L

/**
 * 挂断：整页唯一的高饱和暖色，且比其它按钮大一圈（64dp vs 56dp）。
 *
 * 这符合"破坏性操作要显眼且易达"的常识：它是最不可能被误点的按钮（因为它最不像别的），
 * 也是用户最需要在慌乱时一眼找到的按钮。不放红色以外任何东西在它附近。
 *
 * 但「不可能误点」和「最易误触」只差一个位置：它就在拇指自然落点上。所以单击先进入
 * 待确认态（红圈 + 「再按一次挂断」），第二下才真的挂；3 秒无操作自动撤销。
 * 输入源用 `pointerInput(armed)`：`armed` 变了要重建手势块，否则回调里读到的是旧值。
 */
@Composable
private fun HangUpButton(onClick: () -> Unit) {
    var armed by remember { mutableStateOf(false) }

    LaunchedEffect(armed) {
        if (armed) {
            delay(HANGUP_CONFIRM_WINDOW_MS)
            armed = false
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            // 两下都以同一语义响应：第一下进入待确认，第二下才是挂断。
            .pointerInput(armed) {
                detectTapGestures(onTap = {
                    if (armed) onClick() else armed = true
                })
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFF25A5E), CallColors.HangupRed),
                    ),
                )
                .then(
                    if (armed) {
                        Modifier.border(2.dp, Color(0xFFFFD7D9), CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CallEnd,
                contentDescription = "挂断",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            // 文案随状态变：只换个红圈而没有字，用户不知道「再按一下会怎样」。
            text = if (armed) "再按一次挂断" else "挂断",
            color = if (armed) Color(0xFFFFD7D9) else CallColors.TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

// ============================================================================
// 文字输入浮层
// ============================================================================

/**
 * 通话中的文字输入。
 *
 * 两点刻意的设计：
 * - 浮层是整页半透明黑 + 底部卡片，点空白关闭。通话中弹一个居中 Dialog 会让人
 *   以为通话被打断了；底部升起更像"临时说一句"。
 * - 发送后自动关闭并清空，不保留草稿。通话场景里"说完了"信号比"还能接着说"更重要，
 *   保留浮层会让用户听不清她回的第一句。
 */
@Composable
private fun TextComposerOverlay(
    agentName: String,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // 自动聚焦 + 拉起键盘。delay 80ms 是为了等 AnimatedVisibility 的入场动画把
    // 布局撑开——在尺寸还是 0 的时候 requestFocus 会被系统忽略。
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    fun submit() {
        val text = draft.trim()
        if (text.isNotEmpty()) onSend(text)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩层单独承担"点空白关闭"。用 pointerInput 而不是 clickable，
        // 避免点击时出现水波纹——这里的水波纹会让人以为是"提交"。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB305070F))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                // 吃掉卡片区域的点击，避免点到卡片留白也把浮层关了。
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Text(
                text = "悄悄对${agentName.ifBlank { "她" }}说一句",
                color = CallColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("说点什么…", color = CallColors.TextSecondary.copy(alpha = 0.7f)) },
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                // App 的 MaterialTheme 是浅色的，这里必须显式覆盖文本/容器/光标色，
                // 否则会得到"浅色输入框里写浅灰字"——在深背景上几乎看不见。
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CallColors.TextPrimary,
                    unfocusedTextColor = CallColors.TextPrimary,
                    cursorColor = CallColors.Violet,
                    focusedContainerColor = Color(0xFF141B33),
                    unfocusedContainerColor = Color(0xFF141B33),
                    focusedBorderColor = CallColors.Violet.copy(alpha = 0.65f),
                    unfocusedBorderColor = CallColors.GlassBorder,
                    focusedPlaceholderColor = CallColors.TextSecondary.copy(alpha = 0.7f),
                    unfocusedPlaceholderColor = CallColors.TextSecondary.copy(alpha = 0.7f),
                ),
                trailingIcon = {
                    val enabled = draft.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (enabled) CallColors.Violet.copy(alpha = 0.85f)
                                else CallColors.Glass,
                            )
                            .pointerInput(enabled) {
                                detectTapGestures { if (enabled) submit() }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (enabled) Color.White else CallColors.TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        }
    }
}

// ============================================================================
// 小工具
// ============================================================================

/** 把外部电平收敛到 0f..1f。NaN / 越界值一律当作 0，避免画布出现负半径。 */
private fun Float.toLevelOrZero(): Float =
    if (isNaN()) 0f else coerceIn(0f, 1f)

/**
 * 系统是否要求"减少动效"（开发者选项里的动画时长缩放设为关闭，无障碍里也有对应开关）。
 *
 * Android 没有 Compose 层面的 prefers-reduced-motion，权威来源就是
 * Settings.Global.ANIMATOR_DURATION_SCALE == 0。读一次就 remember 住，
 * 不做动态监听——通话中途改这个设置是极罕见场景，为它挂 ContentObserver 不划算。
 *
 * 生效方式：持续呼吸和旋转弧线停掉，波形相位固定；电平驱动的缩放仍然保留
 * （那是有信息量的反馈，不是装饰）。
 */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}
