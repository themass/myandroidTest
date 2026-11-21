package com.openapi.ks.myapp.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通话页语音舞台的布局决策（纯函数，JVM 可测）。
 *
 * 背景：三个数字人引擎（Simple / Live2D / Duix，之后 Tao）以前和分层立绘共用同一个
 * 「60% 高舞台 + 下方字幕列」的分栏，人物只剩一小块，唇形和动作根本看不清。
 * 主角模式让形象铺满整块舞台、字幕与声效改为叠层；**原来的形象（分层立绘）与圆形
 * 头像的原有布局不变**——那两条路径必须仍走分栏。
 */
class CompanionCallStageLayoutTest {

    @Test
    fun no_stage_slot_stays_classic_circle_layout() {
        assertEquals(VoiceStageMode.Classic, voiceStageMode(hasAvatarStage = false, characterHero = false))
        // characterHero 只在真的有形象舞台时生效，没有插槽时不可能凭空铺满。
        assertEquals(VoiceStageMode.Classic, voiceStageMode(hasAvatarStage = false, characterHero = true))
    }

    @Test
    fun layered_avatar_stage_keeps_the_original_split_layout() {
        // callStage == null（原来的形象）不传 characterHero，保持「形象区 60% 高 + 字幕列」。
        assertEquals(VoiceStageMode.Stacked, voiceStageMode(hasAvatarStage = true, characterHero = false))
    }

    @Test
    fun engine_stage_fills_the_whole_stage() {
        assertEquals(VoiceStageMode.CharacterHero, voiceStageMode(hasAvatarStage = true, characterHero = true))
        assertEquals(900f, voiceStageHeightForMode(VoiceStageMode.CharacterHero, 400f, 900f), 0.001f)
    }

    @Test
    fun stacked_stage_keeps_the_original_60_percent_split() {
        assertEquals(540f, voiceStageHeightForMode(VoiceStageMode.Stacked, 400f, 900f), 0.001f)
    }

    @Test
    fun classic_circle_keeps_the_original_width_clamp() {
        // 高屏：高度主导，min(宽, 高×0.44) 取高×0.44。
        assertEquals(396f, voiceStageHeightForMode(VoiceStageMode.Classic, 400f, 900f), 0.001f)
        // 矮宽屏：宽度封顶，头像不会被拉出屏幕。
        assertEquals(400f, voiceStageHeightForMode(VoiceStageMode.Classic, 400f, 1200f), 0.001f)
    }

    @Test
    fun only_the_engine_stage_runs_behind_the_header_and_controls() {
        // 数字人区域太小的根因：形象被夹在顶栏和操作条之间。主角模式让舞台铺满整屏，
        // 顶栏/操作条改为浮层；原来的形象与圆形头像的分栏布局不动。
        assertTrue(voiceStageIsFullBleed(VoiceStageMode.CharacterHero))
        assertTrue(!voiceStageIsFullBleed(VoiceStageMode.Stacked))
        assertTrue(!voiceStageIsFullBleed(VoiceStageMode.Classic))
    }

    @Test
    fun hero_overlays_clear_the_floating_control_bar() {
        // 字幕和波形必须坐在浮动操作条上方，否则被操作条盖住：操作条 = 56dp 按钮 + 标签 + 边距。
        assertTrue(HERO_CONTROL_BAR_DP >= 100f)
        assertTrue(HERO_TOP_SCRIM_DP >= 96f)
    }

    @Test
    fun route_passes_hero_only_for_engine_stages() {
        // 接线护栏（同 DhLabExternalIncludesRemovedTest 的源码断言风格）：
        // CompanionRoute 必须只在选了引擎舞台（callStage != null）时开主角模式，
        // 「原来的形象」（callStage == null，分层立绘）保持原有分栏布局。
        var dir = java.io.File(System.getProperty("user.dir"))
        while (dir != null && !java.io.File(dir, "settings.gradle").isFile) dir = dir.parentFile
        val route = java.io.File(
            checkNotNull(dir),
            "feature-companion/src/main/java/com/openapi/ks/myapp/companion/CompanionRoute.kt",
        ).readText()
        assertTrue(
            "CompanionRoute must pass characterHero = callStage != null (hero only for engine stages)",
            route.contains("characterHero = callStage != null"),
        )
    }
}
