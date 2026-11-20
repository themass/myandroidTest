package com.openapi.mediaview.edit

import androidx.compose.ui.geometry.Offset

/**
 * 原图坐标 ⇄ 显示坐标（旋转 + 镜像之后）的映射。全部是 0..1 归一化值，不依赖 Android，可以直接单测。
 *
 * 所有标注（涂鸦、文字、马赛克）都存**原图坐标**，这样用户旋转或镜像图片时，已有标注自己跟着转，
 * 不会跑偏。预览、导出、点选、拖动都通过这里换算。
 */
object EditCoords {

    /**
     * 原图归一化坐标 → 显示（旋转 + 镜像后）归一化坐标。
     *
     * **必须和 PhotoRenderer 的管线一致**：先旋转，再在旋转后的（显示）空间里做水平镜像。
     * 以前这里是「先在原图坐标里镜像、再旋转」，turns 为奇数时两者差 180°——
     * 照片跟着渲染器走、标注跟着这里走，旋转+镜像后涂鸦就脱离照片（真机问题，
     * 见 PhotoRendererTest.annotationsStayOnTheSameSpotUnderRotateAndFlip）。
     */
    fun toDisplayNorm(x: Float, y: Float, edits: PhotoEdits): Offset {
        val d = when (edits.quarterTurns % 4) {
            1 -> Offset(1f - y, x)      // 顺时针 90°
            2 -> Offset(1f - x, 1f - y) // 180°
            3 -> Offset(y, 1f - x)      // 顺时针 270°
            else -> Offset(x, y)
        }
        // 镜像作用在显示空间的 x 上（渲染器 mirror 的正是旋转后的位图）
        return if (edits.isFlipped) Offset(1f - d.x, d.y) else d
    }

    fun toDisplayNorm(p: Offset, edits: PhotoEdits): Offset = toDisplayNorm(p.x, p.y, edits)

    /** [toDisplayNorm] 的逆：显示坐标 → 原图坐标。手势把屏幕位置还原成原图位置时用。 */
    fun toSourceNorm(x: Float, y: Float, edits: PhotoEdits): Offset {
        // 先逆镜像（镜像是显示空间的最后一步）
        val sx = if (edits.isFlipped) 1f - x else x
        // 再逆旋转
        return when (edits.quarterTurns % 4) {
            1 -> Offset(y, 1f - sx)
            2 -> Offset(1f - sx, 1f - y)
            3 -> Offset(1f - y, sx)
            else -> Offset(sx, y)
        }
    }

    fun toSourceNorm(p: Offset, edits: PhotoEdits): Offset = toSourceNorm(p.x, p.y, edits)
}
