package com.openapi.feature.companion.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageFxOptionsTest {

    @Test
    fun `her options list every registered asset with a gesture id`() {
        val options = StageFxOptions.options(FxTarget.HER)
        assertEquals(FxAssets.all.map { it.assetId }.toSet(), options.map { it.assetId }.toSet())
        options.forEach { option ->
            assertEquals(FxTarget.HER, option.target)
            assertTrue(option.gestureId != null)
        }
    }

    @Test
    fun `every her gesture id maps back to the same asset`() {
        StageFxOptions.options(FxTarget.HER).forEach { option ->
            val effect = StageEffectMapper.fromGesture(option.gestureId) as StageEffect.Play
            assertEquals(option.assetId, effect.assetId)
        }
    }

    @Test
    fun `ambience is disabled without particles and reduced motion disables all`() {
        val noParticles = StageFxOptions.options(FxTarget.HER, particles = false)
        val ambience = noParticles.first { it.category == FxCategory.AMBIENCE }
        assertEquals(false, ambience.enabled)
        assertTrue(ambience.unavailableReason?.isNotBlank() == true)
        assertTrue(noParticles.first { it.category == FxCategory.EVENT_RESPONSE }.enabled)

        assertTrue(StageFxOptions.options(FxTarget.HER, reducedMotion = true).none { it.enabled })
    }

    @Test
    fun `user stickers are the sticker whitelist not borrowed effect words`() {
        val granted = StageFxOptions.options(FxTarget.USER, cameraGranted = true)
        // 用户 2026-11-28：「贴纸只是挪用其中 3 个……贴纸你也没搞啊」——
        // USER 列必须走自己的贴纸词表（CameraStickers），且比原来多。
        assertEquals(CameraStickers.ALL, granted.map { it.assetId })
        assertTrue(granted.size >= 7)
        assertTrue(granted.all { it.enabled && it.target == FxTarget.USER && it.gestureId == null })
        // 贴纸列里不得出现舞台特效词。
        assertTrue(granted.none { it.assetId == FxAssets.WAVE || it.assetId == FxAssets.SWIPE })
        // 每条都有中文文案（不是裸 id）。
        granted.forEach { assertTrue(it.label.isNotBlank() && it.label != it.assetId) }

        val denied = StageFxOptions.options(FxTarget.USER, cameraGranted = false)
        assertTrue(denied.none { it.enabled })
        assertTrue(denied.all { it.unavailableReason?.contains("相机") == true })
    }

    @Test
    fun `unregistered asset is not offered`() {
        assertTrue(StageFxOptions.options(FxTarget.HER).none { it.assetId == "fx_nope" })
        assertNull(FxAssets.lookup("fx_nope"))
    }
}
