package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 触摸反应选择 + 回落链（超越 OLLMV 第四刀 C6）——纯决策层。
 *
 * 三级回落（`docs/research/2026-11-28-surpass-ollmv-synthesis.md:19` C6）：
 *  1. `DECLARED`  —— wardrobe-spec 的 `touchReactions`（数据里的反应）；
 *  2. `MODEL_OWN` —— 模型包自己声明的（model3 的 HitAreas + 动作/表情表）；
 *  3. `BUILT_IN`  —— 现状硬编码（四轴循环状态机）。
 *
 * 两个结构约束：
 * - **能力门**：候选先按运行时支持的 cue 种类过滤（当前 fork 只认表情，
 *   `JniBridgeJava` 没有 motion API）。不支持的声明不参与抽签，而不是播到一半没动静。
 * - **不发明资产**：变体 cue 只引用 wardrobe-spec 已声明的槽位/变体，落回
 *   `expressionFor` 的组合表情名空间——vendored 表情管理器同时只激活一个表情，
 *   绕开状态机直接发表情会把已激活的其它维度抹掉（Live2dTouchReaction 类注释）。
 */
class TouchReactionDecisionTest {

    private fun variant(slot: String, token: String, weight: Int = 1) =
        TouchCue(TouchCueKind.VARIANT, slot = slot, token = token, weight = weight)

    private fun cycle(slot: String, weight: Int = 1) =
        TouchCue(TouchCueKind.CYCLE, slot = slot, weight = weight)

    private fun motion(id: String, weight: Int = 1) =
        TouchCue(TouchCueKind.MOTION, motionId = id, weight = weight)

    private val expressionOnly = setOf(TouchCueKind.VARIANT, TouchCueKind.CYCLE)

    /** 「四轴全开」的资产事实：ATRI 注册的 180 个组合表情名（与 Live2dTouchReactionTest 同源算法）。 */
    private val ALL_180: Set<String> by lazy {
        buildSet {
            Live2dTop.entries.forEach { top ->
                Live2dShoes.entries.forEach { shoes ->
                    Live2dHeld.entries.forEach { held ->
                        Live2dFace.entries.forEach { face ->
                            add(Live2dTouchReaction.expressionFor(Live2dTouchReactionState(top, shoes, held, face)))
                        }
                    }
                }
            }
        }
    }

    // ---- 权重抽签（纯函数：roll 给定 → 结果确定） ----

    @Test
    fun `weighted pick honours weights over the rolled value`() {
        val cues = listOf(variant("face", "blush", 1), variant("face", "white", 3))
        assertEquals("blush", TouchReactionDecision.pickWeighted(cues, roll = 0)?.token)
        listOf(1, 2, 3).forEach { r -> assertEquals("white", TouchReactionDecision.pickWeighted(cues, roll = r)?.token) }
        // roll 落在权重和之外按取模回绕，调用方可以随便传。
        assertEquals("blush", TouchReactionDecision.pickWeighted(cues, roll = 4)?.token)
        assertEquals("white", TouchReactionDecision.pickWeighted(cues, roll = 7)?.token)
    }

    @Test
    fun `non-positive weights never win`() {
        val cues = listOf(variant("face", "blush", 0), variant("face", "white", 2))
        repeat(5) { assertEquals("white", TouchReactionDecision.pickWeighted(cues, roll = it)?.token) }
        assertNull(TouchReactionDecision.pickWeighted(listOf(variant("face", "blush", 0)), roll = 3))
        assertNull(TouchReactionDecision.pickWeighted(emptyList(), roll = 3))
    }

    @Test
    fun `last cue is excluded so repeat taps change something, but not at the cost of no reaction`() {
        val cues = listOf(variant("face", "blush", 1), variant("face", "white", 1))
        assertEquals("blush", TouchReactionDecision.pickWeighted(cues, roll = 0, excludeId = "face:white")?.token)
        assertEquals("white", TouchReactionDecision.pickWeighted(cues, roll = 5, excludeId = "face:blush")?.token)
        // 只有一个候选且它就是上次那个：宁可重复也不回「没反应」——真机投诉 2026-11-23 的教训。
        val single = listOf(cycle("face", 1))
        assertEquals("face:cycle", TouchReactionDecision.pickWeighted(single, roll = 0, excludeId = "face:cycle")?.key())
    }

    // ---- 三级回落 ----

