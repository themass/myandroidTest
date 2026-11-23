package com.openapi.feature.camerafx

import kotlin.math.hypot

/**
 * 基于 21 点归一化坐标的轻量手势分类（demo 用，非训练模型）。
 * 阈值在 JVM 单测里钉死，便于调参。
 */

enum class HandGestureKind {
    /** 点不足或未判定 */
    NONE,
    /** 五指摊开 */
    OPEN,
    /** 指尖聚向腕部 */
    FIST,
    /** 拇指食指捏合 */
    PINCH,
    UNKNOWN,
}

/** 归一化平面两点距离。 */
fun pointDist(a: Point2d, b: Point2d): Float = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

/**
 * 握拳：所有指尖到腕的距离都小于 [fistMaxTipDist]。
 * 张开：平均指尖距离大于 [openMinAvgTipDist]。
 * 捏合：拇指尖(4)与食指尖(8)距离小于 [pinchMaxThumbIndexDist]，且未满足握拳。
 */
fun classifyHandGesture(
    points: List<Point2d>,
    openMinAvgTipDist: Float = 0.14f,
    fistMaxTipDist: Float = 0.11f,
    pinchMaxThumbIndexDist: Float = 0.07f,
): HandGestureKind {
    if (points.size < HAND_LANDMARK_COUNT) return HandGestureKind.NONE
    val wrist = points[0]
    val tipDists = HAND_FINGER_TIPS.map { pointDist(wrist, points[it]) }
    val avgTip = tipDists.average().toFloat()
    val maxTip = tipDists.maxOrNull() ?: 0f

    val thumbIndex = pointDist(points[4], points[8])
    if (thumbIndex <= pinchMaxThumbIndexDist && maxTip > fistMaxTipDist * 0.85f) {
        return HandGestureKind.PINCH
    }
    if (maxTip <= fistMaxTipDist) return HandGestureKind.FIST
    if (avgTip >= openMinAvgTipDist) return HandGestureKind.OPEN
    return HandGestureKind.UNKNOWN
}

/** HUD 短标签 */
fun HandGestureKind.hudLabel(): String = when (this) {
    HandGestureKind.NONE -> "—"
    HandGestureKind.OPEN -> "张"
    HandGestureKind.FIST -> "拳"
    HandGestureKind.PINCH -> "捏"
    HandGestureKind.UNKNOWN -> "?"
}
