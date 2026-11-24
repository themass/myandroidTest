package com.openapi.feature.camerafx

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraFxStickerRendererTest {

    @Test
    fun rendererOverridesLottieBranch() {
        assertTrue(stickerRendererTakesOverWhenSet(rendererSet = true, compLoaded = true))
    }

    @Test
    fun nullRendererKeepsLottieBranch() {
        assertFalse(stickerRendererTakesOverWhenSet(rendererSet = false, compLoaded = true))
    }

    @Test
    fun rendererCanRenderWithFallbackPlacementWhileCompositionLoads() {
        assertTrue(stickerRendererTakesOverWhenSet(rendererSet = true, compLoaded = false))
    }
}
