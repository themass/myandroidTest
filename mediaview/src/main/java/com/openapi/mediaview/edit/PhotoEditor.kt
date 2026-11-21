package com.openapi.mediaview.edit

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.openapi.mediaview.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 图片本地编辑器（Compose，无第三方依赖）。
 *
 * 交互：
 * - 工具条：旋转 / 镜像 / 涂鸦 / 文字 / 马赛克 / 调整；
 * - **涂鸦、马赛克**：手指划过的地方；颜色、粗细在下方面板里调；
 * - **文字**：点图片添加；点已有文字选中，**拖动移动位置**，面板里改颜色、字号、粗细，或点「修改文字」；
 * - **任何已画的标注**（涂鸦/文字/马赛克）：点一下选中（虚线框），可以删除、拖动（文字和涂鸦）；
 * - **调整**：亮度/对比度/饱和度，点「完成调整」收起；
 * - 撤销、重做、还原；确认时由宿主调用 [PhotoEditExporter.export] 落盘。
 *
 * 预览通过 [PhotoRenderer] 渲染（缩小的预览图），所以马赛克和调整在编辑时就是真实效果，
 * 保存出来和看到的一致。组件只负责「编辑会话」，不知道图片将被发送到哪。
 *
 * ```kotlin
 * PhotoEditor(bitmap = bitmap, onCancel = { dismiss() }, onDone = { edits -> exportAndSend(edits) })
 * ```
 */
