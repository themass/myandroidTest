package com.openapi.mediaview.edit

import android.graphics.Bitmap
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
    @Suppress("UNUSED_PARAMETER") allowCrop: Boolean = true, // 保留参数以兼容旧调用；裁剪目前不在编辑器里
) {
    var edits by remember(bitmap) { mutableStateOf(PhotoEdits.of(bitmap.width, bitmap.height)) }
    var tool by remember { mutableStateOf(EditorTool.None) }
    var selected by remember { mutableStateOf<Int?>(null) }

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
    var textDialog by remember { mutableStateOf<TextDialog?>(null) }

    // 选中的下标可能因撤销/删除而失效，用的时候校验
    val selectedIndex = selected?.takeIf { it in edits.ops.indices && edits.ops[it].isAnnotation() }
    val selectedOp = selectedIndex?.let { edits.ops[it] }

    // 预览用缩小的图：渲染快，且标注坐标是归一化的，与原图等价
    val previewSource = remember(bitmap) { makePreviewSource(bitmap) }

    // 拖动某条标注时，底图先不画它（改用上层实时叠加），拖完再一次性提交
    val moveIndex = moving?.index
    val baseOps = remember(edits.ops, moveIndex) {
        if (moveIndex == null) edits.ops else edits.ops.filterIndexed { i, _ -> i != moveIndex }
    }
    val rendered = remember(previewSource, baseOps, edits.sourceWidth) {
        PhotoRenderer.render(previewSource, edits.withOpsUnrecorded(baseOps), PREVIEW_LONG_EDGE)
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

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val fit = fitRect(rendered.width, rendered.height, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                val imageBitmap = remember(rendered) { rendered.asImageBitmap() }
                val textMeasurer = rememberTextMeasurer()
                val density = androidx.compose.ui.platform.LocalDensity.current

                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )

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

            // 面板：选中了标注 → 编辑那条；否则按当前工具给默认样式
            val index = selectedIndex
            val op = selectedOp
            when {
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
                        val next = base.ops.filterNot { it is EditOp.Adjust } + (if (adj.isIdentity) emptyList() else listOf(adj))
                        edits = base.withOpsUnrecorded(next)
                    },
                    onFinished = {
                        liveBase?.let { base -> edits = base.withOps(edits.ops) }
                        liveBase = null
                    },
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

private enum class EditorTool { None, Draw, Text, Mosaic, Adjust }

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

private fun EditOp.isAnnotation(): Boolean = this is EditOp.Stroke || this is EditOp.Text || this is EditOp.Mosaic

private fun makePreviewSource(source: Bitmap): Bitmap {
    val longEdge = max(source.width, source.height)
    if (longEdge <= PREVIEW_LONG_EDGE) return source
    val ratio = PREVIEW_LONG_EDGE.toFloat() / longEdge
    return Bitmap.createScaledBitmap(
        source,
        (source.width * ratio).roundToInt().coerceAtLeast(1),
        (source.height * ratio).roundToInt().coerceAtLeast(1),
        true,
    )
}

/** 选中框只依赖尺寸/旋转，取一份不带标注的会话即可，避免把整个历史传来传去。 */
private fun editsWithoutOps(edits: PhotoEdits): PhotoEdits =
    edits.withOpsUnrecorded(edits.ops.filter { it is EditOp.Rotate || it == EditOp.FlipHorizontal })

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
    onRotate: () -> Unit,
    onFlip: () -> Unit,
    onSelect: (EditorTool) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolItem(Icons.Filled.Rotate90DegreesCcw, R.string.mediaview_edit_rotate, false, onRotate)
        ToolItem(Icons.Filled.Flip, R.string.mediaview_edit_flip, false, onFlip)
        ToolItem(Icons.Filled.Brush, R.string.mediaview_edit_draw, tool == EditorTool.Draw) { onSelect(EditorTool.Draw) }
        ToolItem(Icons.Filled.TextFields, R.string.mediaview_edit_text, tool == EditorTool.Text) { onSelect(EditorTool.Text) }
        ToolItem(Icons.Filled.BlurOn, R.string.mediaview_edit_mosaic, tool == EditorTool.Mosaic) { onSelect(EditorTool.Mosaic) }
        ToolItem(Icons.Filled.Tune, R.string.mediaview_edit_adjust, tool == EditorTool.Adjust) { onSelect(EditorTool.Adjust) }
    }
}

@Composable
private fun ToolItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, active: Boolean, onClick: () -> Unit) {
    val tint = if (active) Color(0xFFFFCC00) else Color.White
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, stringResource(label), tint = tint) }
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
