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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openapi.feature.photosolve.R
import com.openapi.imaging.CropNorm
import com.openapi.imaging.quad.QuadNorm
import kotlin.math.hypot

/**
 * 四角微调页：把检测到的题目边框画在照片上，用户拖四个角对准纸面边缘。
 *
 * 交互要点：
 * - 角点有 44dp 的触摸热区（比视觉圆点大），按下的瞬间就能抓住，不需要先移动；
 * - 角点被拖到图片外时夹在边界上，不会「飞走」；
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
    val touchRadius = with(density) { 28.dp.toPx() }
    val latestFit by rememberUpdatedState(fit)
    val latestQuad by rememberUpdatedState(quad)
    // 当前正被拖动的角
    var active by remember { mutableStateOf<Int?>(null) }

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
                            active = nearestCorner(p, latestQuad, latestFit, touchRadius)
                        },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                        onDrag = { change, amount ->
                            val index = active ?: return@detectDragGestures
                            change.consume()
                            val f = latestFit
                            if (f.width <= 0f || f.height <= 0f) return@detectDragGestures
                            val dx = amount.x / f.width
                            val dy = amount.y / f.height
                            val corners = latestQuad.corners.toMutableList()
                            val c = corners[index]
                            corners[index] = QuadNorm.Point(
                                (c.x + dx).coerceIn(0f, 1f),
                                (c.y + dy).coerceIn(0f, 1f),
                            )
                            val next = QuadNorm(corners).ordered()
                            if (next.isValid) onQuadChange(next)
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

                // 四个角点
                pts.forEachIndexed { index, p ->
                    val isActive = index == active
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

/** 找离按下点最近的角（在 [radius] 内），否则 null。 */
private fun nearestCorner(
    p: Offset,
    quad: QuadNorm,
    fit: androidx.compose.ui.geometry.Rect,
    radius: Float,
): Int? {
    var best: Int? = null
    var bestDistance = Float.MAX_VALUE
    quad.corners.forEachIndexed { index, point ->
        val x = fit.left + point.x * fit.width
        val y = fit.top + point.y * fit.height
        val d = hypot((p.x - x).toDouble(), (p.y - y).toDouble()).toFloat()
        if (d < bestDistance) {
            bestDistance = d
            best = index
        }
    }
    return if (bestDistance <= radius) best else null
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