@Composable
fun PhotoEditor(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onDone: (PhotoEdits) -> Unit,
    modifier: Modifier = Modifier,
    /** false 时工具条里不出现「裁剪」入口（宿主有些场景不允许改画幅，比如已经裁过）。 */
    allowCrop: Boolean = true,
) {
    var edits by remember(bitmap) { mutableStateOf(PhotoEdits.of(bitmap.width, bitmap.height)) }
    var tool by remember { mutableStateOf(EditorTool.None) }
    var selected by remember { mutableStateOf<Int?>(null) }
    // 裁剪比例的选择跨进出保留（选框本身按视口/比例重铺，见 CropMode）
    var cropAspect by remember { mutableStateOf(CropAspect.Free) }
    // 裁剪选框（预览区像素）：舞台层写、控件层读，所以只能放在两者之上
    var cropSelection by remember { mutableStateOf(Rect.Zero) }

    // 新画的下一笔默认样式（选中某条标注后，面板改的是那条标注自己的样式）
    var brushColor by remember { mutableStateOf(PALETTE[0]) }
    var brushWidth by remember { mutableStateOf(DEFAULT_BRUSH) }
    var mosaicWidth by remember { mutableStateOf(DEFAULT_MOSAIC) }
    var textColor by remember { mutableStateOf(PALETTE[0]) }
    var textSize by remember { mutableStateOf(DEFAULT_TEXT) }
    var textBold by remember { mutableStateOf(true) }

    // 正在画的一笔（原图归一化坐标）
    var drawing by remember { mutableStateOf<List<Offset>>(emptyList()) }
    // 正在拖动的已有标注：下标 + 拖动前的样子 + 起点
    var moving by remember { mutableStateOf<Moving?>(null) }
    // 滑杆拖动期间的「起点快照」：松手时一次性记成一步撤销
    var liveBase by remember { mutableStateOf<PhotoEdits?>(null) }
    // 有滑杆正在被拖：底图降到 640 长边（松手回 1280）。和 liveBase 一起置位/复位，
    // 但它是独立状态——降档只影响渲染边长，不该混进「撤销锚点」的语义里。
    var sliderActive by remember { mutableStateOf(false) }
    // 按住「原图」对比：临时把滤镜从**预览底图**里摘掉，不碰 edits 的历史（松手还原）
    var comparing by remember { mutableStateOf(false) }
    var textDialog by remember { mutableStateOf<TextDialog?>(null) }

    // 选中的下标可能因撤销/删除而失效，用的时候校验
    val selectedIndex = selected?.takeIf { it in edits.ops.indices && edits.ops[it].isAnnotation() }
    val selectedOp = selectedIndex?.let { edits.ops[it] }

    // 预览用缩小的图：渲染快，且标注坐标是归一化的，与原图等价
    val previewSource = remember(bitmap) { makePreviewSource(bitmap) }

    // 滤镜表：同一个 id 只 parse 一次（LutAssets 内部记忆化），所以这里只为稳定 lambda 身份。
    val context = LocalContext.current
    val lutProvider = remember(context) { LutAssets.provider(context) }
    // 底图缓存**必须是会话级实例**（RenderBaseCache 的 KDoc 写死了原因：identityHashCode 会被
    // GC 后复用，跨会话共享就可能把上一张照片的底图当成本张的返回）。remember(bitmap) 正好
    // 和编辑会话同生共死：退出编辑（组件销毁）就整体作废。
    val baseCache = remember(bitmap) { RenderBaseCache() }

    // 拖动某条标注时，底图先不画它（改用上层实时叠加），拖完再一次性提交。
    // 「对比」期间再摘掉 Filter——注意必须在**按 index 丢掉拖动那条之后**再 filter，
    // 否则丢掉一个 Filter 会让后面所有标注的下标整体前移，选中的就不是拖的那条了。
    val moveIndex = moving?.index
    val baseOps = remember(edits.ops, moveIndex, comparing) {
        val kept = if (moveIndex == null) edits.ops else edits.ops.filterIndexed { i, _ -> i != moveIndex }
        if (comparing) kept.filterNot { it is EditOp.Filter } else kept
    }
    // 拖动滑杆时降档：滤镜是整图三线性插值，1280 那一档在拖动手势里最容易被肉眼看成掉帧
    val previewLongEdge = if (sliderActive) PREVIEW_SLIDER_LONG_EDGE else PREVIEW_LONG_EDGE
    val rendered = remember(previewSource, baseOps, previewLongEdge, lutProvider) {
        renderPreview(previewSource, baseOps, previewLongEdge, baseCache, lutProvider)
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(
                edits = edits,
                onCancel = onCancel,
                onUndo = { edits = edits.undo(); selected = null },
                onRedo = { edits = edits.redo(); selected = null },
                onReset = { edits = edits.reset(); selected = null },
                onDone = { onDone(edits) },
            )

            val cropping = tool == EditorTool.Crop

            // 面板随时可能被换掉（选中某条标注抢面板、切工具、进裁剪），而滑杆可能正拖在半路：
            // 那种情况下 onValueChangeFinished 永远不会来。不清这几个标志，预览就一直卡在 640 降档、
            // 「原图」按住状态回不来，下一次滑杆还会拿过期的撤销锚点。
            // 顺序与下面真正决定面板的 when 一致：裁剪 → 选中 → 工具
            val panelSlot = when {
                cropping -> "crop"
                selectedIndex != null -> "selected-$selectedIndex"
                else -> "tool-$tool"
            }
            LaunchedEffect(panelSlot) {
                liveBase = null
                sliderActive = false
                comparing = false
            }

            // 裁剪的控件在预览区**外面**（面板槽），但它得用**同一个**视口矩形做归一化，
            // 所以 fit 在这里公布出去，舞台层和控件层读的是同一个值——不是各算一遍。
            // 归一化的分母因此始终是完整显示帧（0..1 = 全帧），没有第二种坐标系。
            var cropViewport by remember { mutableStateOf(Rect.Zero) }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // rendered 已经是裁剪后的位图（PhotoRenderer.applyCrop），所以 fit 自动就是
                // 「当前画幅」在容器里的位置：裁完之后预览自己变小、黑边自己出现，这里不用特判。
                val fit = fitRect(rendered.width, rendered.height, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                LaunchedEffect(fit) { cropViewport = fit }
                val imageBitmap = remember(rendered) { rendered.asImageBitmap() }
                val textMeasurer = rememberTextMeasurer()
                val density = androidx.compose.ui.platform.LocalDensity.current

                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )

                // 裁剪模式下标注的点选/拖动/实时叠加整层让位：那块手势区会把把手的拖拽吃掉。
                if (!cropping) {
                    // 点按：选中 / 取消选中 / 添加文字。与下面的拖动是两个独立的手势层。
                    // 点击不会被拖动层消耗（拖动要先越过 touch slop 才会接管）。
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(tool, fit) {
                                detectTapGestures { p ->
                                    val display = toDisplayNorm(p, fit) ?: return@detectTapGestures
                                    val hit = EditHitTest.findOpAt(edits, display)
                                    when {
                                        hit != null -> selected = hit
                                        tool == EditorTool.Text -> {
                                            // 锚点是文字块左上角：点哪里，文字就出现在哪里
                                            val src = EditCoords.toSourceNorm(display, edits)
                                            textDialog = TextDialog(index = null, value = "", at = src)
                                            selected = null
                                        }
                                        else -> selected = null
                                    }
                                }
                            }
                            .pointerInput(tool, fit) {
                                detectDragGestures(
                                    onDragStart = { p ->
                                        val display = toDisplayNorm(p, fit, clamp = true) ?: return@detectDragGestures
                                        val src = EditCoords.toSourceNorm(display, edits)
                                        when (tool) {
                                            EditorTool.Draw, EditorTool.Mosaic -> {
                                                selected = null
                                                drawing = listOf(src)
                                            }
                                            else -> {
                                                // 其它工具下按住已有的文字/涂鸦就拖动它（马赛克依赖底图，不支持拖动）
                                                val hit = EditHitTest.findOpAt(edits, display)
                                                val op = hit?.let { edits.ops[it] }
                                                if (hit != null && (op is EditOp.Text || op is EditOp.Stroke)) {
                                                    selected = hit
                                                    moving = Moving(hit, op, src, op)
                                                }
                                            }
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val display = toDisplayNorm(change.position, fit, clamp = true) ?: return@detectDragGestures
                                        val src = EditCoords.toSourceNorm(display, edits)
                                        val m = moving
                                        if (m != null) {
                                            moving = m.copy(current = m.original.translated(src.x - m.start.x, src.y - m.start.y))
                                        } else if (drawing.isNotEmpty()) {
                                            drawing = drawing + src
                                        }
                                    },
                                    onDragEnd = {
                                        val m = moving
                                        if (m != null) {
                                            edits = edits.replaceAt(m.index, m.current)
                                            moving = null
                                        } else if (drawing.size >= 2) {
                                            val op: EditOp = when (tool) {
                                                EditorTool.Mosaic -> EditOp.Mosaic(drawing, mosaicWidth)
                                                else -> EditOp.Stroke(drawing, brushColor.toArgb().toLong(), brushWidth)
                                            }
                                            edits = edits.push(op)
                                        }
                                        drawing = emptyList()
                                    },
                                    onDragCancel = { moving = null; drawing = emptyList() },
                                )
                            },
                    ) {
                        // 正在拖动的那一条：实时叠加
                        moving?.let { drawLiveOp(it.current, edits, fit, textMeasurer, density) }
                        // 正在画的一笔
                        if (drawing.size >= 2) {
                            val live = if (tool == EditorTool.Mosaic) {
                                EditOp.Mosaic(drawing, mosaicWidth)
                            } else {
                                EditOp.Stroke(drawing, brushColor.toArgb().toLong(), brushWidth)
                            }
                            drawLiveOp(live, edits, fit, textMeasurer, density)
                        }
                        // 选中框
                        val box = (moving?.current ?: selectedOp)?.let { EditHitTest.selectionBounds(it, editsWithoutOps(edits)) }
                        if (box != null) drawSelectionBox(box, fit)
                    }
                }

                if (cropping) {
                    // 这一层是**纯舞台**：比例预设与确认/取消在它之外（下面的面板槽），
                    // 否则那些控件会吃掉角把手的命中（见 CropMode 的注释）。
                    // 视口用公布出去的 cropViewport 而不是这里的 fit：两层必须永远拿到同一个值，
                    // 否则裁剪中途预览尺寸变了（TopBar 撤销还活着）就会有一帧用错分母。
                    CropMode(
                        viewport = cropViewport,
                        aspect = cropAspect,
                        selection = cropSelection,
                        onSelectionChange = { cropSelection = it },
                    )
                }
            }

            // 面板：裁剪模式 → 裁剪控件（预设 + 确认/取消，见 CropControls）；
            // 选中了标注 → 编辑那条；否则按当前工具给默认样式。
            val index = selectedIndex
            val op = selectedOp
            when {
                cropping -> {
                    CropControls(
                        selection = cropSelection,
                        viewport = cropViewport,
                        aspect = cropAspect,
                        onAspectChange = { cropAspect = it },
                        onConfirm = { sel ->
                            // 已有 Crop 时视口就是那个窗口，选框要先合成回全幅显示空间，
                            // setCrop 才有「替换旧裁剪」的语义（见 CropGeometry.composedCrop）。
                            edits = edits.setCrop(CropGeometry.composedCrop(EditCoords.cropWindow(edits), sel))
                            tool = EditorTool.None
                            cropSelection = Rect.Zero
                        },
                        onCancel = {
                            tool = EditorTool.None
                            // 选框 hoist 到宿主之后，「下次进裁剪模式要重新铺一屏」这件事
                            // 不能只靠 LaunchedEffect(viewport, aspect)：取消不改 edits，
                            // 视口和比例都没变，就不会重铺，上次的框会莫名其妙留着。
                            // 确认路径也一样清：视口**通常**会因裁剪而变（从而自己重铺），
                            // 但那只是巧合（正方形画面裁掉一半，fit 可能一模一样），不该依赖巧合。
                            cropSelection = Rect.Zero
                        },
                    )
                }
                op != null -> SelectionPanel(
                    op = op,
                    onDelete = { edits = edits.removeAt(index); selected = null },
                    onDeselect = { selected = null },
                    onEditText = { t -> textDialog = TextDialog(index, t.value, Offset(t.x, t.y)) },
                    onColor = { c -> edits = edits.replaceAt(index, op.withColor(c.toArgb().toLong())) },
                    onBold = { edits = edits.replaceAt(index, (op as EditOp.Text).let { it.copy(bold = !it.bold) }) },
                    onSlider = { v ->
                        // 拖动过程中只改预览（不记历史），松手一次提交
                        val base = liveBase ?: edits.also { liveBase = it }
                        edits = base.withOpsUnrecorded(base.ops.toMutableList().also { it[index] = op.withSize(v) })
                    },
                    onSliderDone = {
                        liveBase?.let { base -> edits = base.withOps(edits.ops) }
                        liveBase = null
                    },
                )
                tool == EditorTool.Adjust -> AdjustPanel(
                    adjust = edits.adjust,
                    onChange = { adj ->
                        val base = liveBase ?: edits.also { liveBase = it }
                        // 调整参数在预处理指纹里，每一步都会让底图缓存 miss 并重跑预处理，
                        // 所以它和滤镜滑杆一样要降档（见 PREVIEW_SLIDER_LONG_EDGE）
                        sliderActive = true
                        val next = base.ops.filterNot { it is EditOp.Adjust } + (if (adj.isIdentity) emptyList() else listOf(adj))
                        edits = base.withOpsUnrecorded(next)
                    },
                    onFinished = {
                        liveBase?.let { base -> edits = base.withOps(edits.ops) }
                        liveBase = null
                        sliderActive = false
                    },
                    onClose = { tool = EditorTool.None },
                )
                tool == EditorTool.Filter -> FilterPanel(
                    filter = edits.filter,
                    source = previewSource,
                    lutProvider = lutProvider,
                    onPickPreset = { id ->
                        // 再点已选中的那一格 = 移除（回到无滤镜）；换一格 = 替换（面板互斥，全列表至多一条）
                        edits = if (edits.filter?.id == id) edits.setFilter(null)
                        else edits.setFilter(id, FILTER_PRESET_INTENSITY)
                    },
                    onIntensityChange = { v ->
                        // 拖动过程中只改预览（不记历史），并降档到底图 640
                        val base = liveBase ?: edits.also { liveBase = it }
                        sliderActive = true
                        val i = base.ops.indexOfLast { it is EditOp.Filter }
                        val current = base.ops.getOrNull(i) as? EditOp.Filter
                        if (current != null) {
                            edits = base.withOpsUnrecorded(base.ops.toMutableList().also { it[i] = current.copy(intensity = v) })
                        }
                    },
                    onIntensityDone = {
                        // 以拖动前的快照为底一次提交：恰一步撤销，回到拖动之前的强度
                        liveBase?.let { base -> edits = base.withOps(edits.ops) }
                        liveBase = null
                        sliderActive = false
                    },
                    // 按住=临时把滤镜从预览底图里摘掉（只影响 baseOps，不进 ops、不进历史）
                    onCompare = { down -> comparing = down },
                    onClose = { tool = EditorTool.None },
                )
                tool == EditorTool.Draw -> StylePanel(
                    hint = stringResource(R.string.mediaview_edit_hint_draw),
                    color = brushColor, onColor = { brushColor = it },
                    sliderLabel = stringResource(R.string.mediaview_edit_thickness),
                    slider = brushWidth, range = BRUSH_RANGE, onSlider = { brushWidth = it },
                )
                tool == EditorTool.Text -> StylePanel(
                    hint = stringResource(R.string.mediaview_edit_hint_text),
                    color = textColor, onColor = { textColor = it },
                    sliderLabel = stringResource(R.string.mediaview_edit_size),
                    slider = textSize, range = TEXT_RANGE, onSlider = { textSize = it },
                    bold = textBold, onBold = { textBold = !textBold },
                )
                tool == EditorTool.Mosaic -> StylePanel(
                    hint = stringResource(R.string.mediaview_edit_hint_mosaic),
                    color = null, onColor = {},
                    sliderLabel = stringResource(R.string.mediaview_edit_brush),
                    slider = mosaicWidth, range = MOSAIC_RANGE, onSlider = { mosaicWidth = it },
                )
            }

            ToolBar(
                tool = tool,
                allowCrop = allowCrop,
                // 裁后禁旋转：Crop 归一化于「旋转之后」的画幅，再旋转就得重算窗口，
                // 那是另一个复杂度的事（设计裁定：先把入口灰掉，不给用户造一个错的窗口）。
                rotateEnabled = edits.crop == null,
                flipEnabled = edits.crop == null,
                onRotate = { edits = edits.rotateClockwise(); selected = null },
                onFlip = { edits = edits.flipHorizontal(); selected = null },
                onSelect = { t -> tool = if (tool == t) EditorTool.None else t; selected = null },
            )
        }
    }

    textDialog?.let { dialog ->
        TextEditDialog(
            initial = dialog.value,
            onDismiss = { textDialog = null },
            onConfirm = { text ->
                if (text.isNotBlank()) {
                    val existing = dialog.index?.let { edits.ops.getOrNull(it) as? EditOp.Text }
                    if (existing != null) {
                        edits = edits.replaceAt(dialog.index, existing.copy(value = text))
                    } else {
                        val at = dialog.at
                        val op = EditOp.Text(
                            value = text,
                            x = at.x.coerceIn(0f, 0.95f), y = at.y.coerceIn(0f, 0.95f),
                            colorArgb = textColor.toArgb().toLong(), sizeRatio = textSize, bold = textBold,
                        )
                        edits = edits.push(op)
                        selected = edits.ops.lastIndex
                    }
                }
                textDialog = null
            },
        )
    }
}

