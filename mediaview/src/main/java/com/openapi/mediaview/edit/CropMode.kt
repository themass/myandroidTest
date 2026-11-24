package com.openapi.mediaview.edit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openapi.mediaview.R
import kotlin.math.abs
import kotlin.math.min

/**
 * 裁剪模式的**几何**（纯函数，不碰 Compose 的布局与手势）。
 *
 * 抽出来的理由：裁剪最容易错的不是画得好看与否，而是「把手该缩到哪、比例锁住后怎么夹、
 * 已有的裁剪窗口怎么和新框合成」。这些写成纯函数就能用普通 JVM 单测钉住（见 CropModeTest），
 * [CropMode] 里的 pointerInput 只负责把触摸点喂进来，不承担逻辑。
 *
 * **坐标系**：这里的选框/画幅都是**视口像素**（[CropMode] 那块区域的本地坐标）。
 * 归一化只发生在 [normalizeSelection] 与 [composedCrop] 两处出口。
 * 注意视口就是「当前画幅」——已有裁剪时它是裁剪后的帧，所以选框归一化之后还要经
 * [composedCrop] 叠回全幅显示空间，才是 [EditOp.Crop] 的语义。
 */
object CropGeometry {

    /**
     * 最小保留框：当前画幅**短边**的 1/8（设计裁量 11）。
     *
     * 是「当前画幅」而不是原始 source：已有裁剪时画幅已经被裁小了，再按原图的 1/8 去限
     * 会把视口撑破。渲染层对退化窗口会夹到 1px，但那是最后一道保险，UI 层自己先把人挡在门外。
     */
    fun minCropSide(width: Float, height: Float): Float = min(width, height) / 8f

    /** 按比例在画幅里取一个**居中**的最大选框（点上比例预设时的初始框；Free = 铺满）。 */
    fun fitAspectRect(aspect: CropAspect, frameWidth: Float, frameHeight: Float): Rect {
        if (frameWidth <= 0f || frameHeight <= 0f) return Rect.Zero
        val ratio = aspect.ratio ?: return Rect(0f, 0f, frameWidth, frameHeight)
        // 先按宽铺满；高度超了就改成按高铺满（= 高度钳到画幅）
        var w = frameWidth
        var h = w / ratio
        if (h > frameHeight) {
            h = frameHeight
            w = h * ratio
        }
        val left = (frameWidth - w) / 2f
        val top = (frameHeight - h) / 2f
        return Rect(left, top, left + w, top + h)
    }

    /**
     * 进裁剪模式（或换比例/视口）时铺的初始选框，**预览区绝对坐标**。
     *
     * [fitAspectRect] 按自己的约定以帧原点 (0,0) 居中（[CropModeTest] 钉死了那条约定），
     * 但选框和把手活在预览区的绝对坐标里：图片 Fit 居中时视口带 left/top 内缩
     * （横图上下有黑边、竖图左右有黑边），不平移回来选框就会贴在**画布**左上角、
     * 压住黑边、偏离照片——而后续拖拽用的是带偏移的 `frame = viewport`，前后不一致。
     * 所以这里把帧局部结果平移进视口：Free = 视口本身，其它比例 = 视口内的居中奖。
     */
    fun initialSelection(aspect: CropAspect, viewport: Rect): Rect {
        val r = fitAspectRect(aspect, viewport.width, viewport.height)
        if (r == Rect.Zero) return Rect.Zero
        return Rect(
            r.left + viewport.left, r.top + viewport.top,
            r.right + viewport.left, r.bottom + viewport.top,
        )
    }

