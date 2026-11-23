package com.openapi.feature.companion.fx

import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 特效渲染层：引擎视口之后的 Box 兄弟（合成路径与「复位胶囊盖在 Live2D 画面」
 * 同一条已验证路径——不 setZOrderOnTop、不进 GL）。
 *
 * **不是新事件总线的消费者**——订阅的是既有 CompanionUiState.gesture /
 * CompanionUiState.gestureSeq（cmp.cue → AvatarEventMapper → Gesture 的 UI 镜像）。
 * 每次 gestureSeq 递增，本层用 StageEffectMapper.fromGesture 翻成 StageEffect 再播。
 *
 * 架构红线（验收 grep）：本文件只 import fx 包自己的类型、lottie、Compose。
 * 没有 DhEngineId、没有任何引擎符号；舞台状态以布尔快照（stageZoomed）进来。
 *
 * **绝不加 pointerInput/clickable**：手势仲裁只有 AvatarStageTransform 一处
 * （2026-11-23「捏合必死」教训），本层天然触摸穿透。
 *
 * 预缓存：进入组合即在 IO 把白名单 fromAssetSync 一遍（LottieCompositionFactory
 * 进程级缓存），触发时零解析。未命中 → 丢弃 + 日志（只有 assetId），fail-silent。
 */
@Composable
fun LottieFxLayer(
    /** 既有 UI state 的 gesture id；null / 空 = 无待播。 */
    gestureId: String?,
    /** 既有 UI state 的 gestureSeq；递增触发一次（同 id 连发也能各播一次）。 */
    gestureSeq: Long,
    reducedMotion: Boolean,
    particles: Boolean,
    stageZoomed: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preloaded = remember { mutableStateMapOf<String, LottieComposition?>() }
    // 去重闸：按 assetId 记上次放行时刻。不是总线，是既有 gesture 流上的合并窗口。
    val lastSeen = remember { HashMap<String, Long>() }

    LaunchedEffect(Unit) {
        FxAssets.all.forEach { spec ->
            val composition = withContext(Dispatchers.IO) {
                runCatching {
                    LottieCompositionFactory
                        .fromAssetSync(context, FxAssets.assetPath(spec.assetId))
                }.getOrNull()?.value
            }
            if (composition == null) Log.i(TAG, "prewarm failed: ${spec.assetId}")
            preloaded[spec.assetId] = composition
        }
    }

    // 同一时刻只渲染最近一条（叠帽约束，方案 §2.4.2）。
    // tick 随 gestureSeq 走：同 assetId 再触发时 key 变 → 动画重开。
    var active by remember { mutableStateOf<Pair<StageEffect.Play, Long>?>(null) }
    LaunchedEffect(gestureSeq) {
        if (gestureSeq == 0L) return@LaunchedEffect
        val effect = StageEffectMapper.fromGesture(gestureId) as? StageEffect.Play
            ?: return@LaunchedEffect
        val now = System.nanoTime()
        if (dedupe(lastSeen[effect.assetId], now) == DedupeAction.DROP) {
            Log.d(TAG, "dedupe ${effect.assetId} (<${FX_DEDUPE_WINDOW_MS}ms)")
            return@LaunchedEffect
        }
        lastSeen[effect.assetId] = now
        active = effect to gestureSeq
    }

    val current = active ?: return
    val effect = current.first
    val tick = current.second
    val decision = FxRenderPolicy.decide(effect, reducedMotion, particles, stageZoomed)
    if (decision.mode == FxRenderPolicy.Mode.SKIP) {
        LaunchedEffect(effect, tick) { active = null }
        return
    }
    val composition = preloaded[effect.assetId]
    if (composition == null) {
        LaunchedEffect(effect, tick) {
            Log.i(TAG, "no cached composition for ${effect.assetId}, drop")
            active = null
        }
        return
    }

    // key(tick)：同资产再触发时整段重组合，animate 从头播。
    key(tick) {
        if (decision.mode == FxRenderPolicy.Mode.STATIC_FRAME) {
            RenderFx(effect, composition, modifier) { STATIC_FRAME_PROGRESS }
        } else {
            val animated by animateLottieCompositionAsState(
                composition = composition,
                iterations = if (effect.repeat == StageEffect.LOOP_UNTIL_CANCELLED) {
                    Int.MAX_VALUE
                } else {
                    effect.repeat.coerceAtLeast(1)
                },
            )
            RenderFx(effect, composition, modifier) { animated }
        }
    }
}

@Composable
private fun RenderFx(
    effect: StageEffect.Play,
    composition: LottieComposition,
    modifier: Modifier,
    progressProvider: () -> Float,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val anchor = effect.anchor
        val width = maxWidth * FxAnchors.widthFraction(anchor)
        val height = if (anchor == StageEffect.Anchor.FULL) maxHeight else width
        val point = FxAnchors.point(anchor)
        val left = (maxWidth * point.x) - width / 2f
        val top = (maxHeight * point.y) - height / 2f

        LottieAnimation(
            composition = composition,
            progress = progressProvider,
            modifier = Modifier
                .size(width = width, height = height)
                .offset(x = left, y = top),
        )
    }
}

private const val STATIC_FRAME_PROGRESS = 0.5f
private const val TAG = "LottieFxLayer"
