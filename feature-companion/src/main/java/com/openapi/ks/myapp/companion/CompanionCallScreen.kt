package com.openapi.ks.myapp.companion

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
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
import com.openapi.ks.myapp.dh.DhEngineMenuRow
import com.openapi.ks.myapp.dh.menuLabel
import androidx.compose.runtime.State
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
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
import kotlin.random.Random

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
data class CallCaption(val text: String, val speaker: String, val streaming: Boolean = false)

@Immutable
data class CompanionCallState(
    val agentName: String = "",
    /** 她的声音的人话名字，顶栏名字下面的小标签；null 时不显示。 */
    val voiceLabel: String? = null,
    val captions: List<String> = emptyList(),
    val speakerCaptions: List<CallCaption> = emptyList(),
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
    /**
     * 你的声音的频段能量（[VoiceSpectrum.BANDS] 个，低频在前，0..1）。
     * 音量决定波形多高，它决定波形长什么样。空数组 = 没有频谱，只按音量画。
     */
    val selfBands: FloatArray = FloatArray(0),
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
private const val LEVEL_FOLLOW_MS = 45

/** 呼吸环跟随电平的平滑时长。长，因为环要"绵"，不能跟着音节的起伏抽搐。 */
private const val RING_FOLLOW_MS = 280

/** 波形各条独立相位的循环时长。太快像噪点，太慢像卡住；1.6s 接近说话的语气节奏。 */
private const val PHASE_CYCLE_MS = 1600

/** 中央外圈 7 秒一轮，三圈错开扩散。 */
private const val RIPPLE_CYCLE_MS = 7000

/** 字幕淡入淡出时长。 */
private const val CAPTION_FADE_MS = 260

/**
 * 语音舞台的三种布局。纯数据 + [voiceStageMode] / [voiceStageHeightForMode] 两个纯函数，
 * 单独放在 JVM 测试里钉住（CompanionCallStageLayoutTest），布局改动必须先过它们。
 */
enum class VoiceStageMode {
    /** 无形象插槽：圆形头像 + 呼吸环，形象区高度 min(宽, 高×0.44)。原有布局。 */
    Classic,

    /** 有形象插槽但非主角模式（原来的形象分层立绘）：形象区占高 60%，下方字幕列。原有布局。 */
    Stacked,

    /** 数字人引擎舞台（Simple/Live2D/Duix/Tao）：形象铺满整块舞台，字幕叠在底部。 */
    CharacterHero,
}

fun voiceStageMode(hasAvatarStage: Boolean, characterHero: Boolean): VoiceStageMode = when {
    // 没有形象插槽就没有"主角"可铺——圆形头像布局不受 characterHero 影响。
    !hasAvatarStage -> VoiceStageMode.Classic
    characterHero -> VoiceStageMode.CharacterHero
    else -> VoiceStageMode.Stacked
}

fun voiceStageHeightForMode(mode: VoiceStageMode, maxWidth: Float, maxHeight: Float): Float = when (mode) {
    // 主角模式不裁高度：人物整块拿到舞台，唇形、手势、全身才看得见。
    VoiceStageMode.CharacterHero -> maxHeight
    VoiceStageMode.Stacked -> maxHeight * 0.60f
    VoiceStageMode.Classic -> minOf(maxWidth, maxHeight * 0.44f)
}


/**
 * 主角模式铺满整屏：形象在最底层，顶栏与操作条以浮层压在它上面（各带一层渐变遮罩保证对比度）。
 * 其余两种布局保持原有「顶栏 / 舞台 / 操作条」纵向排布。
 */
fun voiceStageIsFullBleed(mode: VoiceStageMode): Boolean = mode == VoiceStageMode.CharacterHero

/** 浮动操作条占高（56dp 按钮 + 标签 + 上下边距）。主角模式的字幕与波形要坐在它上方。 */
const val HERO_CONTROL_BAR_DP = 108f

/** 顶部遮罩高度：盖住状态行与名字，让亮色立绘上的白字仍可读。 */
const val HERO_TOP_SCRIM_DP = 112f

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
 * @param cameraAvailable 摄像头能力是否可用。false 时按钮显示「即将支持」，不可点击且暴露 disabled 语义。
 * @param modifier 外部布局约束。
 * @param avatarContent 头像插槽。**优先于** [CompanionCallState.avatarEmoji]，
 *   给了就整块渲染在圆形裁剪里（单张立绘、字标这类「头像」从这儿接）。
 * @param avatarStage 全身形象插槽。给了就切换成**形象舞台**：她站在舞台中央、不裁圆、
 *   可以点她互动；中央声场画在她身后。为 null 时用圆形头像布局。
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
    avatarStage: (@Composable () -> Unit)? = null,
    remoteVideoContent: (@Composable () -> Unit)? = null,
    localVideoContent: (@Composable () -> Unit)? = null,
    /** 波形样式与粒子开关。由调用方持有（要持久化），本页只消费。 */
    waveOptions: WaveOptions = WaveOptions(),
    /** 切换波形样式 / 粒子。null 时不显示切换入口。 */
    onWaveOptionsChange: ((WaveOptions) -> Unit)? = null,
    previewState: CallAudioPreviewState = CallAudioPreviewState(),
    onPreviewAudio: ((String) -> Unit)? = null,
    /**
     * 形象舞台是否走**主角模式**：true 时 [avatarStage] 铺满整块舞台（数字人引擎
     * Simple/Live2D/Duix/Tao——人物是页面主体，唇形手势要看得清），字幕改为底部叠层。
     * false（默认，含「原来的形象」分层立绘与圆形头像）时保持原有分栏布局不变。
     */
    characterHero: Boolean = false,
    /**
     * 右上角下拉里的形象引擎。null 时菜单和原来一样，只有波形样式。
     * 点不可切换的一项不改舞台，只回调原因。
     */
    engineMenu: List<DhEngineMenuRow>? = null,
    selectedEngine: com.openapi.ks.myapp.dh.DhEngineId? = null,
    onEngineMenuClick: ((DhEngineMenuRow) -> Unit)? = null,
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
    // 相位改由帧时钟驱动，而不是 rememberInfiniteTransition：后者受系统「动画时长缩放」
    // 控制，缩放为 0（开发者选项关动画、很多测试机的常态）时会整个停转，
    // 表现就是「波形/波纹完全不动」。帧时钟不受这个设置影响。
    val phase = rememberFramePhase(PHASE_CYCLE_MS)
    // 频段每 10ms 更新一次，只在波形的帧循环里读，不触发本页重组。
    val selfBands = rememberUpdatedState(if (state.isMuted) FloatArray(0) else state.selfBands)

    var composerOpen by rememberSaveable { mutableStateOf(false) }

    // 主角模式：形象铺满整屏做最底层，顶栏与操作条以浮层压在上面（各带渐变遮罩）；
    // 其余布局保持原有「顶栏 / 舞台 / 操作条」纵向排布。摄像头开着时仍走视频舞台。
    val fullBleed = !state.cameraActive && avatarStage != null &&
        voiceStageIsFullBleed(voiceStageMode(hasAvatarStage = true, characterHero = characterHero))

    val header: @Composable () -> Unit = {
        CallHeader(
            agentName = state.agentName,
            voiceLabel = callVoiceChipText(state.voiceLabel),
            connection = state.connection,
            elapsedSeconds = state.elapsedSeconds,
            isMuted = state.isMuted,
            statusHint = state.statusHint,
            waveOptions = waveOptions,
            onWaveOptionsChange = onWaveOptionsChange,
            engineMenu = engineMenu,
            selectedEngine = selectedEngine,
            onEngineMenuClick = onEngineMenuClick,
        )
        if (onPreviewAudio != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                androidx.compose.material3.TextButton(onClick = { onPreviewAudio("user") }) {
                    Text(if (previewState.speaker == "user") "■ 停止用户试听" else "▶ 用户试听", color = CallColors.TextPrimary)
                }
                androidx.compose.material3.TextButton(onClick = { onPreviewAudio("ai") }) {
                    Text(if (previewState.speaker == "ai") "■ 停止 AI 试听" else "▶ AI 试听", color = CallColors.TextPrimary)
                }
            }
            if (previewState.speaker != null || previewState.error != null) {
                Text(
                    text = previewState.error ?: if (previewState.preparing) "正在生成本地语音…" else "本地试听 · 已暂停真实麦克风与远端播放",
                    color = CallColors.TextSecondary, fontSize = 11.sp,
                )
            }
        }
    }
    val controls: @Composable () -> Unit = {
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
    val voiceStage: @Composable () -> Unit = {
        VoiceStage(
            state = state,
            ringLevel = ringLevel,
            waveLevel = waveLevel,
            selfBands = selfBands,
            waveOptions = waveOptions,
            phase = phase,
            centerEffect = waveOptions.center,
            avatarContent = avatarContent,
            avatarStage = avatarStage,
            characterHero = characterHero,
            reducedMotion = reducedMotion,
            modifier = Modifier.fillMaxSize(),
        )
    }

    CallBackdrop(level = ringLevel, modifier = modifier.fillMaxSize()) {
        if (fullBleed) {
            // 背景铺满全屏，内容让开状态栏/导航栏；浮层坐在形象之上。
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                voiceStage()
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .drawBehind {
                            drawRect(
                                Brush.verticalGradient(
                                    0f to CallColors.CaptionScrim,
                                    1f to Color.Transparent,
                                    endY = HERO_TOP_SCRIM_DP.dp.toPx(),
                                ),
                            )
                        }
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { header() }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { controls() }
            }
        } else Column(
            modifier = Modifier
                .fillMaxSize()
                // 背景铺满全屏，但内容要让开状态栏/导航栏。用 padding 而不是
                // consumeWindowInsets，因为本页下面还有一层浮层输入框要自己处理 IME。
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            header()

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
                    voiceStage()
                }
            }

            controls()
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
    voiceLabel: String? = null,
    connection: CallConnection,
    elapsedSeconds: Long,
    isMuted: Boolean,
    statusHint: String?,
    waveOptions: WaveOptions = WaveOptions(),
    onWaveOptionsChange: ((WaveOptions) -> Unit)? = null,
    engineMenu: List<DhEngineMenuRow>? = null,
    selectedEngine: com.openapi.ks.myapp.dh.DhEngineId? = null,
    onEngineMenuClick: ((DhEngineMenuRow) -> Unit)? = null,
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
            if (onWaveOptionsChange != null || engineMenu != null) {
                WaveStyleMenu(
                    options = waveOptions,
                    onChange = onWaveOptionsChange,
                    engineMenu = engineMenu,
                    selectedEngine = selectedEngine,
                    onEngineMenuClick = onEngineMenuClick,
                )
            } else {
                Spacer(Modifier.size(40.dp))
            }
        }
        if (voiceLabel != null) {
            // 声音跟形象走：名字下面一枚小标签，告诉用户现在听到的是谁的声音。
            Spacer(Modifier.height(4.dp))
            Text(
                text = "声音 · $voiceLabel",
                color = CallColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CallColors.Glass)
                    .border(1.dp, CallColors.GlassBorder, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
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
                color = when (connection) {
                    CallConnection.RECONNECTING -> CallColors.Amber
                    CallConnection.ENDED -> Color(0xFFFF9297)
                    CallConnection.CONNECTED -> CallColors.Violet
                    CallConnection.CONNECTING -> CallColors.TextPrimary
                },
                fontSize = if (connection == CallConnection.CONNECTING || connection == CallConnection.ENDED || statusHint != null) 15.sp else 13.sp,
                fontWeight = if (connection == CallConnection.CONNECTING || connection == CallConnection.ENDED || statusHint != null) FontWeight.Medium else FontWeight.Normal,
                letterSpacing = 0.sp,
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
 * 样式切换菜单，分两段：
 * - 底部「你的声音」：叠浪（默认，WaveInApp 移植）/ 竖条 / 柔和曲线，加粒子开关；
 * - 中央「她的声音」：呼吸环（默认）/ 环形放射 / 能量团 / 冲击波。
 * 粒子是最耗电的一项，低端机可以单独关掉。
 */
@Composable
private fun WaveStyleMenu(
    options: WaveOptions,
    onChange: ((WaveOptions) -> Unit)?,
    engineMenu: List<DhEngineMenuRow>? = null,
    selectedEngine: com.openapi.ks.myapp.dh.DhEngineId? = null,
    onEngineMenuClick: ((DhEngineMenuRow) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        // 玻璃底板 + 描边 + 亮色图标：深色背景上只有一个灰色小图标时，用户根本发现不了入口。
        IconButton(
            onClick = { open = true },
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CallColors.Glass)
                .border(1.dp, CallColors.GlassBorder, CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "切换波形样式",
                tint = CallColors.TextPrimary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (engineMenu != null && onEngineMenuClick != null) {
                MenuSectionLabel("形象")
                engineMenu.forEach { row ->
                    DropdownMenuItem(
                        text = { Text(row.menuLabel(selected = row.id == selectedEngine)) },
                        onClick = {
                            open = false
                            onEngineMenuClick(row)
                        },
                    )
                }
            }
            if (onChange == null) return@DropdownMenu
            MenuSectionLabel("底部 · 你的声音")
            BottomWaveStyle.entries.forEach { style ->
                val label = when (style) {
                    BottomWaveStyle.Wave -> "叠浪（WaveInApp）"
                    BottomWaveStyle.Bars -> "竖条"
                    BottomWaveStyle.Ribbon -> "柔和曲线"
                }
                DropdownMenuItem(
                    text = { Text(if (options.bottom == style) "✓ $label" else "　 $label") },
                    onClick = {
                        open = false
                        onChange(options.copy(bottom = style))
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

            MenuSectionLabel("中央 · 她的声音")
            CenterEffect.entries.forEach { effect ->
                val label = when (effect) {
                    CenterEffect.Ring -> "呼吸环"
                    CenterEffect.Orbit -> "环形放射"
                    CenterEffect.Blob -> "能量团"
                    CenterEffect.Blast -> "冲击波"
                }
                DropdownMenuItem(
                    text = { Text(if (options.center == effect) "✓ $label" else "　 $label") },
                    onClick = {
                        open = false
                        onChange(options.copy(center = effect))
                    },
                )
            }
        }
    }
}

/** 菜单里的分段小标题：不可点，只做视觉分组。 */
@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text = text,
        color = CallColors.TextSecondary,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp, end = 16.dp),
    )
}

private fun statusText(connection: CallConnection, elapsedSeconds: Long): String =
    when (connection) {
        CallConnection.CONNECTING -> "正在接通…"
        CallConnection.CONNECTED -> formatDuration(elapsedSeconds)
        CallConnection.RECONNECTING -> "连接不稳定，正在重连…"
        CallConnection.ENDED -> "连接失败或已断开"
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
    waveLevel: State<Float>,
    selfBands: State<FloatArray>,
    waveOptions: WaveOptions,
    phase: State<Float>,
    centerEffect: CenterEffect,
    avatarContent: (@Composable () -> Unit)?,
    avatarStage: (@Composable () -> Unit)?,
    characterHero: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        // 布局模式（纯函数 + JVM 测试钉住，见 CompanionCallStageLayoutTest）：
        // - Classic/Stacked 是原有分栏：形象区有确定高度，字幕列在它下面。
        // - CharacterHero 是数字人引擎舞台：形象拿到整块舞台高度（唇形、手势、全身
        //   是这一页存在的理由），字幕与声效改为底部叠层。
        val mode = voiceStageMode(hasAvatarStage = avatarStage != null, characterHero = characterHero)
        // 纯函数只吃 px 以外的纯数值方便 JVM 测试钉住，这里用 Dp.value（密度无关的 dp 数值）再转回 Dp。
        val stageHeight = voiceStageHeightForMode(mode, maxWidth.value, maxHeight.value).dp
        val hero = mode == VoiceStageMode.CharacterHero

        // 波形和声场两条分支共用，抽成局部 composable。align 这类作用域修饰符由调用方
        // 传入（BoxScope 作用域修饰符跨嵌套 Box 复用会在运行时抛 scope 不匹配）。
        val waveform: @Composable (Modifier) -> Unit = { mod ->
            WaveformBars(
                level = waveLevel,
                bands = selfBands,
                phase = phase,
                isMuted = state.isMuted,
                isSpeaking = false,
                reducedMotion = reducedMotion,
                style = waveOptions.bottom,
                particles = waveOptions.particles,
                modifier = mod
                    .fillMaxWidth()
                    .height(150.dp)
                    .graphicsLayer { alpha = 0.90f }
                    .blur(1.2.dp),
            )
        }

        val soundField = @Composable {
            if (centerEffect == CenterEffect.Ring) {
                BreathingRing(
                    level = ringLevel,
                    isMuted = state.isMuted,
                    isSpeaking = state.isAgentSpeaking,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CenterEffectCanvas(
                    effect = centerEffect,
                    level = ringLevel,
                    phase = phase,
                    isMuted = state.isMuted,
                    isSpeaking = state.isAgentSpeaking,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (hero) {
            Box(Modifier.fillMaxSize()) {
                // 声场画在形象身后：Simple（透明背景）能透出呼吸环，GL 引擎自然盖住它。
                soundField()
                // 主角：不裁圆、不缩放、不给字幕让位，让她完整地占满舞台；触摸由形象自己处理。
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    checkNotNull(avatarStage).invoke()
                }
                // 底部渐变遮罩：立绘下摆和波形条可能很亮，字幕压在人物身上也要读得出。
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height((240f + HERO_CONTROL_BAR_DP).dp)
                        .drawBehind {
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.55f to CallColors.CaptionScrim,
                                    1f to CallColors.CaptionScrim,
                                ),
                            )
                        },
                ) {
                    waveform(Modifier.align(Alignment.BottomCenter).padding(bottom = HERO_CONTROL_BAR_DP.dp))
                }
                // 字幕叠层：坐在波形上方，上限两行滚动；不再从人物身上切走一栏。
                CaptionStack(
                    captions = state.captions,
                    speakerCaptions = state.speakerCaptions,
                    streaming = state.isAgentSpeaking,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = (HERO_CONTROL_BAR_DP + 110f).dp),
                )
            }
        } else {
            waveform(Modifier.align(Alignment.BottomCenter))
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(stageHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    soundField()
                    if (avatarStage != null) {
                        // 形象舞台（原来的形象分层立绘）：不裁圆、不缩放，原有布局不变。
                        Box(Modifier.fillMaxSize().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                            avatarStage()
                        }
                    } else {
                        AvatarCircle(
                            name = state.agentName,
                            emoji = state.avatarEmoji,
                            level = ringLevel,
                            isMuted = state.isMuted,
                            slot = avatarContent,
                        )
                    }
                }
                CaptionStack(
                    captions = state.captions,
                    speakerCaptions = state.speakerCaptions,
                    streaming = state.isAgentSpeaking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * 呼吸环：三圈同心描边 + 一层径向填充 + 两圈向屏幕边缘荡开的柔波。
 *
 * 三圈的半径和透明度都随 [level] 变化，但**变化幅度不同**（内圈最灵敏，外圈最迟钝），
 * 这样电平上去的时候环会"一层层往外推"，而不是整组等比例放大——后者看起来像一个
 * 缩放动画，没有"声音顶出来"的物理感。
 *
 * 外圈用独立的 7 秒慢相位，从头像附近扩散到屏幕两侧边缘之外，再渐隐。
 * 原有内圈保持正圆，不按字幕剩余空间缩放。
 * 中央舞台**不画粒子**——粒子只属于底部「你的声音」。
 *
 * 静音时换成青灰，并且压低不透明度：静音是"我关了麦"，不是错误，不该用红色。
 */
@Composable
private fun BreathingRing(
    level: State<Float>,
    isMuted: Boolean,
    isSpeaking: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    // 空场呼吸：即使她完全不说话，环也要缓慢起伏一点，否则整页像挂了一张静态图，
    // 用户会怀疑是不是卡了。幅度上限 0.16，远小于说话时的 1.0，不抢戏。
    // 3.4s 一个来回；同样走帧时钟，避免系统动画缩放为 0 时整个停住。
    val breathPhase = rememberFramePhase(6800)

    // 外圈每 7 秒扩散一轮，三圈错开；头像和原有同心圆的造型不参与拉伸。
    val ripplePhase = rememberFramePhase(RIPPLE_CYCLE_MS)

    val ringColor = if (isMuted) CallColors.Slate else CallColors.Violet

    Canvas(modifier = modifier) {
        // 减少动效时把呼吸压到极小、相位固定：不追求"活着"，只保证有边界。
        val idle = if (reducedMotion) 0.5f else (1f - kotlin.math.cos(breathPhase.value)) / 2f
        // 说话下限：VAD 判定在说话、但电平恰好过零时，保住 0.12，避免环瞬间塌回去。
        val lv = level.value
        val breath = 0.012f * idle

        val center = Offset(size.width / 2f, size.height / 2f)
        val unit = size.width / 2f

        // 填充光晕：比描边更先被看见，负责"整块亮起来"。
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = 0.18f + 0.12f * lv),
                    Color.Transparent,
                ),
                center = center,
                radius = 78.dp.toPx() + unit * (0.18f * lv + breath),
            ),
            radius = 78.dp.toPx() + unit * (0.18f * lv + breath),
            center = center,
        )

        // 三圈描边，整体比旧版放大一圈：内圈仍贴着头像（0.48），外圈满电平推到 0.94，
        // 已经贴近屏幕边缘。[sensitivity] 递增，电平上去时外圈走得更远，
        // 制造"声音把环一层层往外顶"的感觉。
        val rings = listOf(
            Triple(0.42f, 0.10f, 0.55f),
            Triple(0.54f, 0.17f, 0.34f),
            Triple(0.66f, 0.20f, 0.20f),
        )
        rings.forEachIndexed { index, (baseFraction, sensitivity, baseAlpha) ->
            val radius = (74f + index * 7f).dp.toPx() + unit * (sensitivity * lv + breath)
            drawCircle(
                color = ringColor.copy(alpha = (baseAlpha * (0.28f + 0.72f * lv)).coerceIn(0f, 1f)),
                radius = radius,
                center = center,
                style = Stroke(width = if (index == 0) 1.6.dp.toPx() else 1f.dp.toPx()),
            )
        }

        // 扩散波纹：三圈圆形，从头像边缘一路荡到屏幕两侧边缘之外（画布裁掉），边走边淡。
        // 上一版把透明度压到 ≤0.08、又改成按舞台长宽拉伸的椭圆，结果几乎看不见也到不了边——
        // 这里改回正圆（终点按短边=屏幕宽度算，横向一定到边），透明度有可见的底（静默也看得见），
        // 说话时随电平加深。淡入很短（前 12%），淡出用 (1-p)^1.6，越靠边越柔。
        // 波纹是「她在说话」的反馈而不是装饰，不随「减少动效」关掉；
        // 否则系统动画缩放为 0 的设备上，中央就只剩一圈死边界。
        run {
            val cycle = ripplePhase.value / (2f * PI.toFloat())
            val startRadius = 74.dp.toPx()
            // 波纹的行程不乘音量：普通语音电平常在 0.1~0.4，乘上去永远到不了边。
            // 外层内容左右各留 20dp，终点越过这个留白才是真正屏幕边缘。
            val endRadius = maxOf(startRadius + 24.dp.toPx(), unit + 32.dp.toPx())
            for (k in 0..2) {
                val p = (cycle + k / 3f) % 1f
                val fadeIn = (p / 0.12f).coerceAtMost(1f)
                // 大部分行程保持可见，仅最后 22% 渐隐，避免还没到边就消失。
                val fadeOut = ((1f - p) / 0.22f).coerceIn(0f, 1f)
                val alpha = (fadeIn * fadeOut * (0.035f + 0.50f * kotlin.math.sqrt(lv))).coerceIn(0f, 0.6f)
                if (alpha < 0.01f) continue
                val radius = startRadius + (endRadius - startRadius) * p
                // 先画一层宽而淡的光晕，再画细描边：只有一条细线时会显得"硬"。
                drawCircle(
                    color = ringColor.copy(alpha = alpha * 0.35f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = (7f * (1f - p) + 2f).dp.toPx()),
                )
                drawCircle(
                    color = ringColor.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = (2.2f * (1f - p) + 0.8f).dp.toPx()),
                )
            }
        }

        // 最外圈加一条"掠过的亮弧"：用一个短弧沿圆周慢速旋转，暗示持续在听。
        // 只在非减少动效时画；它比让整圈闪烁安静得多。
        if (!reducedMotion) {
            val sweepRadius = 90.dp.toPx() + unit * 0.20f * lv
            drawArc(
                color = ringColor.copy(alpha = 0.06f + 0.24f * lv),
                startAngle = (ripplePhase.value * 180f / PI.toFloat()) % 360f,
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
 * 中央三套放射型效果（Orbit/Blob/Blast）的画布。它们原本就是中心坐标系实现，
 * 直接在舞台尺寸的画布上分派；电平取她的 ringLevel，VAD 说话时保 0.12 下限，
 * 与 [BreathingRing] 的说话兜底一致，避免过零时整团塌没。
 */
@Composable
private fun CenterEffectCanvas(
    effect: CenterEffect,
    level: State<Float>,
    phase: State<Float>,
    isMuted: Boolean,
    isSpeaking: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val effectColor = if (isMuted) CallColors.Slate else CallColors.Violet
    Canvas(modifier = modifier) {
        val lv = level.value
        val ph = if (reducedMotion) 0.40f else phase.value
        when (effect) {
            CenterEffect.Orbit -> drawOrbit(lv, ph, effectColor, isMuted)
            CenterEffect.Blob -> drawBlob(lv, ph, effectColor, isMuted)
            CenterEffect.Blast -> drawBlast(lv, ph, effectColor, isMuted)
            CenterEffect.Ring -> Unit
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
            .requiredSize(diameter)
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
    speakerCaptions: List<CallCaption> = emptyList(),
) {
    // 带原始下标的非空句：下标当 key，追加新句时已有项的 key 不变。
    val visible = remember(captions, speakerCaptions) {
        val lines = speakerCaptions.ifEmpty { captions.map { CallCaption(it, "AI", streaming) } }
        lines.mapIndexedNotNull { index, line -> if (line.text.isNotBlank()) index to line else null }
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

    LaunchedEffect(visible.size, visible.lastOrNull()?.second?.text) {
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
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.Bottom),
    ) {
        itemsIndexed(visible, key = { _, item -> item.first }) { index, (_, line) ->
            val fromNewest = visible.lastIndex - index
            CaptionLine(
                text = "${line.speaker}：${line.text}",
                targetAlpha = when {
                    fromNewest <= 0 -> 1f
                    fromNewest == 1 -> 0.82f
                    else -> 0.68f
                },
                // 只有最新一句带流式光标，且必须真的在说话。
                showCursor = line.streaming && fromNewest <= 0,
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
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
        // 不再截断：一句说完的完整回复要能整段读完，太长由外层滚动承接。
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp),
    )
}

// ============================================================================
// 底部波形
// ============================================================================

/**
 * 底部「你的声音」样式。只有水平构图的三种：
 * - [Wave]：Cleveroad/WaveInApp 叠浪（默认），7 个单拱 + 弯曲上浮粒子；
 * - [Bars]：竖条；[Ribbon]：柔和曲线。
 * 环形/能量团/冲击波是中央「她的声音」效果，见 [CenterEffect]，不属于这里。
 */
enum class BottomWaveStyle { Wave, Bars, Ribbon }

/**
 * 中央「她的声音」效果，由她的音量（mouthLevel）驱动：
 * - [Ring]：默认呼吸环；[Orbit]：环形放射；[Blob]：能量团；[Blast]：冲击波。
 */
enum class CenterEffect { Ring, Orbit, Blob, Blast }

/**
 * 波形风格 + 粒子开关。调用方持有，界面只读。
 */
@Immutable
data class WaveOptions(
    /** 底部「你的声音」样式，默认 WaveInApp 叠浪。 */
    val bottom: BottomWaveStyle = BottomWaveStyle.Wave,
    /** 中央「她的声音」效果，默认呼吸环。 */
    val center: CenterEffect = CenterEffect.Ring,
    /** 是否从波形里飘出粒子。关掉可以省电，低端机建议关。 */
    val particles: Boolean = true,
)

/**
 * 底部「你的声音」可视化：按 [style] 分派。
 *
 * - [BottomWaveStyle.Wave]：WaveInApp 移植，自带帧时钟与状态机，见 [WaveInAppCanvas]；
 * - [BottomWaveStyle.Bars] / [BottomWaveStyle.Ribbon]：相位驱动的备选样式，
 *   形状由电平 × 包络 × 相位三因子相乘，只用电平会像柱状统计，三者相乘才像声波。
 *
 * 电平与相位都是 State，只在 draw 阶段读，因此每帧只重绘不重组。
 */
@Composable
private fun WaveformBars(
    level: State<Float>,
    bands: State<FloatArray>,
    phase: State<Float>,
    isMuted: Boolean,
    isSpeaking: Boolean,
    reducedMotion: Boolean,
    style: BottomWaveStyle = BottomWaveStyle.Bars,
    particles: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val barColor = if (isMuted) CallColors.Slate else CallColors.Violet

    if (style == BottomWaveStyle.Wave) {
        WaveInAppCanvas(
            level = level,
            bands = bands,
            isMuted = isMuted,
            reducedMotion = reducedMotion,
            particles = particles,
            color = barColor,
            modifier = modifier,
        )
        return
    }

    // 每根条对应一个频段位置：条高 = 音量 × 该频段的相对能量，没有随机目标、没有循环相位。
    val field = remember { SoftVoiceField(WAVE_BAR_COUNT) }
    var frame by remember { mutableStateOf(0L) }
    LaunchedEffect(isMuted, reducedMotion) {
        while (true) withFrameMillis { now ->
            field.tick(if (isMuted) 0f else level.value, if (isMuted) FloatArray(0) else bands.value, now, reducedMotion)
            frame = now
        }
    }
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION") frame
        when (style) {
            BottomWaveStyle.Bars -> drawBars(field.values, barColor, isMuted)
            BottomWaveStyle.Ribbon -> drawRibbon(field.values, barColor, isMuted)
            BottomWaveStyle.Wave -> Unit
        }
        if (particles && !isMuted) {
            field.motes.forEach { mote ->
                if (mote.progress < 1f) {
                    val fade = sin(mote.progress * PI.toFloat()).coerceAtLeast(0f)
                    drawCircle(
                        color = barColor.copy(alpha = 0.22f * fade),
                        radius = mote.radius.dp.toPx(),
                        center = Offset(
                            size.width * (mote.x + mote.drift * mote.progress),
                            size.height * (0.52f - mote.progress * 0.42f),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * WaveInApp 叠浪的 Compose 宿主：仿真状态机 [WaveInAppSim] 记住在组合里，
 * 由 [withFrameMillis] 每帧按真实时间推进（对应原版 GLRenderer.onDrawFrame 的 dt 调度），
 * 不做相位回绕，所以不存在周期性跳变。frameTime 这个 State 只在 draw lambda 里读，
 * 每帧只触发重绘、不触发重组。
 */
@Composable
private fun WaveInAppCanvas(
    level: State<Float>,
    bands: State<FloatArray>,
    isMuted: Boolean,
    reducedMotion: Boolean,
    particles: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val sim = remember { WaveInAppSim() }
    var frameTime by remember { mutableStateOf(0L) }

    LaunchedEffect(isMuted, reducedMotion) {
        while (true) {
            withFrameMillis { now ->
                sim.tick(
                    level = level.value,
                    bands = bands.value,
                    muted = isMuted,
                    nowMs = now,
                    reducedMotion = reducedMotion,
                )
                frameTime = now
            }
        }
    }

    Canvas(modifier = modifier) {
        // 读帧时间以注册每帧重绘；仿真本身已在帧循环里推进，这里不做第二遍。
        @Suppress("UNUSED_EXPRESSION") frameTime
        drawWaveInAppSim(sim = sim, color = color, particlesEnabled = particles, isMuted = isMuted)
    }
}

/**
 * 低幅竖条：各条独立、平滑地跟随声音；两端收窄，不按共同相位整排涨落。
 */
private fun DrawScope.drawBars(values: FloatArray, color: Color, isMuted: Boolean) {
    val gap = size.width / values.size
    val barWidth = (gap * 0.25f).coerceAtLeast(2.dp.toPx())
    for (i in values.indices) {
        val edge = sin((i + 0.5f) / values.size * PI.toFloat()).coerceAtLeast(0.12f)
        val h = 2.dp.toPx() + size.height * 0.40f * values[i] * edge
        drawRoundRect(
            color = color.copy(alpha = if (isMuted) 0.25f else 0.62f),
            topLeft = Offset(i * gap + (gap - barWidth) / 2f, (size.height - h) / 2f),
            size = Size(barWidth, h),
            cornerRadius = CornerRadius(barWidth / 2f),
        )
    }
}

/**
 * 三条低振幅贝塞尔曲线：节点来自非循环声场，不使用时间正弦或反复回绕的相位。
 * 各层错开节点取样，端点收窄；真实声音决定整体强度。
 */
private fun DrawScope.drawRibbon(values: FloatArray, color: Color, isMuted: Boolean) {
    val mid = size.height * 0.5f
    for (layer in 0..2) {
        val path = Path()
        path.moveTo(0f, mid)
        var lastX = 0f
        var lastY = mid
        for (i in values.indices) {
            val t = (i + 1f) / (values.size + 1f)
            val x = size.width * t
            val sign = if ((i / 4 + layer) % 2 == 0) 1f else -1f
            val y = mid + sign * values[(i + layer * 3) % values.size] *
                sin(t * PI.toFloat()) * size.height * (0.17f - layer * 0.025f)
            path.cubicTo((lastX + x) / 2f, lastY, (lastX + x) / 2f, y, x, y)
            lastX = x
            lastY = y
        }
        path.cubicTo((lastX + size.width) / 2f, lastY, size.width, mid, size.width, mid)
        drawPath(path, color.copy(alpha = if (isMuted) 0.14f else 0.40f - layer * 0.09f),
            style = Stroke(width = (2.0f - layer * 0.35f).dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * 一阶低通的每帧系数。起音快（30ms）、回落稍慢（110ms）：跟得上一个个音节，
 * 停下来时又不会「啪」地一下归零。减少动效时只把回落放慢，起音不变——声音来了就要有反应。
 */
private fun voiceFollow(dtMs: Float, rising: Boolean, reducedMotion: Boolean): Float {
    val tau = if (rising) 30f else if (reducedMotion) 220f else 110f
    return 1f - kotlin.math.exp(-dtMs / tau)
}

/** 频段里的最大值，作为相对形状的分母；太小时用下限，避免底噪被放大成满幅。 */
private fun bandPeak(bands: FloatArray): Float = maxOf(bands.maxOrNull() ?: 0f, 0.18f)

/**
 * 横向位置 [pos]（0..1，左低频右高频）对应的相对频段能量 0..1，频段之间线性插值。
 * 没有频谱时返回 1：只按音量画，形状平整而不是编一个随机形状。
 */
private fun bandShape(bands: FloatArray, pos: Float, peak: Float): Float {
    if (bands.isEmpty()) return 1f
    val x = (pos.coerceIn(0f, 1f) * (bands.size - 1))
    val i = x.toInt().coerceIn(0, bands.size - 1)
    val j = (i + 1).coerceAtMost(bands.size - 1)
    val v = bands[i] + (bands[j] - bands[i]) * (x - i)
    return (v / peak).coerceIn(0f, 1f)
}

/** 当前最强频段的横向位置（0..1）；没有频谱时在中间。 */
private fun bandPeakPosition(bands: FloatArray): Float {
    if (bands.isEmpty()) return 0.5f
    var best = 0
    for (i in bands.indices) if (bands[i] > bands[best]) best = i
    return (best + 0.5f) / bands.size
}

/** 非循环声场：随机只决定空间细节，振幅始终由真实声音控制；无声时缓慢归零。 */
private class SoftVoiceMote {
    var progress = 1f
    var x = 0f
    var drift = 0f
    var radius = 0f
    var lifetimeMs = 1f
}

private class SoftVoiceField(count: Int) {
    val motes = Array(8) { SoftVoiceMote() }
    val values = FloatArray(count)
    private val random = Random(System.nanoTime())
    private var last = 0L
    private var amplitude = 0f
    private var nextMoteMs = 0L

    /**
     * 每帧推进。条 i 取「它所在位置对应的频段」的相对能量，再乘整体音量：
     * 音量管多高，频段管形状。起音 ~30ms、回落 ~110ms，跟得上音节，又不会逐帧抖。
     * 没有随机目标，也没有循环相位——同样的声音画出同样的形状。
     */
    fun tick(level: Float, bands: FloatArray, now: Long, reducedMotion: Boolean) {
        val dt = if (last == 0L) 0f else (now - last).coerceIn(0L, 64L).toFloat()
        last = now
        val input = level.toLevelOrZero()
        val target = if (input <= 0.02f) 0f else kotlin.math.sqrt(input)
        amplitude += (target - amplitude) * voiceFollow(dt, rising = target > amplitude, reducedMotion)
        val peak = bandPeak(bands)
        for (i in values.indices) {
            val pos = (i + 0.5f) / values.size
            val shape = bandShape(bands, pos, peak)
            val goal = amplitude * (0.22f + 0.78f * shape)
            values[i] += (goal - values[i]) * voiceFollow(dt, rising = goal > values[i], reducedMotion)
        }
        motes.forEach { if (it.progress < 1f) it.progress += dt / it.lifetimeMs }
        // 粒子出生频率跟音量走：越响越密，安静时不出。位置落在当前最强的频段附近。
        if (input > 0.08f && now >= nextMoteMs) {
            motes.firstOrNull { it.progress >= 1f }?.let {
                it.progress = 0f
                it.x = (bandPeakPosition(bands) + (random.nextFloat() - 0.5f) * 0.2f).coerceIn(0.08f, 0.92f)
                it.drift = (random.nextFloat() - 0.5f) * 0.08f
                it.radius = 1f + input * 1.4f
                it.lifetimeMs = 1400f + random.nextFloat() * 1200f
            }
            nextMoteMs = now + (620f - 480f * input).toLong()
        }
    }
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
 * Blob（参考 gauravk95/audio-visualizer-android 的 BlobVisualizer）：
 * 一团随声音呼吸、变形的能量团。
 *
 * 参考实现的做法是沿圆周取 N 个点，每个点的半径 = 基础半径 + 振幅 × 随机重力，
 * 再连成封闭曲面。这里把"随机重力"换成三组不同频率/相位的正弦谐波——
 * 视觉上同样不规则、不重复，而且是相位的纯函数，每帧零分配、可复现。
 * 电平越高，团越大、表面起伏越剧烈；静音时收成一个缓慢蠕动的小圆点，不会彻底僵住。
 */
private fun DrawScope.drawBlob(lv: Float, ph: Float, color: Color, isMuted: Boolean) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxR = size.minDimension * 0.46f
    // 静音/没人说话时保留 0.24 的基础半径 + 极轻微蠕动，让它"还活着"。
    val baseR = maxR * (0.24f + 0.62f * lv)
    val points = 48
    val alpha = if (isMuted) 0.55f else 1f

    fun radiusAt(i: Int): Float {
        val theta = i.toFloat() / points * 2f * PI.toFloat()
        // 三组谐波叠加；系数和为 0.43，保证最剧烈时半径也不会跑出 maxR。
        val deform = 0.22f * sin(ph * 1.3f + theta * 3f) +
            0.13f * sin(ph * 0.7f + theta * 5f + 1.3f) +
            0.08f * sin(theta * 7f - ph * 2.1f)
        val idle = 0.03f * sin(ph + theta * 2f)
        return baseR * (1f + lv * deform + idle)
    }

    val path = Path()
    for (i in 0..points) {
        val theta = i.toFloat() / points * 2f * PI.toFloat()
        val r = radiusAt(i % points)
        val x = cx + r * cos(theta)
        val y = cy + r * sin(theta)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    drawPath(
        path = path,
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.42f * alpha),
                color.copy(alpha = 0.16f * alpha),
                Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = baseR * 1.5f,
        ),
    )
    drawPath(
        path = path,
        color = color.copy(alpha = 0.75f * alpha),
        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
    )
}

/**
 * Blast（参考 gauravk95/audio-visualizer-android 的 BlastVisualizer）：
 * 中心能量核 + 向外放射的冲击条 + 两圈持续荡出、越远越淡的冲击波。
 *
 * 与 [drawOrbit] 的区别在"荡开"：Orbit 的放射条是原地伸缩，Blast 额外有两圈
 * 半径随相位单向推进、透明度按距离平方衰减的冲击波，电平够高时波峰可以一直
 * 推到画布边缘——这正是"波纹荡远一些、到屏幕边上"的那个视觉。
 */
private fun DrawScope.drawBlast(lv: Float, ph: Float, color: Color, isMuted: Boolean) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val hubR = size.minDimension * 0.09f
    val maxR = minOf(size.width / 2f, size.height / 2f) * 0.98f
    val count = 42
    val alpha = if (isMuted) 0.5f else 1f

    // 中心能量核：半径与亮度都随电平走。
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.55f * alpha),
                color.copy(alpha = 0.12f * alpha),
                Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = hubR * (1.6f + 2.2f * lv),
        ),
        radius = hubR * (1.6f + 2.2f * lv),
        center = Offset(cx, cy),
    )

    // 放射冲击条。
    for (i in 0 until count) {
        val angle = i.toFloat() / count * 2f * PI.toFloat()
        val wobble = 0.35f + 0.65f * sin(ph * 1.4f + i * 0.47f).toFloat()
        val len = (maxR - hubR) * lv * wobble
        if (len < 1.5f) continue
        val dirX = cos(angle)
        val dirY = sin(angle)
        drawLine(
            color = color.copy(alpha = 0.7f * alpha * (0.35f + 0.65f * wobble)),
            start = Offset(cx + dirX * hubR, cy + dirY * hubR),
            end = Offset(cx + dirX * (hubR + len), cy + dirY * (hubR + len)),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    // 两圈错相的冲击波：相位 0..1 单向推进，半径从核边走到画布边缘，透明度按 (1-p)^2 衰减。
    val cycle = (ph / (2f * PI.toFloat()))
    for (k in 0..1) {
        val p = (cycle * 0.5f + k * 0.5f) % 1f
        val radius = hubR + p * (maxR - hubR)
        val ringAlpha = (1f - p) * (1f - p) * (0.12f + 0.5f * lv) * alpha
        if (ringAlpha <= 0.01f) continue
        drawCircle(
            color = color.copy(alpha = ringAlpha),
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = (2.2f * (1f - p) + 0.6f).dp.toPx()),
        )
    }
}

// ============================================================================
// WaveInApp 移植（对照 Cleveroad/WaveInApp：GLRenderer / GLWaveLayer / GLWave / GLBubble）
//
// 原版是 GLES 实现，这里改成纯 Kotlin 状态机 + Compose Canvas 渲染，类对应关系：
// GLRenderer 的帧调度 -> WaveInAppSim.tick；GLWaveLayer -> WiaLayer；GLWave -> WiaWave；
// GLBubble -> WiaBubble。保留造型；按用户的柔和要求，周期驱动改为独立低通漂移，
// 并降低振幅与粒子密度。
// ============================================================================


/** GLBubble.BUBBLE_ANIMATION_DURATION：粒子寿命 1000ms。 */
private const val WIA_BUBBLE_PERIOD_MS = 1000L

/** Constants.DEFAULT_WAVES_COUNT：每层 7 个单拱。 */
private const val WIA_WAVES_COUNT = 7

/** 偏差 1：参考库默认 4 层，140dp 的条带里 3 层密度已足够，多出的一层只会糊成色块。 */
private const val WIA_LAYER_COUNT = 3

/** 偏差 2：参考库每层粒子池 8 个；我们只有前景一层产粒子，池给 10 个。 */
private const val WIA_BUBBLE_POOL = 10



/** 粒子直径基准，参考库默认 20px 是全屏 GLES 场景，缩到 140dp 条带取 4.5dp。 */
private const val WIA_BUBBLE_SIZE_DP = 4.5f

/** 三层浪的浪高占比与填充透明度，沿用改版前实测好看的一组值。 */
private val WIA_LAYER_AMP = floatArrayOf(0.58f, 0.46f, 0.36f)
private val WIA_LAYER_ALPHA = floatArrayOf(0.24f, 0.32f, 0.44f)



/**
 * 保留原版贝塞尔单拱的造型（相邻拱一上一下），但高度和拱顶位置**全部来自声音**：
 * 高度 = 音量 × 本拱所在频段的相对能量；拱顶向能量更强的一侧偏。没有随机漂移目标。
 */
private class WiaWave(
    val fromX: Float,
    val toX: Float,
    up: Boolean,
) {
    var waveX = 0f
    var displacement = 0f
    private val direction = if (up) 1f else -1f

    /** [height] 0..1：本拱此刻应有的高度；[lean] -1..1：拱顶偏向。按帧低通跟随。 */
    fun advance(dtMs: Float, height: Float, lean: Float, reducedMotion: Boolean) {
        val goal = direction * height
        displacement += (goal - displacement) *
            voiceFollow(dtMs, rising = kotlin.math.abs(goal) > kotlin.math.abs(displacement), reducedMotion)
        waveX += (lean * 0.55f - waveX) * (1f - kotlin.math.exp(-dtMs / 90f))
    }
}

private class WiaLayer(
    val waves: List<WiaWave>,
    val speedCoef: Float,
    val ampFrac: Float,
    val fillAlpha: Float,
)

/** 对应 GLBubble：1 秒寿命、正弦弯曲上浮、透明度随上升线性衰减。 */
private class WiaBubble {
    var active = false
    var startX = 0f
    var angle = 0f
    var centerY = -1f
    var virtualSpeed = 0f
    var sizeDp = 0f
}

/**
 * WaveInApp 造型状态机：保留分层贝塞尔拱形、渐变和粒子。
 *
 * 每帧按真实时间推进，直接吃两路输入：音量决定整体高度，频段决定每个拱的高低与偏向。
 * 不再有 100ms 节流、0.7..1.3 的随机化或随机漂移目标——那些让波形「自己动自己的」，
 * 和你说话的轻重、高低对不上。
 */
private class WaveInAppSim {
    private val random = Random(0x5EED1234L)
    val layers: List<WiaLayer>
    val bubbles = Array(WIA_BUBBLE_POOL) { WiaBubble() }
    private var amplitude = 0f
    private var lastFrameMs = 0L
    private var nextBubbleMs = 0L

    init {
        // GLWaveLayer.randomPoints：7 拱的边界点带 ±0.15 拱宽抖动，首尾固定在屏幕两端。
        val waveWidth = 2f / WIA_WAVES_COUNT
        val points = FloatArray(WIA_WAVES_COUNT + 1)
        for (i in points.indices) {
            points[i] = when (i) {
                0 -> -1f
                points.lastIndex -> 1f
                else -> {
                    val shift = random.nextFloat() * 0.15f * waveWidth
                    -1f + i * waveWidth + if (random.nextBoolean()) shift else -shift
                }
            }
        }
        layers = List(WIA_LAYER_COUNT) { li ->
            val waves = List(WIA_WAVES_COUNT) { i ->
                // 相邻拱方向交替（GLWaveLayer: i % 2 == 0 ? UP : DOWN）。
                WiaWave(points[i], points[i + 1], i % 2 == 0)
            }
            WiaLayer(waves, 1f - li.toFloat() / WIA_LAYER_COUNT * 0.8f, WIA_LAYER_AMP[li], WIA_LAYER_ALPHA[li])
        }
    }

    /**
     * @param level 用户电平 0..1
     * @param bands 用户声音频段能量（低频在前）；空数组时只按音量画
     * @param muted 手动静音：彻底平息
     * @param nowMs 帧时间，dt 在内部计算并钳到 64ms，防止后台回来跳帧
     */
    fun tick(level: Float, bands: FloatArray, muted: Boolean, nowMs: Long, reducedMotion: Boolean) {
        if (lastFrameMs == 0L) lastFrameMs = nowMs
        val dt = (nowMs - lastFrameMs).coerceIn(0L, 64L).toFloat()
        lastFrameMs = nowMs

        val target = when {
            muted || level <= 0.02f -> 0f
            // 视觉响度压缩：轻声也能看见，大声不会一下顶死。
            else -> kotlin.math.sqrt(level.coerceIn(0f, 1f))
        }
        amplitude += (target - amplitude) * voiceFollow(dt, rising = target > amplitude, reducedMotion)

        val peak = bandPeak(bands)
        layers.forEachIndexed { li, layer ->
            // 后层取稍高一点的频段，三层不会完全重合，但都来自同一段声音。
            val shift = li * 0.06f
            layer.waves.forEach { wave ->
                val center = ((wave.fromX + wave.toX) * 0.5f + 1f) * 0.5f
                val pos = (center + shift).coerceIn(0f, 1f)
                val shape = bandShape(bands, pos, peak)
                val height = amplitude * (0.25f + 0.75f * shape)
                // 拱顶偏向能量更强的相邻频段：元音、辅音切换时浪尖会左右挪。
                val lean = if (bands.isEmpty()) 0f else
                    (bandShape(bands, pos + 0.08f, peak) - bandShape(bands, pos - 0.08f, peak)).coerceIn(-1f, 1f)
                wave.advance(dt, height, lean, reducedMotion)
            }
        }

        // 粒子出生频率随音量：越响越密，安静时不出。
        if (!muted && target > 0.2f && nowMs >= nextBubbleMs) {
            produceBubbles(bandPeakPosition(bands))
            nextBubbleMs = nowMs + (560f - 420f * target).toLong()
        }

        val bubbleDAngle = (2.0 * PI / WIA_BUBBLE_PERIOD_MS).toFloat() * dt
        bubbles.forEach { b ->
            if (!b.active) return@forEach
            b.angle += bubbleDAngle
            b.centerY += dt * b.virtualSpeed
            if (b.centerY > 1f) b.active = false
        }
    }

    /** 每批 1..2 个，出生在当前最强频段的位置附近；速度随音量。 */
    private fun produceBubbles(peakPos: Float) {
        repeat(1 + random.nextInt(2)) {
            val b = bubbles.firstOrNull { !it.active } ?: return
            b.active = true
            b.startX = ((peakPos * 2f - 1f) + (random.nextFloat() - 0.5f) * 0.5f).coerceIn(-0.95f, 0.95f)
            b.angle = random.nextFloat() * 2f * PI.toFloat()
            b.centerY = -1f
            val coef = 0.6f + amplitude * 0.8f
            b.virtualSpeed = 2f / (WIA_BUBBLE_PERIOD_MS * 2.2f) * coef
            b.sizeDp = WIA_BUBBLE_SIZE_DP * (0.5f + amplitude * 0.8f)
        }
    }
}

/**
 * 把 [WaveInAppSim] 的状态画成底部叠浪。拱用二次贝塞尔（对应原版 Utils.quad 的 40 点采样，
 * 但路径原生曲边更顺滑）；填充用纵向渐变做出全版本可用的"虚化"，不用 RenderEffect.blur。
 */
private fun DrawScope.drawWaveInAppSim(
    sim: WaveInAppSim,
    color: Color,
    particlesEnabled: Boolean,
    isMuted: Boolean,
) {
    val w = size.width
    val h = size.height
    val baseline = h * 0.62f
    val alpha = if (isMuted) 0.5f else 1f

    sim.layers.forEach { layer ->
        val waveH = h * layer.ampFrac
        val path = Path()
        path.moveTo(0f, baseline)
        layer.waves.forEach { wave ->
            val x1 = (wave.toX + 1f) * 0.5f * w
            // Utils.normalizeGl(waveX, fromX, toX)：拱顶横移量映射回本拱的归一化区间。
            val controlNX = wave.fromX + (wave.waveX + 1f) * 0.5f * (wave.toX - wave.fromX)
            val cx = (controlNX + 1f) * 0.5f * w
            val cy = baseline - wave.displacement * waveH
            path.quadraticBezierTo(cx, cy, x1, baseline)
        }
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = layer.fillAlpha * alpha),
                    color.copy(alpha = layer.fillAlpha * 0.35f * alpha),
                    Color.Transparent,
                ),
                startY = baseline - h * 0.4f,
                endY = h,
            ),
        )
    }

    // 最前层的浪尖亮线：给软渐变一个"芯"。
    val front = sim.layers.last()
    val core = Path()
    front.waves.forEachIndexed { i, wave ->
        val x0 = (wave.fromX + 1f) * 0.5f * w
        val x1 = (wave.toX + 1f) * 0.5f * w
        val controlNX = wave.fromX + (wave.waveX + 1f) * 0.5f * (wave.toX - wave.fromX)
        val cx = (controlNX + 1f) * 0.5f * w
        val cy = baseline - wave.displacement * h * front.ampFrac
        if (i == 0) core.moveTo(x0, baseline)
        core.quadraticBezierTo(cx, cy, x1, baseline)
    }
    drawPath(
        path = core,
        color = color.copy(alpha = 0.5f * alpha),
        style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
    )

    // GLBubble 移植：x = startX + 0.05·sin(angle) 的弯曲上浮，alpha 随上升线性衰减。
    if (particlesEnabled && !isMuted) {
        val riseH = h * 0.72f
        sim.bubbles.forEach { b ->
            if (!b.active) return@forEach
            val nx = b.startX + 0.05f * sin(b.angle)
            val x = (nx + 1f) * 0.5f * w
            val progress = (b.centerY + 1f) * 0.5f
            val y = baseline - progress * riseH
            val fade = (1f - progress).coerceIn(0f, 1f)
            drawCircle(
                color = color.copy(alpha = 0.78f * fade),
                radius = b.sizeDp.dp.toPx(),
                center = Offset(x, y),
            )
        }
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
    /** false = 能力还没有，禁用点击并暴露 accessibility disabled 语义。 */
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
            .then(
                if (enabled) Modifier.pointerInput(onClick) {
                    detectTapGestures(onTap = { onClick() })
                } else {
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "$label，暂不可用"
                        disabled()
                    }
                },
            )
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

/**
 * 帧时钟驱动的循环相位（0..2π，每 [periodMs] 一圈）。
 *
 * 不用 rememberInfiniteTransition：它受系统「动画时长缩放」影响，缩放为 0 时整条动画停转。
 * withFrameMillis 只依赖 Choreographer 帧回调，与该设置无关。State 只在 draw 阶段读取，
 * 每帧只重绘、不重组。
 */
@Composable
private fun rememberFramePhase(periodMs: Int): State<Float> {
    val phase = remember { mutableStateOf(0f) }
    LaunchedEffect(periodMs) {
        var startMs = -1L
        while (true) {
            withFrameMillis { now ->
                if (startMs < 0L) startMs = now
                val t = ((now - startMs) % periodMs).toFloat() / periodMs
                phase.value = t * (2f * PI).toFloat()
            }
        }
    }
    return phase
}

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