    /**
     * 拖某个角之后的新选框。
     *
     * 三条不变量（都有测试）：**对角钉住**、**始终落在 [frame] 内**、**短边 ≥ [minSide]**；
     * [aspect] 锁定时再额外保证比例不变（靠等比缩放，所以不破上面三条）。
     *
     * 关键是「拖过对角不翻转」：被拖的边只允许在锚点的**外侧**，越界就停在最小框处，
     * 而不是镜像成一个反向的框——翻转会让归一化出来的窗口左右手颠倒。
     */
    fun resizeForHandle(
        rect: Rect,
        handle: CropHandle,
        touch: Offset,
        frame: Rect,
        minSide: Float,
        aspect: CropAspect,
    ): Rect {
        // 没被拖的那个角是锚点；增长方向永远指向画幅的另一侧
        val growsLeft = handle == CropHandle.TopLeft || handle == CropHandle.BottomLeft
        val growsUp = handle == CropHandle.TopLeft || handle == CropHandle.TopRight
        val anchorX = if (growsLeft) rect.right else rect.left
        val anchorY = if (growsUp) rect.bottom else rect.top
        // 锚点到画幅边缘的可用空间（这就是天然的上界，所以后面不用再夹矩形）
        val availW = if (growsLeft) anchorX - frame.left else frame.right - anchorX
        val availH = if (growsUp) anchorY - frame.top else frame.bottom - anchorY
        if (availW <= 0f || availH <= 0f) return rect

        val tx = touch.x.coerceIn(frame.left, frame.right)
        val ty = touch.y.coerceIn(frame.top, frame.bottom)
        // 想要的尺寸：拖过锚点时取 0（交给最小框夹回来），不做镜像
        var w = (if (growsLeft) anchorX - tx else tx - anchorX).coerceIn(0f, availW)
        var h = (if (growsUp) anchorY - ty else ty - anchorY).coerceIn(0f, availH)

        val ratio = aspect.ratio
        if (ratio == null) {
            w = maxOf(w, min(minSide, availW))
            h = maxOf(h, min(minSide, availH))
        } else {
            // 比例框必须同时不超过两个方向上想要的尺寸 → 取较小的那个，再按比例定另一边
            var bw = w
            var bh = bw / ratio
            if (bh > h) { bh = h; bw = bh * ratio }
            bw = maxOf(bw, min(minSide, availW))
            bh = bw / ratio
            if (bh < min(minSide, availH)) { bh = min(minSide, availH); bw = bh * ratio }
            // 等比缩到可用空间内（比例不变）
            val k = min(min(availW / bw, availH / bh), 1f)
            w = bw * k
            h = bh * k
        }

        val left = if (growsLeft) anchorX - w else anchorX
        val top = if (growsUp) anchorY - h else anchorY
        return Rect(left, top, left + w, top + h)
    }

    /** 视口像素选框 → 视口（= 当前画幅）归一化选框。 */
    fun normalizeSelection(selectionPx: Rect, viewportPx: Rect): Rect {

        val w = viewportPx.width
        val h = viewportPx.height
        if (w <= 0f || h <= 0f) return Rect(0f, 0f, 1f, 1f)
        return Rect(
            ((selectionPx.left - viewportPx.left) / w).coerceIn(0f, 1f),
            ((selectionPx.top - viewportPx.top) / h).coerceIn(0f, 1f),
            ((selectionPx.right - viewportPx.left) / w).coerceIn(0f, 1f),
            ((selectionPx.bottom - viewportPx.top) / h).coerceIn(0f, 1f),
        )
    }

    /**
     * 把「视口内的新选框」合成成一条 [EditOp.Crop]。
     *
     * 视口 = 当前画幅 = 已有裁剪在**全幅显示空间**里的窗口 [window]（[EditCoords.cropWindow]）。
     * 用户在这个窗口里又框了一块，而 [EditOp.Crop] 只有一条、且永远归一化于全幅，
     * 所以这里做窗口复合：`全幅 = window.left + sel.left × window.width`。
     *
     * 这一步归裁剪模式的确认路径管：[PhotoEditor] 拿它的结果直接 [PhotoEdits.setCrop]。
     * `setCrop` 是纯粹替换，它不知道「新框是在哪个视口里框的」——所以复合必须在外面做。
     * 效果是**一步 undo 覆盖掉旧裁剪**，而不是叠两层窗口；没有叠层就没有「撤销只脱一层」的中间态。
     */
    fun composedCrop(window: Rect, selectionNorm: Rect): EditOp.Crop {
        val lo = 0.0001f // EditOp.Crop 要求 left < right，贴边的极细选框要留一条缝
        var l = window.left + selectionNorm.left * window.width
        var t = window.top + selectionNorm.top * window.height
        var r = window.left + selectionNorm.right * window.width
        var b = window.top + selectionNorm.bottom * window.height
        if (r - l < lo) { r = (l + lo).coerceAtMost(1f); l = (r - lo).coerceAtLeast(0f) }
        if (b - t < lo) { b = (t + lo).coerceAtMost(1f); t = (b - lo).coerceAtLeast(0f) }
        return EditOp.Crop(
            l.coerceIn(0f, 1f), t.coerceIn(0f, 1f), r.coerceIn(0f, 1f), b.coerceIn(0f, 1f),
        )
    }
}

