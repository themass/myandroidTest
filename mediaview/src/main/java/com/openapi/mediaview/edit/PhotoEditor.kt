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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Brush
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
    // 「这一帧渲染因为有 Filter 但表取不到而跳过了滤镜」的会话级信号（实机抱怨「滤镜点了没反应」
    // ——降级必须看得见）。渲染跑在 remember 里，组合期不许直接回写 Compose state，所以渲染侧
    // 只写这个普通持有槽，由下面的 LaunchedEffect 落到界面状态。
    // 跳过滤镜的降级帧**不进底图缓存**（见 renderPreview）：表恢复可用（LutAssets 不再缓存失败）
    // 后下一次渲染就能真的套上表，不会把「跳过滤镜」的帧在缓存里钉一辈子。
    val filterSkippedId = remember(bitmap) { arrayOfNulls<String>(1) }
    val filterNotice = remember(bitmap) { mutableStateOf<String?>(null) }
    val rendered = remember(previewSource, baseOps, previewLongEdge, lutProvider) {
        filterSkippedId[0] = null
        renderPreview(previewSource, baseOps, previewLongEdge, baseCache, lutProvider, filterSkippedId)
    }
    LaunchedEffect(rendered) {
        filterNotice.value = filterSkippedId[0]
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            val cropping = tool == EditorTool.Crop

            TopBar(
                edits = edits,
                // 裁剪模式期间把 undo/redo/reset 整组灰掉（与「裁后禁旋转」同款处置，入口即封死）。
                // 三个理由，缺一个都会漏：
                // 1) 手拖的选框只活在 cropSelection 里，不进 edits——撤销会改画幅、视口重铺，
                //    选框被静默丢弃重铺默认居中框，用户的手拖没了却没有任何提示；
                // 2) 舞台/控件用的是 state 的 cropViewport，而 onConfirm 用的是即时的
                //    EditCoords.cropWindow(edits)——「旧视口 × 新窗口」这种跨快照配对只要发生一次
                //    就是偏移裁剪，而这一帧正好由上面那类动作触发；
                // 3) 裁剪中途改 edits 会让预览尺寸在一帧之内对不上正在拖的把手。
                // 与其去追每一帧的配对，不如让用户在这条路径上根本没有可点的按钮。
                actionsEnabled = !cropping,
                onCancel = onCancel,
                onUndo = { edits = edits.undo(); selected = null },
                onRedo = { edits = edits.redo(); selected = null },
                onReset = { edits = edits.reset(); selected = null },
                onDone = { onDone(edits) },
            )

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
                    // 视口用公布出去的 cropViewport 而不是这里的 fit：两层必须永远拿到同一个值。
                    // 裁剪中途改 edits 的那条入口（TopBar 的 undo/redo/reset）已经在
                    // [TopBar] 的 actionsEnabled 上封死，所以这里不再会「预览尺寸变了但选框还按旧分母」。
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

            // 「滤镜没生效」的如实提示（渲染层上报，见上面 filterNotice 的接线）：表取不到时
            // 画面停在 Adjust 之后，这里必须说出来，而不是让用户对着「点了没反应」的画面猜。
            filterNotice.value?.let { brokenId ->
                Text(
                    filterSkippedNotice(brokenId),
                    color = EditPalette.Accent,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EditPalette.PanelBg)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            // 面板槽在工具条**上方**（原本就在），但它必须**封顶 + 内部可滚动**：
            // 16 色色板 + 自定义行把面板垫高之后，原先无界的面板会顶到屏幕底边
            // （实机截图里面板标签被切一半、工具条被挤出去）。封顶 + 滚动保证
            // 任何面板内容都完整可见，工具条永远贴底。
            val hasPanel = cropping || op != null || tool != EditorTool.None
            if (hasPanel) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = PANEL_MAX_HEIGHT)
                        .background(EditPalette.PanelBg)
                        .verticalScroll(rememberScrollState()),
                ) {
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
                            // 改色**和滑杆同一条 liveBase 路**：常驻取色器（A33）每拖一格就吐一个颜色，
                            // 直接 replaceAt 会把「扫一遍色相」变成几十步撤销。拖动中只改预览
                            // （withOpsUnrecorded），松手一次提交（withOps）——恰一步撤销，
                            // 和粗细/字号滑杆完全一致（见 PhotoEditsTest）。点预制格只吐一次，
                            // 走同一条路结果也一样：base 快照 + 一次提交。
                            onColor = { c ->
                                val base = liveBase ?: edits.also { liveBase = it }
                                edits = base.withOpsUnrecorded(base.ops.toMutableList().also { it[index] = op.withColor(c.toArgb().toLong()) })
                            },
                            onColorDone = {
                                liveBase?.let { base -> edits = base.withOps(edits.ops) }
                                liveBase = null
                            },
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
                            track = TrackPreview.Thickness,
                            slider = brushWidth, range = BRUSH_RANGE, onSlider = { brushWidth = it },
                        )
                        tool == EditorTool.Text -> StylePanel(
                            hint = stringResource(R.string.mediaview_edit_hint_text),
                            color = textColor, onColor = { textColor = it },
                            sliderLabel = stringResource(R.string.mediaview_edit_size),
                            track = TrackPreview.FontSize,
                            slider = textSize, range = TEXT_RANGE, onSlider = { textSize = it },
                            bold = textBold, onBold = { textBold = !textBold },
                        )
                        tool == EditorTool.Mosaic -> StylePanel(
                            hint = stringResource(R.string.mediaview_edit_hint_mosaic),
                            color = null, onColor = {},
                            sliderLabel = stringResource(R.string.mediaview_edit_brush),
                            track = TrackPreview.MosaicBlock,
                            slider = mosaicWidth, range = MOSAIC_RANGE, onSlider = { mosaicWidth = it },
                        )
                    }
                }
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

