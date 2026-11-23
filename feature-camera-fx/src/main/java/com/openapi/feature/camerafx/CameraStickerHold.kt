package com.openapi.feature.camerafx

/**
 * 头顶贴纸的丢失容错（纯函数，JVM 可测）。
 *
 * MediaPipe 对侧脸/快速移动会**单帧漏检**：`face.headTop == null` 只闪一帧，
 * 贴纸却「啪」地消失再出现，比抖动更刺眼。这里定义一个小的决策状态机：
 *
 * ```
 *   检测到 ────────────────► Anchored（正常跟随）
 *   连续 miss ≤ graceFrames ─► Held（钉在最后一次成功位置，不闪）
 *   连续 miss > graceFrames ─► Off（真没脸了，贴纸消失）
 * ```
 *
 * 与 [PlacementSmoother] 的分工：Held 期间不喂 smoother（避免把「没有观测」
 * 当成「目标不动」把 EMA 拉死）；重新检测到后直接以当时目标 snap 回平滑器
 * （`reset()` + 目标），因为头在漏检期间可能已经移动。
 */
sealed interface StickerTrack {
    /** 从未检测到或超过宽限帧数：不画。 */
    data object Off : StickerTrack

    /** 本帧有锚点：按 [PlacementSmoother] 平滑后画。 */
    data class Anchored(val observed: Boolean = true) : StickerTrack

    /** 连续漏检但在宽限内：画在最后一次成功位置（不更新 smoother）。 */
    data class Held(val missedFrames: Int) : StickerTrack
}

/**
 * 每帧根据 `hasAnchor` 推进状态；返回本帧应该怎么画。
 *
 * [graceFrames] = 0 表示不容错（每帧独立）。默认 3 帧 ≈ MediaPipe 常规
 * run 速率下 ~100ms 级别的漏检不闪。
 */
class StickerHoldTracker(private val graceFrames: Int = 3) {
    private var missed = 0
    private var everAnchored = false

    /** 目标切换（snap 换脸）或资产切换时调用。 */
    fun reset() {
        missed = 0
        everAnchored = false
    }

    fun advance(hasAnchor: Boolean): StickerTrack {
        require(graceFrames >= 0) { "graceFrames must be >= 0" }
        return if (hasAnchor) {
            missed = 0
            everAnchored = true
            StickerTrack.Anchored()
        } else {
            if (!everAnchored) return StickerTrack.Off
            missed += 1
            if (missed <= graceFrames) StickerTrack.Held(missed) else StickerTrack.Off
        }
    }
}
