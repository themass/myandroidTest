package com.openapi.app.fxlab

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraConsentGateTest {

    @Test
    fun fullSceneShowsHudPipDoesNot() {
        assertTrue(labCameraPolicy(FxEngineId.LOTTIE, FxScene.CAMERA_FULL).showDebugHud)
        assertFalse(labCameraPolicy(FxEngineId.LOTTIE, FxScene.CAMERA_PIP).showDebugHud)
    }

    @Test
    fun wireframeOnlyForLottieFullScreen() {
        assertTrue(labCameraPolicy(FxEngineId.LOTTIE, FxScene.CAMERA_FULL).drawWireframe)
        assertFalse(labCameraPolicy(FxEngineId.RIVE, FxScene.CAMERA_FULL).drawWireframe)
        assertFalse(labCameraPolicy(FxEngineId.LOTTIE, FxScene.CAMERA_PIP).drawWireframe)
    }

    @Test
    fun stickerAlwaysOnForEveryEngine() {
        FxEngineCatalog.all.forEach {
            assertTrue(labCameraPolicy(it, FxScene.CAMERA_FULL).drawStickers)
            assertTrue(labCameraPolicy(it, FxScene.CAMERA_PIP).drawStickers)
        }
    }

    @Test
    fun consentAskedOnlyWhenNotAcceptedAndNotGranted() {
        assertTrue(cameraNeedsConsent(accepted = false, granted = false))
        assertFalse(cameraNeedsConsent(accepted = true, granted = false))
        assertFalse(cameraNeedsConsent(accepted = false, granted = true))
    }
}
