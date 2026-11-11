package com.openapi.mediaview.edit

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

/**
 * 图片编辑器的纯数据模型。不含 Android Bitmap，也不含绘制代码，
 * 因此可以单测（见 PhotoEditsTest）：
 *
 * - 一条**操作记录**（[PhotoEdits]）描述「对原图做了什么」，渲染时按顺序重放；
 * - 渲染发生在 CPU 上（[PhotoEditor] 里的位图处理），Compose 只负责显示和手势。
 *
 * 之所以用「操作列表」而不是「每画一笔就改位图」：
 * 撤销要能精确回到上一步，而且保存时只渲染一次，不会因为几十笔涂鸦就反复整图重编码。
 */
@Immutable
sealed interface EditOp {

    /** 旋转 90° 的倍数，[quarterTurns] 0..3，顺时针。 */
    data class Rotate(val quarterTurns: Int) : EditOp

    /** 水平镜像。 */
    data object FlipHorizontal : EditOp

    /** 亮度 / 对比度 / 饱和度，都是 -100..100 的整数（界面上是滑杆）。 */
    data class Adjust(val brightness: Int, val contrast: Int, val saturation: Int) : EditOp {
        val isIdentity: Boolean get() = brightness == 0 && contrast == 0 && saturation == 0
    }

    /** 一次自由涂鸦：一段连续的折线。[widthRatio] 相对图片短边的比例，保证不同分辨率下手感一致。 */
    data class Stroke(
        val points: List<Offset>,
        val colorArgb: Long,
        val widthRatio: Float,
    ) : EditOp

    /** 一段文字。坐标同样是相对原图的 0..1 归一化坐标，左下角为锚点。 */
    data class Text(
        val value: String,
        val x: Float,
        val y: Float,
        val colorArgb: Long,
        val sizeRatio: Float,
    ) : EditOp

    /** 一块马赛克（像素化）。[blockRatio] 是每个色块相对图片短边的比例。 */
    data class Mosaic(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val blockRatio: Float = 0.02f,
    ) : EditOp
}

/**
 * 编辑会话：原图尺寸 + 操作列表。不可变，界面每次改动都生成新实例（便于 Compose 比较）。
 *
 * 坐标约定：所有 [Offset] / 归一化矩形都相对**原图**（未旋转、未镜像），
 * 渲染时先应用 [EditOp.Rotate] / [EditOp.FlipHorizontal]，再按同一坐标系绘制标注，
 * 这样用户旋转图片后已有的涂鸦不会跟着跑偏。
 */
@Immutable
data class PhotoEdits(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val ops: List<EditOp> = emptyList(),
    /** 重做栈：只有 [undo] 会填，新的编辑动作进来就清空。 */
    val redo: List<EditOp> = emptyList(),
) {
    val canUndo: Boolean get() = ops.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()
    val isDirty: Boolean get() = ops.isNotEmpty()

    /** 新增一个操作，同时清空重做栈。 */
    fun push(op: EditOp): PhotoEdits = copy(ops = ops + op, redo = emptyList())

    fun undo(): PhotoEdits =
        if (ops.isEmpty()) this else copy(ops = ops.dropLast(1), redo = redo + ops.last())

    fun redo(): PhotoEdits =
        if (redo.isEmpty()) this else copy(ops = ops + redo.last(), redo = redo.dropLast(1))

    /** 清掉所有编辑（回到原图），并可重做回来。 */
    fun reset(): PhotoEdits = if (ops.isEmpty()) this else copy(ops = emptyList(), redo = ops.reversed())

    /** 旋转是「累加」的：连续点旋转按钮不应该产生四条记录，而是一条。 */
    fun rotateClockwise(): PhotoEdits {
        val currentTurns = (ops.lastOrNull { it is EditOp.Rotate } as? EditOp.Rotate)?.quarterTurns ?: 0
        val next = (currentTurns + 1) % 4
        val others = ops.filterNot { it is EditOp.Rotate }
        // 旋转放最前面：绘制标注时坐标不需要跟着旋转
        return copy(ops = listOf(EditOp.Rotate(next)) + others, redo = emptyList())
    }

    fun flipHorizontal(): PhotoEdits {
        val flipped = ops.count { it == EditOp.FlipHorizontal } % 2 == 1
        val others = ops.filterNot { it == EditOp.FlipHorizontal }
        return copy(
            ops = if (flipped) others else listOf(EditOp.FlipHorizontal) + others,
            redo = emptyList(),
        )
    }

    /**
     * 「镜像」按钮用这个：让**屏幕上看到的画面**左右翻转。
     *
     * 内部模型是「先在原图坐标里镜像（[EditOp.FlipHorizontal]），再旋转（[EditOp.Rotate]）」。
     * 在显示坐标里左右翻转 = H∘R∘M；当旋转为 90°/270° 时 R⁻¹HR 是原图坐标里的**上下**翻转，
     * 而上下翻转 = 左右翻转 ∘ 180°，所以等价于「切换原图镜像，同时多转 180°」。
     * 0°/180° 时 R⁻¹HR 就是左右翻转，直接切换即可。
     */
    fun flipHorizontalOnScreen(): PhotoEdits {
        val toggled = flipHorizontal()
        if (quarterTurns % 2 == 0) return toggled
        val others = toggled.ops.filterNot { it is EditOp.Rotate }
        return toggled.copy(ops = listOf(EditOp.Rotate((quarterTurns + 2) % 4)) + others)
    }

    /** 当前旋转后的显示尺寸（旋转 90/270 时宽高互换）。 */
    val displayWidth: Int get() = if (quarterTurns % 2 == 1) sourceHeight else sourceWidth
    val displayHeight: Int get() = if (quarterTurns % 2 == 1) sourceWidth else sourceHeight

    val quarterTurns: Int get() = (ops.lastOrNull { it is EditOp.Rotate } as? EditOp.Rotate)?.quarterTurns ?: 0

    /** 当前的调整参数（没有就是全 0）。 */
    val adjust: EditOp.Adjust get() = ops.lastOrNull { it is EditOp.Adjust } as? EditOp.Adjust ?: EditOp.Adjust(0, 0, 0)

    companion object {
        fun of(width: Int, height: Int): PhotoEdits = PhotoEdits(width, height)
    }
}
