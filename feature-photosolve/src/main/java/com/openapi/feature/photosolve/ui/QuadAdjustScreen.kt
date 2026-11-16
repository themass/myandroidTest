package com.openapi.feature.photosolve.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openapi.feature.photosolve.R
import com.openapi.imaging.CropNorm
import com.openapi.imaging.quad.QuadEdit
import com.openapi.imaging.quad.QuadHandle
import com.openapi.imaging.quad.QuadNorm
import kotlin.math.atan2

/**
 * 四角微调页：把检测到的题目边框画在照片上，用户拖四个角对准纸面边缘。
 *
 * 交互要点：
 * - **四个角、四条边都能拖**。拖角：该角自由移动；拖边：整条边沿它的法线平移，两端的角沿相邻两条边滑动，
 *   其余三条边方向不变（矩形拖完仍是矩形，梯形仍是梯形）。几何见 [QuadEdit]，有单测；
 * - 角有 28dp 触摸半径，边的整条线段都有 22dp 触摸半径（不只是中点）；角和边重叠处角优先；
 * - 按下的瞬间就能抓住，不需要先移动；
 * - 拖角时被夹在图片边界上，不会「飞走」；拖边碰到边界/临界形状就停住；
 * - 拖动时实时显示矫正后的区域（四边形填充 + 边框），确认后交给 ViewModel 做透视矫正；
 * - 提供「重新检测」：模型有时会把桌面边缘当纸面，用户可以让它再试一次。
 */
@Composable
fun QuadAdjustScreen(
    bitmap: Bitmap,
    quad: QuadNorm,
    detecting: Boolean,
    onQuadChange: (QuadNorm) -> Unit,
    onRedetect: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_back), tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.photosolve_quad_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.photosolve_quad_hint),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (detecting) {
                Text(stringResource(R.string.photosolve_detecting), color = Color.White.copy(alpha = 0.7f))
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val fit = fitRect(bitmap.width, bitmap.height, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            QuadCanvas(
                bitmap = bitmap,
                quad = quad.ordered(),
                fit = fit,
                onQuadChange = onQuadChange,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onRedetect, enabled = !detecting) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White)
                Text(stringResource(R.string.photosolve_auto_detect), color = Color.White)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = quad.isValid && !detecting,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Text(stringResource(R.string.photosolve_quad_confirm))
            }
        }
    }
}

/** 图片 Fit 之后的矩形（与裁剪页同一套算法）。 */
private fun fitRect(srcW: Int, srcH: Int, boxW: Float, boxH: Float): androidx.compose.ui.geometry.Rect {
    if (srcW <= 0 || srcH <= 0) return androidx.compose.ui.geometry.Rect(0f, 0f, boxW, boxH)
    val ratio = srcW.toFloat() / srcH
    val (w, h) = if (boxW / boxH > ratio) boxH * ratio to boxH else boxW to boxW / ratio
    val left = (boxW - w) / 2f
    val top = (boxH - h) / 2f
    return androidx.compose.ui.geometry.Rect(left, top, left + w, top + h)
}

