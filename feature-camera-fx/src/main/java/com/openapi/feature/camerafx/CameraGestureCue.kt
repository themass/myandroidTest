package com.openapi.feature.camerafx

/**
 * 相机手势 → 舞台 cue 的**单一事实源**（纯函数，JVM 单测钉死）。
 *
 * 舞台词表在 `feature-companion` 的 `StageEffectMapper`（"pat_head" / "wave" / "connected"）。
 * feature 之间不建依赖边，所以这里只产出**词表字符串**，由路由层喂给
 * `emitStageGesture`——不另造总线（架构红线，见
 * `docs/superpowers/specs/2026-11-23-camera-fx-stage-integration.md`）。
 */
object CameraGestureCue {

    /** 手势连续 [DEFAULT_HOLD_FRAMES] 帧才算稳定（滤抖）。 */
    const val DEFAULT_HOLD_FRAMES = 2

    /** 一次 cue 之后至少隔 [DEFAULT_COOLDOWN_FRAMES] 帧才允许下一个 cue（防连发刷屏）。 */
    const val DEFAULT_COOLDOWN_FRAMES = 24

    /**
     * 词表映射（与 `StageEffectMapper.fromGesture` 对齐）：
     * - 张开（举手）→ `wave`
     * - 捏合 → `pat_head`
     * - 握拳 → 暂无舞台特效（返回 null，不硬凑）
     */
    fun stageGestureId(kind: HandGestureKind): String? = when (kind) {
        HandGestureKind.OPEN -> "wave"
        HandGestureKind.PINCH -> "pat_head"
        HandGestureKind.FIST, HandGestureKind.NONE, HandGestureKind.UNKNOWN -> null
    }
}

/**
 * 边沿检测（纯逻辑，JVM 可测）：手势**稳定出现**时发一次 cue。
 *
 * 规则：
 * 1. 连续 [holdFrames] 帧同一手势 → 稳定；
 * 2. 稳定手势映射到词表（[CameraGestureCue.stageGestureId]）且 ≠ 上次已发的手势
 *    且已过冷却 → 发一次 cue；
 * 3. NONE 稳定 = 「收手」，复位已发标记 → 再举手可再触发（每次举手各发一次）；
 * 4. 发过之后 [cooldownFrames] 帧内不再发任何 cue（防交替刷屏）。
 *
 * 单线程使用（与 FxAnalyzer / UI 帧流同纪律），无锁。
 */
class StageGestureEdge(
    private val holdFrames: Int = CameraGestureCue.DEFAULT_HOLD_FRAMES,
    private val cooldownFrames: Int = CameraGestureCue.DEFAULT_COOLDOWN_FRAMES,
) {
    private var frame = 0L
    private var nextAllowedFrame = 0L
    private var candidate: HandGestureKind = HandGestureKind.NONE
    private var run = 0
    private var lastEmittedKind: HandGestureKind = HandGestureKind.NONE

    /** 喂一帧的主手势（多手时调用方选一个）；返回一次性 cue id 或 null。 */
    fun update(kind: HandGestureKind): String? {
        frame += 1
        if (kind == candidate) {
            run += 1
        } else {
            candidate = kind
            run = 1
        }
        if (run < holdFrames) return null

        if (kind == HandGestureKind.NONE) {
            // 收手：允许同一手势下次再触发
            lastEmittedKind = HandGestureKind.NONE
            return null
        }
        if (kind == lastEmittedKind) return null
        if (frame < nextAllowedFrame) return null

        val cue = CameraGestureCue.stageGestureId(kind) ?: return null
        lastEmittedKind = kind
        nextAllowedFrame = frame + cooldownFrames
        run = 0
        return cue
    }

    /** 重新打开相机/小窗时复位。 */
    fun reset() {
        frame = 0
        nextAllowedFrame = 0
        candidate = HandGestureKind.NONE
        run = 0
        lastEmittedKind = HandGestureKind.NONE
    }
}
