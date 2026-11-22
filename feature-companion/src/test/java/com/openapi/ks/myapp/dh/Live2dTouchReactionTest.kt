package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Live2D 触点互动的纯决策层（demo #2 的「摸头 + 换装」核心）。
 *
 * 状态 = (服装, 是否脸红)。点头切换脸红，点身体循环换装——没有定时器，
 * 完全确定，所以每个迁移都能钉死；时间相关的东西（native 表情淡入淡出）
 * 归真机验收。表情名与 scripts/dh-lab/live2d-lab-assets/ 里的 exp3 文件
 * 一一对应，改名必须两边同步（下面的资产钉桩会拦）。
 */
class Live2dTouchReactionTest {

    private val labAssetsDir: File by lazy {
        var dir = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "settings.gradle").isFile) {
            dir = dir.parentFile
        }
        File(checkNotNull(dir) { "cannot locate repo root from ${System.getProperty("user.dir")}" },
            "scripts/dh-lab/live2d-lab-assets")
    }

    @Test
    fun `initial state maps to base expression`() {
        assertEquals("base", Live2dTouchReaction.expressionFor(Live2dTouchReactionState()))
    }

    @Test
    fun `head tap toggles blush on then off, costume untouched`() {
        val s0 = Live2dTouchReactionState()
        val s1 = Live2dTouchReaction.step(s0, AvatarRegion.HEAD)
        assertEquals(Live2dTouchReactionState(Live2dCostume.NONE, blushed = true), s1)
        assertEquals("blush", Live2dTouchReaction.expressionFor(s1))

        val s2 = Live2dTouchReaction.step(s1, AvatarRegion.HEAD)
        assertEquals(Live2dTouchReactionState(Live2dCostume.NONE, blushed = false), s2)
        assertEquals("base", Live2dTouchReaction.expressionFor(s2))
    }

    @Test
    fun `body tap cycles costume none then bikini then pajamas then back`() {
        var s = Live2dTouchReactionState()
        s = Live2dTouchReaction.step(s, AvatarRegion.BODY)
        assertEquals(Live2dCostume.BIKINI, s.costume)
        assertEquals("bikini", Live2dTouchReaction.expressionFor(s))

        s = Live2dTouchReaction.step(s, AvatarRegion.BODY)
        assertEquals(Live2dCostume.PAJAMAS, s.costume)
        assertEquals("pajamas", Live2dTouchReaction.expressionFor(s))

        s = Live2dTouchReaction.step(s, AvatarRegion.BODY)
        assertEquals(Live2dCostume.NONE, s.costume)
        assertEquals("base", Live2dTouchReaction.expressionFor(s))
    }

    @Test
    fun `costume switch keeps blush and maps to combo expression`() {
        // 表情管理器同时只有一个激活表情——换装必须把脸红一并编进组合表情，
        // 否则换装会把脸红抹掉。组合值相同（同参 Add 同值），交叉淡入期也恒定。
        val s = Live2dTouchReactionState(Live2dCostume.NONE, blushed = true)
        val after = Live2dTouchReaction.step(s, AvatarRegion.BODY)
        assertEquals(Live2dTouchReactionState(Live2dCostume.BIKINI, blushed = true), after)
        assertEquals("bikini_blush", Live2dTouchReaction.expressionFor(after))
    }

    @Test
    fun `unknown region is a no-op state transition`() {
        val s = Live2dTouchReactionState(Live2dCostume.PAJAMAS, blushed = true)
        assertEquals(s, Live2dTouchReaction.step(s, "hair"))
    }

    // ---- demo #2b：分区反应的手/脚两个新维度 ----
    // 设计裁决（见 dh-demo2b 笔记）：motion 播放这条路被排除（.so 无 StartMotion
    // 入口、上游 ATRI 一个 motion 组都没注册、合成 tap 还会把 gaze 甩回中心），
    // 沿用 demo #2 已验证的组合表情机制：手=手里出现 kani（原版 ATRI 就抱着它），
    // 脚=穿上凉鞋。参数值全部取上游 16 个 exp3 已验证值（30/1），无发明值。

    @Test
    fun `hand tap toggles crab on then off, other axes untouched`() {
        val s0 = Live2dTouchReactionState(Live2dCostume.BIKINI, blushed = true, sandal = true)
        val s1 = Live2dTouchReaction.step(s0, AvatarRegion.HAND)
        assertEquals(Live2dTouchReactionState(Live2dCostume.BIKINI, blushed = true, crab = true, sandal = true), s1)
        assertEquals(s0, Live2dTouchReaction.step(s1, AvatarRegion.HAND))
    }

    @Test
    fun `foot tap toggles sandal on then off, other axes untouched`() {
        val s0 = Live2dTouchReactionState(blushed = true, crab = true)
        val s1 = Live2dTouchReaction.step(s0, AvatarRegion.FOOT)
        assertEquals(Live2dTouchReactionState(blushed = true, crab = true, sandal = true), s1)
        assertEquals(s0, Live2dTouchReaction.step(s1, AvatarRegion.FOOT))
    }

    @Test
    fun `new single-axis states map to their own expression names`() {
        assertEquals("crab", Live2dTouchReaction.expressionFor(Live2dTouchReactionState(crab = true)))
        assertEquals("sandal", Live2dTouchReaction.expressionFor(Live2dTouchReactionState(sandal = true)))
        assertEquals(
            "blush_crab",
            Live2dTouchReaction.expressionFor(Live2dTouchReactionState(blushed = true, crab = true)),
        )
        // 词缀顺序固定：服装 → blush → crab → sandal，老名字（demo #2 那 6 个）不受影响。
        assertEquals(
            "pajamas_blush_crab_sandal",
            Live2dTouchReaction.expressionFor(
                Live2dTouchReactionState(Live2dCostume.PAJAMAS, blushed = true, crab = true, sandal = true),
            ),
        )
    }

    // ---- 资产一致性：Kotlin 状态机与 lab exp3 / model3.json 不许漂移 ----

    private val allStates: List<Live2dTouchReactionState> by lazy {
        Live2dCostume.entries.flatMap { c ->
            listOf(false, true).flatMap { b ->
                listOf(false, true).flatMap { k ->
                    listOf(false, true).map { Live2dTouchReactionState(c, b, k, it) }
                }
            }
        }
    }

    @Test
    fun `every reachable state has a distinct expression with a lab exp3 file`() {
        // 3 服装 × 2 脸红 × 2 kani × 2 凉鞋 = 24 态。空间有界是因为四个维度都是
        // 持久 toggle（点了挂上、再点摘掉），不是叠计数——这是不爆状态空间的前提。
        assertEquals(24, allStates.size)
        val names = allStates.map { Live2dTouchReaction.expressionFor(it) }
        assertEquals(names.size, names.distinct().size)
        names.forEach { name ->
            val f = File(labAssetsDir, "expressions/$name.exp3.json")
            assertTrue("missing lab expression asset: ${f.path}", f.isFile)
        }
    }

    @Test
    fun `lab model3 json registers the reachable set under FileReferences dot Expressions`() {
        // vendored CubismModelSettingJson 只读 FileReferences.Expressions
        // （CubismModelSettingJson.cpp:186）——写成上游示例那种顶层 Expressions
        // native 会静默注册 0 个表情，这里把嵌套位置钉死。
        val text = File(labAssetsDir, "ATRI.model3.json").readText()
        val nested = Regex("\"FileReferences\"\\s*:\\s*\\{[^}]*\"Expressions\"")
        assertTrue("Expressions must nest under FileReferences", nested.containsMatchIn(text))
        allStates.forEach { s ->
            val name = Live2dTouchReaction.expressionFor(s)
            assertTrue("model3.json must register expression \"$name\"", text.contains("\"Name\": \"$name\""))
            val asset = File(labAssetsDir, "expressions/$name.exp3.json")
            assertTrue("model3.json references missing asset: ${asset.path}", asset.isFile)
        }
    }

    @Test
    fun `every exp3 parameter value is an upstream-validated pair`() {
        // 「只用上游已验证值」纪律的机器钉桩：上游 16 个 exp3 里出现过的
        // (Id,Value) 只有这些——Param17/18 服装=30、Param19 凉鞋=30、Param38 kani=30、
        // ParamCheek=1。任何新参数或新数值都必须先在上游同款 exp3 找到出处。
        val upstream = setOf(
            "ParamCheek" to 1, "Param9" to 30, "Param10" to 30, "Param17" to 30,
            "Param18" to 30, "Param19" to 30, "Param20" to 30, "Param21" to 30,
            "Param22" to 30, "Param31" to 30, "Param36" to 30, "Param37" to 30,
            "Param38" to 30, "Param39" to 30, "Param39" to -30,
        )
        File(labAssetsDir, "expressions").listFiles { f -> f.extension == "json" }!!.forEach { f ->
            Regex("\"Id\":\\s*\"(\\w+)\",\\s*\"Value\":\\s*(-?\\d+)")
                .findAll(f.readText())
                .forEach { m ->
                    val pair = m.groupValues[1] to m.groupValues[2].toInt()
                    assertTrue("$f uses non-upstream pair $pair", upstream.contains(pair))
                }
        }
    }
}
