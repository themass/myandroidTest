package com.openapi.feature.companion.fx

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * HER 侧特效词表的钉死测试（用户 2026-11-28：「特效只有 4 个……常规的短视频特效不是该有么」）。
 *
 * 钉四件事：
 * 1. 词表规模与每条的**锚点 + 类别**（类别错了降级就错）；
 * 2. 降级按类别生效：particles 关 → 氛围类全不播、事件响应类全播；reducedMotion 同理；
 * 3. 每条资产都有 gesture 触发词，且词表**全局唯一**（两个特效抢同一个词 = 不可名状的叠加）；
 * 4. 白名单里的 JSON 真实存在、可解析、含 v/fr/ip/op/w/h/layers，且 ≤100KB。
 */
class FxWordTableTest {

    /** JUnit 的 assertNotNull 返回 void，这里要的是「非 null 且类型收窄」。 */
    private fun <T : Any> nonNull(message: String, value: T?): T {
        assertTrue("$message（实际 null）", value != null)
        return value!!
    }

    /** 新词表的期望锚点/类别（词表改了这里必须一起改——这是设计意图的单一记录点）。 */
    private val expected: Map<String, Pair<StageEffect.Anchor, FxCategory>> = linkedMapOf(
        // 既有四条（不动）
        FxAssets.PAT_HEAD to (StageEffect.Anchor.HEAD to FxCategory.EVENT_RESPONSE),
        FxAssets.WAVE to (StageEffect.Anchor.HAND to FxCategory.EVENT_RESPONSE),
        FxAssets.CONNECTED to (StageEffect.Anchor.FULL to FxCategory.AMBIENCE),
        FxAssets.SWIPE to (StageEffect.Anchor.FULL to FxCategory.EVENT_RESPONSE),
        // 新增：头部
        FxAssets.CAT_EARS to (StageEffect.Anchor.HEAD to FxCategory.EVENT_RESPONSE),
        FxAssets.STAR_EYES to (StageEffect.Anchor.HEAD to FxCategory.EVENT_RESPONSE),
        FxAssets.BLUSH to (StageEffect.Anchor.HEAD to FxCategory.EVENT_RESPONSE),
        // 新增：身体
        FxAssets.BUBBLES to (StageEffect.Anchor.BODY to FxCategory.EVENT_RESPONSE),
        FxAssets.HALO to (StageEffect.Anchor.BODY to FxCategory.EVENT_RESPONSE),
        // 新增：手部
        FxAssets.SPARKLE to (StageEffect.Anchor.HAND to FxCategory.EVENT_RESPONSE),
        // 新增：全屏事件
        FxAssets.FIREWORKS to (StageEffect.Anchor.FULL to FxCategory.EVENT_RESPONSE),
        FxAssets.COINS to (StageEffect.Anchor.FULL to FxCategory.EVENT_RESPONSE),
        // 新增：全屏氛围
        FxAssets.HEART_RAIN to (StageEffect.Anchor.FULL to FxCategory.AMBIENCE),
        FxAssets.SNOW to (StageEffect.Anchor.FULL to FxCategory.AMBIENCE),
        FxAssets.RAIN to (StageEffect.Anchor.FULL to FxCategory.AMBIENCE),
        FxAssets.SPOTLIGHT to (StageEffect.Anchor.FULL to FxCategory.AMBIENCE),
    )

    @Test
    fun `registry covers the whole agreed word table and nothing else`() {
        assertEquals(
            "FxAssets 词表与钉死词表不一致",
            expected.keys.toSet(),
            FxAssets.all.map { it.assetId }.toSet(),
        )
        assertTrue("HER 侧特效应扩到常规短视频级（≥16 条）", FxAssets.all.size >= 16)
    }

    @Test
    fun `every entry carries the agreed anchor and category`() {
        for ((assetId, pair) in expected) {
            val spec = nonNull("词表缺条目 $assetId", FxAssets.lookup(assetId))
            assertEquals("$assetId 锚点", pair.first, spec.anchor)
            assertEquals("$assetId 类别", pair.second, spec.category)
        }
    }

    @Test
    fun `particles off drops every ambience and keeps every event response`() {
        for (spec in FxAssets.all) {
            val d = FxRenderPolicy.decide(
                StageEffect.Play(spec.assetId, spec.anchor),
                reducedMotion = false, particles = false, stageZoomed = false,
            )
            if (spec.category == FxCategory.AMBIENCE) {
                assertEquals("${spec.assetId} 氛围类必须关", FxRenderPolicy.Mode.SKIP, d.mode)
                assertEquals(FxRenderPolicy.SkipReason.PARTICLES_OFF, d.reason)
            } else {
                assertEquals("${spec.assetId} 事件响应必须仍播", FxRenderPolicy.Mode.PLAY, d.mode)
            }
        }
    }

    @Test
    fun `reduced motion frames events and skips every ambience`() {
        for (spec in FxAssets.all) {
            val d = FxRenderPolicy.decide(
                StageEffect.Play(spec.assetId, spec.anchor),
                reducedMotion = true, particles = true, stageZoomed = false,
            )
            if (spec.category == FxCategory.AMBIENCE) {
                assertEquals("${spec.assetId} 氛围类减弱动态下不播", FxRenderPolicy.Mode.SKIP, d.mode)
            } else {
                assertEquals("${spec.assetId} 减弱动态下降静态帧", FxRenderPolicy.Mode.STATIC_FRAME, d.mode)
            }
        }
    }

