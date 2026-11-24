package com.openapi.feature.photosolve.ui

import android.graphics.Bitmap
import android.graphics.Matrix
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
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import com.openapi.imaging.cropassist.CropAssistResult
import com.openapi.imaging.CropNorm
import com.openapi.imaging.cropassist.CropAssistRegistry
import com.openapi.feature.photosolve.R

/**
 * 裁剪覆盖层：图片按 Fit 显示，框外变暗；四角角标、四边中点粗横线。
 * [norm] 是相对图片的 0..1 坐标；拖动结果通过 [onNormChange] 回传。
 */
@Composable
internal fun CropOverlay(
    bitmap: Bitmap,
    norm: CropNorm,
    onNormChange: (CropNorm) -> Unit,
) {
    val density = LocalDensity.current
    val handleRadius = with(density) { 36.dp.toPx() }
    val minSide = 0.12f
    val current by rememberUpdatedState(norm)
    val imageBitmap: ImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 图片在容器里的实际位置（Fit 之后的矩形，像素坐标）
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val fitW: Float
        val fitH: Float
        if (containerW / containerH > imageRatio) {
            fitH = containerH
            fitW = containerH * imageRatio
        } else {
            fitW = containerW
            fitH = containerW / imageRatio
        }
        val originX = (containerW - fitW) / 2f
        val originY = (containerH - fitH) / 2f
        val cropRect = Rect(
            left = originX + current.left * fitW,
            top = originY + current.top * fitH,
            right = originX + current.right * fitW,
            bottom = originY + current.bottom * fitH,
        )

        // 手势回调里要读到最新的框（pointerInput 的 key 不变，闭包不会自动更新）
        val latestRect by rememberUpdatedState(cropRect)
        val latestFit by rememberUpdatedState(floatArrayOf(originX, originY, fitW, fitH))

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
                    var grabbed: Grab? = null
                    detectDragGestures(
                        onDragStart = { start ->
                            grabbed = pickGrab(start, latestRect, handleRadius)
                        },
                        onDragEnd = { grabbed = null },
                        onDragCancel = { grabbed = null },
                        onDrag = { change, amount ->
                            change.consume()
                            val g = grabbed ?: return@detectDragGestures
                            val fw = latestFit[2]
                            val fh = latestFit[3]
                            val dx = amount.x / fw
                            val dy = amount.y / fh
                            onNormChange(g.apply(current, dx, dy, minSide))
                        },
                    )
                },
        ) {
            // 框外变暗
            val dim = Color.Black.copy(alpha = 0.55f)
            drawRect(dim, topLeft = Offset.Zero, size = Size(size.width, cropRect.top))
            drawRect(dim, topLeft = Offset(0f, cropRect.bottom), size = Size(size.width, size.height - cropRect.bottom))
            drawRect(dim, topLeft = Offset(0f, cropRect.top), size = Size(cropRect.left, cropRect.height))
            drawRect(dim, topLeft = Offset(cropRect.right, cropRect.top), size = Size(size.width - cropRect.right, cropRect.height))

            // 框线（细）
            drawRect(
                Color.White.copy(alpha = 0.85f),
                topLeft = cropRect.topLeft,
                size = cropRect.size,
                style = Stroke(width = 1.dp.toPx()),
            )

            // 四角粗角标：L 形，长 22dp，粗 4dp，末端圆头
            val corner = 22.dp.toPx()
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            val l = cropRect.left
            val t = cropRect.top
            val r = cropRect.right
            val b = cropRect.bottom
            drawCorner(l, t, corner, 1f, 1f, stroke)
            drawCorner(r, t, corner, -1f, 1f, stroke)
            drawCorner(l, b, corner, 1f, -1f, stroke)
            drawCorner(r, b, corner, -1f, -1f, stroke)

            // 每条边中点一根粗横线（设计稿：中间粗横线）
            val bar = 4.dp.toPx()
            val half = 16.dp.toPx()
            val cx = (l + r) / 2f
            val cy = (t + b) / 2f
            drawLine(Color.White, Offset(cx - half, t), Offset(cx + half, t), strokeWidth = bar, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(cx - half, b), Offset(cx + half, b), strokeWidth = bar, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(l, cy - half), Offset(l, cy + half), strokeWidth = bar, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(r, cy - half), Offset(r, cy + half), strokeWidth = bar, cap = StrokeCap.Round)
        }
    }
}

/** 抓取的对象：四个角、四条边（移动一条边），或框内整体平移。 */
internal sealed interface Grab {
    data object TopLeft : Grab
    data object TopRight : Grab
    data object BottomLeft : Grab
    data object BottomRight : Grab
    data object Top : Grab
    data object Bottom : Grab
    data object Left : Grab
    data object Right : Grab
    data object Move : Grab

