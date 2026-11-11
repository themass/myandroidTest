package com.openapi.mediaview.edit

import com.openapi.mediaview.R

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.drawText
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * 图片本地编辑器（Compose，无第三方依赖）。
 *
 * 交互设计：
 * - 顶部是原图/编辑结果预览，所有标注都画在预览上（[PreviewCanvas]），拖动即所见；
 * - 底部分工具条：裁剪 / 旋转 / 涂鸦 / 文字 / 马赛克 / 调整；
 * - 撤销、重做、还原；
 * - 确认时由宿主调用 [PhotoEditExporter.export] 落盘。
 *
 * 组件只负责「编辑会话」本身，不知道图片将被发送到哪；宿主管缓存、上传和回传。
 * 这样对话、拍照解题、头像等场景可以共用同一个编辑器。
 *
 * ```kotlin
 * PhotoEditor(
 *     bitmap = bitmap,
 *     onCancel = { dismiss() },
 *     onDone = { edits -> exportAndSend(edits) },
 * )
 * ```
 */
@Composable
fun PhotoEditor(
    bitmap: android.graphics.Bitmap,
    onCancel: () -> Unit,
    onDone: (PhotoEdits) -> Unit,
    modifier: Modifier = Modifier,
    /** 裁剪是否可用（外部只想要涂鸦标注时可以关掉）。 */
    allowCrop: Boolean = true,
) {
    var edits by remember(bitmap) { mutableStateOf(PhotoEdits.of(bitmap.width, bitmap.height)) }
    var tool by remember { mutableStateOf(EditorTool.None) }
    // 当前编辑中的涂鸦点（归一化）
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var textDialog by remember { mutableStateOf(false) }
    var textDraft by remember { mutableStateOf("") }
    var pendingTextAt by remember { mutableStateOf(Offset(0.2f, 0.5f)) }
    var mosaicRect by remember { mutableStateOf<Rect?>(null) }

    Surface(modifier = modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            // 顶栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.mediaview_edit_cancel), tint = Color.White)
                }
                Text(
                    stringResource(R.string.mediaview_edit_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { edits = edits.undo() }, enabled = edits.canUndo) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.mediaview_edit_undo),
                        tint = if (edits.canUndo) Color.White else Color.Gray,
                    )
                }
                IconButton(onClick = { edits = edits.redo() }, enabled = edits.canRedo) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(R.string.mediaview_edit_redo),
                        tint = if (edits.canRedo) Color.White else Color.Gray,
                    )
                }
                IconButton(onClick = { edits = edits.reset() }, enabled = edits.canUndo) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.mediaview_edit_reset),
                        tint = if (edits.canUndo) Color.White else Color.Gray,
                    )
                }
                IconButton(onClick = { onDone(edits) }) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.mediaview_edit_done), tint = Color.White)
                }
            }

            // 预览 + 手势
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val fit = fitRect(bitmap.width, bitmap.height, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                PreviewCanvas(
                    bitmap = bitmap,
                    edits = edits,
                    currentStroke = currentStroke,
                    mosaicRect = mosaicRect,
                    tool = tool,
                    fit = fit,
                    onStrokeStart = { currentStroke = listOf(it) },
                    onStrokeMove = { currentStroke = currentStroke + it },
                    onStrokeEnd = {
                        if (currentStroke.size >= 2) {
                            edits = edits.push(
                                EditOp.Stroke(currentStroke, strokeColor.toArgb().toLong(), strokeWidthRatio),
                            )
                        }
                        currentStroke = emptyList()
                    },
                    onTextTap = { point ->
                        pendingTextAt = point
                        textDraft = ""
                        textDialog = true
                    },
                    onMosaicChange = { mosaicRect = it },
                    onMosaicEnd = {
                        mosaicRect?.let { r ->
                            edits = edits.push(
                                EditOp.Mosaic(r.left, r.top, r.right, r.bottom),
                            )
                        }
                        mosaicRect = null
                    },
                )
            }

            // 工具条 / 工具面板
            when (tool) {
                EditorTool.Adjust -> AdjustPanel(
                    adjust = edits.adjust,
                    onChange = { adj -> edits = edits.copy(ops = edits.ops.filterNot { it is EditOp.Adjust } + adj) },
                )
                else -> ToolBar(
                    tool = tool,
                    allowCrop = allowCrop,
                    onRotate = { edits = edits.rotateClockwise() },
                    onFlip = { edits = edits.flipHorizontal() },
                    onSelect = { tool = if (tool == it) EditorTool.None else it },
                )
            }
        }
    }

    if (textDialog) {
        AlertDialog(
            onDismissRequest = { textDialog = false },
            title = { Text(stringResource(R.string.mediaview_edit_text_title)) },
            text = {
                TextField(
                    value = textDraft,
                    onValueChange = { textDraft = it },
                    placeholder = { Text(stringResource(R.string.mediaview_edit_text_hint)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (textDraft.isNotBlank()) {
                        edits = edits.push(
                            EditOp.Text(textDraft, pendingTextAt.x, pendingTextAt.y, strokeColor.toArgb().toLong(), 0.05f),
                        )
                    }
                    textDialog = false
                }) { Text(stringResource(R.string.mediaview_edit_text_add)) }
            },
            dismissButton = {
                TextButton(onClick = { textDialog = false }) { Text(stringResource(R.string.mediaview_edit_cancel)) }
            },
        )
    }
}

