package com.openapi.feature.camerafx

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFxOverlayPolicyTest {

    @Test
    fun lottieAssetPath() {
        val p = CameraFxOverlayPolicy(stickerAssetId = StickerAssetIds.WAVE)
        assertEquals("fx/fx_wave.json", p.lottieAssetPath)
    }
}