    private val declaredHead = TouchReactionZone(
        id = "head", label = "头", priority = 0,
        rects = listOf(TouchRect(0.0, 0.0, 1.0, 0.42)),
        reactions = listOf(variant("face", "blush", 1), variant("face", "white", 1)),
    )

    @Test
    fun `declared reactions win when present`() {
        val d = TouchReactionDecision.resolve(
            hit = declaredHead,
            modelOwnCues = listOf(cycle("face")),
            supported = expressionOnly,
            lastCueKey = null,
            roll = 0,
        )
        assertEquals(ReactionLevel.DECLARED, d.level)
        assertEquals("head", d.zoneId)
        assertEquals(TouchCueKind.VARIANT, d.cue?.kind)
    }

    @Test
    fun `model-own declarations are the second level when the zone declares nothing`() {
        val emptyZone = declaredHead.copy(reactions = emptyList())
        val d = TouchReactionDecision.resolve(
            hit = emptyZone,
            modelOwnCues = listOf(motion("HeadTap"), cycle("face")),
            supported = expressionOnly,
            lastCueKey = null,
            roll = 0,
        )
        assertEquals(ReactionLevel.MODEL_OWN, d.level)
        assertEquals(TouchCueKind.CYCLE, d.cue?.kind)
    }

    @Test
    fun `capability gate drops unsupported cues and falls through to the built-in cycle`() {
        // 当前 fork 只认表情（无 motion API）：只声明 motion 的模型必须落到硬编码层，
        // 而不是回一个播不出来的 cue。
        val d = TouchReactionDecision.resolve(
            hit = declaredHead.copy(reactions = listOf(motion("HeadTap"))),
            modelOwnCues = listOf(motion("HeadTap")),
            supported = expressionOnly,
            lastCueKey = null,
            roll = 0,
        )
        assertEquals(ReactionLevel.BUILT_IN, d.level)
        assertEquals(TouchCueKind.CYCLE, d.cue?.kind)
        assertEquals("face", d.cue?.slot)
    }

    @Test
    fun `no hit zone at all falls to the built-in level with no zone`() {
        val d = TouchReactionDecision.resolve(
            hit = null,
            modelOwnCues = emptyList(),
            supported = expressionOnly,
            lastCueKey = null,
            roll = 0,
        )
        assertEquals(ReactionLevel.BUILT_IN, d.level)
        assertNull(d.zoneId)
        assertEquals("face", d.cue?.slot)
    }

    @Test
    fun `runtime that supports motion picks motion cues too`() {
        val all = setOf(TouchCueKind.VARIANT, TouchCueKind.CYCLE, TouchCueKind.MOTION)
        val d = TouchReactionDecision.resolve(
            hit = declaredHead.copy(reactions = listOf(motion("HeadTap", 4), variant("face", "blush", 1))),
            modelOwnCues = emptyList(),
            supported = all,
            lastCueKey = null,
            roll = 0,
        )
        assertEquals(ReactionLevel.DECLARED, d.level)
        assertEquals(TouchCueKind.MOTION, d.cue?.kind)
        assertEquals("HeadTap", d.cue?.motionId)
    }

    // ---- cue → 状态机：不发明资产、不抹掉已激活维度 ----

    @Test
    fun `variant cue sets its axis and keeps the other axes' expression`() {
        val base = Live2dTouchReactionState(top = Live2dTop.PAJAMAS, shoes = Live2dShoes.LEATHER)
        val s = Live2dTouchReaction.applyCue(base, variant("face", "white"))
        assertEquals(Live2dFace.WHITE, s.face)
        // 组合表情名必须自带全部已激活维度（同时只激活一个表情）。
        assertEquals("pajamas_leather_white", Live2dTouchReaction.expressionFor(s))
    }

    @Test
    fun `cycle cue advances its axis exactly like the old hardcoded step`() {
        val s0 = Live2dTouchReactionState()
        val viaCue = Live2dTouchReaction.applyCue(s0, cycle("held"))
        val viaStep = Live2dTouchReaction.step(s0, AvatarRegion.HAND)
        assertEquals(viaStep, viaCue)
        assertEquals("kani", Live2dTouchReaction.expressionFor(viaCue))
    }

    @Test
    fun `unknown slot or token is a no-op so a bad declaration cannot invent an asset`() {
        val s0 = Live2dTouchReactionState()
        assertEquals(s0, Live2dTouchReaction.applyCue(s0, variant("hat", "ribbon")))
        assertEquals(s0, Live2dTouchReaction.applyCue(s0, variant("face", "cat")))
        assertEquals(s0, Live2dTouchReaction.applyCue(s0, variant("", "")))
        assertTrue(Live2dTouchReaction.knownSlots().contains("top"))
    }