/**
 * 16 档墨色（实机抱怨「颜色只有 6 个色块」→ 按设计稿扩到 16 色，见
 * `protos/photosolve-ui/src/EditorScreen.tsx` 的 SWATCHES）。
 *
 * 色值取 Astryx neutral 主题色阶的档位（设计稿的 16 色按语义就近映射到主题里存在的色阶：
 * lime≈green-80、sky≈cyan-70、indigo≈blue-30、violet≈purple-50、fuchsia≈pink-50、棕≈orange-30），
 * **只在这里出现一次**——面板/UI 代码只引用名字，不散写 hex。
 */
internal object EditSwatches {
    val Red = Color(0xFFED5753)      // red-60
    val Orange = Color(0xFFD97000)   // orange-60
    val Amber = Color(0xFFC29900)    // yellow-65
    val Yellow = Color(0xFFEEC448)   // yellow-80
    val Lime = Color(0xFFA4D6A3)     // green-80
    val Green = Color(0xFF3C8A3F)    // green-50
    val Teal = Color(0xFF009986)     // teal-55
    val Sky = Color(0xFF45BDD7)      // cyan-70
    val Blue = Color(0xFF1E81F0)     // blue-55
    val Indigo = Color(0xFF00458C)   // blue-30
    val Violet = Color(0xFF9F3CB5)   // purple-50
    val Fuchsia = Color(0xFFC5387C)  // pink-50
    val Pink = Color(0xFFFFA9CA)     // pink-80
    val Brown = Color(0xFF733100)    // orange-30
    val Gray = Color(0xFF848484)     // neutral-55
    val White = Color(0xFFFFFFFF)    // neutral-100

    /** 展示顺序与设计稿一致（每行 8 个由 Swatches 的 chunked(8) 保证）。 */
    val All = listOf(
        Red, Orange, Amber, Yellow, Lime, Green, Teal, Sky,
        Blue, Indigo, Violet, Fuchsia, Pink, Brown, Gray, White,
    )

    /**
     * 「自定义色」判定：不在预制 16 档里 = 取色器调出来的颜色（A33），界面上用强调色描边
     * 区分它。判定只认 argb 精确相等——取色器滑出来的颜色和预制档几乎不会撞值，
     * 撞上了也无妨：那本来就是同一个颜色。
     */
    fun isCustom(c: Color): Boolean = All.none { it.toArgb() == c.toArgb() }

    /** 色相光谱条：拇指落点 fraction（0..1）→ 色相 0..360。两端都是红（0°≡360°），首尾相接。 */
    fun hueAt(fraction: Float): Float = fraction.coerceIn(0f, 1f) * 360f
}

private val PALETTE = EditSwatches.All

