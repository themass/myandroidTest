package com.openapi.app.fxlab

import com.openapi.app.fxlab.RendererKind.AGSL
import com.openapi.app.fxlab.RendererKind.GL
import com.openapi.app.fxlab.RendererKind.LOTTIE_LAYER
import com.openapi.app.fxlab.RendererKind.RIVE
import com.openapi.app.fxlab.RendererKind.THREE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererDispatchTest {

    @Test
    fun everyEngineDispatchesToItsOwnKindOnStage() {
        assertEquals(LOTTIE_LAYER, stageRendererFor(FxEngineId.LOTTIE))
        assertEquals(RIVE, stageRendererFor(FxEngineId.RIVE))
        assertEquals(GL, stageRendererFor(FxEngineId.GL_PARTICLE))
        assertEquals(THREE, stageRendererFor(FxEngineId.THREE_WEBGL))
        assertEquals(AGSL, stageRendererFor(FxEngineId.AGSL_SHADER))
    }

    @Test
    fun cameraUsesOwnRenderersAndOnlyLottieFallsBackToModuleBranch() {
        val slot = LabCueSlot(active = true, tick = 0L, cueId = null)
        assertNull(cameraRendererFor(FxEngineId.LOTTIE, slot))
        FxEngineCatalog.all.filter { it != FxEngineId.LOTTIE }.forEach {
            assertNotNull("camera needs its own renderer for $it", cameraRendererFor(it, slot))
        }
    }

    // 终审 Important #1：闸不可用时绝不能让相机模块的 null→Lottie 分支出像素。
    @Test
    fun unavailableCameraGateNeverShowsModuleLottiePixels() {
        val gate = GateOutcome.Unavailable("需要 API 33+，本机 API 28")
        val slot = LabCueSlot(active = true, tick = 0L, cueId = null)
        FxEngineCatalog.all.filter { it != FxEngineId.LOTTIE }.forEach {
            assertSame(
                "unavailable $it must not fall through to the module's Lottie sticker",
                blankCameraSticker,
                cameraStickerFor(it, gate, slot),
            )
        }
        assertNull(cameraStickerFor(FxEngineId.LOTTIE, GateOutcome.Enabled, slot))
    }

    // 终审 Important #2：停要能停住所有后端；停后再触发必须重播。
    @Test
    fun stopSilencesEveryBackendAndNextTriggerRestarts() {
        assertTrue(labEffectActive(gestureId = null, stopSeq = 0L)) // 未停过：待机允许
        assertTrue(labEffectActive(gestureId = "pat_head", stopSeq = 1L))
        assertFalse(labEffectActive(gestureId = null, stopSeq = 1L)) // 停后没有任何东西继续跑
    }

    @Test
    fun stopBumpsReplayTickSoNextTriggerRestartsEffect() {
        val before = replayTick(triggerSeq = 5, stopSeq = 2)
        val afterStop = replayTick(triggerSeq = 5, stopSeq = 3)
        assertTrue(afterStop > before)
        assertTrue(replayTick(triggerSeq = 6, stopSeq = 3) > afterStop)
    }

    @Test
    fun unavailableGateNeverFallsBackToAnotherEngine() {
        assertNull(rendererKindIfEnabled(FxEngineId.AGSL_SHADER, GateOutcome.Unavailable("本机 API 28，需 33")))
        assertEquals(AGSL, rendererKindIfEnabled(FxEngineId.AGSL_SHADER, GateOutcome.Enabled))
    }
}
