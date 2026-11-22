package com.openapi.ks.myapp.dh

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot

/** 捏合诊断（用户报告「双手放大缩小不见了」2026-11-22）：adb 注入多指不成 gesture，
 *  只有真手指能定性——这个 tag 每次手势收尾一行，`adb logcat -s DhStage` 即判。 */
private const val STAGE_LOG_TAG = "DhStage"

/** 放大上限：4x 够看清唇形，再多 TextureView 就开始糊。 */
private const val MAX_SCALE = 4f

fun clampStageScale(scale: Float): Float = scale.coerceIn(1f, MAX_SCALE)

/**
 * 平移边界：放大后的内容比视口宽出 `(scale-1)*viewport`，围绕中心最多各挪一半，
 * 再多就会把人物推出屏幕露出背景。1x 时上界是 0 —— 不放大就不可拖。
 */
fun clampStageOffset(offset: Float, scale: Float, viewportSize: Float): Float {
    val max = (scale - 1f) * viewportSize / 2f
    return offset.coerceIn(-max, max)
}

/**
 * 一步手势的纯函数：以捏合中心 [centroid]（视口坐标）为锚点缩放 [zoom] 并平移 [pan]，
 * 返回新的 (scale, offset)。变换以视口中心为原点（View scale 的默认 pivot /
 * graphicsLayer 的默认 origin），锚点下的内容点在缩放前后保持不动，捏合才「跟手」。
 */
fun stageStep(
    scale: Float,
    offset: Float,
    centroid: Float,
    pan: Float,
    zoom: Float,
    viewportSize: Float,
): Pair<Float, Float> {
    val next = clampStageScale(scale * zoom)
    val c = centroid - viewportSize / 2f
    val ratio = next / scale
    val newOffset = clampStageOffset(c - (c - offset) * ratio + pan, next, viewportSize)
    return next to newOffset
}

/**
 * 抬手时这一笔是不是轻触（纯决策，AvatarStageGesturesTest 钉死）。
 * 两指出现过 → 用户在做捏合，永不算点击；拖过 slop → 是拖图不是点她；
 * 长按已触发（托盘弹出）→ 抬手不再补一发 tap。
 */
fun stageTapDecision(twoFingerSeen: Boolean, slopBroken: Boolean, heldLong: Boolean): Boolean =
    !twoFingerSeen && !slopBroken && !heldLong

/**
 * 长按（呼出托盘）成不成立的判据：一根安静的手指按过了时限。
 * 两指出现过就不算——这正是「live2d 无法放大，我觉得是跟弹窗冲突了」（2026-11-23）
 * 的根治点：旧结构里单指 500ms 先弹托盘、托盘薄纱吃掉随后落下的第二根手指，
 * 缩放必死。现在长按与捏合在同一个判定器里：第二指一落地长按计时当场作废，
 * 事件留在舞台上直接进缩放。
 */
fun stageLongPressDecision(twoFingerSeen: Boolean, slopBroken: Boolean, heldLong: Boolean): Boolean =
    !twoFingerSeen && !slopBroken && heldLong

/**
 * 一次手势结束时的诊断行。四个问题一行答完：手指到没到（`fingers`）→
 * 认成两指了吗（`twoFinger`）→ 状态机真的动了吗（`moved`）→
 * 这一笔最终认成了什么（`tap`/`long`）。false/false = 事件根本没到这层；
 * fingers=2 而 moved=false = 步进没生效；true/true 而屏幕不变 = 变换没写到 View。
 */
fun stageGestureLog(
    maxFingers: Int,
    twoFingerSeen: Boolean,
    startScale: Float,
    endScale: Float,
    startOffset: Float,
    endOffset: Float,
    tap: Boolean,
    longPress: Boolean,
): String =
    "gesture fingers=$maxFingers twoFinger=$twoFingerSeen " +
        "scale ${"%.2f".format(startScale)}->${"%.2f".format(endScale)} " +
        "moved=${startScale != endScale || startOffset != endOffset} " +
        "tap=$tap long=$longPress"

