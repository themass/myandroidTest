package com.openapi.ks.myapp.dh

import kotlin.math.PI
import kotlin.math.sin

/**
 * 说话时的头部摆动：口型电平高于门限时，视线在两个轴上做慢速正弦（周期 ~2.4s / ~3.1s），
 * 幅度随电平；闭口回正。Live2D 的头/眼/身体角度都跟随「视线」，所以头会跟着动。
 */
internal object Live2dHeadSway {
    private const val GATE = 0.05f

    fun gaze(openness: Float, nowMs: Long): Pair<Float, Float> {
        if (openness < GATE) return 0f to 0f
        val amp = (0.35f + 0.65f * openness.coerceIn(0f, 1f))
        val t = nowMs / 1000.0
        val x = (sin(2 * PI * t / 2.4) * 0.8 * amp).toFloat()
        val y = (sin(2 * PI * t / 3.1 + 1.0) * 0.35 * amp).toFloat()
        return x to y
    }
}
