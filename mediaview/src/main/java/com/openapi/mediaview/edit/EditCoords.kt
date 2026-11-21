package com.openapi.mediaview.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * 原图坐标 ⇄ 显示坐标（旋转 + 镜像 + **裁剪**之后）的映射。全部是 0..1 归一化值，
 * 不依赖 Android，可以直接单测。
 *
 * 所有标注（涂鸦、文字、马赛克）都存**原图**坐标，这样用户旋转或镜像图片时，已有标注自己跟着转，
 * 不会跑偏。预览、导出、点选、拖动都通过这里换算。
 *
 * 裁剪框 [EditOp.Crop] 是唯一活在**显示空间**里的操作，所以「原图空间」和「裁剪后显示帧」
 * 之间只有这里一次换算——[EditHitTest]、[PhotoRenderer]、手势层都不许再自己写一遍，
 * 否则同一个点会在两个地方算出两个位置（这是历史上旋转+镜像后标注脱离照片的根因）。
 */
object EditCoords {

    /**
     * 裁剪窗口在「旋转 + 镜像后的全幅显示归一化」空间里的位置。没有 [EditOp.Crop] 时就是整个画幅。
     *
     * 公开给裁剪 UI（Task 5）用：它拿到的是显示框，要反推回原图才能画选中框。
     */
    fun cropWindow(edits: PhotoEdits): Rect {
        val crop = edits.crop ?: return Rect(0f, 0f, 1f, 1f)
        return Rect(crop.left, crop.top, crop.right, crop.bottom)
    }

    /**
     * 原图归一化坐标 → **裁剪后**显示帧的归一化坐标。
     *
     * **必须和 PhotoRenderer 的管线一致**：先旋转，再在旋转后的（显示）空间里做水平镜像，
     * 最后按 [cropWindow] 取窗口。以前这里是「先在原图坐标里镜像、再旋转」，turns 为奇数时两者差 180°——
     * 照片跟着渲染器走、标注跟着这里走，旋转+镜像后涂鸦就脱离照片（真机问题，
     * 见 PhotoRendererTest.annotationsStayOnTheSameSpotUnderRotateAndFlip）。
     *
     * 落在窗口外的源点会得到 0..1 **之外**的值（不是被夹回来）：渲染时画布边界就是裁剪帧，
     * 超出部分自然被截断，这样「出界的笔迹」不需要改数据，撤销裁剪后能原样回来。
     */
    fun toDisplayNorm(x: Float, y: Float, edits: PhotoEdits): Offset {
        val full = toFullDisplayNorm(x, y, edits)
        return windowFromFull(full.x, full.y, edits)
    }

    fun toDisplayNorm(p: Offset, edits: PhotoEdits): Offset = toDisplayNorm(p.x, p.y, edits)

    /** [toDisplayNorm] 的逆：裁剪后显示坐标 → 原图坐标。手势把屏幕位置还原成原图位置时用。 */
    fun toSourceNorm(x: Float, y: Float, edits: PhotoEdits): Offset {
        val full = windowToFull(x, y, edits)
        return fullDisplayToSourceNorm(full.x, full.y, edits)
    }

    fun toSourceNorm(p: Offset, edits: PhotoEdits): Offset = toSourceNorm(p.x, p.y, edits)

    // ---- 两层拆开：全幅映射（与裁剪无关）+ 窗口 ----

    /**
     * 原图 → 「旋转 + 镜像后的**全幅**显示」归一化坐标（不考虑裁剪窗口）。
     *
     * 保留成私有是因为裁剪是叠在它上面的第二层：`toDisplayNorm = 全幅映射 − 窗口`。
     * 没有 Crop 时窗口是恒等的，公开入口的语义和加裁剪之前逐点相同。
     */
    private fun toFullDisplayNorm(x: Float, y: Float, edits: PhotoEdits): Offset {
        val d = when (edits.quarterTurns % 4) {
            1 -> Offset(1f - y, x)      // 顺时针 90°
            2 -> Offset(1f - x, 1f - y) // 180°
            3 -> Offset(y, 1f - x)      // 顺时针 270°
            else -> Offset(x, y)
        }
        // 镜像作用在显示空间的 x 上（渲染器 mirror 的正是旋转后的位图）
        return if (edits.isFlipped) Offset(1f - d.x, d.y) else d
    }

    private fun fullDisplayToSourceNorm(x: Float, y: Float, edits: PhotoEdits): Offset {
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

    /** 全幅显示坐标 → 裁剪帧坐标。窗口宽/高必定 > 0（[EditOp.Crop] 的 init 保证）。 */
    private fun windowFromFull(x: Float, y: Float, edits: PhotoEdits): Offset {
        val w = cropWindow(edits)
        return Offset((x - w.left) / (w.right - w.left), (y - w.top) / (w.bottom - w.top))
    }

    /** [windowFromFull] 的逆。 */
    private fun windowToFull(x: Float, y: Float, edits: PhotoEdits): Offset {
        val w = cropWindow(edits)
        return Offset(w.left + x * (w.right - w.left), w.top + y * (w.bottom - w.top))
    }
}