// ---------------------------------------------------------------------------
// 状态与常量
// ---------------------------------------------------------------------------

/** [Crop] 是「模式」而不是「样式」：进去之后整块预览区归裁剪层，见 [CropMode]。 */
private enum class EditorTool { None, Draw, Text, Mosaic, Adjust, Filter, Crop }

private data class Moving(val index: Int, val original: EditOp, val start: Offset, val current: EditOp)

private data class TextDialog(val index: Int?, val value: String, val at: Offset)

private val PALETTE = listOf(
    Color(0xFFFF3B30), Color(0xFFFFCC00), Color(0xFF34C759),
    Color(0xFF0A84FF), Color.White, Color.Black,
)
private const val DEFAULT_BRUSH = 0.008f
private const val DEFAULT_MOSAIC = 0.06f
private const val DEFAULT_TEXT = 0.05f
private val BRUSH_RANGE = 0.003f..0.03f
private val MOSAIC_RANGE = 0.02f..0.15f
private val TEXT_RANGE = 0.025f..0.12f

/** 编辑时用的预览图长边。够清晰、渲染快（马赛克/调整每次都要重放）。 */
private const val PREVIEW_LONG_EDGE = 1280

/**
 * 滑杆拖动期间的底图长边。
 *
 * 拖强度/亮度滑杆时每帧都要重跑预处理，而滤镜是整图逐像素三线性插值——1280 那一档
 * 在连续手势里最容易被肉眼读成「卡」。降到 640 像素量级约省 4 倍插值，松手立刻回 1280 重渲一帧，
 * 用户看到的是「拖动时略糊、松手变清」，这比「拖动时掉帧」划算。
 */