/**
 * 编辑器 UI 的**令牌**：mediaview 不依赖 :common，所以「一处定义、多处引用」就落在本文件的
 * 这个对象上——面板底色、强调色、文字层级、色块描边、间距只在这里出现一次。
 *
 * [PALETTE] 不在这里：它是涂鸦/文字落到图上的墨色，是**数据**，不是界面 chrome。
 */
private object EditPalette {
    val PanelBg = Color(0xFF111111)
    val Accent = Color(0xFFFFCC00)
    val Danger = Color(0xFFFF6B6B)
    val TextPrimary = Color.White
    /** 未选中标签、面板提示：60% 白 */
    val TextSecondary = Color.White.copy(alpha = 0.6f)
    val Disabled = Color.Gray
    /** 选中色块的描边（白）与未选中的淡描边 */
    val SwatchRing = Color.White
    val SwatchIdleRing = Color.Gray
    /** Swatches 行的行高与色块间距共用这一个间距常量 */
    val SwatchGap = 8.dp
}
private const val DEFAULT_BRUSH = 0.008f
private const val DEFAULT_MOSAIC = 0.06f
private const val DEFAULT_TEXT = 0.05f
private val BRUSH_RANGE = 0.003f..0.03f
private val MOSAIC_RANGE = 0.02f..0.15f
private val TEXT_RANGE = 0.025f..0.12f

/**
 * 面板槽的封顶高度：任何面板超过这个高度就在槽内滚动（见 PhotoEditor 的面板槽注释）。
 * 没有封顶时，16 色色板 + 自定义行会把面板垫到屏幕底边——实机截图里面板标签被切一半、
 * 工具条被顶出去，就是这个无界增长的结果。
 */
private val PANEL_MAX_HEIGHT = 240.dp

/** 16 色色板每行的格数（设计稿的 swatch grid 规范：每行 8）。 */
private const val SWATCHES_PER_ROW = 8

private const val SAT_LABEL = "饱和度"
private const val LIGHT_LABEL = "明度"

/** 色相光谱条的高度（拇指圆按它的一半画，见 [HueSpectrumBar]）。 */
private val HUE_BAR_HEIGHT = 20.dp

/** 预览侧「这次滤镜没生效」的如实提示（渲染层把取不到的表上报到这里，界面不许沉默）。 */
internal fun filterSkippedNotice(filterId: String): String =
    "滤镜 $filterId 的表取不到，这次预览没有套上滤镜；重装应用或稍后再试"

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
 *
 * **兜底半径必须罩住整条链路（renderBase *和* drawOps），而且只有一个 catch。**
 * 这里不能直接调 [PhotoRenderer.render]（那会丢掉底图缓存），但**失败怎么处理**不许自己写：
 * 一律走 [PhotoRenderer.guardRender]（渲染路径唯一吞异常处），本函数只负责「失败就换源图」
 * 这个预览专属的决定。drawOps 每次都全幅 `copy()` 一份再叠马赛克的 block 位图，正是低端机
 * 拖滑杆时最可能 OOM 的分配，让它漏出去就是把这个异常打进 composition、直接杀掉编辑器。
 * **别再把它拆回「只包 renderBase」**，也不要在这里再复制一对 catch——预览有图可显示，
 * 唯一合理的失败方式就是退回源图。
 */
private fun renderPreview(
    source: Bitmap,
    ops: List<EditOp>,
    maxLongEdge: Int,
    cache: RenderBaseCache,
    lutProvider: (String) -> Lut3D?,
    filterSkippedId: Array<String?>,
): Bitmap {
    // 完全没编辑时直接返回源图：和 PhotoRenderer.render 的旧快捷路径一致。
    // 少了这一句，空白编辑会话的每帧预览都会白白拷一次整幅位图（drawOps 恒复制）。
    if (ops.isEmpty()) return source
    return PhotoRenderer.guardRender("PhotoEditor") {
        // 只为拿一个「不带标注」的会话算指纹；PhotoEdits 是 data class，构造很便宜
        // （probe 刻意留在罩住的范围**里面**：兜底半径要罩住整条链路，一个字都不该缩）
        val probe = PhotoEdits.of(source.width, source.height).withOpsUnrecorded(ops)
        val cached = cache.get(source, probe, maxLongEdge)
        if (cached != null) {
            // 命中帧必然不是「跳过滤镜」的降级帧：下面刻意不把降级帧写进缓存，所以这里无需复查。
            return@guardRender PhotoRenderer.drawOps(cached, probe)
        }
        val base = PhotoRenderer.renderBase(
            source, probe, maxLongEdge,
            onFilterUnavailable = { id ->
                // 渲染跳过了滤镜（表取不到）：写普通持有槽，由宿主的 LaunchedEffect 落到界面提示。
                filterSkippedId[0] = id
            },
            lutProvider = lutProvider,
        )
        // 「跳过滤镜」的降级帧**不进缓存**：LutAssets 不再缓存失败，表恢复可用后下一次渲染
        // 要能真的套上表，而不是把降级帧在缓存里钉到会话结束。
        if (filterSkippedId[0] == null) cache.put(source, probe, maxLongEdge, base)
        PhotoRenderer.drawOps(base, probe)
    } ?: source
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
        EditPalette.Accent, tl, size,
        style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
    )
}

