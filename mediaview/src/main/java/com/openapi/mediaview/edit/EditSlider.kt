package com.openapi.mediaview.edit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * A34：**参数控件的轨道就是该参数的预览**（台账抽象点，与 A38 同族）。
 *
 * 通用 `Slider` 的等粗轨道体现不出「往哪边拖是大」——所以粗细/字号/笔刷三根滑杆换成
 * 自定义轨道：轨道本身按当前值画成渐变（线从细到粗、字从小到大、方块从小到大），
 * 拇指旁实时显示数值。几何映射全部在 [EditSliderTrack]（纯函数，可单测，见
 * EditSliderTrackTest），Compose 层只负责画和把手势转成值。
 */
enum class TrackPreview {
    /** 画笔/涂鸦粗细：轨道是一条从细到粗的线。 */
    Thickness,

    /** 文字大小：轨道上是从小到大的「A」。 */
    FontSize,

    /** 马赛克笔刷：轨道上是一排从小到大的方块（块边长 = 打码粒度）。 */
    MosaicBlock,
}

/**
 * 预览滑杆的纯几何。三条不变量都有测试钉住：
 * - [fraction] 单调、夹在 0..1，range 退化为 0；
 * - [samples] 含两端点、单调不减；
 * - [visualSizePx] 单调、落在 [minPx, maxPx]，最细的一端仍可见（≥ minPx）。
 */
object EditSliderTrack {

    /** 拇指位置（0..1）。range 退化时返回 0。 */
    fun fraction(value: Float, range: ClosedFloatingPointRange<Float>): Float {
        val span = range.endInclusive - range.start
        if (span <= 0f) return 0f
        return ((value - range.start) / span).coerceIn(0f, 1f)
    }

    /** 档位化：把连续值吸到含端点共 [steps] + 1 个位置之一（与 Material `steps` 同口径）。 */
    fun snap(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
        val clamped = value.coerceIn(range.start, range.endInclusive)
        if (steps <= 0) return clamped
        val k = (fraction(clamped, range) * steps).roundToInt().coerceIn(0, steps)
        return range.start + (range.endInclusive - range.start) * k / steps
    }

    /** 在 [range] 上均匀取 [count] 个采样值（含两端点），单调不减。 */
    fun samples(range: ClosedFloatingPointRange<Float>, count: Int): List<Float> {
        if (count <= 0) return emptyList()
        if (count == 1) return listOf(range.start)
        val span = range.endInclusive - range.start
        return List(count) { i -> range.start + span * i / (count - 1f) }
    }

    /**
     * 采样值 → 轨道上该点的视觉尺寸（px）：按 [fraction] 线性映射进 [minPx, maxPx]。
     * 这就是「拖动的减变动过程」：手指走过的每一像素，轨道线宽跟着连续变化。
     */
    fun visualSizePx(value: Float, range: ClosedFloatingPointRange<Float>, maxPx: Float, minPx: Float): Float {
        val lo = minOf(minPx, maxPx)
        return (lo + (maxPx - lo) * fraction(value, range)).coerceIn(lo, maxOf(minPx, maxPx))
    }
}

/**
 * 横向「按下即取值 → 拖动跟手 → 松手收尾」的一个手势循环，[PreviewSlider] 与
 * [PhotoEditor] 的色相光谱条共用。
 *
 * **为什么不用 `detectTapGestures` + `detectDragGestures` 两个检测器**：写进同一个
 * `pointerInput` 块时它们是**顺序**执行的，而 `detectTapGestures` 内部是个永不返回的循环，
 * 排在它后面的拖拽检测器永远轮不到——编译不报错，行为上就是「只能点不能拖」。
 * 一个循环同时覆盖点与拖：落下就取值（= 点轨道跳到该值，和 Material Slider 一致），
 * 每次移动都取值（= 连续吐值，A34 的「减变动过程」靠的就是这个），主指针抬起收尾一次。
 *
 * 每个变化都 [consume]：面板整体在 `verticalScroll` 里，不消费的话纵向分量会被嵌套滚动抢走，
 * 手指斜着拖就变成滚面板。
 *
 * `@Composable` + [rememberUpdatedState]：手势块只按 Unit 起一次，直接捕获调用方的 lambda
 * 就会停在首次组合的那一份（Material Slider 内部也是这么保活的）。
 */