private const val PREVIEW_SLIDER_LONG_EDGE = 640

private fun EditOp.isAnnotation(): Boolean = this is EditOp.Stroke || this is EditOp.Text || this is EditOp.Mosaic

/**
 * 预览 = 底图（带缓存）+ 标注。
 *
 * 命中缓存时**只跳过预处理**，标注照样走 [PhotoRenderer.drawOps]（它在副本上画，
 * 不会污染缓存里那一帧）——绝不允许把缓存的底图直接当结果画出去，那样标注会整层消失。
 *
 * 无预处理 ops 时缓存恒 miss 是预期（指纹里没有标注），不是 bug，别为它加特例。
 */
private fun renderPreview(
    source: Bitmap,
    ops: List<EditOp>,
    maxLongEdge: Int,
    cache: RenderBaseCache,
    lutProvider: (String) -> Lut3D?,
): Bitmap {
    // 完全没编辑时直接返回源图：和 PhotoRenderer.render 的旧快捷路径一致。
    // 少了这一句，空白编辑会话的每帧预览都会白白拷一次整幅位图（drawOps 恒复制）。
    if (ops.isEmpty()) return source
    // 只为拿一个「不带标注」的会话算指纹；PhotoEdits 是 data class，构造很便宜
    val probe = PhotoEdits.of(source.width, source.height).withOpsUnrecorded(ops)
    val hit = cache.get(source, probe, maxLongEdge)
    if (hit != null) return PhotoRenderer.drawOps(hit, probe)
    val base = try {
        PhotoRenderer.renderBase(source, probe, maxLongEdge, lutProvider)
    } catch (e: OutOfMemoryError) {
        // 兜底语义与 PhotoRenderer.render 一致：预览必须有图，宁可退回原图
        Log.e("PhotoEditor", "preview renderBase out of memory", e)
        return source
    } catch (e: RuntimeException) {
        Log.e("PhotoEditor", "preview renderBase failed", e)
        return source
    }
    cache.put(source, probe, maxLongEdge, base)
    return PhotoRenderer.drawOps(base, probe)
}

