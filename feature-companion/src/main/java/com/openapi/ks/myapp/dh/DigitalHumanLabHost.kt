package com.openapi.ks.myapp.dh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.openapi.feature.companion.fx.LottieFxLayer
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.AvatarTouch
import com.openapi.ks.myapp.avatar.AvatarTouchKind
import com.openapi.ks.myapp.avatar.LayeredAvatar
import com.openapi.ks.myapp.avatar.SimpleAvatar
import com.openapi.ks.myapp.avatar.TouchResult
import kotlinx.coroutines.launch
import java.io.File

/**
 * 引擎视口：只画当前引擎的画面。
 *
 * 切换不在这里——chips 叠在通话控件上点不到，也把原来的形象盖没了。
 * 选哪个引擎由通话页右上角下拉决定，调用方传入 [engineId]。
 */
@Composable
fun DigitalHumanLabHost(
    spec: AvatarSpec?,
    mouthLevel: kotlinx.coroutines.flow.StateFlow<Float>?,
    modifier: Modifier = Modifier,
    remotePcm: kotlinx.coroutines.flow.Flow<ByteArray>? = null,
    engineId: DhEngineId = DhEngineId.SIMPLE,
    /**
     * 点了她一下：上报归一化区域（"head"/"body"，与分层立绘同一语义），由 agent 决定
     * 回不回。本地脉冲反馈不等这个回调——点了没反应是体验底线。null = 不监听（聊天页）。
     */
    onTapped: ((String) -> Unit)? = null,
    /**
     * 长按她：呼出动作轮盘。null = 不监听（聊天页与调试舞台）。
     * 长按不做脉冲——长按的即时反馈就是轮盘本身弹出来。
     */
    onLongPressed: (() -> Unit)? = null,
    /**
     * dh-lab 测试语音 rig 的**生产注入路径**：把 PCM 块送进远端音频轨回调共用的
     * 下游入口（[com.openapi.ks.myapp.companion.CompanionTransport.injectLabAudioPcm16k]
     * 的实现方，通话页由 CompanionRoute 接 `chatViewModel::injectLabAudioPcm16k`）。
     * 返回 false = 这条连接没有可注入通路（Fake 替身），rig 降级回 bridge 直喂。
     * null（聊天页/调试舞台）= 直接用 bridge 直喂。
     */
    audioInjector: ((ByteArray) -> Boolean)? = null,
    /**
     * 通话页主题。GL 系引擎（Live2D）把背景色推进 native 当每帧清屏色——页面渐变
     * 画在 Compose 层，GLSurfaceView 把它整个盖住，不透进去就是大黑底。null =
     * 无主题（聊天页/调试舞台），引擎保持各自默认背景。
     */
    theme: com.openapi.ks.myapp.companion.CallTheme? = null,
    /**
     * Live2D 多形象（用户 2026-11-23「demo里有好几个形象，是否可以都集成建立可切换」）：
     * cacheRoot(LIVE2D) 下的模型目录名（白名单见 [Live2dModels]）。变化时 Live2D 引擎
     * 原地重建（release 旧的再 create，禁两个 native 叠加）。非 Live2D 引擎忽略。
     */
    live2dModel: String = "ATRI",
    /**
     * 当前引擎实例的快照（换形象/换装托盘直接调 wardrobeRows()/advanceWardrobe()/
     * modelLabel()）。引擎销毁时回 null。null 参数 = 调用方不需要（聊天页/调试舞台）。
     */
    onEngineChanged: ((DigitalHumanEngine?) -> Unit)? = null,
    /**
     * Simple 舞台的形象回退（真机投诉 2026-11-23「simple 没有形象，只有一个字」）：
     * 角色没有旧格式形象包（avatarFiles==null）却有分层立绘时，把分层立绘喂进
     * Simple 舞台，而不是退回首字占位圆。分层立绘自绘互动波纹（[LayeredAvatar]），
     * 捏合缩放与长按托盘仍由舞台判定器管（onTap 传 null = 单指事件落给分层立绘）。
     */
    simpleFallback: com.openapi.ks.myapp.avatar.LayeredAvatarPack? = null,
    /**
     * 舞台特效：订阅既有 [CompanionUiState.gesture]/[CompanionUiState.gestureSeq]
     * （cmp.cue → AvatarEventMapper → Gesture 的 UI 镜像），**不另起事件总线**。
     * null / 0 = 不播（聊天页/调试舞台）。
     */
    gestureId: String? = null,
    gestureSeq: Long = 0L,
    /** 通话页飘散粒子开关；关 → 氛围类特效不播（[FxRenderPolicy]）。 */
    particles: Boolean = true,
    /** dh-lab 门内「特效测试」按钮；null = 不显示。Route 接到 emitStageGesture("pat_head"). */
    onLabFxTest: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    DhLabBootstrap.ensureDefaults()

    // 进程级形象熔断：native 引擎崩在 SIGSEGV 里 Java 层拦不住，冷启动发现上次死在
    // 形象页就隔离该引擎，本次直接降级 SIMPLE（最基础的无形象页面），不再尝试加载。
    val watchdog = remember { DhEngineWatchdog(context).also { it.onColdStart() } }
    val effectiveEngineId = if (watchdog.isQuarantined(engineId)) DhEngineId.SIMPLE else engineId

    var swapping by remember { mutableStateOf(false) }

    // 不 key live2dModel：bridge 不 key initial（见下），多造的那个实例没人接管会直接泄漏。
    // 换形象由下面的 LaunchedEffect(live2dModel) 走 replaceWith，与切引擎同一条路径。
    val initial = remember(effectiveEngineId) {
        val row = DhEngineRegistry.available(context).firstOrNull { it.id == effectiveEngineId }
        if (row?.available == true) DhEngineRegistry.create(context, effectiveEngineId)
        else DhEngineRegistry.create(context, DhEngineId.SIMPLE)
    }
    val bridge = remember { DigitalHumanBridge(initial) }
    var engine by remember { mutableStateOf(bridge.current()) }
    val rigScope = rememberCoroutineScope()

    // 双指缩放/拖动：Live2D 人物太小看不到唇形，所有引擎统一支持 1..4x 捏合放大，
    // 放大后单指拖动，1x 时单指事件原样透传给引擎（点击触发形象动画、Live2D 视线）。
    val stage = rememberAvatarStageTransform()
    LaunchedEffect(engine) { stage.reset() }

    // 点她一下的本地脉冲：按下缩 2% 抬手弹回。减弱动态时缩放归零，只剩震动。
    val reducedMotion = remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
            ) == 0f
        }.getOrDefault(false)
    }
    val pulseSpec = remember(reducedMotion) { stageTapPulse(reducedMotion) }
    var tapPressed by remember { mutableStateOf(false) }
    val tapScale by animateFloatAsState(
        targetValue = if (tapPressed) 1f - pulseSpec.scaleDelta else 1f,
        animationSpec = tween(if (tapPressed) pulseSpec.pressDownMs else pulseSpec.returnMs),
        label = "tapPulse",
    )
    LaunchedEffect(tapPressed) {
        if (tapPressed) {
            kotlinx.coroutines.delay(pulseSpec.pressDownMs.toLong())
            tapPressed = false
        }
    }
    val haptics = LocalHapticFeedback.current
    val tappedNow by androidx.compose.runtime.rememberUpdatedState(onTapped)

    // 激活即上膛：正常销毁走 markClean；native crash 让标记残留 → 下次冷启动熔断。
    // release 仍只归 DisposableEffect(Unit)/replaceWith 管——切换时 bridge.current()
    // 已经是新引擎，这里绝不能碰它。
    DisposableEffect(Unit) {
        onDispose { bridge.current().release() }
    }
    DisposableEffect(engine) {
        val live = bridge.current().id
        watchdog.markActive(live)
        onDispose {
            if (bridge.current().id == live) watchdog.markClean()
        }
    }

    // 测试语音 rig（debug/lab 包）：
    //   adb shell am broadcast -a com.openapi.ks.chat.action.DH_TEST_AUDIO --es clip long --ei motion 0
    // 喂 PCM/幅度给**当前**引擎；未推素材或 clip 不存在时回 0，画面不受影响。
    DisposableEffect(context) {
        val debuggable = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable) return@DisposableEffect onDispose { }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val clip = intent.getStringExtra(DhTestAudio.EXTRA_CLIP) ?: "sentence"
                val motion = intent.getIntExtra(DhTestAudio.EXTRA_MOTION, -1).takeIf { it >= 0 }
                val file = File(DhTestAudio.clipsDir(context.filesDir), "$clip.wav")
                if (!file.isFile) {
                    Log.i("DhTestAudio", "missing clip ${file.name}（先跑 seed-assets.sh audio）")
                    return
                }
                rigScope.launch {
                    // 有 VM 注入通道时优先走它：PCM 从远端音频轨回调共用的下游入口
                    // 进 transport（电平提取 → mouthLevel → bridge → setMouth），
                    // 与真通话同路。返回 false（Fake 替身/无通路）时 rig 内部降级
                    // 回 bridge 直喂——聊天页没有 VM 注入通道也是这条路。
                    val n = DhTestAudio.play(file, bridge, motion, audioInjector)
                    Log.i("DhTestAudio", "played ${clip}: ${n} bytes -> ${bridge.current().id}")
                }
            }
        }
        val filter = IntentFilter(DhTestAudio.ACTION_PLAY)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // 下拉换了引擎：先 release 再 create，禁止两个 native 引擎叠在一起。
    // 首帧不在这里切（初始引擎已由 effectiveEngineId 决定）——尤其被熔断的持久偏好
    // 绝不能自动重试，否则每次冷启动都崩一次。只有用户随后**主动**选中被熔断的引擎
    // 才解除隔离、再试一次（再崩再熔）。
    // 只跳过「首次组合」；之后用户选回最初那个 id（例如：持久偏好是 Duix 但被熔断降级成
    // SIMPLE，用户先点 Live2D 再点回 Duix）必须照常切换。以前拿 initialEngineId 比较，
    // 选回初始 id 会被当成「没变」吞掉，菜单打勾 Duix、屏幕却还是 Live2D。
    var lastRequestedEngineId by remember { mutableStateOf(engineId) }
    LaunchedEffect(engineId) {
        if (engineId == lastRequestedEngineId) return@LaunchedEffect
        val previous = lastRequestedEngineId
        lastRequestedEngineId = engineId
        watchdog.clear(engineId)
        val target = DhEngineSwapPolicy.target(engineId, previous, watchdog::isQuarantined)
            ?: return@LaunchedEffect
        val row = DhEngineRegistry.available(context).firstOrNull { it.id == target }
        if (row?.available != true) return@LaunchedEffect
        swapping = true
        val next = bridge.replaceWith { createLabEngine(context, target, live2dModel) }
        spec?.let { next.bind(it) }
        engine = next
        swapping = false
    }

    LaunchedEffect(spec) {
        spec?.let { bridge.current().bind(it) }
    }

    // 换形象（托盘「换形象」行 → 偏好 → 参数）：Live2D 引擎实例里烧着模型目录名，
    // 换模型 = 换实例。先 release 旧的再 create（禁两个 native 叠加，与切引擎同一条铁律）。
    // 首跳也要跑：注册表造的实例永远是默认 ATRI，持久偏好存别的形象时要换过去。
    // 拿本 effect 捕获的那个引擎实例去比（切引擎后引擎 effect 与这个 effect 同帧重启、
    // 引擎 effect 先跑——此时 last 已同步为新实例的模型；stale 校验兜并发切换）。
    // 候选先验 availability：模型目录没推上设备就不换，画面保持旧形象而不是黑屏。
    LaunchedEffect(live2dModel, engine) {
        val current = bridge.current()
        if (current.id != DhEngineId.LIVE2D) return@LaunchedEffect
        val currentModel = (current as? Live2dDigitalHumanEngine)?.modelLabel() ?: return@LaunchedEffect
        if (live2dModel == currentModel) return@LaunchedEffect
        val candidate = Live2dDigitalHumanEngine(modelDirName = live2dModel)
        if (!candidate.availability(context).available) {
            candidate.release()
            return@LaunchedEffect
        }
        if (bridge.current() !== current) {
            // 探测期间引擎被切走（例如切到 Duix）：这个候选不能顶替别的引擎。
            candidate.release()
            return@LaunchedEffect
        }
        swapping = true
        val next = bridge.replaceWith { candidate }
        spec?.let { next.bind(it) }
        engine = next
        swapping = false
    }

    // 当前引擎实例发布给调用方（收纳盒换装 / advanceWardrobe）。换形象时 engine 引用
    // 会变，但 dispose 里不能推 null——真机「换装全无」就是这一帧 stageEngine 被清空。
    DisposableEffect(Unit) {
        onDispose { onEngineChanged?.invoke(null) }
    }
    SideEffect {
        onEngineChanged?.invoke(bridge.current())
    }
    LaunchedEffect(mouthLevel, engine) {
        val flow = mouthLevel ?: return@LaunchedEffect
        // 吃 PCM 的引擎（Duix）有真 PCM 流时，不再用音量合成假 PCM。
        flow.collect { if (!(remotePcm != null && bridge.current().pcmDriven)) bridge.onMouthLevel(it) }
    }
    LaunchedEffect(remotePcm, engine) {
        val flow = remotePcm ?: return@LaunchedEffect
        flow.collect { if (bridge.current().pcmDriven) bridge.onRemotePcm(it) }
    }

    // 主题 → 舞台背景：折成一色推给认识的引擎（Live2D 进 native 清屏色，其余 no-op）。
    // 引擎换实例（下拉切引擎）后旧的去重记录作废——新实例还没收到过主题色，必须重推。
    var lastStageArgb by remember { mutableStateOf<Pair<DigitalHumanEngine, Int>?>(null) }
    LaunchedEffect(theme, engine) {
        val argb = theme?.let { stageClearColorArgb(it.topArgb, it.midArgb, it.bottomArgb) }
        val last = lastStageArgb?.takeIf { it.first === engine }?.second
        val push = stageBackgroundPush(argb, last) ?: return@LaunchedEffect
        engine.setBackground(push)
        lastStageArgb = engine to push
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val e = engine) {
            is SimpleDigitalHumanEngine -> {
                if (simpleFallback != null && spec == null) {
                    // 真机投诉 2026-11-23「simple 没有形象，只有一个字」：这个角色的
                    // 形象是分层立绘、没有旧格式形象包，Simple 舞台退回首字占位圆。
                    // 回退成分层立绘——它自带部位触点波纹与嘴型驱动，单指事件不被舞台
                    // 截走（onTap=null），捏合/长按仍归舞台判定器。
                    var mouthNow by remember { mutableFloatStateOf(0f) }
                    LaunchedEffect(mouthLevel) { mouthLevel?.collect { mouthNow = it } }
                    LayeredAvatar(
                        pack = simpleFallback,
                        modifier = Modifier
                            .fillMaxSize()
                            .avatarStageTransform(
                                stage,
                                onTap = null,
                                onLongPress = onLongPressed?.let { { it() } },
                            )
                            .avatarStageLayer(stage),
                        mouthLevel = { mouthNow },
                        onTapped = { region -> tappedNow?.invoke(region) },
                    )
                } else {
                    SimpleAvatar(
                        renderer = e.renderer,
                        modifier = Modifier
                            .fillMaxSize()
                            .avatarStageTransform(
                                stage,
                                // Simple 的触点判定在渲染器里（renderer.onTouch 认部位），
                                // 这里只把归一化坐标转成 TAP 喂进去；Handled 才上报 agent。
                                onTap = { off ->
                                    val result = bridge.onAvatarTouch(
                                        AvatarTouch(off.x, off.y, AvatarTouchKind.TAP),
                                    )
                                    if (result is TouchResult.Handled) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        tapPressed = true
                                        tappedNow?.invoke(result.region ?: stageTapRegion(off.x, off.y))
                                    }
                                },
                                onLongPress = onLongPressed?.let { { it() } },
                            )
                            .avatarStageLayer(stage)
                            .graphicsLayer { scaleX = tapScale; scaleY = tapScale },
                        mouthLevel = mouthLevel,
                    )
                }
            }
            // Duix / Live2D / Tao：Android View 视口
            // key(e)：AndroidView 的 factory 只在首次组合时执行。Live2D→Duix 这类
            // View 引擎之间切换若不换 key，屏幕上还是上一个（已 release、冻结）的 View，
            // 新引擎在后台收 PCM 却没有画面——表现为「唇形一直闭口」。
            else -> key(e) {
                val view = remember(e) { e.createView(context) }
                if (view != null) {
                    // 变换直接写 View 自身的 scale/translation——Compose 的 graphicsLayer
                    // 到不了 AndroidView 里的 View。update 里读 stage 的快照状态，state
                    // 一变就重执行。手势（捏合/拖图/轻触/长按）全在 avatarStageTransform
                    // 这一个判定器里——以前另有一层 Main pass 的 tap/长按覆盖 Box，与
                    // 托盘薄纱一起把第二根手指吃掉，捏合缩放必死（2026-11-23 投诉）。
                    // VRM 例外：onTap 传 null = 静止轻触原样透传给 WebView（页面做
                    // 分区触点反应）；单指横拖改由这层的 onOrbit 档吃掉（用户 2026-11-23
                    // 要「支持旋转」），页面侧的拖动视线跟随同步退役，不再两边同吃。
                    AndroidView(
                        factory = { view },
                        modifier = Modifier
                            .fillMaxSize()
                            .avatarStageTransform(
                                stage,
                                onTap = if (e.id == DhEngineId.VRM) null else { off ->
                                    val result = bridge.onAvatarTouch(
                                        AvatarTouch(off.x, off.y, AvatarTouchKind.TAP),
                                    )
                                    if (result is TouchResult.Handled) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        // Live2D 双击被误认成「缩放脉冲」→ 关 View 引擎点按脉冲
                                        if (e.id != DhEngineId.LIVE2D) tapPressed = true
                                        tappedNow?.invoke(result.region ?: stageTapRegion(off.x, off.y))
                                    }
                                },
                                onLongPress = onLongPressed?.let { { it() } },
                                onOrbit = (e as? VrmDigitalHumanEngine)?.let { vrmEngine ->
                                    { deltaDeg -> vrmEngine.onStageOrbit(deltaDeg) }
                                },
                            ),
                        update = { v ->
                            // 触摸脉冲乘在引擎 View 自身的 scale 上（与双指缩放同源，
                            // Compose 图层到不了这个 View，见 AvatarStageTransform 注释）。
                            val pulse = if (e.id == DhEngineId.LIVE2D) 1f else tapScale
                            v.scaleX = stage.scale * pulse
                            v.scaleY = stage.scale * pulse
                            v.translationX = stage.offsetX
                            v.translationY = stage.offsetY
                            theme?.let {
                                val argb = stageClearColorArgb(it.topArgb, it.midArgb, it.bottomArgb)
                                v.setBackgroundColor(argb)
                            }
                        },
                        onRelease = { /* release 由 bridge.replaceWith / DisposableEffect 负责 */ },
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0B0D14)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when (e.id) {
                                DhEngineId.DUIX -> "Duix 无画面（缺模型或未编入）"
                                DhEngineId.LIVE2D -> "Live2D 无画面（未编入）"
                                DhEngineId.TAO_AVATAR -> "Tao 无画面（机型/未编入）"
                                DhEngineId.VRM -> "VRM 无画面（未下载模型）"
                                else -> "引擎无画面"
                            },
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }

        // ==== Lottie 特效层（MVP）=============================================
        // 叠在引擎视口之后的 Box 兄弟——与复位胶囊/托盘同一条已验证合成路径：
        // 不 setZOrderOnTop、不进 GL、**不加任何 pointerInput**（手势仲裁只有
        // avatarStageTransform 一处，2026-11-23「捏合必死」教训）。
        // 触发走既有 gestureSeq（cmp.cue → AvatarEventMapper → Gesture），不另起总线。
        LottieFxLayer(
            gestureId = gestureId,
            gestureSeq = gestureSeq,
            reducedMotion = reducedMotion,
            particles = particles,
            stageZoomed = stage.isZoomed,
            modifier = Modifier.fillMaxSize(),
        )
        // dh-lab 调试：点一下 = 走同一条 Gesture 词表（pat_head），验证合成/触摸回归。
        if (onLabFxTest != null && DhLabGate.isEnabled(context)) {
            Text(
                text = "特效测试",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 96.dp, end = 16.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable { onLabFxTest.invoke() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        // ======================================================================

        // 放大后的「复位」小胶囊：捏合跑偏或想看全貌时一键回 1x 居中。
        if (stage.isZoomed) {
            Text(
                text = "复位",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 150.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable { stage.reset() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        if (swapping) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // 请求了 native 引擎却落在基础形象：说明原因，别只剩一个「形象准备中」。
        val notice = DhDegradeNotice.text(
            requested = lastRequestedEngineId,
            actual = engine.id,
            quarantined = watchdog.isQuarantined(lastRequestedEngineId),
        )
        if (notice != null && !swapping) {
            Text(
                text = notice,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 32.dp, end = 32.dp, bottom = 190.dp),
            )
        }
    }
}

/**
 * 切引擎时的统一构造口：Live2D 走带模型的构造器（注册表工厂永远是默认 ATRI），
 * 其余引擎走注册表。偏好里的形象名先过 [Live2dModels.sanitize] 白名单。
 */
private fun createLabEngine(context: android.content.Context, id: DhEngineId, live2dModel: String): DigitalHumanEngine =
    if (id == DhEngineId.LIVE2D) {
        Live2dDigitalHumanEngine(modelDirName = Live2dModels.sanitize(live2dModel))
    } else {
        DhEngineRegistry.create(context, id)
    }
