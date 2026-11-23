package com.openapi.feature.camerafx

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * MediaPipe 结果 → 本模块纯数据（[Point2d]）。
 *
 * 这层刻意薄且隔离：纯函数层（FxAnchors）不 import 任何 MediaPipe 类型，
 * JVM 单测才能不依赖 native。这里只搬运，不做过滤——过滤在 [FxTracker]。
 */

fun NormalizedLandmark.toPoint(): Point2d = Point2d(x(), y())

/** 按索引表从关键点列表取点；越界索引直接跳过（防御式，不抛）。 */
fun pick(landmarks: List<NormalizedLandmark>, indices: IntArray): List<Point2d> {
    val out = ArrayList<Point2d>(indices.size)
    for (i in indices) if (i in landmarks.indices) out += landmarks[i].toPoint()
    return out
}