/** 等比缩到长边 [longEdge]；本来就不超过就原样返回（不白拷一份）。 */
internal fun scaleToLongEdge(source: Bitmap, longEdge: Int): Bitmap {
    val srcLongEdge = max(source.width, source.height)
    if (srcLongEdge <= longEdge) return source
    val ratio = longEdge.toFloat() / srcLongEdge
    return Bitmap.createScaledBitmap(
        source,
        (source.width * ratio).roundToInt().coerceAtLeast(1),
        (source.height * ratio).roundToInt().coerceAtLeast(1),
        true,
    )
}

private fun makePreviewSource(source: Bitmap): Bitmap = scaleToLongEdge(source, PREVIEW_LONG_EDGE)

/**
 * 选中框只依赖画幅（旋转 / 镜像 / 裁剪），取一份不带标注的会话即可，避免把整个历史传来传去。
 *
 * **Crop 必须保留**：[EditHitTest.selectionBounds] 的分母是 `displayWidth/Height`，
 * 而它已经计入裁剪窗口。少了 Crop 就会拿整幅的尺寸去解释裁剪帧里的坐标，
 * 于是裁剪之后选中框整体错位、缩放不对。
 *
 * 滤镜/调整也丢掉：它们只改像素，不改几何。
 */
internal fun editsWithoutOps(edits: PhotoEdits): PhotoEdits =
    edits.withOpsUnrecorded(
        edits.ops.filter { it is EditOp.Rotate || it == EditOp.FlipHorizontal || it is EditOp.Crop },
    )

