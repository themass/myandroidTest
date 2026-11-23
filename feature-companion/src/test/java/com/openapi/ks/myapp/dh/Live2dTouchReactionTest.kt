package com.openapi.ks.myapp.dh

import com.openapi.ks.myapp.avatar.AvatarRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Live2D 触点互动的纯决策层（demo #2/#2b 的 v2）。
 *
 * v2 因真机投诉 2026-11-23「点击头和脚只能点一次，再点就没反应了」重设计：
 * 旧语义是持久 toggle（点一次挂上、再点摘掉），第二次点把状态**还原**——还原
 * 也是变化，但用户读作「没反应」。新语义每个区域**循环**到自己的轴：
 * 头→表情（正常→脸红→白眼）、躯干→上衣（日常→比基尼→睡衣→南瓜裤→血衣）、
 * 手→手持（无→kani→小鸟→枕头）、脚→鞋（无→凉鞋→皮鞋）。
 * 每一次点击都迁移到一个**新的可见状态**，「再点没反应」在结构上不可能。
 * 换装的显式选择走长按托盘（wardrobeRows / advanceWardrobe 同一状态机）。
 *
 * 表情名与 scripts/dh-lab/live2d-lab-assets/ 里的 exp3 文件一一对应，
 * 改名必须两边同步（下面的资产钉桩会拦）。参数只允许上游已验证 (Id,Value)。
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
    fun `head tap cycles face normal blush white then back`() {
        var s = Live2dTouchReactionState()
        s = Live2dTouchReaction.step(s, AvatarRegion.HEAD)
        assertEquals(Live2dFace.BLUSH, s.face)
        assertEquals("blush", Live2dTouchReaction.expressionFor(s))
        s = Live2dTouchReaction.step(s, AvatarRegion.HEAD)
        assertEquals(Live2dFace.WHITE, s.face)
        assertEquals("white", Live2dTouchReaction.expressionFor(s))
        s = Live2dTouchReaction.step(s, AvatarRegion.HEAD)
        assertEquals(Live2dFace.NONE, s.face)
        assertEquals("base", Live2dTouchReaction.expressionFor(s))
    }

    @Test
    fun `body tap cycles costume five ways so every tap changes the outfit`() {
        var s = Live2dTouchReactionState()
        val seen = mutableListOf<String>()
        repeat(5) {
            s = Live2dTouchReaction.step(s, AvatarRegion.BODY)
            seen += Live2dTouchReaction.expressionFor(s)
        }
        assertEquals(listOf("bikini", "pajamas", "pumpkin", "blood", "base"), seen)
    }

    @Test
    fun `hand tap cycles held items and foot cycles shoes`() {
        var s = Live2dTouchReactionState()
        s = Live2dTouchReaction.step(s, AvatarRegion.HAND)
        assertEquals(Live2dHeld.KANI, s.held)
        s = Live2dTouchReaction.step(s, AvatarRegion.HAND)
        assertEquals(Live2dHeld.BIRD, s.held)
        s = Live2dTouchReaction.step(s, AvatarRegion.HAND)
        assertEquals(Live2dHeld.PILLOW, s.held)
        s = Live2dTouchReaction.step(s, AvatarRegion.HAND)
        assertEquals(Live2dHeld.NONE, s.held)

        s = Live2dTouchReaction.step(s, AvatarRegion.FOOT)
        assertEquals(Live2dShoes.SANDAL, s.shoes)
        s = Live2dTouchReaction.step(s, AvatarRegion.FOOT)
        assertEquals(Live2dShoes.LEATHER, s.shoes)
        s = Live2dTouchReaction.step(s, AvatarRegion.FOOT)
        assertEquals(Live2dShoes.NONE, s.shoes)
    }

    @Test
    fun `no region tap ever lands on the same state twice in a row`() {
        // 「只能点一次」的结构性根治：任何区域的连续两步都必须改变画面表达式。
        for (region in listOf(AvatarRegion.HEAD, AvatarRegion.BODY, AvatarRegion.HAND, AvatarRegion.FOOT)) {
            var s = Live2dTouchReactionState(Live2dTop.BIKINI, Live2dShoes.SANDAL, Live2dHeld.BIRD, Live2dFace.BLUSH)
            repeat(8) {
                val next = Live2dTouchReaction.step(s, region)
                assertNotEquals("two-finger dead tap on $region",
                    Live2dTouchReaction.expressionFor(s), Live2dTouchReaction.expressionFor(next))
                s = next
            }
        }
    }

    @Test
    fun `axes are independent so a combo keeps every other axis`() {
        val s = Live2dTouchReactionState(Live2dTop.BIKINI, Live2dShoes.LEATHER, Live2dHeld.KANI, Live2dFace.NONE)
        val after = Live2dTouchReaction.step(s, AvatarRegion.HEAD)
        assertEquals(
            Live2dTouchReactionState(Live2dTop.BIKINI, Live2dShoes.LEATHER, Live2dHeld.KANI, Live2dFace.BLUSH),
            after,
        )
        assertEquals("bikini_leather_kani_blush", Live2dTouchReaction.expressionFor(after))
    }

    @Test
    fun `unknown region is a no-op state transition`() {
        val s = Live2dTouchReactionState(Live2dTop.PUMPKIN, face = Live2dFace.WHITE)
        assertEquals(s, Live2dTouchReaction.step(s, "hair"))
    }

    // ---- 换装托盘：显式推进某一根轴，和分区循环同一状态机 ----

    @Test
    fun `advanceWardrobe steps one axis and labels show the current choice`() {
        val s = Live2dTouchReactionState()
        val next = Live2dTouchReaction.advanceWardrobe(s, Live2dWardrobeAxis.TOP)
        assertEquals(Live2dTop.BIKINI, next.top)
        assertEquals(s.shoes, next.shoes)

        val rows = Live2dTouchReaction.wardrobeRows(next)
        assertEquals(4, rows.size)
        assertEquals("上衣", rows[0].label)
        assertEquals("比基尼", rows[0].value)
        assertEquals("日常", Live2dTouchReaction.wardrobeRows(Live2dTouchReactionState())[0].value)
        assertEquals("南瓜裤", Live2dTouchReaction.wardrobeRows(Live2dTouchReactionState(top = Live2dTop.PUMPKIN))[0].value)
        assertEquals("凉鞋", Live2dTouchReaction.wardrobeRows(Live2dTouchReactionState(shoes = Live2dShoes.SANDAL))[1].value)
    }

    // ---- 资产一致性：Kotlin 状态机与 lab exp3 / model3.json 不许漂移 ----

    private val allStates: List<Live2dTouchReactionState> by lazy {
        Live2dTop.entries.flatMap { t ->
            Live2dShoes.entries.flatMap { sh ->
                Live2dHeld.entries.flatMap { h ->
                    Live2dFace.entries.map { f -> Live2dTouchReactionState(t, sh, h, f) }
                }
            }
        }
    }

    @Test
    fun `every reachable state has a distinct expression with a lab exp3 file`() {
        // 5 上衣 × 3 鞋 × 4 手持 × 3 表情 = 180 态。空间有界是因为每根轴都是
        // 有穷枚举的循环位，不是叠计数——这是不爆状态空间的前提。
        assertEquals(180, allStates.size)
        val names = allStates.map { Live2dTouchReaction.expressionFor(it) }
        assertEquals(names.size, names.distinct().size)
        names.forEach { name ->
            val f = File(labAssetsDir, "expressions/$name.exp3.json")
            assertTrue("missing lab expression asset: ${f.path}", f.isFile)
        }
    }

    /**
     * FileReferences 的对象体里是否真的有 "Expressions" 键。
     * 手写括号配对（跳过字符串字面量）而不是正则——`[^"]*` 那种写法会被
     * Textures 数组里的引号截断，验证不了嵌套；纯 JVM 单测也不能靠 org.json
     * （android.jar 里它是未 mock 的桩）。
     */
    private fun expressionsNestedUnderFileReferences(model3Path: File): Boolean {
        val text = model3Path.readText()
        val key = text.indexOf("\"FileReferences\"")
        if (key < 0) return false
        var i = text.indexOf('{', key)
        if (i < 0) return false
        var depth = 0
        var inString = false
        var escaped = false
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else when (c) {
                '"' -> {
                    // 扫描从 FileReferences 自己的 '{' 起算：它的对象体里 depth==1，
                    // 且键位置（上一个非空白是 { 或 ,）才是 JSON 键而不是字符串值。
                    if (depth == 1 && text.startsWith("\"Expressions\"", i)) {
                        val prev = text.take(i).trimEnd().lastOrNull()
                        if (prev == '{' || prev == ',') return true
                    }
                    inString = true
                }
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return false
                }
            }
            i++
        }
        return false
    }

    @Test
    fun `lab model3 json registers the reachable set under FileReferences dot Expressions`() {
        // vendored CubismModelSettingJson 只读 FileReferences.Expressions
        // （CubismModelSettingJson.cpp:186）——写成上游示例那种顶层 Expressions
        // native 会静默注册 0 个表情，这里把嵌套位置钉死。
        val model3 = File(labAssetsDir, "ATRI.model3.json")
        val text = model3.readText()
        assertTrue(
            "Expressions must nest under FileReferences",
            expressionsNestedUnderFileReferences(model3),
        )
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
        // (Id,Value) 只有这些（cdi3 中文标签核对见 2026-11-23 盘点笔记）。
        // 任何新参数或新数值都必须先在上游同款 exp3 找到出处。
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

    @Test
    fun `every exp3 pins a fast fade so taps feel instant`() {
        // 真机反馈 2026-11-22「触摸效果没有」：exp3 不写 FadeInTime/FadeOutTime 时
        // CubismExpressionMotion 用 DefaultFadeTime=1.0s，淡入比人的耐心还长，按下去
        // 「像没反应」。每个组合必须显式写 ≤0.3s 的双向淡变（生成器 FADE_SECONDS）。
        File(labAssetsDir, "expressions").listFiles { f -> f.extension == "json" }!!
            .forEach { f ->
                val text = f.readText()
                Regex("\"Fade(In|Out)Time\":\\s*([0-9.]+)").findAll(text).forEach { m ->
                    val seconds = m.groupValues[2].toFloat()
                    assertTrue("$f ${m.groupValues[1]} too slow: $seconds", seconds in 0f..0.3f)
                }
                assertTrue("$f must pin both FadeInTime and FadeOutTime",
                    Regex("\"FadeInTime\"").containsMatchIn(text) &&
                        Regex("\"FadeOutTime\"").containsMatchIn(text))
            }
    }

    // ---- 多形象：Amadeus / Yuuka 的最小 lab 装扮层（base+blush，ParamCheek 双语义一致） ----

    @Test
    fun `amadeus and yuuka lab overlays register base and blush under FileReferences`() {
        for (model in listOf("Amadeus", "Yuuka")) {
            val dir = File(labAssetsDir.parentFile, "live2d-lab-assets-$model")
            val model3 = File(dir, "$model.model3.json")
            assertTrue("missing lab model3: ${model3.path}", model3.isFile)
            val text = model3.readText()
            assertTrue(
                "$model Expressions must nest under FileReferences",
                expressionsNestedUnderFileReferences(model3),
            )
            listOf("base", "blush").forEach { name ->
                assertTrue("$model must register $name", text.contains("\"Name\": \"$name\""))
                val asset = File(dir, "expressions/$name.exp3.json")
                assertTrue("missing $model expression asset: ${asset.path}", asset.isFile)
                val expText = asset.readText()
                Regex("\"Fade(In|Out)Time\":\\s*([0-9.]+)").findAll(expText).forEach { m ->
                    assertTrue("$asset fade too slow", m.groupValues[2].toFloat() in 0f..0.3f)
                }
            }
        }
    }

    @Test
    fun `amadeus expressions all bake the upstream fix logo gate or the model never draws`() {
        // 真机取证 2026-11-23：kurisu.moc3 有名为 logo 的绘制总闸——上游靠常驻注册
        // fix.exp3.json（logo=0.01 Add）让模型可见。lab 的状态机切换表情，旧表情
        // FadeOut 时 logo 回到 moc 默认（不画）→ 画面全黑只剩两点（就是那次投诉）。
        // 所以 Amadeus 的**每一个** lab 表情都必须自带 logo=0.01（上游值，非发明）。
        val dir = File(labAssetsDir.parentFile, "live2d-lab-assets-Amadeus")
        listOf("base", "blush").forEach { name ->
            val asset = File(dir, "expressions/$name.exp3.json")
            val pairs = Regex("\"Id\":\\s*\"(\\w+)\",\\s*\"Value\":\\s*(-?[0-9.]+)")
                .findAll(asset.readText())
                .map { it.groupValues[1] to it.groupValues[2] }
                .toSet()
            assertTrue("$name must carry the logo gate: $pairs", pairs.contains("logo" to "0.01"))
        }
        // blush 额外只有 ParamCheek=1；Yuuka 无总闸（上游无 fix 文件，画面正常），
        // 它的表情不许混进 logo——防止把 Amadeus 的补丁无脑复制。
        val amadeusBlush = Regex("\"Id\":\\s*\"(\\w+)\"")
            .findAll(File(dir, "expressions/blush.exp3.json").readText())
            .map { it.groupValues[1] }.toSet()
        assertEquals(setOf("logo", "ParamCheek"), amadeusBlush)
        val yuukaDir = File(labAssetsDir.parentFile, "live2d-lab-assets-Yuuka")
        listOf("base", "blush").forEach { name ->
            assertFalse("Yuuka must not get the logo gate",
                File(yuukaDir, "expressions/$name.exp3.json").readText().contains("\"logo\""))
        }
        // Yuuka 的脸轴仍只许 ParamCheek=1（不许发明值）。
        val yuukaIds = Regex("\"Id\":\\s*\"(\\w+)\"")
            .findAll(File(yuukaDir, "expressions/blush.exp3.json").readText())
            .map { it.groupValues[1] }.toSet()
        assertEquals(setOf("ParamCheek"), yuukaIds)
    }
}
