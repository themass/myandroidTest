package com.openapi.feature.camerafx

/**
 * 摄像头画面 → 头顶 Lottie 贴纸的几何（纯函数，JVM 单测）。
 *
 * 与 [CanvasMap] 使用同一 FILL_CENTER + mirrorX 语义，避免 HUD 与贴纸各算一套。
 */
data class StickerPlacementPx(
    val centerX: Float,
    val centerY: Float,
    val sizePx: Float,
    val rotationDeg: Float,
)

/**
 * @param headTop 归一化头顶锚点（来自 [headTopAnchor]）
 * @param faceWidthNorm 人脸框宽（归一化），用于按脸缩放贴纸
 * @param tiltDegForDisplay 与 HUD 一致的显示倾角（[lineTiltDegrees] + mirrorX）
 */
fun stickerAtHeadTop(
    map: CanvasMap,
    headTop: Point2d,
    faceWidthNorm: Float,
    tiltDegForDisplay: Float,
    widthFractionOfFace: Float = 0.85f,
): StickerPlacementPx {
    val m = map.map(headTop)
    val scale = map.scalePx
    val w = (faceWidthNorm * scale * widthFractionOfFace).coerceIn(24f, 512f)
    return StickerPlacementPx(
        centerX = m.x,
        centerY = m.y,
        sizePx = w,
        rotationDeg = tiltDegForDisplay,
    )
}