class AvatarStageTransformState internal constructor() {
    var scale by mutableFloatStateOf(1f)
        internal set
    var offsetX by mutableFloatStateOf(0f)
        internal set
    var offsetY by mutableFloatStateOf(0f)
        internal set

    val isZoomed: Boolean get() = scale > 1.01f

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }
}

@Composable
fun rememberAvatarStageTransform(): AvatarStageTransformState = remember { AvatarStageTransformState() }

/**
 * 形象舞台的**唯一手势判定器**（所有引擎共用），挂在引擎视口的 Modifier 链上。
 *
 * 真机投诉 2026-11-23「live2d 无法放大，我觉得是跟弹窗冲突了」逼出的重构：
 * 以前捏合层（Initial pass）、点按/长按层（Main pass 覆盖 Box）、托盘薄纱是三个
 * 互不知晓的 pointerInput——单指按住 500ms 弹托盘，第二指落进薄纱里被吃掉，
 * 缩放与弹窗从结构上必然打架。现在捏合/拖动/轻触/长按在同一个 awaitEachGesture
 * 里仲裁（规则是 stageTapDecision / stageLongPressDecision 两个纯函数）：
 * - 两指一旦出现：这一笔归缩放，抬手时 tap/长按全否决；
 * - 拖过 slop：tap/长按作废，放大状态下单指拖动平移；
 * - 一切在**抬手时刻**判定：按住超过 longPressTimeoutMillis 才叫长按，否则叫轻触。
 *
 * [onTap] 收到**视口归一化坐标**（0..1，与旧覆盖 Box 的 offset/size 同源）。
 * 传 null = 这层不管单笔事件（原样落给引擎 View——VRM 的 WebView 要自己吃触摸
 * 做视线旋转），双指缩放仍然生效。[onTap] 非 null 时单指事件也被这层消费：
 * GL 引擎的视线本来就由 setGaze 合成驱动，不给它看原始触摸（与旧覆盖 Box 持平）。
 *
 * 变换不走 graphicsLayer 到引擎 View——AndroidView 里的引擎 View 由 View 系统绘制，
 * Compose 图层变换到不了它；由宿主在 AndroidView update 里把 state 写到 View 自身的
 * scale/translation（TextureView 缩放干净；GLSurfaceView 的图层变换 API 29+ 由
 * SurfaceFlinger 处理，最低支持机型是 Mate 60/API 31）。Compose 形象（Simple）
 * 用 [avatarStageLayer] 直接吃 graphicsLayer。
 */