// ---------------------------------------------------------------------------
// 面板
// ---------------------------------------------------------------------------

@Composable
private fun TopBar(
    edits: PhotoEdits,
    actionsEnabled: Boolean,
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
        BarIcon(Icons.AutoMirrored.Filled.Undo, R.string.mediaview_edit_undo, actionsEnabled && edits.canUndo, onUndo)
        BarIcon(Icons.AutoMirrored.Filled.Redo, R.string.mediaview_edit_redo, actionsEnabled && edits.canRedo, onRedo)
        BarIcon(Icons.Filled.RestartAlt, R.string.mediaview_edit_reset, actionsEnabled && edits.isDirty, onReset)
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
        modifier = Modifier.fillMaxWidth().background(EditPalette.PanelBg).padding(vertical = 8.dp),
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
 *
 * 选中态**不再整项染黄**（黄色是全局强调色，八个入口里两块染黄既抢又看不出边界）：
 * 图标和文字都保持白色，选中项在标签下方点一条 2dp 的 [EditPalette.Accent] 短横线。
 * 横线槽位恒在（未选中时透明），这样八项的标签基线不会因为选中态移动。
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
    val tint = if (enabled) EditPalette.TextPrimary else EditPalette.Disabled
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, stringResource(label), tint = tint) }
        Text(
            stringResource(label),
            color = when {
                !enabled -> EditPalette.Disabled
                active -> EditPalette.TextPrimary
                else -> EditPalette.TextSecondary
            },
            style = MaterialTheme.typography.labelSmall,
        )
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(width = 16.dp, height = 2.dp)
                .background(if (active) EditPalette.Accent else Color.Transparent),
        )
    }
}

/**
 * 没有选中标注时：当前工具的默认样式（颜色、粗细/字号/笔刷、粗体）。
 *
 * 滑杆**离散成 5 档**（`steps = SLIDER_STEPS`，range 与 label 语义都不变）：连续的 0.003–0.03 这种
 * 小数区间在手机上根本停不准，档位化之后「粗细 N」的当前值文本才有意义。
 * 轨道换成 [PreviewSlider]（A34）：轨道本身就是粗细/字号的渐变预览，数值画在拇指上方，
 * 所以这里**不再**单独放一个档位数字。
 * 注意这三根滑杆只写 brushWidth/textSize/mosaicWidth 的默认样式，**不经过** liveBase
 * 那条「松手一步撤销」的路（那是 SelectionPanel/Adjust/Filter 的事，见 PhotoEditsTest），
 * 所以档位化不碰撤销语义。
 */
@Composable
private fun StylePanel(
    hint: String,
    color: Color?,
    onColor: (Color) -> Unit,
    sliderLabel: String,
    track: TrackPreview,
    slider: Float,
    range: ClosedFloatingPointRange<Float>,
    onSlider: (Float) -> Unit,
    bold: Boolean? = null,
    onBold: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().background(EditPalette.PanelBg).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(hint, color = EditPalette.TextSecondary, style = MaterialTheme.typography.labelSmall)
        if (color != null) Swatches(color, onColor)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sliderLabel, color = EditPalette.TextPrimary, modifier = Modifier.width(48.dp))
            PreviewSlider(
                value = slider,
                range = range,
                preview = track,
                onValueChange = onSlider,
                steps = SLIDER_STEPS,
                modifier = Modifier.weight(1f),
            )
            if (bold != null) {
                TextButton(onClick = onBold) {
                    Text(stringResource(R.string.mediaview_edit_bold), color = if (bold) EditPalette.Accent else EditPalette.TextPrimary)
                }
            }
        }
    }
}

