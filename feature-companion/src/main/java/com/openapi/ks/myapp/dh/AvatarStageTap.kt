package com.openapi.ks.myapp.dh

import androidx.compose.runtime.Immutable

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