@Composable
internal fun Modifier.onHorizontalScrub(
    onValue: (x: Float, widthPx: Float) -> Unit,
    onFinished: () -> Unit = {},
): Modifier {
    val currentOnValue = rememberUpdatedState(onValue)
    val currentOnFinished = rememberUpdatedState(onFinished)
    return pointerInput(Unit) {
        awaitEachGesture {
            val width = size.width.toFloat()
            val down = awaitFirstDown(requireUnconsumed = false)
            val id = down.id
            down.consume()
            currentOnValue.value(down.position.x, width)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == id } ?: break
                if (!change.pressed) break
                change.consume()
                currentOnValue.value(change.position.x, width)
            }
            currentOnFinished.value()
        }
    }
}

// 轨道尺寸常量（见 PreviewSlider 的画布）。
private val TRACK_ROW_HEIGHT = 40.dp
private val TRACK_THUMB = 18.dp
private const val TRACK_LINE_MAX = 10f   // dp：粗细轨道最粗端线宽
private const val TRACK_LINE_MIN = 1f    // 最细端（必须看得见）
private const val TRACK_FONT_MAX = 18f   // dp：字号轨道最大「A」的字号
private const val TRACK_FONT_MIN = 7f
private const val TRACK_BLOCK_MAX = 12f  // dp：马赛克轨道最大块边长
private const val TRACK_BLOCK_MIN = 3f
private const val TRACK_SAMPLES = 24
private const val TRACK_BLOCK_ITEMS = 7

/**
 * 轨道即预览的滑杆（A34）。拇指行程与 Material Slider 同约定：轨道宽 − 拇指直径。
 *
 * @param steps > 0 时按 [EditSliderTrack.snap] 档位化（StylePanel 的 5 档语义不变）；
 *   0 = 连续（SelectionPanel 用）。连续模式松手走 [onValueChangeFinished]（一步撤销）。
 */
@Composable
fun PreviewSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    preview: TrackPreview,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val thumbPx = with(density) { TRACK_THUMB.toPx() }
    val trackColor = Color.White.copy(alpha = 0.35f)
    val fill = Color.White.copy(alpha = 0.85f)
    val labelStyle = TextStyle(fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f))
    val textMeasurer = rememberTextMeasurer()

    // 按下点/拖动点 → 值：先按拇指行程归一，再按 steps 档位化。
    // 手势层与绘制层用同一个 fraction 口径，拇指和轨道渐变才不会错位。
    fun pick(x: Float, widthPx: Float): Float {
        val travel = (widthPx - thumbPx).coerceAtLeast(1f)
        val t = ((x - thumbPx / 2f) / travel).coerceIn(0f, 1f)
        return EditSliderTrack.snap(range.start + (range.endInclusive - range.start) * t, range, steps)
    }

    BoxWithConstraints(modifier.fillMaxWidth().height(TRACK_ROW_HEIGHT)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val centerY = with(density) { (TRACK_ROW_HEIGHT / 2).toPx() }

        Canvas(
            Modifier
                .fillMaxSize()
                .onHorizontalScrub(
                    onValue = { x, w -> onValueChange(pick(x, w)) },
                    onFinished = { onValueChangeFinished?.invoke() },
                ),
        ) {
            val minPx = with(density) {
                when (preview) {
                    TrackPreview.Thickness -> TRACK_LINE_MIN
                    TrackPreview.FontSize -> TRACK_FONT_MIN
                    TrackPreview.MosaicBlock -> TRACK_BLOCK_MIN
                }
            }.dp.toPx()
            val maxPx = with(density) {
                when (preview) {
                    TrackPreview.Thickness -> TRACK_LINE_MAX
                    TrackPreview.FontSize -> TRACK_FONT_MAX
                    TrackPreview.MosaicBlock -> TRACK_BLOCK_MAX
                }
            }.dp.toPx()
            val thumbX = thumbPx / 2f + EditSliderTrack.fraction(value, range) * (size.width - thumbPx)

            when (preview) {
                TrackPreview.Thickness -> drawThicknessTrack(range, minPx, maxPx, centerY, trackColor, fill, thumbX)
                TrackPreview.FontSize -> drawFontTrack(range, minPx, maxPx, centerY, trackColor, textMeasurer, density)
                TrackPreview.MosaicBlock -> drawBlockTrack(range, minPx, maxPx, centerY, trackColor)
            }

            // 拇指：白圆 + 细描边
            drawCircle(fill, radius = thumbPx / 2f - 1f, center = Offset(thumbX, centerY))
            drawCircle(
                Color.Black.copy(alpha = 0.4f), radius = thumbPx / 2f - 1f,
                center = Offset(thumbX, centerY),
                style = Stroke(width = with(density) { 1.dp.toPx() }),
            )

            // 拇指正上方的实时数值：档位模式显示档位（1..5），连续模式显示百分比。
            val label = if (steps > 0) {
                (EditSliderTrack.fraction(value, range) * steps).roundToInt().plus(1).toString()
            } else {
                "${(EditSliderTrack.fraction(value, range) * 100f).roundToInt()}%"
            }
            val layout = textMeasurer.measure(label, labelStyle)
            drawText(
                layout,
                topLeft = Offset(
                    (thumbX - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width),
                    centerY - thumbPx / 2f - layout.size.height,
                ),
            )
        }
    }
}

