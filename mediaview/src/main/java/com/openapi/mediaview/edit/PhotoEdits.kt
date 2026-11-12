package com.openapi.mediaview.edit

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

/**
 * 图片编辑器的纯数据模型。不含 Android Bitmap，也不含绘制代码，
 * 因此可以单测（见 PhotoEditsTest）：
 *
 * - 一条**操作记录**（[PhotoEdits]）描述「对原图做了什么」，渲染时按顺序重放；
 * - 渲染发生在 CPU 上（[PhotoRenderer]），Compose 只负责显示和手势。
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

    /**
     * 一次自由涂鸦：一段连续的折线。[widthRatio] 相对图片短边的比例，保证不同分辨率下手感一致。
     */
    data class Stroke(
        val points: List<Offset>,
        val colorArgb: Long,
        val widthRatio: Float,
    ) : EditOp

    /**
     * 一段文字。
     *
     * [x] / [y] 是**左上角**的 0..1 归一化坐标（多行时是第一行的左上角），
     * 这样拖动时算出的位置和界面上看到的位置一致。
     * [sizeRatio] 是字号相对图片短边的比例；[bold] 控制粗细。
     */
    data class Text(
        val value: String,
        val x: Float,
        val y: Float,
        val colorArgb: Long,
        val sizeRatio: Float,
        val bold: Boolean = true,
    ) : EditOp

    /**
     * 马赛克：**沿笔迹**打码，不是矩形。
     *
     * [points] 是笔迹经过的点（归一化），[widthRatio] 是笔刷宽度（相对短边），
     * 渲染时把「到折线距离 ≤ widthRatio/2」的像素替换成色块平均值。
     * 这样手指滑到哪里就打到哪里，也能处理曲线和来回涂抹。
     */
    data class Mosaic(
        val points: List<Offset>,
        val widthRatio: Float,
        val blockRatio: Float = 0.02f,
    ) : EditOp
}

/**
 * 编辑会话：原图尺寸 + 操作列表 + 撤销/重做栈。不可变，界面每次改动都生成新实例。
 *
 * 坐标约定：所有 [Offset] / 归一化矩形都相对**原图**（未旋转、未镜像）。
 * 渲染和预览都通过 [PhotoRenderer] 统一映射到显示坐标，所以旋转/镜像后已有的标注不会跑偏。
 *
 * 撤销栈存的是**操作列表的快照**而不是单条记录。这样「删除选中笔迹」「拖动文字改位置」
 * 这类会改动中间某个元素的操作也能正确撤销（如果只弹出最后一条，撤销会把被拖动的文字整个删掉）。
 */
@Immutable
data class PhotoEdits(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val ops: List<EditOp> = emptyList(),
    /** 每次改动前的 [ops] 快照，最后一个是最新的一步。 */
    val undoStack: List<List<EditOp>> = emptyList(),
    /** 撤销后可以前进回去的快照。 */
    val redoStack: List<List<EditOp>> = emptyList(),
) {
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val isDirty: Boolean get() = ops.isNotEmpty()

    /** 把 [ops] 换成新列表并记一步撤销。内容没变时不产生历史。 */
    private fun commit(next: List<EditOp>): PhotoEdits =
        if (next == ops) this
        else copy(ops = next, undoStack = undoStack + listOf(ops), redoStack = emptyList())

    /** 新增一个操作。 */
    fun push(op: EditOp): PhotoEdits = commit(ops + op)

    /** 删除第 [index] 条操作（选中后删除）。 */
    fun removeAt(index: Int): PhotoEdits =
        if (index !in ops.indices) this
        else commit(ops.toMutableList().also { it.removeAt(index) })

    /** 替换第 [index] 条操作（拖动文字、改文字内容/颜色都用它）。 */
    fun replaceAt(index: Int, op: EditOp): PhotoEdits =
        if (index !in ops.indices) this
        else commit(ops.toMutableList().also { it[index] = op })

    fun undo(): PhotoEdits =
        if (undoStack.isEmpty()) this
        else copy(
            ops = undoStack.last(),
            undoStack = undoStack.dropLast(1),
            redoStack = redoStack + listOf(ops),
        )

    fun redo(): PhotoEdits =
        if (redoStack.isEmpty()) this
        else copy(
            ops = redoStack.last(),
            redoStack = redoStack.dropLast(1),
            undoStack = undoStack + listOf(ops),
        )

    /** 整体换成新的操作列表并记一步撤销（滑杆拖完松手时一次性提交用）。 */
    fun withOps(next: List<EditOp>): PhotoEdits = commit(next)

    /** 换成新的操作列表但**不记历史**：滑杆拖动过程中的实时预览用，松手后再 [withOps] 提交。 */
    fun withOpsUnrecorded(next: List<EditOp>): PhotoEdits = copy(ops = next)

    /** 清掉所有编辑（回到原图），可以撤销回来。 */
    fun reset(): PhotoEdits = commit(emptyList())

    /** 旋转是「累加」的：连续点旋转按钮不应该产生四条记录，而是一条。 */
    fun rotateClockwise(): PhotoEdits {
        val next = (quarterTurns + 1) % 4
        val others = ops.filterNot { it is EditOp.Rotate }
        // 旋转放最前面：绘制标注时坐标不需要跟着旋转
        return commit(listOf(EditOp.Rotate(next)) + others)
    }

    fun flipHorizontal(): PhotoEdits {
        val flipped = ops.count { it == EditOp.FlipHorizontal } % 2 == 1
        val others = ops.filterNot { it == EditOp.FlipHorizontal }
        return commit(if (flipped) others else listOf(EditOp.FlipHorizontal) + others)
    }

    /** 当前旋转后的显示尺寸（旋转 90/270 时宽高互换）。 */
    val displayWidth: Int get() = if (quarterTurns % 2 == 1) sourceHeight else sourceWidth
    val displayHeight: Int get() = if (quarterTurns % 2 == 1) sourceWidth else sourceHeight

    val quarterTurns: Int get() = (ops.lastOrNull { it is EditOp.Rotate } as? EditOp.Rotate)?.quarterTurns ?: 0

    val isFlipped: Boolean get() = ops.contains(EditOp.FlipHorizontal)

    /** 当前的调整参数（没有就是全 0）。 */
    val adjust: EditOp.Adjust get() = ops.lastOrNull { it is EditOp.Adjust } as? EditOp.Adjust ?: EditOp.Adjust(0, 0, 0)

    companion object {
        fun of(width: Int, height: Int): PhotoEdits = PhotoEdits(width, height)
    }
}

/** 整体平移（原图归一化坐标）。拖动文字/涂鸦时用；其它类型原样返回。 */
fun EditOp.translated(dx: Float, dy: Float): EditOp = when (this) {
    is EditOp.Stroke -> copy(points = points.map { Offset(it.x + dx, it.y + dy) })
    is EditOp.Mosaic -> copy(points = points.map { Offset(it.x + dx, it.y + dy) })
    is EditOp.Text -> copy(x = x + dx, y = y + dy)
    else -> this
}