/**
 * 把手命中测试：[touch] 落在某个角的 [slopPx] 方块内，**或**到角点的欧氏距离 ≤ [slopPx]，
 * 就算抓住它（方形与圆形取并集，见 [CROP_HANDLE_SLOP_DP] 为什么放大到 32dp）。
 *
 * 热区**不夹在框内**：贴边的角（初始 Free 框 = 铺满 fit 矩形）热区照样向框外延伸，
 * 而框内侧同样是有效命中区——手指粗、角标只有 [CROP_HANDLE_ARM] 那么短，贴边时
 * 「往框里按一点」是最自然的落点，必须抓住。
 *
 * 两个角都命中时取更近的（极小的框里会重叠），返回 null 表示抓的是框内/框外空白——
 * 本任务不做「拖动整框」，所以那种情况下选框不动。
 */
fun hitHandle(rect: Rect, touch: Offset, slopPx: Float): CropHandle? {
    val corners = arrayOf(
        CropHandle.TopLeft to Offset(rect.left, rect.top),
        CropHandle.TopRight to Offset(rect.right, rect.top),
        CropHandle.BottomLeft to Offset(rect.left, rect.bottom),
        CropHandle.BottomRight to Offset(rect.right, rect.bottom),
    )
    var best: CropHandle? = null
    var bestDist = Float.MAX_VALUE
    for ((handle, c) in corners) {
        val dx = abs(touch.x - c.x)
        val dy = abs(touch.y - c.y)
        val d = dx * dx + dy * dy
        val inSquare = dx <= slopPx && dy <= slopPx
        val inCircle = d <= slopPx * slopPx
        if (!inSquare && !inCircle) continue
        if (d < bestDist) {
            bestDist = d
            best = handle
        }
    }
    return best
}

/** 裁剪比例预设。[ratio] 为 null 表示自由（不锁比例）。 */
enum class CropAspect(val ratio: Float?, val labelRes: Int) {
    Free(null, R.string.mediaview_edit_crop_free),
    Square(1f, R.string.mediaview_edit_crop_square),
    FourThree(4f / 3f, R.string.mediaview_edit_crop_four_three),
    SixteenNine(16f / 9f, R.string.mediaview_edit_crop_sixteen_nine),
}

/** 四个角把手。只做角把手，边中点与整框平移不做（把手只有一类，手势层才可推理）。 */
enum class CropHandle { TopLeft, TopRight, BottomLeft, BottomRight }

// ---------------------------------------------------------------------------
// Compose 叠加层
// ---------------------------------------------------------------------------

private val CROP_MASK = Color(0x99000000)
private val CROP_FRAME_LINE = Color.White
private val CROP_GRID = Color.White.copy(alpha = 0.45f)
// 可见角标 18dp、触摸热区 32dp：实机反馈是贴边的角「抓不住」——初始 Free 框铺满 fit 矩形，
// 四个角全在画幅边上，14dp 的角标加 24dp 的方形热区在手指宽度下仍然常常按空。
// 热区半径随 [hitHandle] 一并放宽（方形 ∪ 圆形），可见臂加长到和热区比例更协调。
private val CROP_HANDLE_ARM = 18.dp
private const val CROP_HANDLE_SLOP_DP = 32