/** 滑杆档数：`steps = SLIDER_STEPS` → 含端点共 5 档。 */
private const val SLIDER_STEPS = 4

/**
 * 色块上 ✓ 的着色：按色块的感知亮度取反（亮块配黑勾、暗块配白勾）。
 * [PALETTE] 里有白块，白勾压白块就隐形了——白描边环仍然恒是 [EditPalette.SwatchRing]。
 * 权重是 Rec.601 的近似，只需要「哪边更亮」这个粗判断。
 */
private fun checkTintOn(swatch: Color): Color {
    val luminance = 0.299f * swatch.red + 0.587f * swatch.green + 0.114f * swatch.blue
    return if (luminance > 0.6f) Color.Black else EditPalette.SwatchRing
}

/** 选中了一条标注：对它本身改样式、删除；文字额外可以改内容、粗细。 */
@Composable
private fun SelectionPanel(
    op: EditOp,
    onDelete: () -> Unit,
    onDeselect: () -> Unit,
    onEditText: (EditOp.Text) -> Unit,
    onColor: (Color) -> Unit,
    onColorDone: () -> Unit,
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
    Column(Modifier.fillMaxWidth().background(EditPalette.PanelBg).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(title), color = EditPalette.Accent, modifier = Modifier.weight(1f))
            if (op is EditOp.Text) {
                TextButton(onClick = { onEditText(op) }) {
                    Icon(Icons.Filled.Edit, null, tint = EditPalette.TextPrimary, modifier = Modifier.size(18.dp))
                    Text(" " + stringResource(R.string.mediaview_edit_edit_text), color = EditPalette.TextPrimary)
                }
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, null, tint = EditPalette.Danger, modifier = Modifier.size(18.dp))
                Text(" " + stringResource(R.string.mediaview_edit_delete), color = EditPalette.Danger)
            }
            TextButton(onClick = onDeselect) {
                Text(stringResource(R.string.mediaview_edit_deselect), color = EditPalette.TextPrimary)
            }
        }
        if (currentColor != null) Swatches(currentColor, onColor, onPickDone = onColorDone)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(sliderLabel), color = EditPalette.TextPrimary, modifier = Modifier.width(48.dp))
            // 连续模式（steps=0）：保持原有「拖动实时预览、松手一步撤销」的语义，
            // 轨道同样是该参数的渐变预览（A34）。
            PreviewSlider(
                value = op.sizeValue().coerceIn(range.start, range.endInclusive),
                range = range,
                preview = when (op) {
                    is EditOp.Text -> TrackPreview.FontSize
                    is EditOp.Mosaic -> TrackPreview.MosaicBlock
                    else -> TrackPreview.Thickness
                },
                onValueChange = onSlider,
                onValueChangeFinished = onSliderDone,
                modifier = Modifier.weight(1f),
            )
            if (op is EditOp.Text) {
                TextButton(onClick = onBold) {
                    Text(stringResource(R.string.mediaview_edit_bold), color = if (op.bold) EditPalette.Accent else EditPalette.TextPrimary)
                }
            }
        }
    }
}

/**
 * 色板 = 16 档预制墨色 **+ 常驻取色器**（A33：用户原话「应该有个色板选择器，在上面选择任何一种颜色」）。
 *
 * 取色器不是「第二层弹窗里的隐藏入口」：色相光谱条和饱和度/明度滑杆就在色板下方，
 * 面板打开即可用（见 [InlineColorPicker]）。预制 16 格保留——一键取常用色仍然比拖光谱快，
 * 两者共用同一个 [onPick]，下游数据模型不变（任意 `Color` → `toArgb().toLong()`）。
 *
 * [onPickDone] 是「这次选色结束了」：选中一条标注时宿主用它把连续吐色的取色器收成一步撤销
 * （见 PhotoEditor 里 onColor/onColorDone）；默认样式面板（StylePanel）不记历史，留空。
 */
