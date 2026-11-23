package com.openapi.feature.video.screening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 车道调度的 JVM 单测。排布全是纯计算，不碰 Compose，
 * 所以照 `LocalSubtitleTrackTest` 的老路子直接跑，不用真机。
 *
 * 用例里统一 width = 1000、travel = 1000，宽度写死，让「尾巴进场」的时间好算：
 * tailIn = travel × w / (width + w)。
 */
class DanmakuLaneTest {

    private fun plan(
        comments: List<TimedComment>,
        lanes: Int = 5,
        width: Float = 1000f,
        travel: Long = 1000L,
        tailGap: Float = 0f,
        textWidth: Float = 0f,
    ) = planLanes(comments, width, lanes, travel, tailGap) { textWidth }

    private fun comment(at: Long, id: Int = at.toInt()) = TimedComment(id, at, "t$id")

    @Test
    fun `排布按媒体时间顺序，跟数据顺序无关`() {
        val plan = plan(listOf(comment(3000, 3), comment(1000, 1), comment(2000, 2)))
        assertEquals(listOf(1000L, 2000L, 3000L), plan.map { it.comment.atMs })
    }

    @Test
    fun `同一时刻一起出来的弹幕各占一条车道，不互相压`() {
        val burst = listOf(comment(1500, 1), comment(1500, 2), comment(1500, 3))
        // 宽度 0 的弹幕不占位置，车道立刻让出来；真实弹幕总有宽度，这里按半屏宽算
        assertEquals(listOf(0, 1, 2), plan(burst, textWidth = 500f).map { it.lane })
    }

    @Test
    fun `尾巴完全进场之后同一车道才接下一条`() {
        // w = 1000 → tailIn = 1000×1000/2000 = 500ms：499ms 的车道还没让出来
        val tooSoon = plan(listOf(comment(0, 1), comment(499, 2)), textWidth = 1000f)
        assertEquals(listOf(0, 1), tooSoon.map { it.lane })

        val reuse = plan(listOf(comment(0, 1), comment(500, 2)), textWidth = 1000f)
        assertEquals(listOf(0, 0), reuse.map { it.lane })
    }

    @Test
    fun `弹幕越宽占用车道越久`() {
        val narrow = plan(listOf(comment(0, 1), comment(260, 2)), textWidth = 200f)
        val wide = plan(listOf(comment(0, 1), comment(260, 2)), textWidth = 800f)
        assertEquals(listOf(0, 0), narrow.map { it.lane })
        assertEquals(listOf(0, 1), wide.map { it.lane })
    }

    @Test
    fun `间隔要求不止尾巴，两条之间还要留空隙`() {
        // tailIn = 500，再加 tailGap 折算的时间；刚好卡在 tailIn 上会被挤到下一道
        val plan = plan(listOf(comment(0, 1), comment(500, 2)), width = 1000f, tailGap = 36f, textWidth = 1000f)
        assertEquals(listOf(0, 1), plan.map { it.lane })
    }

    @Test
    fun `车道排满就丢弃后来的，而不是叠在同一位置`() {
        val burst = (1..7).map { comment(1000, it) }
        // 同一条弹幕尾巴进场要 500ms，此刻三条车道全被占着 → 后四条直接丢弃
        val plan = plan(burst, lanes = 3, textWidth = 1000f)
        assertEquals(3, plan.size)
        assertEquals(listOf(1, 2, 3), plan.map { it.comment.id })
        assertEquals(listOf(0, 1, 2), plan.map { it.lane })
    }

    @Test
    fun `没有车道就全部丢弃`() {
        assertTrue(plan(listOf(comment(0)), lanes = 0).isEmpty())
        assertTrue(plan(emptyList()).isEmpty())
    }

    @Test
    fun `样例时间轴在普通手机上排得开，且长句不会互相压`() {
        // 1256px 宽的设备，弹幕区整宽；文字宽度按每字 13px 粗估
        val plan = planLanes(sampleTimeline, 1256f, 5, 6500L, 36f) { it.text.length * 13f }
        assertEquals("样例应该全部上屏", sampleTimeline.size, plan.size)
        // 同一车道上任意两条：后一条出发时，前一条的尾巴已经进场并留出空隙
        for (lane in 0 until 5) {
            val onLane = plan.filter { it.lane == lane }.sortedBy { it.comment.atMs }
            for (index in 1 until onLane.size) {
                val previous = onLane[index - 1]
                val previousTailIn = 6500f * previous.comment.text.length * 13f / (1256f + previous.comment.text.length * 13f)
                val previousGapIn = 36f / (1256f + previous.comment.text.length * 13f) * 6500f
                assertTrue(
                    "车道 $lane 上「${previous.comment.text}」还没让开，「${onLane[index].comment.text}」就出发了",
                    onLane[index].comment.atMs >= previous.comment.atMs + previousTailIn + previousGapIn - 1f,
                )
            }
        }
    }
}