/**
 * 裁剪模式的**舞台层**：外遮罩（四块半透明）、当前画幅轮廓、三分线、四角把手，
 * 以及四角把手的拖拽手势。铺满整块预览区，这一层上面不放任何别的控件。
 *
 * 它盖在预览之上，自己**不改 edits**：选框以视口像素交给宿主，宿主确认时经
 * [CropGeometry.composedCrop] 合成成 [EditOp.Crop]。
 *
 * **为什么比例预设和确认/取消不在这一层里**（评审 Important 1）：Box 里后声明的子项先吃到
 * 命中。原先那两行就在本层这个 Box 内，于是它们的高度带内永远轮不到 [detectDragGestures]；
 * 而初始的 Free 框正好铺满视口，上下边与那两行齐平——右下把手几乎压在确认按钮上、
 * 左下压在取消上、左上压在「自由」预设上，拖底角变成「确认/取消裁剪」而不是缩放。
 * 现在控件挪到预览区**之外**（[CropControls]）：两边不共享一个像素，而不是去抢同一块。
 *
 * 挪出去还顺便保住了坐标系：本层的矩形与视口同在预览区坐标里，归一化的分母始终是
 * **完整显示帧**（0..1 = 全帧），没有内缩矩形、没有新增换算。
 *
 * @param viewport 图片（= 当前画幅）在本层里的像素矩形。所有夹取都相对它，
 *   归一化也以它为基准——不然选框会跟实际画面错位（预览是 Fit 摆放的，四周可能有黑边）。
 * @param aspect 当前比例（选择权在宿主，见 [CropControls]）；换比例时选框按新比例重铺。
 * @param selection 当前选框（预览区像素矩形）。由宿主持有，因为 [CropControls] 的确认按钮
 *   也要读它；[Rect.Zero] 表示还没铺过。
 */