@Composable
private fun Swatches(selected: Color, onPick: (Color) -> Unit, onPickDone: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = EditPalette.SwatchGap)) {
        // 16 色每行 8 个（设计稿的 swatch grid 规范）。色块语义与原 6 色版一致：
        // 选中 = 描边 + ✓（勾色按块面明度取反），未选中 = 细灰环。
        EditSwatches.All.chunked(SWATCHES_PER_ROW).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(EditPalette.SwatchGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { c ->
                    val on = c.toArgb() == selected.toArgb()
                    Box(
                        Modifier
                            .size(if (on) 28.dp else 24.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (on) 2.dp else 1.dp, if (on) EditPalette.SwatchRing else EditPalette.SwatchIdleRing, CircleShape)
                            .clickable { onPick(c); onPickDone() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) {
                            Icon(
                                Icons.Filled.Check,
                                null,
                                tint = checkTintOn(c),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                // 末行不满 8 格时补位，保证每格等宽（16 = 2×8 正好满；防御性保留）
                repeat(SWATCHES_PER_ROW - row.size) { Box(Modifier.size(24.dp)) }
            }
        }
        // 常驻取色器：色相光谱条 + 饱和度/明度滑杆 + 当前色预览。
        // 当前色不在 16 档里 = 它是取色器调出来的自定义色，预览块描强调边。
        InlineColorPicker(selected, onPick, onPickDone)
    }
}

/**
 * 面板内常驻取色器（A33）：把「选择任何一种颜色」做成**就在色板下面**的控件，
 * 而不是「自定义」按钮后面的 Dialog——用户要的是色板本身就是选择器。
 *
 * 形态（沿用设计稿 HSL 口径，只是把 Dialog 摊平进面板）：
 * - **色相光谱条**：整条彩虹就是轨道，拇指落在哪就是哪个色相——「任何一种颜色」的入口；
 * - **饱和度 / 明度**两根滑杆，右侧实时数值；
 * - 左侧预览块显示合成出来的当前色（不在 16 档里就描强调边）。
 *
 * 滑动即选：每次拖动直接 [onPick]（和点预制格同一条路，下游只认任意 `Color`）；
 * 手势结束（松手/取消）调 [onPickDone]，宿主用它把连续吐色收成一步撤销。
 *
 * **滑杆位置不从 selected 逐帧反推**（那是个坑）：灰调（sat=0）下 [rgbToHsl] 的色相恒为 0，
 * 用户把饱和度拉到 0 再去拖色相，拇指会被反推弹回左端。所以三个滑杆值是组件自有状态，
 * 只有**外部改色**（点预制格、选中另一条标注）才重新播种——判定就是 `selected` 与
 * `emitted`（我们自己上一次吐出去的颜色）是否相等。
 */
@Composable
private fun InlineColorPicker(selected: Color, onPick: (Color) -> Unit, onPickDone: () -> Unit) {
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(100f) }
    var light by remember { mutableFloatStateOf(50f) }
    // 我们自己上一次吐给宿主的 argb；Int.MIN_VALUE = 还没吐过（首次必然播种）
    var emitted by remember { mutableIntStateOf(Int.MIN_VALUE) }
    if (selected.toArgb() != emitted) {
        val seed = rgbToHsl(selected)
        hue = seed.hue; sat = seed.sat; light = seed.light
        emitted = selected.toArgb()
    }
    fun pick(h: Float, s: Float, l: Float) {
        hue = h; sat = s; light = l
        val c = hslToColor(h, s, l)
        emitted = c.toArgb()
        onPick(c)
    }
    val preview = hslToColor(hue, sat, light)

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditPalette.SwatchGap),
    ) {
        // 当前色块：取色器调出来的颜色要看得见它落进了哪里（16 格里没有它）。
        val isCustom = EditSwatches.isCustom(selected)
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(preview)
                .border(if (isCustom) 2.dp else 1.dp, if (isCustom) EditPalette.Accent else EditPalette.SwatchIdleRing, CircleShape),
        )
        HueSpectrumBar(hue, Modifier.weight(1f), onDone = onPickDone) { h -> pick(h, sat, light) }
    }
    HslSliderRow(SAT_LABEL, sat, 0f..100f, onFinished = onPickDone) { s -> pick(hue, s, light) }
    HslSliderRow(LIGHT_LABEL, light, 0f..100f, onFinished = onPickDone) { l -> pick(hue, sat, l) }
}

