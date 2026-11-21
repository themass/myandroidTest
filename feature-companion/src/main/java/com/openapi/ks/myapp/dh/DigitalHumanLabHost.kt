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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.SimpleAvatar
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
) {
    val context = LocalContext.current
    DhLabBootstrap.ensureDefaults()

    // 进程级形象熔断：native 引擎崩在 SIGSEGV 里 Java 层拦不住，冷启动发现上次死在
    // 形象页就隔离该引擎，本次直接降级 SIMPLE（最基础的无形象页面），不再尝试加载。
    val watchdog = remember { DhEngineWatchdog(context).also { it.onColdStart() } }
    val effectiveEngineId = if (watchdog.isQuarantined(engineId)) DhEngineId.SIMPLE else engineId

    var swapping by remember { mutableStateOf(false) }

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
    val longPressedNow by androidx.compose.runtime.rememberUpdatedState(onLongPressed)

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
                    val n = DhTestAudio.play(file, bridge, motion)
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
        val next = bridge.replaceWith { DhEngineRegistry.create(context, target) }
        spec?.let { next.bind(it) }
        engine = next
        swapping = false
    }

    LaunchedEffect(spec) {
        spec?.let { bridge.current().bind(it) }
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

    Box(modifier = modifier.fillMaxSize()) {
        when (val e = engine) {
            is SimpleDigitalHumanEngine -> {
                SimpleAvatar(
                    renderer = e.renderer,
                    modifier = Modifier
                        .fillMaxSize()
                        .avatarStageTransform(stage)
                        .avatarStageLayer(stage)
                        .graphicsLayer { scaleX = tapScale; scaleY = tapScale },
                    mouthLevel = mouthLevel,
                )
            }
            // Duix / Live2D / Tao：Android View 视口
            // key(e)：AndroidView 的 factory 只在首次组合时执行。Live2D→Duix 这类
            // View 引擎之间切换若不换 key，屏幕上还是上一个（已 release、冻结）的 View，
            // 新引擎在后台收 PCM 却没有画面——表现为「唇形一直闭口」。
            else -> key(e) {
                val view = remember(e) { e.createView(context) }
                if (view != null) {
                    // 手势层盖在引擎视口上；变换直接写 View 自身的 scale/translation——
                    // Compose 的 graphicsLayer 到不了 AndroidView 里的 View。update 里
                    // 读 stage 的快照状态，state 一变就重执行。
                    AndroidView(
                        factory = { view },
                        modifier = Modifier
                            .fillMaxSize()
                            .avatarStageTransform(stage),
                        update = { v ->
                            // 触摸脉冲乘在引擎 View 自身的 scale 上（与双指缩放同源，
                            // Compose 图层到不了这个 View，见 AvatarStageTransform 注释）。
                            v.scaleX = stage.scale * tapScale
                            v.scaleY = stage.scale * tapScale
                            v.translationX = stage.offsetX
                            v.translationY = stage.offsetY
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
                                else -> "引擎无画面"
                            },
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }

        // 点她一下（GL/TextureView 引擎）：引擎 View 自己不回单点触摸，这里补上。
        // 事件顺序：缩放/拖动层在 Initial pass 先看手指（双指、放大拖拽被它吃掉），
        // 剩下的单点轻触由这层在 Main pass 认成 tap——捏合时多点按下不会误判成点击。
        // 长按同一层认领：抬手前被认成长按的就不是轻触，tap 与长按天然互斥。
        if (engine.id != DhEngineId.SIMPLE) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(engine) {
                        detectTapGestures(
                            onTap = { offset ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                tapPressed = true
                                tappedNow?.invoke(
                                    stageTapRegion(offset.x / size.width, offset.y / size.height),
                                )
                            },
                            onLongPress = { longPressedNow?.invoke() },
                        )
                    },
            )
        }

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