private fun EditOp.withColor(argb: Long): EditOp = when (this) {
    is EditOp.Stroke -> copy(colorArgb = argb)
    is EditOp.Text -> copy(colorArgb = argb)
    else -> this
}

/** 面板上那根滑杆对应的数值：涂鸦/马赛克是笔宽，文字是字号。 */
private fun EditOp.withSize(v: Float): EditOp = when (this) {
    is EditOp.Stroke -> copy(widthRatio = v)
    is EditOp.Mosaic -> copy(widthRatio = v)
    is EditOp.Text -> copy(sizeRatio = v)
    else -> this
}

private fun EditOp.sizeValue(): Float = when (this) {
    is EditOp.Stroke -> widthRatio
    is EditOp.Mosaic -> widthRatio
    is EditOp.Text -> sizeRatio
    else -> 0f
}

/** 图片按 Fit 放进容器后的矩形。 */
private fun fitRect(srcW: Int, srcH: Int, boxW: Float, boxH: Float): Rect {
    if (srcW <= 0 || srcH <= 0 || boxW <= 0f || boxH <= 0f) return Rect(0f, 0f, boxW, boxH)
    val ratio = srcW.toFloat() / srcH
    val (w, h) = if (boxW / boxH > ratio) boxH * ratio to boxH else boxW to boxW / ratio
    val left = (boxW - w) / 2f
    val top = (boxH - h) / 2f
    return Rect(left, top, left + w, top + h)
}

/** 屏幕坐标 → 显示归一化坐标。落在图片外：[clamp] 为 false 返回 null（点黑边不算），true 则夹到边界（拖出去仍继续）。 */
private fun toDisplayNorm(p: Offset, fit: Rect, clamp: Boolean = false): Offset? {
    if (!clamp && !fit.contains(p)) return null
    return Offset(
        ((p.x - fit.left) / fit.width).coerceIn(0f, 1f),
        ((p.y - fit.top) / fit.height).coerceIn(0f, 1f),
    )
}

// ---------------------------------------------------------------------------
// 画布叠加层
// ---------------------------------------------------------------------------

