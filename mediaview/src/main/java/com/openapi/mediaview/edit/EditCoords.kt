package com.openapi.mediaview.edit

import androidx.compose.ui.geometry.Offset

/**
 * 原图坐标 ⇄ 显示坐标（旋转 + 镜像之后）的映射。全部是 0..1 归一化值，不依赖 Android，可以直接单测。
 *
 * 所有标注（涂鸦、文字、马赛克）都存**原图坐标**，这样用户旋转或镜像图片时，已有标注自己跟着转，
 * 不会跑偏。预览、导出、点选、拖动都通过这里换算。
 */
object EditCoords {

    /** 原图归一化坐标 → 显示（旋转 + 镜像后）归一化坐标。 */
    fun toDisplayNorm(x: Float, y: Float, edits: PhotoEdits): Offset {
        // 镜像在原图坐标系内先做，再旋转
        val mx = if (edits.isFlipped) 1f - x else x
        return when (edits.quarterTurns % 4) {
            1 -> Offset(1f - y, mx)      // 顺时针 90°
            2 -> Offset(1f - mx, 1f - y) // 180°
            3 -> Offset(y, 1f - mx)      // 顺时针 270°
            else -> Offset(mx, y)
        }
    }

    fun toDisplayNorm(p: Offset, edits: PhotoEdits): Offset = toDisplayNorm(p.x, p.y, edits)

    /** [toDisplayNorm] 的逆：显示坐标 → 原图坐标。手势把屏幕位置还原成原图位置时用。 */
    fun toSourceNorm(x: Float, y: Float, edits: PhotoEdits): Offset {
        // 先逆旋转
        val px: Float
        val py: Float
        when (edits.quarterTurns % 4) {
            1 -> { px = y; py = 1f - x }
            2 -> { px = 1f - x; py = 1f - y }
            3 -> { px = 1f - y; py = x }
            else -> { px = x; py = y }
        }
        // 再逆镜像
        return Offset(if (edits.isFlipped) 1f - px else px, py)
    }

    fun toSourceNorm(p: Offset, edits: PhotoEdits): Offset = toSourceNorm(p.x, p.y, edits)
}