    /** 按拖动量更新裁剪框，并夹在图片范围内、保持最小尺寸。 */
    fun apply(n: CropNorm, dx: Float, dy: Float, min: Float): CropNorm {
        var l = n.left
        var t = n.top
        var r = n.right
        var b = n.bottom
        when (this) {
            TopLeft -> { l += dx; t += dy }
            TopRight -> { r += dx; t += dy }
            BottomLeft -> { l += dx; b += dy }
            BottomRight -> { r += dx; b += dy }
            Top -> t += dy
            Bottom -> b += dy
            Left -> l += dx
            Right -> r += dx
            Move -> {
                val w = r - l
                val h = b - t
                l = (l + dx).coerceIn(0f, 1f - w)
                t = (t + dy).coerceIn(0f, 1f - h)
                r = l + w
                b = t + h
            }
        }
        // 夹取顺序：先把每条边各自夹进 [0,1]，再保证最小边长。
        // 旧写法 `l.coerceIn(0f, r - min)` 在 r < min 时是 coerceIn(0, 负数) → min > max，
        // Kotlin 直接抛 IllegalArgumentException：把一个角一路拖向对边就崩（真机可复现路径）。
        l = l.coerceIn(0f, 1f - min)
        r = r.coerceIn(0f, 1f).coerceAtLeast(l + min)
        t = t.coerceIn(0f, 1f - min)
        b = b.coerceIn(0f, 1f).coerceAtLeast(t + min)
        return CropNorm(l, t, r, b)
    }
}

/**
 * 按下的位置抓哪个把手。
 *
 * 判定顺序（对拇指粗、贴边的情况都不误判）：
 * 1. **角优先**：离任一角的切比雪夫距离 ≤ [radius] → 那个角。用切比雪夫（max(|dx|,|dy|)）
 *    而不是欧氏距离：贴边时手指只能落在角内侧，欧氏判定下半径被对角线吃掉，用户按 24dp
 *    已经按到角标上却抓不住（用户原话「根本无法移动」）。
 * 2. **边其次**：不在角带内、但离某条边（沿边方向上的线段）垂直距离 ≤ [radius] → 那条边。
 * 3. 只有明确落在框内部（离四边都 > [radius]）才是整体平移。
 *
 * 之前是「8 个点取最近、半径内命中，否则框内一律 Move」：角/边中点的可视尺寸远小于 36dp，
 * 落在两者之间或贴边角外侧就掉进 Move，看起来就是「裁剪框跟图片不搭噶、一拖整个框在动」。
 * 热区一律向框内侧扩展（贴边角的外侧本来在图片外，点不到），框外空白仍返回 null 不吞事件。
 */
internal fun pickGrab(p: Offset, rect: Rect, radius: Float): Grab? {
    // 1. 角（切比雪夫）
    val corners = listOf(
        Offset(rect.left, rect.top) to Grab.TopLeft,
        Offset(rect.right, rect.top) to Grab.TopRight,
        Offset(rect.left, rect.bottom) to Grab.BottomLeft,
        Offset(rect.right, rect.bottom) to Grab.BottomRight,
    )
    var best: Grab? = null
    var bestDist = Float.MAX_VALUE
    for ((pos, grab) in corners) {
        val d = maxOf(abs(pos.x - p.x), abs(pos.y - p.y))
        if (d <= radius && d < bestDist) {
            best = grab
            bestDist = d
        }
    }
    best?.let { return it }

    // 2. 边：沿该边的参数区间内、垂直距离在半径内（中点粗横线只是一半宽，热区沿整条边）
    val withinX = p.x >= rect.left - radius && p.x <= rect.right + radius
    val withinY = p.y >= rect.top - radius && p.y <= rect.bottom + radius
    if (withinX && abs(p.y - rect.top) <= radius) return Grab.Top
    if (withinX && abs(p.y - rect.bottom) <= radius) return Grab.Bottom
    if (withinY && abs(p.x - rect.left) <= radius) return Grab.Left
    if (withinY && abs(p.x - rect.right) <= radius) return Grab.Right

    // 3. 明确在框内部才整体平移
    return if (rect.contains(p)) Grab.Move else null
}

/** 画一个 L 形角标：从 (x, y) 沿 dx、dy 方向各画 [len]。 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCorner(
    x: Float,
    y: Float,
    len: Float,
    dx: Float,
    dy: Float,
    stroke: Stroke,
) {
    drawLine(Color.White, Offset(x, y), Offset(x + dx * len, y), strokeWidth = stroke.width, cap = stroke.cap)
    drawLine(Color.White, Offset(x, y), Offset(x, y + dy * len), strokeWidth = stroke.width, cap = stroke.cap)
}