    @Test
    fun `variant cue to the current value is reported as no-change`() {
        val s = Live2dTouchReactionState(face = Live2dFace.BLUSH)
        assertEquals(s, Live2dTouchReaction.applyCue(s, variant("face", "blush")))
    }

    // ---- 完整决定（选 cue + 资产闸）：引擎真正调的是这一层 ----

    private fun tap(
        zone: TouchReactionZone,
        state: Live2dTouchReactionState,
        gate: Set<String>,
        roll: Int = 0,
        modelOwn: List<TouchCue> = emptyList(),
    ) = TouchReactionDecision.resolveTap(
        hit = zone,
        modelOwnCues = modelOwn,
        supported = expressionOnly,
        lastCueKey = null,
        roll = roll,
        state = state,
        expressionGate = { it in gate },
    )

    @Test
    fun `declared variant already active falls back to that slot's cycle so the tap still moves`() {
        val zone = declaredHead.copy(reactions = listOf(variant("face", "none")))
        val choice = tap(zone, Live2dTouchReactionState(), gate = ALL_180)
        assertTrue(choice.changed)
        assertEquals(TouchCueKind.CYCLE, choice.cue?.kind)
        assertEquals("blush", Live2dTouchReaction.expressionFor(choice.state))
    }

    @Test
    fun `asset gate drops cues whose expression name is not registered in this model`() {
        // Amadeus/Yuuka 的真实资产事实：包内只注册 base 与 blush（盘点 2026-11-23）。
        // 声明里 cycle→white 与 variant→white 都发明资产，必须被闸掉，选中 blush。
        val onlyBaseBlush = setOf("base", "blush")
        val first = tap(declaredHead, Live2dTouchReactionState(), onlyBaseBlush)
        assertTrue(first.changed)
        assertEquals(TouchCueKind.VARIANT, first.cue?.kind)
        assertEquals("blush", Live2dTouchReaction.expressionFor(first.state))

        // 第二次点同一处：blush 已是当前值，cycle 的下一格 white 未注册 → 循环跳过它回到 base。
        // 这正是旧 stepFaceOnly 的语义，但它现在是**资产事实推出来的**，不是写死的分支。
        val second = tap(declaredHead, first.state, onlyBaseBlush)
        assertTrue(second.changed)
        assertEquals("base", Live2dTouchReaction.expressionFor(second.state))
    }

    @Test
    fun `every level gated out gives no change so the caller can report Ignored instead of a fake axis`() {
        val zone = TouchReactionZone(
            id = "body", label = "躯干", priority = 0,
            rects = listOf(TouchRect(0.0, 0.42, 1.0, 0.57)),
            reactions = listOf(variant("top", "bikini"), cycle("top")),
        )
        val choice = tap(zone, Live2dTouchReactionState(), gate = setOf("base"))
        assertFalse(choice.changed)
        assertNull(choice.cue)
    }

    @Test
    fun `full asset set keeps every tap changing something (the v2 invariant) across repeats`() {
        var state = Live2dTouchReactionState()
        repeat(12) { i ->
            val choice = tap(declaredHead, state, ALL_180, roll = i * 7)
            assertTrue("tap #$i must move the visible state", choice.changed)
            state = choice.state
        }
    }

    @Test
    fun `built-in level uses the zone's own axis when nothing else is viable`() {
        val zone = declaredHead.copy(reactions = emptyList())
        val choice = tap(zone, Live2dTouchReactionState(), ALL_180)
        assertEquals(ReactionLevel.BUILT_IN, choice.level)
        assertEquals("face", choice.cue?.slot)
        assertTrue(choice.changed)
        // 未识别的区名退回头轴（与 v2 的「其余区域不动」不同：这里区已由声明给出，退到头轴
        // 至少不是假轴——真正的未知区在路由侧就返回 null）。
        val unknown = TouchReactionZone("tail", "尾巴", 0, listOf(TouchRect(0.0, 0.9, 1.0, 1.0)), emptyList())
        assertEquals("face", TouchReactionDecision.resolveTap(
            hit = unknown, modelOwnCues = emptyList(), supported = expressionOnly,
            lastCueKey = null, roll = 0, state = Live2dTouchReactionState(),
        ).cue?.slot)
    }
}