private fun DrawScope.drawLiveOp(
    op: EditOp,
    edits: PhotoEdits,
    fit: Rect,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density,
) {
    val shortest = min(fit.width, fit.height)
    fun px(p: Offset): Offset {
        val d = EditCoords.toDisplayNorm(p, edits)
        return Offset(fit.left + d.x * fit.width, fit.top + d.y * fit.height)
    }
    when (op) {
        is EditOp.Stroke, is EditOp.Mosaic -> {
            val points = if (op is EditOp.Stroke) op.points else (op as EditOp.Mosaic).points
            val width = (if (op is EditOp.Stroke) op.widthRatio else (op as EditOp.Mosaic).widthRatio) * shortest
            val color = if (op is EditOp.Stroke) Color(op.colorArgb.toInt()) else Color.White.copy(alpha = 0.45f)
            val path = Path()
            points.forEachIndexed { i, p -> px(p).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
            drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        is EditOp.Text -> {
            val anchor = px(Offset(op.x, op.y))
            val fontPx = op.sizeRatio * shortest
            val style = TextStyle(
                color = Color(op.colorArgb.toInt()),
                fontSize = with(density) { fontPx.toSp() },
                fontWeight = if (op.bold) FontWeight.Bold else FontWeight.Normal,
            )
            op.value.split('\n').forEachIndexed { i, line ->
                drawText(textMeasurer, line, topLeft = Offset(anchor.x, anchor.y + i * fontPx * 1.2f), style = style)
            }
        }
        else -> Unit
    }
}

private fun DrawScope.drawSelectionBox(box: Rect, fit: Rect) {
    val tl = Offset(fit.left + box.left * fit.width, fit.top + box.top * fit.height)
    val size = Size(box.width * fit.width, box.height * fit.height)
    drawRect(
        Color(0xFFFFCC00), tl, size,
        style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
    )
}

// ---------------------------------------------------------------------------
// 面板
// ---------------------------------------------------------------------------

@Composable
private fun TopBar(
    edits: PhotoEdits,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, stringResource(R.string.mediaview_edit_cancel), tint = Color.White)
        }
        Text(
            stringResource(R.string.mediaview_edit_title),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        BarIcon(Icons.AutoMirrored.Filled.Undo, R.string.mediaview_edit_undo, edits.canUndo, onUndo)
        BarIcon(Icons.AutoMirrored.Filled.Redo, R.string.mediaview_edit_redo, edits.canRedo, onRedo)
        BarIcon(Icons.Filled.RestartAlt, R.string.mediaview_edit_reset, edits.isDirty, onReset)
        IconButton(onClick = onDone) {
            Icon(Icons.Filled.Check, stringResource(R.string.mediaview_edit_done), tint = Color.White)
        }
    }
}

@Composable
private fun BarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, stringResource(label), tint = if (enabled) Color.White else Color.Gray)
    }
}

@Composable
private fun ToolBar(
    tool: EditorTool,
    allowCrop: Boolean,
    rotateEnabled: Boolean,
    flipEnabled: Boolean,
    onRotate: () -> Unit,
    onFlip: () -> Unit,
    onSelect: (EditorTool) -> Unit,
) {
    // 每项 weight(1f) 等分，而不是 SpaceEvenly：8 项在 360dp 宽的机器上是 8×48dp=384dp，
    // SpaceEvenly 只会在算出负间距后退成 0 间距，于是最后一项被挤出可视区（点不到也看不见）。
    // 等分保证任何宽度下八项都在，宽屏上的观感与原来的 SpaceEvenly 同节奏。
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val slot = Modifier.weight(1f)
        ToolItem(slot, Icons.Filled.Rotate90DegreesCcw, R.string.mediaview_edit_rotate, false, enabled = rotateEnabled, onClick = onRotate)
        ToolItem(slot, Icons.Filled.Flip, R.string.mediaview_edit_flip, false, enabled = flipEnabled, onClick = onFlip)
        if (allowCrop) {
            ToolItem(slot, Icons.Filled.Crop, R.string.mediaview_edit_crop, tool == EditorTool.Crop) { onSelect(EditorTool.Crop) }
        }
        ToolItem(slot, Icons.Filled.Brush, R.string.mediaview_edit_draw, tool == EditorTool.Draw) { onSelect(EditorTool.Draw) }
        ToolItem(slot, Icons.Filled.TextFields, R.string.mediaview_edit_text, tool == EditorTool.Text) { onSelect(EditorTool.Text) }
        ToolItem(slot, Icons.Filled.BlurOn, R.string.mediaview_edit_mosaic, tool == EditorTool.Mosaic) { onSelect(EditorTool.Mosaic) }
        ToolItem(slot, Icons.Filled.AutoFixHigh, R.string.mediaview_edit_filter, tool == EditorTool.Filter) { onSelect(EditorTool.Filter) }
        ToolItem(slot, Icons.Filled.Tune, R.string.mediaview_edit_adjust, tool == EditorTool.Adjust) { onSelect(EditorTool.Adjust) }
    }
}

/**
 * 一个工具入口。[enabled] = false 时置灰且点不动（IconButton 自己吞掉点击）——
 * 用的是 Material 自己的 enabled 视觉，不另造一套「看起来不能点」的样式。
 */
@Composable
private fun ToolItem(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> Color.Gray
        active -> Color(0xFFFFCC00)
        else -> Color.White
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, stringResource(label), tint = tint) }
        Text(stringResource(label), color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

/** 没有选中标注时：当前工具的默认样式（颜色、粗细/字号/笔刷、粗体）。 */
@Composable
private fun StylePanel(
    hint: String,
    color: Color?,
    onColor: (Color) -> Unit,
    sliderLabel: String,
    slider: Float,
    range: ClosedFloatingPointRange<Float>,
    onSlider: (Float) -> Unit,
    bold: Boolean? = null,
    onBold: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(hint, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        if (color != null) Swatches(color, onColor)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sliderLabel, color = Color.White, modifier = Modifier.width(48.dp))
            Slider(value = slider, onValueChange = onSlider, valueRange = range, modifier = Modifier.weight(1f))
            if (bold != null) {
                TextButton(onClick = onBold) {
                    Text(stringResource(R.string.mediaview_edit_bold), color = if (bold) Color(0xFFFFCC00) else Color.White)
                }
            }
        }
    }
}