@Composable
fun CropMode(
    viewport: Rect,
    aspect: CropAspect,
    selection: Rect,
    onSelectionChange: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slopPx = with(LocalDensity.current) { CROP_HANDLE_SLOP_DP.dp.toPx() }
    val minSide = CropGeometry.minCropSide(viewport.width, viewport.height)
    // 选框现在是宿主传进来的普通参数：pointerInput 的闭包在整次拖拽里只有一份，
    // 直接读参数就会停在按下那一刻的值（每步都从旧框重算 → 框抖回原处、拖不动）。
    // rememberUpdatedState 让闭包每次都读到最新值。
    val currentSelection by rememberUpdatedState(selection)

    // 拿到尺寸、或尺寸/比例变了，就按当前比例重铺居中奖。
    // 写在 LaunchedEffect 而不是组合期：组合期回写宿主的 state 会一直重组下去。
    // 换比例时丢掉手调的框是刻意的：保锚点重算很容易在边界上算出一个贴边的框，
    // 而「点了比例、框变成该比例的最大居中奖」是用户一看就懂的行为。
    LaunchedEffect(viewport, aspect) {
        if (viewport.width > 0f && viewport.height > 0f) {
            onSelectionChange(CropGeometry.initialSelection(aspect, viewport))
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .pointerInput(viewport, minSide, slopPx, aspect) {
                var grabbed: CropHandle? = null
                detectDragGestures(
                    onDragStart = { p -> grabbed = hitHandle(currentSelection, p, slopPx) },
                    onDrag = { change, _ ->
                        val h = grabbed ?: return@detectDragGestures
                        change.consume()
                        onSelectionChange(
                            CropGeometry.resizeForHandle(
                                rect = currentSelection,
                                handle = h,
                                touch = change.position,
                                frame = viewport,
                                minSide = minSide,
                                aspect = aspect,
                            ),
                        )
                    },
                    onDragEnd = { grabbed = null },
                    onDragCancel = { grabbed = null },
                )
            },
    ) {
        val r = selection
        val whole = Size(size.width, size.height)
        if (r.width > 0f && r.height > 0f) {
            // 外遮罩：选框四周各一块（比整屏半透明再挖洞省一次 saveLayer）
            if (r.left > 0f) drawRect(CROP_MASK, Offset.Zero, Size(r.left, whole.height))
            if (r.right < whole.width) drawRect(CROP_MASK, Offset(r.right, 0f), Size(whole.width - r.right, whole.height))
            if (r.top > 0f) drawRect(CROP_MASK, Offset(r.left, 0f), Size(r.width, r.top))
            if (r.bottom < whole.height) drawRect(CROP_MASK, Offset(r.left, r.bottom), Size(r.width, whole.height - r.bottom))

            // 当前画幅的轮廓（黑边里的部分也要看得见边界）
            if (viewport.width > 0f) {
                drawRect(
                    CROP_FRAME_LINE.copy(alpha = 0.35f), Offset(viewport.left, viewport.top), Size(viewport.width, viewport.height),
                    style = Stroke(width = 1f.dp.toPx()),
                )
            }
            drawRect(
                CROP_FRAME_LINE, Offset(r.left, r.top), Size(r.width, r.height),
                style = Stroke(width = 2f.dp.toPx()),
            )
            // 三分线
            val grid = 1f.dp.toPx()
            for (i in 1..2) {
                val x = r.left + r.width * i / 3f
                val y = r.top + r.height * i / 3f
                drawLine(CROP_GRID, Offset(x, r.top), Offset(x, r.bottom), strokeWidth = grid)
                drawLine(CROP_GRID, Offset(r.left, y), Offset(r.right, y), strokeWidth = grid)
            }
            // 角把手：从角点往框内伸的两条短边
            val arm = CROP_HANDLE_ARM.toPx()
            val thick = 3f.dp.toPx()
            val corners = listOf(
                Offset(r.left, r.top) to listOf(Offset(arm, 0f), Offset(0f, arm)),
                Offset(r.right, r.top) to listOf(Offset(-arm, 0f), Offset(0f, arm)),
                Offset(r.left, r.bottom) to listOf(Offset(arm, 0f), Offset(0f, -arm)),
                Offset(r.right, r.bottom) to listOf(Offset(-arm, 0f), Offset(0f, -arm)),
            )
            corners.forEach { (corner, arms) ->
                arms.forEach { drawLine(CROP_FRAME_LINE, corner, corner + it, strokeWidth = thick) }
            }
        }
    }
}

/**
 * 裁剪模式的控件行：比例预设 + 取消/提示/确认。
 *
 * 宿主把它放在预览区**之外**（编辑器里占的是原来标注面板那个槽位），于是它和 [CropMode]
 * 的手势层不共享一个像素；塞进同一个 Box 就会吃掉把手的命中，见 [CropMode] 的注释。
 *
 * @param selection 舞台层当前的选框（[viewport] 所在坐标系的像素矩形）
 * @param viewport 与 [CropMode] 同一个视口矩形；确认时用它把选框归一化成分母为全帧的 0..1
 * @param onConfirm 收到视口归一化选框（0..1）
 */
@Composable
fun CropControls(
    selection: Rect,
    viewport: Rect,
    aspect: CropAspect,
    onAspectChange: (CropAspect) -> Unit,
    onConfirm: (Rect) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(Color(0xFF111111))) {
        // 比例预设
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CropAspect.values().forEach { a ->
                val on = aspect == a
                TextButton(onClick = { onAspectChange(a) }) {
                    Text(
                        stringResource(a.labelRes),
                        color = if (on) Color(0xFFFFCC00) else Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        // 取消 / 提示 / 确认
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, stringResource(R.string.mediaview_edit_cancel), tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Text(
                stringResource(R.string.mediaview_edit_crop_hint),
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
            )
            IconButton(onClick = {
                onConfirm(CropGeometry.normalizeSelection(selection, viewport))
            }, enabled = viewport.width > 0f && viewport.height > 0f) {
                // 视口还没量出来（首帧）时归一化的分母是 0，确认会当成「整幅都留」写一条无意义的
                // Crop，所以直接不让点。
                Icon(Icons.Filled.Check, stringResource(R.string.mediaview_edit_crop_done), tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}
