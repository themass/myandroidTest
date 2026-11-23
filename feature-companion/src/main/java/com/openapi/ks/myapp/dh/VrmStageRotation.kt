package com.openapi.ks.myapp.dh

/**
 * VRM 舞台轨道旋转的纯判定（AvatarStageTransformTest 钉死）。
 *
 * 用户要「支持旋转」（2026-11-23）：单指横拖越过 slop 后进入轨道旋转，
 * 未越 slop 仍走原 tap 分区（stageTapDecision 不改），两指捏合缩放保持现状。
 * 只有 VRM 走这条——[onOrbit] 非 null 时才启用，Live2D/Duix/Simple 不受影响。
 */
object VrmStageRotation {

    /** 横扫一整屏宽 = 转一整圈（360°）：「抓着她转」的手感，跟屏宽自适应。 */
    fun dragYawDelta(dxPx: Float, viewportWidthPx: Float): Float =
        if (viewportWidthPx <= 0f) 0f else dxPx * 360f / viewportWidthPx

    /** 累计角不做夹取：水平转一圈（±360° 以上）是验收项，角度绕圈不是有界量。 */
    fun addYaw(yawDeg: Float, deltaDeg: Float): Float = yawDeg + deltaDeg

    /**
     * 单指拖动归不归旋转：1x 归（「支持旋转」的主路径，未放大也要能转她）；
     * 放大后让给拖图平移——那笔拖动是捏合缩放的配套动作，再兼职旋转就成了
     * 「一划又挪画面又转角色」，不可预测。
     */
    fun rotatesOnSingleFingerDrag(zoomed: Boolean): Boolean = !zoomed
}
