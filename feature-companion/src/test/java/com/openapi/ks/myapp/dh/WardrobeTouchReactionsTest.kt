package com.openapi.ks.myapp.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * `touchReactions` 进 wardrobe-spec（单一事实源）——解析 + 校验。
 *
 * 纪律来自 `docs/research/2026-11-28-waifu-desktop-pet-deepread.md` §10.2 纪律 2：
 * **payload 里每加一个字段，就必须同时有一个生产者、一个校验点和一个测试**，
 * 否则它一定变成死代码。这里三者齐备：
 * - 生产者：wardrobe-spec.json 的 `touchReactions` 块（本测试读真实文件，不造假数据）；
 * - 校验点：`WardrobeTouchReactions.parse` 的封闭词表（kind/slot/token/rect/权重/重复 id）；
 * - 测试：本文件 + `TouchZoneRouterTest` / `TouchReactionDecisionTest`（消费侧）。
 *
 * 校验失败**不能静默**：返回的错误列表要能指出具体第几区第几个 cue。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WardrobeTouchReactionsTest {

    private val labAssetsDir: File by lazy {
        var dir = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "settings.gradle").isFile) {
            dir = dir.parentFile
        }
        File(checkNotNull(dir) { "cannot locate repo root from ${System.getProperty("user.dir")}" },
            "scripts/dh-lab/live2d-lab-assets")
    }

    private val shippedText: String by lazy { File(labAssetsDir, "wardrobe-spec.json").readText() }

    // ---- 出货声明本身 ----

    @Test
    fun `shipped spec declares touch reactions with no validation errors`() {
        val parsed = WardrobeTouchReactions.parse(shippedText)
        assertEquals("spec errors: ${parsed.errors}", emptyList<String>(), parsed.errors)
        val decl = checkNotNull(parsed.declaration) { "shipped spec must declare touchReactions" }
        assertTrue("must declare zones", decl.zones.isNotEmpty())
    }

    @Test
    fun `shipped zones are stable lowercase ids and cover the four legacy regions`() {
        val decl = checkNotNull(WardrobeTouchReactions.parse(shippedText).declaration)
        val ids = decl.zones.map { it.id }
        assertEquals("ids must be unique", ids.size, ids.toSet().size)
        // 语义 id 是小集合、可枚举（§10.3 收紧 2）：与旧的 AvatarRegion 词表对齐，
        // 上报给 agent 的 region 语义不因换刀而变。
        assertTrue("must cover head/body/hand/foot: $ids",
            setOf("head", "body", "hand", "foot").all { it in ids })
        ids.forEach { assertTrue("bad zone id $it", Regex("^[a-z][a-z0-9_.-]{0,63}$").matches(it)) }
    }

    @Test
    fun `shipped zones tile the stage so every tap reaches a zone`() {
        // 分区必须铺满 [0,1]²：留缝 = 点空白没反应 = 用户读作「点了没动静」。
        val decl = checkNotNull(WardrobeTouchReactions.parse(shippedText).declaration)
        for (iy in 0..100 step 5) {
            for (ix in 0..100 step 5) {
                val hit = TouchZoneRouter.hit(decl, ix / 100f, iy / 100f)
                assertNotNull("no zone covers ($ix,$iy)", hit)
            }
        }
    }

    @Test
    fun `shipped zones split overlap by explicit priority not order`() {
        val decl = checkNotNull(WardrobeTouchReactions.parse(shippedText).declaration)
        val priorities = decl.zones.map { it.priority }
        assertEquals("priority must be distinct for the shipped set", priorities.size, priorities.toSet().size)
        // 手在两臂、躯干在中列：同一 y 带上中列赢躯干、两侧赢手。
        val mid = TouchZoneRouter.hit(decl, 0.50f, 0.50f)!!.id
        val arm = TouchZoneRouter.hit(decl, 0.30f, 0.50f)!!.id
        assertEquals("body", mid)
        assertEquals("hand", arm)
    }

    @Test
    fun `every shipped cue references an active slot and token`() {
        val decl = checkNotNull(WardrobeTouchReactions.parse(shippedText).declaration)
        val slots = WardrobeTouchReactions.parseSlots(shippedText)
        decl.zones.forEach { zone ->
            assertTrue("zone ${zone.id} declares no reaction", zone.reactions.isNotEmpty())
            zone.reactions.forEach { cue ->
                when (cue.kind) {
                    TouchCueKind.VARIANT -> {
                        val slot = slots[cue.slot] ?: error("unknown slot ${cue.slot}")
                        val variant = slot.tokens[cue.token] ?: error("unknown token ${cue.token}")
                        assertEquals("${cue.slot}:${cue.token} must be active", "active", variant.status)
                    }
                    TouchCueKind.CYCLE -> assertNotNull("unknown slot ${cue.slot}", slots[cue.slot])
                    TouchCueKind.MOTION -> assertTrue("motion id blank", cue.motionId.isNotBlank())
                }
            }
        }
    }

    @Test
    fun `shipped declarations stay inside the expression capability`() {
        // 当前 fork 没有 motion API（JniBridgeJava 只有 nativeApplyExpression）：
        // 出货声明里不许出现播不出来的 motion cue。
        val decl = checkNotNull(WardrobeTouchReactions.parse(shippedText).declaration)
        decl.zones.forEach { zone ->
            zone.reactions.forEach { assertTrue("motion cue not supported yet", it.kind != TouchCueKind.MOTION) }
        }
    }

    // ---- 校验器必须拦住坏声明（负面用例） ----

    private fun errorsOf(json: String): List<String> = WardrobeTouchReactions.parse(json).errors

    @Test
    fun `invented slot or token is rejected`() {
        // 词表必须真实存在，坏声明才能走到 token 级校验（缺 slots 时会在 slot 级先拦下）。
        val slots = """"slots":[{"slot":"face","variants":[{"token":"none","status":"active"},{"token":"blush","status":"active"}]}],"""
        val bad = """{$slots"touchReactions":{"zones":[{"id":"head","priority":0,"rects":[[0,0,1,0.42]],
              "reactions":[{"kind":"variant","slot":"face","token":"cat"}]}]}}""".trimIndent()
        assertTrue(errorsOf(bad).any { it.contains("token") })
        val badSlot = """{$slots"touchReactions":{"zones":[{"id":"head","priority":0,"rects":[[0,0,1,0.42]],
              "reactions":[{"kind":"variant","slot":"hat","token":"blush"}]}]}}""".trimIndent()
        assertTrue(errorsOf(badSlot).any { it.contains("slot") })
    }

    @Test
    fun `pending slot is rejected so a tap cannot reach unshipped art`() {
        val bad = """
            {"touchReactions":{"zones":[{"id":"body","priority":0,"rects":[[0,0,1,1]],
              "reactions":[{"kind":"variant","slot":"underwear","token":"lace"}]}]}}
        """.trimIndent()
        assertTrue(errorsOf(bad).any { it.contains("underwear") })
    }

    @Test
    fun `unknown cue kind is rejected rather than ignored`() {
        val bad = """
            {"touchReactions":{"zones":[{"id":"head","priority":0,"rects":[[0,0,1,0.42]],
              "reactions":[{"kind":"dance","slot":"face"}]}]}}
        """.trimIndent()
        assertTrue(errorsOf(bad).any { it.contains("dance") })
    }

    @Test
    fun `non-positive weight and empty reaction list are rejected`() {
        val zero = """
            {"touchReactions":{"zones":[{"id":"head","priority":0,"rects":[[0,0,1,0.42]],
              "reactions":[{"kind":"cycle","slot":"face","weight":0}]}]}}
        """.trimIndent()
        assertTrue(errorsOf(zero).any { it.contains("weight") })
        val empty = """
            {"touchReactions":{"zones":[{"id":"head","priority":0,"rects":[[0,0,1,0.42]],"reactions":[]}]}}
        """.trimIndent()
        assertTrue(errorsOf(empty).any { it.contains("reactions") })
    }

    @Test
    fun `malformed rects and out-of-range geometry are rejected`() {
        listOf(
            """[[0.42,0,0,0]]""",              // x1 <= x0
            """[[0,0.6,1,0.42]]""",            // y1 <= y0
            """[[-0.1,0,1,1]]""",              // 越界
            """[[0,0,1.2,1]]""",               // 越界
            """[]""",                          // 没有几何
        ).forEach { rects ->
            val bad = """{"touchReactions":{"zones":[{"id":"head","priority":0,"rects":$rects,
                "reactions":[{"kind":"cycle","slot":"face"}]}]}}""".trimIndent()
            assertTrue("must reject rects=$rects", errorsOf(bad).isNotEmpty())
        }
    }

    @Test
    fun `duplicate zone ids are rejected`() {
        val dup = """
            {"touchReactions":{"zones":[
              {"id":"head","priority":0,"rects":[[0,0,1,0.3]],"reactions":[{"kind":"cycle","slot":"face"}]},
              {"id":"head","priority":1,"rects":[[0,0.3,1,0.6]],"reactions":[{"kind":"cycle","slot":"face"}]}]}}
        """.trimIndent()
        assertTrue(errorsOf(dup).any { it.contains("head") })
    }

    @Test
    fun `missing or malformed block reports cleanly instead of throwing`() {
        assertEquals(null, WardrobeTouchReactions.parse("""{"slots":[]}""").declaration)
        assertEquals(emptyList<String>(), WardrobeTouchReactions.parse("""{"slots":[]}""").errors)
        val broken = WardrobeTouchReactions.parse("""{"touchReactions":{""")
        assertNotNull(broken.errors)
        assertTrue(broken.errors.isNotEmpty())
    }
}
