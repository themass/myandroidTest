package com.openapi.mediaview.edit

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt

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
     * 3D LUT 滤镜。[id] 是 [FilterPresets.ids] 中的一个，对应 assets 下的 `<id>.cube`；
     * [intensity] 是 0..100 的强度（界面上是滑杆），0 等同于不套表。
     *
     * 这里只有「选了哪张表、多强」。表本身由渲染时通过 `lutProvider` 现取，
     * 数据模型不碰文件、也不碰 Bitmap。
     */
    data class Filter(val id: String, val intensity: Int) : EditOp

    /**
     * 裁剪框。**归一化于「旋转 + 镜像之后」的显示画幅**，不是原图：
     * 用户拖的就是屏幕上看到的画面，所以它天然活在显示空间里。
     *
     * 这跟标注（[Stroke] / [Text] / [Mosaic]）的约定不同——那些永远归一化于**原始未变换**的位图。
     * 两个空间之间唯一的换算在 [EditCoords]，别处不许再写一遍。
     *
     * 裁剪**不改写任何标注数据**：窗口外的笔迹靠渲染截断（画布边界就是裁剪帧），
     * 撤销裁剪后它们原样回来。这也是撤销能保持简单的前提——没有数据迁移就没有迁移失败。
     */
    data class Crop(val left: Float, val top: Float, val right: Float, val bottom: Float) : EditOp {
        init {
            require(left in 0f..1f && right in 0f..1f && top in 0f..1f && bottom in 0f..1f) {
                "裁剪框必须落在 0..1 之内：$this"
            }
            require(left < right && top < bottom) { "裁剪框不能是空的：$this" }
        }
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

    /**
     * 「镜像」按钮用这个：让**屏幕上看到的画面**左右翻转。
     *
     * 渲染管线是「先旋转，再在旋转后的显示空间里水平镜像」（和 [EditCoords.toDisplayNorm]
     * 一致），所以屏幕上的左右翻转就是在显示空间再叠一次水平镜像 = [EditOp.FlipHorizontal]
     * 的开/关本身，不需要额外补旋转。
     *
     * （旧实现在这里多转了 180°：那时坐标映射是「原图坐标里先镜像再旋转」，
     * 与渲染器的显示空间镜像差 180°，补偿只是把差值抵掉，反而让标注脱离照片。）
     */
    fun flipHorizontalOnScreen(): PhotoEdits = flipHorizontal()

    /**
     * 当前旋转 + 镜像后的显示画幅尺寸，**已计入裁剪窗口**（旋转 90/270 时宽高互换）。
     *
     * 这是全局的「显示帧有多大」唯一口径：预览布局、点选像素换算、选中框都从这里取，
     * 所以裁剪之后它们自动跟着变，不需要各自再乘一次窗口。
     */
    val displayWidth: Int get() = ((if (quarterTurns % 2 == 1) sourceHeight else sourceWidth) * cropWindowWidthFraction).roundToInt()
    val displayHeight: Int get() = ((if (quarterTurns % 2 == 1) sourceWidth else sourceHeight) * cropWindowHeightFraction).roundToInt()

    private val cropWindowWidthFraction: Float get() = crop?.let { it.right - it.left } ?: 1f
    private val cropWindowHeightFraction: Float get() = crop?.let { it.bottom - it.top } ?: 1f

    val quarterTurns: Int get() = (ops.lastOrNull { it is EditOp.Rotate } as? EditOp.Rotate)?.quarterTurns ?: 0

    val isFlipped: Boolean get() = ops.contains(EditOp.FlipHorizontal)

    /** 当前的调整参数（没有就是全 0）。 */
    val adjust: EditOp.Adjust get() = ops.lastOrNull { it is EditOp.Adjust } as? EditOp.Adjust ?: EditOp.Adjust(0, 0, 0)

    /** 当前生效的滤镜（ops 里最后一条；一条都没有就是 null = 不套表）。 */
    val filter: EditOp.Filter? get() = ops.lastOrNull { it is EditOp.Filter } as? EditOp.Filter

    /** 当前的裁剪框（ops 里最后一条；一条都没有就是 null = 不裁剪）。全列表至多一条，由 [setCrop] 保证。 */
    val crop: EditOp.Crop? get() = ops.lastOrNull { it is EditOp.Crop } as? EditOp.Crop

    /**
     * 设置裁剪框，全列表**至多一条**：
     * - 已有 Crop → [replaceAt] 原位替换（占一步撤销，回到上一个窗口）；
     * - 没有 → [push]；
     * - [crop] 为 null → 移除那条记录（可撤销回来）。
     *
     * 内容没变时 [commit] 不产生历史，所以重复提交同一窗口不会堆积。
     */
    fun setCrop(crop: EditOp.Crop?): PhotoEdits {
        val index = ops.indexOfLast { it is EditOp.Crop }
        return when {
            crop == null -> if (index < 0) this else removeAt(index)
            index >= 0 -> replaceAt(index, crop)
            else -> push(crop)
        }
    }

    /**
     * 选滤镜，全列表**至多一条**（面板互斥，见设计裁量 7；渲染只认最后一条，位置无实现含义）：
     * - 已有 Filter → [replaceAt] 原位替换（占一步撤销，回到上一个滤镜/强度）；
     * - 没有 → [push]；
     * - [filter] 为 null → 移除那条记录（可撤销回来）。
     *
     * 与 [setCrop] 同构。滑杆**拖动过程中不要用它**（那会每一下都记一步历史），
     * 拖动走 [withOpsUnrecorded]、松手再回到这里提交一次。
     */
    fun setFilter(filter: EditOp.Filter?): PhotoEdits {
        val index = ops.indexOfLast { it is EditOp.Filter }
        return when {
            filter == null -> if (index < 0) this else removeAt(index)
            index >= 0 -> replaceAt(index, filter)
            else -> push(filter)
        }
    }

    /** [setFilter] 的面板入口：选一个预设、强度拉满。 */
    fun setFilter(id: String, intensity: Int): PhotoEdits = setFilter(EditOp.Filter(id, intensity))

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
