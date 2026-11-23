package com.openapi.feature.companion.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** FxRenderPolicy 验收判据：reducedMotion / particles / 放大态退化。 */
class FxRenderPolicyTest {

    private val heart = StageEffect.Play(FxAssets.PAT_HEAD, StageEffect.Anchor.HEAD)
    private val petals = StageEffect.Play(FxAssets.CONNECTED, StageEffect.Anchor.FULL)

    @Test
    fun eventResponse_playsNormally() {
        val d = FxRenderPolicy.decide(heart, reducedMotion = false, particles = true, stageZoomed = false)
        assertEquals(FxRenderPolicy.Mode.PLAY, d.mode)
        assertNull(d.reason)
    }

    @Test
    fun reducedMotion_eventBecomesStatic_ambienceSkipped() {
        val heartD = FxRenderPolicy.decide(heart, reducedMotion = true, particles = true, stageZoomed = false)
        assertEquals(FxRenderPolicy.Mode.STATIC_FRAME, heartD.mode)

        val petalsD = FxRenderPolicy.decide(petals, reducedMotion = true, particles = true, stageZoomed = false)
        assertEquals(FxRenderPolicy.Mode.SKIP, petalsD.mode)
        assertEquals(FxRenderPolicy.SkipReason.REDUCED_MOTION_AMBIENCE, petalsD.reason)
    }

    @Test
    fun particlesOff_skipsAmbience_keepsEvent() {
        val heartD = FxRenderPolicy.decide(heart, reducedMotion = false, particles = false, stageZoomed = false)
        assertEquals(FxRenderPolicy.Mode.PLAY, heartD.mode)

        val petalsD = FxRenderPolicy.decide(petals, reducedMotion = false, particles = false, stageZoomed = false)
        assertEquals(FxRenderPolicy.Mode.SKIP, petalsD.mode)
        assertEquals(FxRenderPolicy.SkipReason.PARTICLES_OFF, petalsD.reason)
    }

    @Test
    fun zoomed_skipsAnchor_keepsFull() {
        val heartD = FxRenderPolicy.decide(heart, reducedMotion = false, particles = true, stageZoomed = true)
        assertEquals(FxRenderPolicy.Mode.SKIP, heartD.mode)
        assertEquals(FxRenderPolicy.SkipReason.ZOOMED_ANCHOR, heartD.reason)

        val petalsD = FxRenderPolicy.decide(petals, reducedMotion = false, particles = true, stageZoomed = true)
        assertEquals(FxRenderPolicy.Mode.PLAY, petalsD.mode)
    }

    @Test
    fun unknownAsset_skipped() {
        val unknown = StageEffect.Play("fx_nope", StageEffect.Anchor.HEAD)
        assertEquals(FxRenderPolicy.Mode.SKIP, FxRenderPolicy.decide(unknown, false, true, false).mode)
    }
}