@Composable
private fun QuadCanvas(
    bitmap: Bitmap,
    quad: QuadNorm,
    fit: androidx.compose.ui.geometry.Rect,
    onQuadChange: (QuadNorm) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    val cornerRadius = with(density) { 28.dp.toPx() }
    val edgeRadius = with(density) { 22.dp.toPx() }
    val latestFit by rememberUpdatedState(fit)
    val latestQuad by rememberUpdatedState(quad)
    // 当前正被拖动的角或边
    var active by remember { mutableStateOf<QuadHandle?>(null) }

    Box(modifier) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(bitmap) {
                    detectDragGestures(
                        onDragStart = { p ->
                            val f = latestFit
                            active = QuadEdit.hitTest(
                                quad = latestQuad,
                                x = (p.x - f.left),
                                y = (p.y - f.top),
                                width = f.width,
                                height = f.height,
                                cornerRadius = cornerRadius,
                                edgeRadius = edgeRadius,
                            )
                        },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                        onDrag = { change, amount ->
                            val handle = active ?: return@detectDragGestures
                            change.consume()
                            val f = latestFit
                            if (f.width <= 0f || f.height <= 0f) return@detectDragGestures
                            when (handle) {
                                is QuadHandle.Corner -> {
                                    val corners = latestQuad.corners.toMutableList()
                                    val c = corners[handle.index]
                                    corners[handle.index] = QuadNorm.Point(
                                        (c.x + amount.x / f.width).coerceIn(0f, 1f),
                                        (c.y + amount.y / f.height).coerceIn(0f, 1f),
                                    )
                                    val next = QuadNorm(corners).ordered()
                                    if (next.isValid) onQuadChange(next)
                                }
                                is QuadHandle.Edge -> {
                                    // 边在像素空间里沿法线移动；不重新排序角点，拖动过程中边的编号才不会漂移
                                    QuadEdit.moveEdge(latestQuad, handle.index, amount.x, amount.y, f.width, f.height)
                                        ?.let(onQuadChange)
                                }
                            }
                        },
                    )
                },
        ) {
            val f = fit
            fun toScreen(p: QuadNorm.Point) = Offset(f.left + p.x * f.width, f.top + p.y * f.height)

            val pts = quad.corners.map(::toScreen)
            if (pts.size == 4) {
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until 4) lineTo(pts[i].x, pts[i].y)
                    close()
                }
                // 四边形外变暗：用四块矩形近似（不做裁剪路径，实现简单且不会吃掉性能）
                val dim = Color.Black.copy(alpha = 0.5f)
                drawRect(dim, Offset.Zero, androidx.compose.ui.geometry.Size(size.width, f.top))
                drawRect(dim, Offset(0f, f.bottom), androidx.compose.ui.geometry.Size(size.width, size.height - f.bottom))
                drawRect(dim, Offset(0f, f.top), androidx.compose.ui.geometry.Size(f.left, f.height))
                drawRect(dim, Offset(f.right, f.top), androidx.compose.ui.geometry.Size(size.width - f.right, f.height))

                drawPath(path, Color.White.copy(alpha = 0.10f))
                drawPath(path, Color.White, style = Stroke(width = 2.dp.toPx()))

                // 正在拖的那条边：加粗高亮，用户能看到自己抓住的是哪条
                (active as? QuadHandle.Edge)?.let { edge ->
                    drawLine(
                        color = Color(0xFFFFCC00),
                        start = pts[edge.index],
                        end = pts[(edge.index + 1) % 4],
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // 四条边的中点手柄：暗示"边也能拖"。药丸形，长边与所在边平行
                for (i in 0 until 4) {
                    val a = pts[i]
                    val b = pts[(i + 1) % 4]
                    val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                    val angle = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
                    val isActiveEdge = (active as? QuadHandle.Edge)?.index == i
                    val half = androidx.compose.ui.geometry.Size(
                        (if (isActiveEdge) 22.dp else 18.dp).toPx(),
                        (if (isActiveEdge) 7.dp else 6.dp).toPx(),
                    )
                    rotate(angle, pivot = mid) {
                        drawRoundRect(
                            color = if (isActiveEdge) Color(0xFFFFCC00) else Color.White,
                            topLeft = Offset(mid.x - half.width, mid.y - half.height / 2f),
                            size = androidx.compose.ui.geometry.Size(half.width * 2f, half.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(half.height / 2f),
                        )
                    }
                }

                // 四个角点
                pts.forEachIndexed { index, p ->
                    val isActive = (active as? QuadHandle.Corner)?.index == index
                    drawCircle(
                        color = if (isActive) Color(0xFFFFCC00) else Color.White,
                        radius = if (isActive) 11.dp.toPx() else 9.dp.toPx(),
                        center = p,
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.35f),
                        radius = if (isActive) 11.dp.toPx() else 9.dp.toPx(),
                        center = p,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    // 从角点往框内画一小段，提示拖动方向
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = p,
                        end = Offset(
                            (p.x + (f.left + f.width / 2f - p.x) * 0.08f),
                            (p.y + (f.top + f.height / 2f - p.y) * 0.08f),
                        ),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/** 把四边形交给 ViewModel 时用它包一层，避免调用方重复写 `QuadNorm(list)`。 */
fun CropNorm.toQuad(): QuadNorm = QuadNorm(
    listOf(
        QuadNorm.Point(left, top),
        QuadNorm.Point(right, top),
        QuadNorm.Point(right, bottom),
        QuadNorm.Point(left, bottom),
    ),
)
