package com.openapi.ks.myapp.dh

import androidx.compose.runtime.Immutable
import com.openapi.ks.myapp.avatar.AvatarRegion
import kotlin.math.abs

/**
 * 点数字人舞台时的区域划分（纯函数，JVM 可测）。
 *
 * 区域名与旧版分层立绘一致（"head"/"body"）：服务端 `companion.touch` 的语义
 * 不该因为换了引擎而变。竖向 0.42 以上算头——人物在竖屏里头部大致在上四成。
 */
internal fun stageTapRegion(nx: Float, ny: Float): String {
    val y = ny.coerceIn(0f, 1f)
    return if (y < 0.42f) "head" else "body"
}

/**
 * Live2D 全身立绘的细分区（demo #2b）：头 / 躯干 / 手 / 脚。
 *
 * 阈值来自真机截图实测（两张不同服装的截图测量一致，说明分区与服装无关）：
 * ATRI 立绘占 norm_y≈0.10–0.73；头是 0.42 以上的窄条；0.42–0.57 手臂向两侧
 * 展开（最宽到 x 0.33/0.71）；0.57 以下收窄成双腿直到脚。横向手判据取
 * |x−0.5|≥0.12：躯干中列宽约 ±0.1，臂展尖端在 ±0.17 以外，留了余量。
 *
 * 触摸刀 C6 之后**生产路径走 [TouchZoneRouter]**（声明优先、无声明用
 * [TouchZoneRouter.builtIn]，几何与此处同一批实测值）。这个函数保留作**对照判据**：
 * `TouchZoneRouterTest` 逐点钉「换刀不换分区手感」，删掉它测试会拦住并说明原因。
 */
internal fun live2dTapZone(nx: Float, ny: Float): String {
    val x = nx.coerceIn(0f, 1f)
    val y = ny.coerceIn(0f, 1f)
    return when {
        y < 0.42f -> AvatarRegion.HEAD
        y >= 0.57f -> AvatarRegion.FOOT
        abs(x - 0.5f) >= 0.12f -> AvatarRegion.HAND
        else -> AvatarRegion.BODY
    }
}

/** 点击脉冲的时长与缩放参数。 */
@Immutable
data class TapPulse(val scaleDelta: Float, val pressDownMs: Int, val returnMs: Int)

/**
 * 点她一下的本地反馈脉冲：按下缩一点、抬手弹回。
 * 幅度刻意小（2%）——反馈要「动一下」，不是让人物跳起来抢戏。
 * 减弱动态（动画缩放关闭）时缩放归零：反馈交给触觉震动，画面不缩放。
 */
internal fun stageTapPulse(reducedMotion: Boolean): TapPulse =
    if (reducedMotion) TapPulse(scaleDelta = 0f, pressDownMs = 120, returnMs = 120)
    else TapPulse(scaleDelta = 0.02f, pressDownMs = 110, returnMs = 190)
