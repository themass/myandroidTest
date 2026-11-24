package com.openapi.feature.camerafx

import kotlin.math.abs

/**
 * 头顶贴纸的跨帧跟随（纯函数，JVM 可测）。
 *
 * MediaPipe 每帧独立检测，headTop/倾角在亚像素级抖动；把每帧的 [stickerAtHeadTop]
 * 结果直接贴到 Compose 上，头饰边缘会有可见的高频抖动（真机 lab 观感）。
 * 这里做一阶 EMA，并对「跳脸」做 snap：
 *
 * `FaceFx` 没有跨帧身份（MediaPipe 不保证多脸顺序稳定），贴纸按 list 下标挂 smoother。
 * 下标换了人时若仍走 EMA，贴纸会从上一张脸**滑**到下一张脸。所以位移/尺寸/倾角
 * 任一超过阈值就当换目标，直接贴到位，不做插值。
 */
class PlacementSmoother(
    /** 新值权重，(0, 1]；1f = 不平滑。 */
    val alpha: Float = 0.35f,
    /** 中心位移超过贴纸宽的这个比例 → snap（换脸/大幅甩头）。 */
    val jumpFractionOfWidth: Float = 0.45f,
    /** 尺寸变化超过这个比例 → snap（凑近凑远、检测框突变）。 */
    val sizeRatioToSnap: Float = 0.6f,
    /** 倾角差超过这个度数 → snap（±90 归一化边界翻转时不该缓慢转半圈）。 */
    val tiltDegToSnap: Float = 45f,
) {
    private var prev: StickerPlacementPx? = null

    /** 目标丢失/换资产时清状态，下一次从 target 直接起。 */
    fun reset() {
        prev = null
    }

    /**
     * @return 平滑后的摆放；首帧或 snap 时原样返回 [target]。
     */
    fun smooth(target: StickerPlacementPx): StickerPlacementPx {
        require(alpha > 0f && alpha <= 1f) { "alpha must be in (0, 1], was $alpha" }
        val p = prev ?: run { prev = target; return target }
        val ref = maxOf(target.sizePx, p.sizePx)
        // 参考宽度兜底：sizePx 被 coerceIn(24,512) 钉住时仍要能判断位移
        val jump = hypotLike(target.centerX - p.centerX, target.centerY - p.centerY)
        val sizeJump = if (p.sizePx <= 0f) 1f else abs(target.sizePx - p.sizePx) / p.sizePx
        if (jump > ref * jumpFractionOfWidth ||
            sizeJump > sizeRatioToSnap ||
            abs(angleDelta(p.rotationDeg, target.rotationDeg)) > tiltDegToSnap
        ) {
            prev = target
            return target
        }
        val a = alpha
        val next = StickerPlacementPx(
            centerX = p.centerX + (target.centerX - p.centerX) * a,
            centerY = p.centerY + (target.centerY - p.centerY) * a,
            sizePx = p.sizePx + (target.sizePx - p.sizePx) * a,
            rotationDeg = wrap180(p.rotationDeg + angleDelta(p.rotationDeg, target.rotationDeg) * a),
        )
        prev = next
        return next
    }
}

/** 最短弧角差，落在 (-180, 180]。 */
fun angleDelta(from: Float, to: Float): Float {
    var d = to - from
    while (d > 180f) d -= 360f
    while (d <= -180f) d += 360f
    return d
}

fun wrap180(deg: Float): Float {
    var d = deg
    while (d > 180f) d -= 360f
    while (d <= -180f) d += 360f
    return d
}

private fun hypotLike(dx: Float, dy: Float): Float {
    val ax = abs(dx)
    val ay = abs(dy)
    // 免 sqrt：用 max + 0.4*min 的近似（单测只钉单调性，不需要精确欧氏距离）
    return if (ax >= ay) ax + 0.4f * ay else ay + 0.4f * ax
}

/**
 * 单张贴纸的跨帧状态：一路平滑器 + 一路丢失容错 tracker + 最后一次成功摆放。
 * 放在 composable 的 `remember` 列表里按槽位复用；槽位下标不是身份，
 * 跳脸由 smoother snap、漏检由 tracker 决定。
 */
class StickerSlotState {
    val smoother = PlacementSmoother()
    val tracker = StickerHoldTracker()
    var lastPlacement: StickerPlacementPx? = null
        private set
    private var lastWasAnchored: Boolean = false

    /** Smooth observed placements; reacquisition after Held starts a fresh EMA at the target. */
    fun anchor(target: StickerPlacementPx): StickerPlacementPx {
        if (lastPlacement != null && !lastWasAnchored) smoother.reset()
        return smoother.smooth(target).also {
            lastPlacement = it
            lastWasAnchored = true
        }
    }

    /** Keep the last visible placement and mark the next anchor as a reacquisition. */
    fun hold(): StickerPlacementPx? {
        if (lastPlacement != null) lastWasAnchored = false
        return lastPlacement
    }

    fun reset() {
        smoother.reset()
        tracker.reset()
        lastPlacement = null
        lastWasAnchored = false
    }
}
