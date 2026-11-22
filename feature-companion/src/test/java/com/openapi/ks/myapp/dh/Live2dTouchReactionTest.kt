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

    // ---- 资产一致性：Kotlin 状态机与 lab exp3 / model3.json 不许漂移 ----

    @Test
    fun `every reachable state has a distinct expression with a lab exp3 file`() {
        val states = Live2dCostume.entries.flatMap { c -> listOf(false, true).map { Live2dTouchReactionState(c, it) } }
        val names = states.map { Live2dTouchReaction.expressionFor(it) }
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
        Live2dCostume.entries.forEach { c ->
            listOf(false, true).forEach { blushed ->
                val name = Live2dTouchReaction.expressionFor(Live2dTouchReactionState(c, blushed))
                assertTrue("model3.json must register expression \"$name\"", text.contains("\"Name\": \"$name\""))
                val asset = File(labAssetsDir, "expressions/$name.exp3.json")
                assertTrue("model3.json references missing asset: ${asset.path}", asset.isFile)
            }
        }
    }
}