private enum class EditorTool { None, Crop, Draw, Text, Mosaic, Adjust }

private val strokeColor = Color(0xFFFF3B30)
private const val strokeWidthRatio = 0.008f

/** 图片按 Fit 放进容器后的矩形。 */
private fun fitRect(srcW: Int, srcH: Int, boxW: Float, boxH: Float): Rect {
    if (srcW <= 0 || srcH <= 0) return Rect(0f, 0f, boxW, boxH)
    val ratio = srcW.toFloat() / srcH
    val (w, h) = if (boxW / boxH > ratio) boxH * ratio to boxH else boxW to boxW / ratio
    val left = (boxW - w) / 2f
    val top = (boxH - h) / 2f
    return Rect(left, top, left + w, top + h)
}

@Composable
private fun PreviewCanvas(
    bitmap: android.graphics.Bitmap,
    edits: PhotoEdits,
    currentStroke: List<Offset>,
    mosaicRect: Rect?,
    tool: EditorTool,
    fit: Rect,
    onStrokeStart: (Offset) -> Unit,
    onStrokeMove: (Offset) -> Unit,
    onStrokeEnd: () -> Unit,
    onTextTap: (Offset) -> Unit,
    onMosaicChange: (Rect?) -> Unit,
    onMosaicEnd: () -> Unit,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(tool, fit) {
                    when (tool) {
                        EditorTool.Draw -> detectDragGestures(
                            onDragStart = { p -> toNorm(p, fit)?.let(onStrokeStart) },
                            onDragEnd = { onStrokeEnd() },
                            onDragCancel = { onStrokeEnd() },
                            onDrag = { change, _ ->
                                change.consume()
                                toNorm(change.position, fit)?.let(onStrokeMove)
                            },
                        )
                        EditorTool.Mosaic -> {
                            var start: Offset? = null
                            detectDragGestures(
                                onDragStart = { p -> start = toNorm(p, fit) },
                                onDragEnd = { onMosaicEnd(); start = null },
                                onDragCancel = { onMosaicChange(null); start = null },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val s = start
                                    val e = toNorm(change.position, fit)
                                    if (s != null && e != null) {
                                        onMosaicChange(
                                            Rect(
                                                minOf(s.x, e.x), minOf(s.y, e.y),
                                                maxOf(s.x, e.x), maxOf(s.y, e.y),
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                        EditorTool.Text -> detectTapGestures { p ->
                            toNorm(p, fit)?.let(onTextTap)
                        }
                        else -> Unit
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            // 已有操作按原图坐标绘制：这里只做「原图 → 显示」的映射
            val scale = fit.width / edits.sourceWidth.coerceAtLeast(1)
            fun px(x: Float, y: Float) = Offset(fit.left + x * edits.sourceWidth * scale, fit.top + y * edits.sourceHeight * scale)

            edits.ops.forEach { op ->
                when (op) {
                    is EditOp.Stroke -> {
                        if (op.points.size >= 2) {
                            val path = androidx.compose.ui.graphics.Path()
                            op.points.forEachIndexed { i, p ->
                                val q = px(p.x, p.y)
                                if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
                            }
                            drawPath(
                                path,
                                Color(op.colorArgb),
                                style = Stroke(
                                    width = op.widthRatio * minOf(w, h),
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                                ),
                            )
                        }
                    }
                    is EditOp.Text -> {
                        val anchor = px(op.x, op.y)
                        val style = androidx.compose.ui.text.TextStyle(
                            color = Color(op.colorArgb),
                            fontSize = with(density) { (op.sizeRatio * minOf(edits.sourceWidth, edits.sourceHeight) * scale).toSp() },
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                        op.value.split('\n').forEachIndexed { i, line ->
                            val measured = textMeasurer.measure(line, style)
                            drawText(
                                measured,
                                topLeft = Offset(
                                    anchor.x,
                                    anchor.y + i * measured.size.height - measured.size.height,
                                ),
                            )
                        }
                    }
                    is EditOp.Mosaic -> {
                        val tl = px(op.left, op.top)
                        val br = px(op.right, op.bottom)
                        drawRect(Color.White.copy(alpha = 0.18f), tl, Size(br.x - tl.x, br.y - tl.y))
                    }
                    else -> Unit
                }
            }
            // 正在画的这一笔
            if (currentStroke.size >= 2) {
                val path = androidx.compose.ui.graphics.Path()
                currentStroke.forEachIndexed { i, p ->
                    val q = px(p.x, p.y)
                    if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
                }
                drawPath(path, strokeColor, style = Stroke(width = strokeWidthRatio * minOf(w, h)))
            }
            mosaicRect?.let { r ->
                val tl = Offset(fit.left + r.left * fit.width, fit.top + r.top * fit.height)
                val br = Offset(fit.left + r.right * fit.width, fit.top + r.bottom * fit.height)
                drawRect(Color.White.copy(alpha = 0.25f), tl, Size(br.x - tl.x, br.y - tl.y))
                drawRect(Color.White, tl, Size(br.x - tl.x, br.y - tl.y), style = Stroke(width = 2f))
            }
        }
    }
}

/** 屏幕坐标 → 原图归一化坐标；落在图片外返回 null（避免涂到黑边）。 */
private fun toNorm(p: Offset, fit: Rect): Offset? {
    if (!fit.contains(p)) return null
    return Offset(((p.x - fit.left) / fit.width).coerceIn(0f, 1f), ((p.y - fit.top) / fit.height).coerceIn(0f, 1f))
}

@Composable
private fun ToolBar(
    tool: EditorTool,
    allowCrop: Boolean,
    onRotate: () -> Unit,
    onFlip: () -> Unit,
    onSelect: (EditorTool) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolItem(Icons.Filled.Rotate90DegreesCcw, R.string.mediaview_edit_rotate, active = false, onClick = onRotate)
        ToolItem(Icons.Filled.Flip, R.string.mediaview_edit_flip, active = false, onClick = onFlip)
        ToolItem(Icons.Filled.Brush, R.string.mediaview_edit_draw, active = tool == EditorTool.Draw) { onSelect(EditorTool.Draw) }
        ToolItem(Icons.Filled.TextFields, R.string.mediaview_edit_text, active = tool == EditorTool.Text) { onSelect(EditorTool.Text) }
        ToolItem(Icons.Filled.BlurOn, R.string.mediaview_edit_mosaic, active = tool == EditorTool.Mosaic) { onSelect(EditorTool.Mosaic) }
        ToolItem(Icons.Filled.Tune, R.string.mediaview_edit_adjust, active = tool == EditorTool.Adjust) { onSelect(EditorTool.Adjust) }
    }
}

@Composable
private fun ToolItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = stringResource(labelRes), tint = if (active) Color(0xFFFFCC00) else Color.White)
        }
        Text(stringResource(labelRes), color = if (active) Color(0xFFFFCC00) else Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AdjustPanel(adjust: EditOp.Adjust, onChange: (EditOp.Adjust) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(16.dp)) {
        AdjustRow(stringResource(R.string.mediaview_edit_brightness), adjust.brightness) { onChange(adjust.copy(brightness = it)) }
        AdjustRow(stringResource(R.string.mediaview_edit_contrast), adjust.contrast) { onChange(adjust.copy(contrast = it)) }
        AdjustRow(stringResource(R.string.mediaview_edit_saturation), adjust.saturation) { onChange(adjust.copy(saturation = it)) }
    }
}

@Composable
private fun AdjustRow(label: String, value: Int, onChange: (Int) -> Unit) {
    var slider by remember(label, value) { mutableFloatStateOf(value.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.size(width = 56.dp, height = 24.dp))
        Slider(
            value = slider,
            onValueChange = { slider = it; onChange(it.toInt()) },
            valueRange = -100f..100f,
            modifier = Modifier.weight(1f),
        )
        Text("${slider.toInt()}", color = Color.White)
    }
}