/**
 * 色相光谱条：整条彩虹就是轨道，拇指落在哪一段就是哪个色相（0..360）。
 * 「轨道就是该参数的预览」——和 A34 的粗细渐变滑杆同一个原则。
 */
@Composable
private fun HueSpectrumBar(hue: Float, modifier: Modifier = Modifier, onDone: () -> Unit = {}, onChange: (Float) -> Unit) {
    val spectrum = remember {
        // Compose 没有现成的 rainbow Brush：按等距色相拼 13 档全饱和中间明度——
        // 端点重合（0° 与 360° 同为红），首尾相接，视觉上连续。
        Brush.linearGradient(List(13) { i -> hslToColor(i * 360f / 12f, 100f, 50f) })
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(HUE_BAR_HEIGHT)
            .clip(RoundedCornerShape(HUE_BAR_HEIGHT / 2))
            .onHorizontalScrub(
                onValue = { x, w -> onChange(EditSwatches.hueAt(x / w)) },
                onFinished = onDone,
            ),
    ) {
        val barWidth = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            drawRect(spectrum)
            // 拇指：白色小圆停在当前色相上
            val r = HUE_BAR_HEIGHT.toPx() / 2f
            val cx = (hue / 360f).coerceIn(0f, 1f) * barWidth.toPx()
            drawCircle(Color.White, radius = r - 1.5f, center = Offset(cx, r))
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = r - 1.5f, center = Offset(cx, r), style = Stroke(width = 1.5f))
        }
    }
}

@Composable
private fun HslSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onFinished: () -> Unit = {}, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = EditPalette.TextPrimary, modifier = Modifier.width(56.dp))
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = onFinished, valueRange = range, modifier = Modifier.weight(1f))
        Text("${value.roundToInt()}", color = EditPalette.TextPrimary, modifier = Modifier.width(36.dp))
    }
}

/** HSL 三元组：[hue] 0..360，[sat]/[light] 0..100（界面上滑杆就是这个口径）。 */
internal data class Hsl(val hue: Float, val sat: Float, val light: Float)

/**
 * RGB → HSL（自定义取色对话框打开时滑杆停在当前色）。纯函数、可单测。
 * 灰度色（max == min）没有色相，取 0——设计上「灰色从 0 度开始调」是常规做法。
 */
internal fun rgbToHsl(c: Color): Hsl {
    val r = c.red; val g = c.green; val b = c.blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return Hsl(0f, 0f, l * 100f)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val hDeg = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f) * 60f
        g -> ((b - r) / d + 2f) * 60f
        else -> ((r - g) / d + 4f) * 60f
    }
    return Hsl(hDeg, s * 100f, l * 100f)
}

/**
 * HSL → RGB。选完的颜色走回和色板完全同一条 [Swatches] onPick → toArgb().toLong() 的路，
 * 数据模型不为此多任何字段。
 */
internal fun hslToColor(hue: Float, sat: Float, light: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f / 360f
    val s = (sat / 100f).coerceIn(0f, 1f)
    val l = (light / 100f).coerceIn(0f, 1f)
    if (s == 0f) return Color(l, l, l)
    fun hue2rgb(p: Float, q: Float, t: Float): Float {
        var tt = t
        if (tt < 0f) tt += 1f
        if (tt > 1f) tt -= 1f
        return when {
            tt < 1f / 6f -> p + (q - p) * 6f * tt
            tt < 1f / 2f -> q
            tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
            else -> p
        }
    }
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    return Color(
        hue2rgb(p, q, h + 1f / 3f),
        hue2rgb(p, q, h),
        hue2rgb(p, q, h - 1f / 3f),
    )
}

/** 亮度/对比度/饱和度。右上角「完成调整」收起面板（之前一旦打开就关不掉）。 */
@Composable
private fun AdjustPanel(
    adjust: EditOp.Adjust,
    onChange: (EditOp.Adjust) -> Unit,
    onFinished: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(EditPalette.PanelBg).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.mediaview_edit_adjust), color = EditPalette.Accent, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.mediaview_edit_adjust_done), color = EditPalette.TextPrimary)
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
        Text(label, color = EditPalette.TextPrimary, modifier = Modifier.width(56.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onFinished,
            valueRange = -100f..100f,
            modifier = Modifier.weight(1f),
        )
        Text("$value", color = EditPalette.TextPrimary, modifier = Modifier.width(36.dp))
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
