package com.openapi.ks.myapp.dh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    engineId: DhEngineId = DhEngineId.SIMPLE,
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
    val initialEngineId = remember { engineId }
    LaunchedEffect(engineId) {
        if (engineId == initialEngineId) return@LaunchedEffect
        watchdog.clear(engineId)
        val target = if (watchdog.isQuarantined(engineId)) DhEngineId.SIMPLE else engineId
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
        flow.collect { bridge.onMouthLevel(it) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val e = engine) {
            is SimpleDigitalHumanEngine -> {
                SimpleAvatar(
                    renderer = e.renderer,
                    modifier = Modifier.fillMaxSize(),
                    mouthLevel = mouthLevel,
                )
            }
            // Duix / Live2D / Tao：Android View 视口
            else -> {
                val view = remember(e) { e.createView(context) }
                if (view != null) {
                    AndroidView(
                        factory = { view },
                        modifier = Modifier.fillMaxSize(),
                        update = { /* engine owns the View */ },
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

        if (swapping) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