@Composable
fun Modifier.avatarStageTransform(
    state: AvatarStageTransformState,
    onTap: ((Offset) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
): Modifier {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val zoomedNow = rememberUpdatedState(state.isZoomed)
    // 回调过 rememberUpdatedState，pointerInput 只 key state：tapPulse 每帧换 lambda
    // 身份，若把它当 key 会在手指还按在屏上时重启整个手势。
    val onTapNow = rememberUpdatedState(onTap)
    val onLongPressNow = rememberUpdatedState(onLongPress)
    return this
        .onSizeChanged { viewport = it }
        .pointerInput(state) {
            val slop = viewConfiguration.touchSlop
            val longPressMs = viewConfiguration.longPressTimeoutMillis
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val vw = viewport.width.toFloat()
                val vh = viewport.height.toFloat()
                if (vw <= 0f || vh <= 0f) return@awaitEachGesture
                // 诊断账本（DhStage）：手势收尾一行，定性「捏合没反应」断在哪一环。
                val startScale = state.scale
                val startOffset = state.offsetX + state.offsetY
                var maxFingers = 0
                var twoFingerSeen = false
                var slopBroken = false
                var lastPos = down.position
                // compose-ui 1.9：时间戳在 PointerInputChange.uptimeMillis（毫秒）上，
                // PointerEvent 本身没有时间属性——别按 MotionEvent 的直觉写。
                var upMillis = down.uptimeMillis
                while (true) {
                    // Initial pass：先于子 View（引擎）看到事件，决定吃不吃。
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) {
                        upMillis = event.changes.maxOf { it.uptimeMillis }
                        break
                    }
                    maxFingers = maxOf(maxFingers, pressed.size)
                    lastPos = pressed.first().position
                    if (pressed.size >= 2) {
                        // 第二指落地：这一笔从此只可能是捏合——抬手时的长按判定
                        // （stageLongPressDecision）会因 twoFingerSeen 直接否决。
                        twoFingerSeen = true
                        val centroidX = pressed.map { it.position.x }.average().toFloat()
                        val centroidY = pressed.map { it.position.y }.average().toFloat()
                        var panX = 0f
                        var panY = 0f
                        var zoom = 1f
                        pressed.forEach { ch ->
                            val delta = ch.position - ch.previousPosition
                            panX += delta.x
                            panY += delta.y
                        }
                        panX /= pressed.size
                        panY /= pressed.size
                        val prev = hypot(
                            pressed[0].previousPosition.x - pressed[1].previousPosition.x,
                            pressed[0].previousPosition.y - pressed[1].previousPosition.y,
                        )
                        val curr = hypot(
                            pressed[0].position.x - pressed[1].position.x,
                            pressed[0].position.y - pressed[1].position.y,
                        )
                        if (prev > 0f) zoom = curr / prev
                        val (sx, ox) = stageStep(state.scale, state.offsetX, centroidX, panX, zoom, vw)
                        val (_, oy) = stageStep(state.scale, state.offsetY, centroidY, panY, zoom, vh)
                        state.offsetX = ox
                        state.offsetY = oy
                        state.scale = sx
                        event.changes.forEach { if (it.pressed) it.consume() }
                    } else {
                        val ch = pressed[0]
                        if (!slopBroken &&
                            hypot(ch.position.x - down.position.x, ch.position.y - down.position.y) > slop
                        ) {
                            slopBroken = true
                        }
                        if (slopBroken && zoomedNow.value) {
                            // 放大后的单指拖图（zoom=1 的一步 stageStep 就是纯平移）。
                            val dx = ch.position.x - ch.previousPosition.x
                            val dy = ch.position.y - ch.previousPosition.y
                            state.offsetX = stageStep(state.scale, state.offsetX, ch.position.x, dx, 1f, vw).second
                            state.offsetY = stageStep(state.scale, state.offsetY, ch.position.y, dy, 1f, vh).second
                            event.changes.forEach { if (it.pressed) it.consume() }
                        } else if (onTapNow.value != null) {
                            // 舞台拥有这笔单指事件（与旧覆盖 Box 持平：GL 引擎的
                            // 视线由 setGaze 合成驱动，原始触摸不给它）。
                            event.changes.forEach { if (it.pressed) it.consume() }
                        }
                    }
                }
                // 抬手时刻仲裁（AvatarStageGesturesTest 钉死的两个纯函数）。
                // 长按不在手指按着的途中触发——慢起手捏合（先落一指、停顿、再落
                // 第二指）里第二指永远来得及否决长按；旧结构正是「按 500ms 托盘先弹、
                // 薄纱吃掉第二指」把缩放杀死。
                val heldLong = upMillis - down.uptimeMillis >= longPressMs
                val longPress = onLongPressNow.value != null &&
                    stageLongPressDecision(twoFingerSeen, slopBroken, heldLong)
                val tap = !longPress && stageTapDecision(twoFingerSeen, slopBroken, heldLong)
                if (longPress) {
                    onLongPressNow.value?.invoke()
                } else if (tap) {
                    onTapNow.value?.invoke(Offset(lastPos.x / vw, lastPos.y / vh))
                }
                if (maxFingers >= 2 || tap || longPress) {
                    android.util.Log.i(
                        STAGE_LOG_TAG,
                        stageGestureLog(
                            maxFingers, twoFingerSeen,
                            startScale, state.scale,
                            startOffset, state.offsetX + state.offsetY,
                            tap, longPress,
                        ),
                    )
                }
            }
        }
}

/** Composable 形象（Simple）专用：state → graphicsLayer。 */
fun Modifier.avatarStageLayer(state: AvatarStageTransformState): Modifier =
    graphicsLayer {
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.offsetX
        translationY = state.offsetY
    }