/** 粗细轨道：切成 [TRACK_SAMPLES] 段，每段按该点的值取线宽 → 一条从细到粗的连续线。 */
private fun DrawScope.drawThicknessTrack(
    range: ClosedFloatingPointRange<Float>,
    minPx: Float,
    maxPx: Float,
    centerY: Float,
    trackColor: Color,
    fill: Color,
    thumbX: Float,
) {
    val segW = size.width / (TRACK_SAMPLES - 1)
    for (v in EditSliderTrack.samples(range, TRACK_SAMPLES)) {
        val x = EditSliderTrack.fraction(v, range) * size.width
        drawLine(
            if (x <= thumbX) fill else trackColor,
            Offset(x, centerY), Offset(x + segW, centerY),
            strokeWidth = EditSliderTrack.visualSizePx(v, range, maxPx, minPx),
            cap = StrokeCap.Butt,
        )
    }
}

/** 字号轨道：三个「A」从小到大（小/中/大各占 0%、50%、100% 处），一眼看出往右拖字变大。 */
private fun DrawScope.drawFontTrack(
    range: ClosedFloatingPointRange<Float>,
    minPx: Float,
    maxPx: Float,
    centerY: Float,
    trackColor: Color,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    density: Density,
) {
    for (v in EditSliderTrack.samples(range, 3)) {
        val x = EditSliderTrack.fraction(v, range) * size.width
        val px = EditSliderTrack.visualSizePx(v, range, maxPx, minPx)
        val layout = textMeasurer.measure("A", TextStyle(fontSize = with(density) { px.toSp() }, color = trackColor))
        drawText(layout, topLeft = Offset(x - layout.size.width / 2f, centerY - layout.size.height / 2f))
    }
}

/** 马赛克轨道：一排方块从小到大，块边长就是打码粒度。 */
private fun DrawScope.drawBlockTrack(
    range: ClosedFloatingPointRange<Float>,
    minPx: Float,
    maxPx: Float,
    centerY: Float,
    trackColor: Color,
) {
    val gap = size.width / (TRACK_BLOCK_ITEMS + 1)
    EditSliderTrack.samples(range, TRACK_BLOCK_ITEMS).forEachIndexed { i, v ->
        val side = EditSliderTrack.visualSizePx(v, range, maxPx, minPx)
        drawRect(trackColor, topLeft = Offset(gap * (i + 1) - side / 2f, centerY - side / 2f), size = Size(side, side))
    }
}