    @Test
    fun `anchor classes skip when zoomed and full classes survive`() {
        for (spec in FxAssets.all) {
            val d = FxRenderPolicy.decide(
                StageEffect.Play(spec.assetId, spec.anchor),
                reducedMotion = false, particles = true, stageZoomed = true,
            )
            if (spec.anchor == StageEffect.Anchor.FULL) {
                assertEquals("${spec.assetId} 全屏类放大态照播", FxRenderPolicy.Mode.PLAY, d.mode)
            } else {
                assertEquals(FxRenderPolicy.Mode.SKIP, d.mode)
                assertEquals(FxRenderPolicy.SkipReason.ZOOMED_ANCHOR, d.reason)
            }
        }
    }

    @Test
    fun `every registered asset has a unique gesture trigger that maps back`() {
        val seen = HashMap<String, String>()
        for (spec in FxAssets.all) {
            val aliases = StageEffectMapper.gesturesFor(spec.assetId)
            assertTrue("${spec.assetId} 没有触发词", aliases.isNotEmpty())
            val primary = nonNull("${spec.assetId} 没有主触发词", StageEffectMapper.primaryGesture(spec.assetId))
            for (alias in aliases) {
                val owner = seen.put(alias.lowercase(), spec.assetId)
                assertTrue("触发词 $alias 同时属于 $owner 和 ${spec.assetId}", owner == null)
                val effect = nonNull("触发词 $alias 没有翻成特效",
                    StageEffectMapper.fromGesture(alias) as? StageEffect.Play)
                assertEquals("触发词 $alias 翻错资产", spec.assetId, effect.assetId)
                assertEquals("触发词 $alias 锚点与词表不符", spec.anchor, effect.anchor)
            }
            assertEquals("主触发词必须在别名里", spec.assetId, (StageEffectMapper.fromGesture(primary) as StageEffect.Play).assetId)
        }
    }

    @Test
    fun `panel exposes every registered asset with the registry category`() {
        val options = StageFxOptions.options(FxTarget.HER)
        assertEquals(FxAssets.all.size, options.size)
        options.forEach { option ->
            assertEquals(FxAssets.lookup(option.assetId)?.category, option.category)
            assertTrue("${option.assetId} 面板文案缺失", option.label.isNotBlank() && option.label != option.assetId)
        }
    }

    // ---- 资产结构校验：白名单加了 id 但 JSON 不合法，运行时只会静默「加载…」 ------

    private val assetsDir: File by lazy {
        val direct = File("src/main/assets/fx")
        if (direct.isDirectory) direct else File(repoRoot(), "feature-companion/src/main/assets/fx")
    }

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "settings.gradle").isFile) dir = dir.parentFile
        return checkNotNull(dir)
    }

    @Test
    fun `every whitelisted lottie json parses and carries the required keys`() {
        assertTrue("找不到资产目录 $assetsDir", assetsDir.isDirectory)
        for (spec in FxAssets.all) {
            val f = File(assetsDir, "${spec.assetId}.json")
            assertTrue("缺资产：${spec.assetId}", f.isFile)
            assertTrue("${spec.assetId} 超过 100KB", f.length() <= 100_000L)
            val root = JsonParser.parseString(f.readText()).asJsonObject
            assertTrue("${spec.assetId} v 必须是版本号字符串", root.get("v").isJsonPrimitive && root.get("v").asString.isNotBlank())
            assertEquals("${spec.assetId} nm", spec.assetId, root.get("nm").asString)
            assertEquals("${spec.assetId} ddd", 0, root.get("ddd").asInt)
            for (key in listOf("fr", "ip", "op", "w", "h")) {
                assertTrue("$key 必须是数字", root.get(key).isJsonPrimitive)
            }
            assertTrue("${spec.assetId} w>0", root.get("w").asInt > 0)
            assertTrue("${spec.assetId} h>0", root.get("h").asInt > 0)
            assertTrue("${spec.assetId} op>ip", root.get("op").asInt > root.get("ip").asInt)
            val fr = root.get("fr").asInt
            assertTrue("${spec.assetId} fr=$fr", fr in listOf(24, 30))
            assertTrue("${spec.assetId} 有图层", root.getAsJsonArray("layers").size() > 0)
            // 规格红线（方案 §3）：无内嵌位图 / 无 matte / 无表达式 / 无 3D
            val text = f.readText()
            for (banned in listOf("\"ty\":\"img\"", "\"ty\":\"tt\"", "\"ty\":\"kd\"", "\"tx\":\"ex\"", "\"ddd\":1")) {
                assertTrue("${spec.assetId} 含禁用结构 $banned", !text.contains(banned))
            }
        }
    }

    @Test
    fun `asset dir holds no stray files outside the whitelist`() {
        val whitelisted = FxAssets.all.map { "${it.assetId}.json" }.toSet()
        val strays = assetsDir.listFiles()
            ?.filter { it.extension == "json" && it.name !in whitelisted }
            ?.map { it.name }
            .orEmpty()
        assertTrue("资产目录有白名单外的 json：$strays", strays.isEmpty())
    }
}
