package com.openapi.ks.myapp.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 触摸命中区路由（超越 OLLMV 第四刀 C6）——纯决策层。
 *
 * 语义钉死自 `docs/research/2026-11-28-waifu-desktop-pet-deepread.md` §1 结论 1 +
 * §10.3 收紧 2 的四条纪律：
 * 1. 区域自带**稳定 id**（声明里写的），绝不复用 Cubism `HitAreas[].Name`——空名/重名
 *    会静默塌表（pixi `InternalModel.ts:170-176`），路由的是语义 id 而不是几何；
 * 2. 重叠必须按**显式 priority** 决出胜者，不是各家那种「未声明的第一个赢」；
 * 3. 边界是半开区间 `[min, max)`（dsh-pet `src/gameplay.ts:938-941` 同语义），
 *    相邻区不双算；
 * 4. 区域附着在**模型**上，不附着在动作/动画上——所以路由只吃归一化坐标 + 声明，
 *    不吃当前播放的 motion。
 *
 * 内置分区（无声明时的兜底）必须与 `live2dTapZone` 的真机实测阈值一致——换实现不换手感。
 */
class TouchZoneRouterTest {

    private fun rect(x0: Double, y0: Double, x1: Double, y1: Double) = TouchRect(x0, y0, x1, y1)

    private fun zone(id: String, priority: Int, vararg rects: TouchRect) =
        TouchReactionZone(id = id, label = id, priority = priority, rects = rects.toList(), reactions = emptyList())

    private fun decl(vararg zones: TouchReactionZone) = TouchReactionDeclaration(zones = zones.toList())

    @Test
    fun `built-in partition matches the measured live2d bands`() {
        // 四个取样点与 Live2dEngineTouchTest / live2dTapZone 用的是同一批真机坐标。
        val points = listOf(
            Triple(0.5f, 0.10f, "head"),
            Triple(0.5f, 0.45f, "body"),
            Triple(0.34f, 0.50f, "hand"),
            Triple(0.47f, 0.65f, "foot"),
            Triple(0.90f, 0.50f, "hand"),
            Triple(0.5f, 0.99f, "foot"),
        )
        points.forEach { (x, y, expected) ->
            assertEquals("($x,$y)", expected, TouchZoneRouter.hit(TouchZoneRouter.builtIn, x, y)?.id)
            // 兜底与旧的硬编码判据逐点同解——换刀不换分区手感。
            assertEquals("($x,$y) vs live2dTapZone", expected, live2dTapZone(x, y))
        }
    }

    @Test
    fun `overlap resolves by declared priority not declaration order`() {
        // 两条都覆盖 (0.5,0.45)：声明顺序 body 在前，priority 说 head 赢。
        val d = decl(
            zone("body", 3, rect(0.0, 0.0, 1.0, 1.0)),
            zone("head", 0, rect(0.4, 0.4, 0.6, 0.5)),
        )
        assertEquals("head", TouchZoneRouter.hit(d, 0.5f, 0.45f)?.id)
        // 反过来声明顺序，结果不变——胜负只由 priority 决定。
        val flipped = decl(
            zone("head", 0, rect(0.4, 0.4, 0.6, 0.5)),
            zone("body", 3, rect(0.0, 0.0, 1.0, 1.0)),
        )
        assertEquals("head", TouchZoneRouter.hit(flipped, 0.5f, 0.45f)?.id)
    }

    @Test
    fun `same priority falls back to declaration order deterministically`() {
        val d = decl(
            zone("first", 1, rect(0.0, 0.0, 1.0, 1.0)),
            zone("second", 1, rect(0.0, 0.0, 1.0, 1.0)),
        )
        assertEquals("first", TouchZoneRouter.hit(d, 0.5f, 0.5f)?.id)
    }

    @Test
    fun `bands are half-open so neighbours never both claim the seam`() {
        val d = decl(
            zone("head", 0, rect(0.0, 0.0, 1.0, 0.42)),
            zone("body", 1, rect(0.0, 0.42, 1.0, 0.57)),
            zone("foot", 2, rect(0.0, 0.57, 1.0, 1.0)),
        )
        assertEquals("head", TouchZoneRouter.hit(d, 0.5f, 0.4199f)?.id)
        assertEquals("body", TouchZoneRouter.hit(d, 0.5f, 0.42f)?.id)
        assertEquals("body", TouchZoneRouter.hit(d, 0.5f, 0.5699f)?.id)
        assertEquals("foot", TouchZoneRouter.hit(d, 0.5f, 0.57f)?.id)
    }

    @Test
    fun `zone ids are the declaration's own, not Cubism hit area names`() {
        // 一个 id 与任何 HitArea 名无关的声明也必须原样返回——语义 id 是防抖/仲裁的 key。
        val d = decl(zone("抚摸头顶", 0, rect(0.0, 0.0, 1.0, 0.3)))
        assertEquals("抚摸头顶", TouchZoneRouter.hit(d, 0.5f, 0.1f)?.id)
    }

    @Test
    fun `a point inside no rect yields null so empty space does not fire`() {
        val d = decl(zone("head", 0, rect(0.0, 0.0, 1.0, 0.42)))
        assertNull(TouchZoneRouter.hit(d, 0.5f, 0.9f))
        assertNull(TouchZoneRouter.hit(decl(), 0.5f, 0.5f))
        assertNull(TouchZoneRouter.hit(null, 0.5f, 0.5f))
    }

    @Test
    fun `out-of-range coordinates are clamped not dropped`() {
        val d = decl(zone("head", 0, rect(0.0, 0.0, 1.0, 0.42)))
        assertEquals("head", TouchZoneRouter.hit(d, -0.4f, -2f)?.id)
        assertEquals("head", TouchZoneRouter.hit(d, 1.7f, 0.0f)?.id)
    }

    @Test
    fun `multi-rect zone covers both arms of the real model`() {
        // 手在两臂：一个区两条矩形，正是「几何要超出 rect」的最小答案。
        val hand = TouchReactionZone(
            id = "hand", label = "手", priority = 2,
            rects = listOf(rect(0.0, 0.42, 0.38, 0.57), rect(0.62, 0.42, 1.0, 0.57)),
            reactions = emptyList(),
        )
        assertEquals("hand", TouchZoneRouter.hit(decl(hand), 0.10f, 0.50f)?.id)
        assertEquals("hand", TouchZoneRouter.hit(decl(hand), 0.95f, 0.50f)?.id)
        assertNull(TouchZoneRouter.hit(decl(hand), 0.50f, 0.50f))
    }
}
