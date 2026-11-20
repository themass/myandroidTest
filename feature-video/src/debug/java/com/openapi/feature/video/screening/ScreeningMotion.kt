package com.openapi.feature.video.screening

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 放映场的动效契约，数值对齐设计系统的 motion token：
 *
 * - 进场 `--duration-medium`（410ms）：页面切换会重组布局，给眼睛跟上内容；
 * - 出场 `--duration-exit`（= fast，175ms）：用户注意力已经移走，快速让位；
 * - 位移 24dp：方向要能读出来「进去/回来」，但不要整屏推——那是系统抽屉的语言。
 *
 * 系统「减弱动态效果」（过渡动画缩放为 0）时全部降级为 ≤200ms 的纯透明度：
 * 位移与按压缩放归零，只剩淡入淡出。
 */
internal class MotionPlan(val reduceMotion: Boolean) {

    fun enterDurationMs(): Int = if (reduceMotion) 150 else 410
    fun exitDurationMs(): Int = if (reduceMotion) 120 else 175

    /** 页面切换的水平位移（dp）；减弱动态时为 0，退化成淡入淡出。 */
    fun slideDistanceDp(): Float = if (reduceMotion) 0f else 24f

    /** 按下时缩掉的量（1 → 1-此值）；减弱动态时为 0。 */
    fun pressScale(): Float = if (reduceMotion) 0f else 0.02f

    /** 列表条目首次入场的上浮（dp）；减弱动态时为 0，只剩淡入。 */
    fun enterLiftDp(): Float = if (reduceMotion) 0f else 6f
}

/**
 * 过渡动画缩放为 0 = 用户要求减弱动态。读取失败按「不减弱」处理：
 * 动效是可感增强，宁可给动效，也不因一个读不到的开关砍掉体验。
 */
internal fun isReduceMotion(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
}.getOrDefault(false)

/** `--ease-standard`/`--ease-entrance`：减速收尾，进场用。 */
internal val EaseStandard = CubicBezierEasing(0.24f, 1f, 0.4f, 1f)

/** `--ease-exit`：从静止起步加速离开，全程都看得见，出场用。 */
internal val EaseExit = CubicBezierEasing(0.3f, 0f, 0.6f, 0.6f)

/**
 * 页面内的组件从这里取动效契约，不逐层传参。
 * 默认是不减弱的契约；`ScreeningHome` 按设备设置提供真值。
 */
internal val LocalScreeningMotion = staticCompositionLocalOf { MotionPlan(false) }

/**
 * 页面切换转场：新页从前进/后退方向滑入，旧页只退半步并淡出（视差感，读得出层级）。
 * [slidePx] 是 [MotionPlan.slideDistanceDp] 换算成像素后的值；减弱动态时距离为 0，
 * 这段代码自动退化为一次纯淡入淡出。
 */
internal fun screenSlide(motion: MotionPlan, slidePx: Float, forward: Boolean): ContentTransform =
    slideInHorizontally(tween(motion.enterDurationMs(), easing = EaseStandard)) {
        (if (forward) slidePx else -slidePx).roundToInt()
    } + fadeIn(tween(motion.enterDurationMs(), easing = EaseStandard)) togetherWith
    fadeOut(tween(motion.exitDurationMs(), easing = EaseExit)) +
    slideOutHorizontally(tween(motion.exitDurationMs(), easing = EaseExit)) {
        (if (forward) -slidePx / 2f else slidePx / 2f).roundToInt()
    }

/** 覆盖层（开屏遮罩、全屏阅览）的淡入淡出对。 */
internal fun screenFade(motion: MotionPlan): Pair<androidx.compose.animation.EnterTransition, androidx.compose.animation.ExitTransition> =
    fadeIn(tween(motion.enterDurationMs(), easing = EaseStandard)) to
        fadeOut(tween(motion.exitDurationMs(), easing = EaseExit))

/**
 * 可点卡片的手感：按下缩 [MotionPlan.pressScale]，抬手弹回，替换裸 clickable。
 * 缩放放在 clip 之前，圆角与波纹都跟着一起缩；减弱动态时缩放为 0，只剩波纹。
 */
@Composable
fun Modifier.pressable(shape: Shape = Tile, onClick: () -> Unit): Modifier {
    val motion = LocalScreeningMotion.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1f - motion.pressScale() else 1f,
        animationSpec = tween(if (pressed) 130 else 175, easing = EaseStandard),
        label = "pressScale",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .clickable(interactionSource = source, indication = ripple(), onClick = onClick)
}

/**
 * 列表条目首次入场的错峰淡入上浮：同一批里每个比上一个晚 40ms 起步（最多排 6 拍），
 * 让新数据「铺开」而不是「砸下来」。看过的条目不再播——回滚不该让已经在那里的东西重闪。
 */
@Composable
fun Modifier.enterFeedItem(seen: MutableSet<String>, key: String, order: Int): Modifier {
    val motion = LocalScreeningMotion.current
    if (motion.reduceMotion || key in seen) return this
    var target by remember { mutableStateOf(0f) }
    val stagger = order.coerceAtMost(6) * 40
    val t by animateFloatAsState(
        target, tween(230, delayMillis = stagger, easing = EaseStandard), label = "itemEnter",
    )
    val liftPx = with(LocalDensity.current) { motion.enterLiftDp().dp.toPx() }
    LaunchedEffect(Unit) { seen += key; target = 1f }
    return graphicsLayer { alpha = t; translationY = (1f - t) * liftPx }
}