/** 选中了一条标注：对它本身改样式、删除；文字额外可以改内容、粗细。 */
@Composable
private fun SelectionPanel(
    op: EditOp,
    onDelete: () -> Unit,
    onDeselect: () -> Unit,
    onEditText: (EditOp.Text) -> Unit,
    onColor: (Color) -> Unit,
    onBold: () -> Unit,
    onSlider: (Float) -> Unit,
    onSliderDone: () -> Unit,
) {
    val title = when (op) {
        is EditOp.Text -> R.string.mediaview_edit_selected_text
        is EditOp.Mosaic -> R.string.mediaview_edit_selected_mosaic
        else -> R.string.mediaview_edit_selected_stroke
    }
    val (range, sliderLabel) = when (op) {
        is EditOp.Text -> TEXT_RANGE to R.string.mediaview_edit_size
        is EditOp.Mosaic -> MOSAIC_RANGE to R.string.mediaview_edit_brush
        else -> BRUSH_RANGE to R.string.mediaview_edit_thickness
    }
    val currentColor = when (op) {
        is EditOp.Stroke -> Color(op.colorArgb.toInt())
        is EditOp.Text -> Color(op.colorArgb.toInt())
        else -> null
    }
    Column(Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(title), color = Color(0xFFFFCC00), modifier = Modifier.weight(1f))
            if (op is EditOp.Text) {
                TextButton(onClick = { onEditText(op) }) {
                    Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(" " + stringResource(R.string.mediaview_edit_edit_text), color = Color.White)
                }
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
                Text(" " + stringResource(R.string.mediaview_edit_delete), color = Color(0xFFFF6B6B))
            }
            TextButton(onClick = onDeselect) {
                Text(stringResource(R.string.mediaview_edit_deselect), color = Color.White)
            }
        }
        if (currentColor != null) Swatches(currentColor, onColor)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(sliderLabel), color = Color.White, modifier = Modifier.width(48.dp))
            Slider(
                value = op.sizeValue().coerceIn(range.start, range.endInclusive),
                onValueChange = onSlider,
                onValueChangeFinished = onSliderDone,
                valueRange = range,
                modifier = Modifier.weight(1f),
            )
            if (op is EditOp.Text) {
                TextButton(onClick = onBold) {
                    Text(stringResource(R.string.mediaview_edit_bold), color = if (op.bold) Color(0xFFFFCC00) else Color.White)
                }
            }
        }
    }
}

@Composable
private fun Swatches(selected: Color, onPick: (Color) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PALETTE.forEach { c ->
            val on = c.toArgb() == selected.toArgb()
            Box(
                Modifier
                    .size(if (on) 30.dp else 26.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(if (on) 3.dp else 1.dp, if (on) Color(0xFFFFCC00) else Color.Gray, CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

/** 亮度/对比度/饱和度。右上角「完成调整」收起面板（之前一旦打开就关不掉）。 */
@Composable
private fun AdjustPanel(
    adjust: EditOp.Adjust,
    onChange: (EditOp.Adjust) -> Unit,
    onFinished: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.mediaview_edit_adjust), color = Color(0xFFFFCC00), modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.mediaview_edit_adjust_done), color = Color.White)
            }
        }
        AdjustRow(stringResource(R.string.mediaview_edit_brightness), adjust.brightness, { onChange(adjust.copy(brightness = it)) }, onFinished)
        AdjustRow(stringResource(R.string.mediaview_edit_contrast), adjust.contrast, { onChange(adjust.copy(contrast = it)) }, onFinished)
        AdjustRow(stringResource(R.string.mediaview_edit_saturation), adjust.saturation, { onChange(adjust.copy(saturation = it)) }, onFinished)
    }
}

@Composable
private fun AdjustRow(label: String, value: Int, onChange: (Int) -> Unit, onFinished: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.width(56.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onFinished,
            valueRange = -100f..100f,
            modifier = Modifier.weight(1f),
        )
        Text("$value", color = Color.White, modifier = Modifier.width(36.dp))
    }
}

@Composable
private fun TextEditDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mediaview_edit_text_title)) },
        text = {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(stringResource(R.string.mediaview_edit_text_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }) { Text(stringResource(R.string.mediaview_edit_text_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.mediaview_edit_cancel)) }
        },
    )
}
