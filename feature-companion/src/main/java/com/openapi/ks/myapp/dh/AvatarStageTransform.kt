package com.openapi.ks.myapp.dh

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
 * 一次手势结束时的诊断行。三个问题一行答完（用户 2026-11-22「双手放大缩小不见了」）：
 * 手势层看到几个手指（`fingers`）→ 认成两指了吗（`twoFingerSeen`）→
 * 状态机真的动了吗（`moved`）。false/false = 事件根本没到这层（被上层吃掉）；
 * true/false = 两指到了但步进没生效；true/true 而屏幕不变 = 变换没写到 View。
 */
fun stageGestureLog(
    maxFingers: Int,
    twoFingerSeen: Boolean,
    startScale: Float,
    endScale: Float,
    startOffset: Float,
    endOffset: Float,
): String =
    "gesture fingers=$maxFingers twoFinger=$twoFingerSeen " +
        "scale ${"%.2f".format(startScale)}->${"%.2f".format(endScale)} " +
        "moved=${startScale != endScale || startOffset != endOffset}"

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
 * 形象舞台的双指缩放/拖动包装层（所有引擎共用），挂在引擎视口的 Modifier 链上。
 *
 * 手势约定（不牺牲原有交互）：
 * - 双指：任意倍率下捏合缩放 + 平移，吃掉事件；
 * - 单指：只有放大状态才拖动；1x 时事件原样落到引擎 View（点击触发形象动画、
 *   Live2D 的触摸视线都不受影响）；
 * - 复位：放大后由宿主画一枚「复位」小胶囊，点回 1x 居中。
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
): Modifier {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val zoomedNow = rememberUpdatedState(state.isZoomed)
    return this
        .onSizeChanged { viewport = it }
        .pointerInput(state) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val vw = viewport.width.toFloat()
                val vh = viewport.height.toFloat()
                if (vw <= 0f || vh <= 0f) return@awaitEachGesture
                // 诊断账本（DhStage）：手势收尾一行，定性「捏合没反应」断在哪一环。
                val startScale = state.scale
                val startOffset = state.offsetX + state.offsetY
                var maxFingers = 0
                var twoFingerSeen = false
                while (true) {
                    // Initial pass：先于子 View（引擎）看到事件，决定吃不吃。
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    maxFingers = maxOf(maxFingers, pressed.size)
                    val twoFinger = pressed.size >= 2
                    val dragWhenZoomed = pressed.size == 1 && zoomedNow.value
                    if (twoFinger || dragWhenZoomed) {
                        twoFingerSeen = twoFingerSeen || twoFinger
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
                        if (twoFinger) {
                            val prev = hypot(
                                pressed[0].previousPosition.x - pressed[1].previousPosition.x,
                                pressed[0].previousPosition.y - pressed[1].previousPosition.y,
                            )
                            val curr = hypot(
                                pressed[0].position.x - pressed[1].position.x,
                                pressed[0].position.y - pressed[1].position.y,
                            )
                            if (prev > 0f) zoom = curr / prev
                        }
                        val (sx, ox) = stageStep(state.scale, state.offsetX, centroidX, panX, zoom, vw)
                        val (_, oy) = stageStep(state.scale, state.offsetY, centroidY, panY, zoom, vh)
                        state.offsetX = ox
                        state.offsetY = oy
                        state.scale = sx
                        event.changes.forEach { if (it.pressed) it.consume() }
                    }
                }
                if (maxFingers >= 2) {
                    android.util.Log.i(
                        STAGE_LOG_TAG,
                        stageGestureLog(
                            maxFingers, twoFingerSeen,
                            startScale, state.scale,
                            startOffset, state.offsetX + state.offsetY,
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
