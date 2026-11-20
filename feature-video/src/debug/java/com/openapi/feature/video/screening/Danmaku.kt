package com.openapi.feature.video.screening

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * 弹幕：一条按**媒体时间轴**调度的纯文本滚动轨。
 *
 * 与设计稿一致的三条硬规则：
 * - 位置由播放位置算出来，不用墙钟：暂停就冻住，倍速跟着媒体时钟走，拖动进度条立刻换成新位置附近的弹幕，
 *   不会有「暂停时弹幕还在飘」或「seek 之后旧弹幕残留」。
 * - 车道分配在**媒体时间**里一次算完，所以变速不需要重新排布，同一时刻两行不会叠在一起。
 * - 只用一块 Canvas 画，不逐条起 Compose 布局：条数多的时候也不会把每帧的测量拖垮。
 *
 * 数据从哪来（本地样例 / 服务端 / 注入的假服务端）由 [DanmakuSource.kt] 的 `TimedCommentSource` 决定，
 * 这里只吃 [DanmakuState]：换来源不改渲染，见 `docs/design/media-suite.md`。
 */
internal data class TimedComment(val id: Int, val atMs: Long, val text: String, val author: String = "")

/** 一条弹幕横向走完全程需要的**媒体时间**；变速时整体等比缩短或拉长，不用单独处理。 */
private const val TRAVEL_MS = 6500L

private val DanmakuStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
private val DanmakuStroke = DanmakuStyle.copy(drawStyle = Stroke(width = 3f))

/** 排好车道、量好宽度的一条弹幕，之后每帧只算 x。 */
private class Track(
    val text: String,
    val startMs: Long,
    val lane: Int,
    val fill: TextLayoutResult,
    val stroke: TextLayoutResult,
)

/**
 * [clock] 每帧读一次播放位置：控件层的 125ms 同步不够顺滑，这里自己跟帧走，
 * 但只有这块 Canvas 会重组，页面其它部分不受影响。
 * [state] 来自 [DanmakuFeed]：来源（本地样例 / 服务端 / 注入实现）已经在它后面被换掉了，
 * 渲染这一段对来源无感知。
 */
@Composable
internal fun DanmakuOverlay(clock: () -> Long, state: DanmakuState, modifier: Modifier = Modifier) {
    val comments = state.comments
    val measurer = rememberTextMeasurer()
    var box by remember { mutableStateOf(IntSize.Zero) }
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            now = clock()
        }
    }
    // 车道和宽度只跟尺寸、字号有关，跟播放位置无关：位置变了不重新排版
    val tracks = remember(box, comments, measurer) {
        if (box.width <= 0 || box.height <= 0) emptyList() else layout(comments, measurer, box)
    }
    Canvas(modifier.onSizeChanged { box = it }) {
        val width = size.width
        val height = size.height
        if (width <= 0f || tracks.isEmpty()) return@Canvas
        val laneHeight = height / LANES
        for (track in tracks) {
            val elapsed = now - track.startMs
            if (elapsed < 0 || elapsed > TRAVEL_MS) continue
            val span = width + track.fill.size.width
            val x = width - span * (elapsed.toFloat() / TRAVEL_MS)
            val top = track.lane * laneHeight + (laneHeight - track.fill.size.height) / 2f
            if (top + track.fill.size.height > height) continue
            // 先描边再填色：压在亮天空上也读得清，和播放控件区分得开
            drawText(track.stroke, topLeft = Offset(x, top), color = Color(0xB3000000))
            drawText(track.fill, topLeft = Offset(x, top), color = Color.White)
        }
    }
}

/** 车道数。弹幕固定待在上半区，底部永远留给字幕和控件，两边不会互相压。 */
private const val LANES = 5

/** 相邻两条弹幕的尾巴之间至少留这么多，别首尾相接成一长串。 */
private const val TAIL_GAP = 36f

/** 一条弹幕排到的车道。 */
internal data class LaneAssignment(val comment: TimedComment, val lane: Int)

/**
 * 纯车道调度（[docs/design/media-suite.md] 里的密度上限 + 碰撞避让规则）：
 * 按媒体时间顺序，一条弹幕「尾巴完全进场」之后同一车道才接下一条，全满就丢弃。
 * 不碰 Compose，文字宽度由 [textWidthOf] 给，可以单独跑 JVM 单测（同 LocalSubtitleTrack 的做法）。
 */
internal fun planLanes(
    comments: List<TimedComment>,
    width: Float,
    lanes: Int,
    travelMs: Long,
    tailGap: Float,
    textWidthOf: (TimedComment) -> Float,
): List<LaneAssignment> {
    val freeAt = LongArray(lanes) { Long.MIN_VALUE }
    val plan = ArrayList<LaneAssignment>(comments.size)
    for (comment in comments.sortedBy { it.atMs }) {
        var lane = -1
        for (index in 0 until lanes) {
            if (comment.atMs >= freeAt[index]) { lane = index; break }
        }
        // 车道全满就丢掉这条：宁可不显示，也不要几条叠在同一个位置糊成一团
        if (lane < 0) continue
        val textWidth = textWidthOf(comment)
        // 尾巴进场所需的时间与它占的宽度成比例
        val tailIn = travelMs * textWidth / (width + textWidth)
        freeAt[lane] = comment.atMs + tailIn.toLong() + (tailGap / (width + textWidth) * travelMs).toLong()
        plan.add(LaneAssignment(comment, lane))
    }
    return plan
}

/**
 * 量宽度并落成可绘制的 [Track]。排布本身在媒体时间里一次算完，
 * 所以暂停、变速、seek 都不需要重排。
 */
private fun layout(comments: List<TimedComment>, measurer: TextMeasurer, box: IntSize): List<Track> {
    val height = box.height.toFloat()
    val laneHeight = height / LANES
    // 单条占不到车道高度时宁可少画一行，也不要压到上一行
    val probe = measurer.measure(AnnotatedString("弹"), DanmakuStyle)
    val usable = laneHeight >= probe.size.height * 1.15f
    if (!usable) return emptyList()

    val fillOf: (TimedComment) -> TextLayoutResult = { measurer.measure(AnnotatedString(it.text), DanmakuStyle) }
    return planLanes(comments, box.width.toFloat(), LANES, TRAVEL_MS, TAIL_GAP) {
        fillOf(it).size.width.toFloat()
    }.map { (comment, lane) ->
        Track(comment.text, comment.atMs, lane, fillOf(comment), measurer.measure(AnnotatedString(comment.text), DanmakuStroke))
    }
}
