package com.openapi.app.fxlab

import com.openapi.app.fxlab.RendererKind.AGSL
import com.openapi.app.fxlab.RendererKind.GL
import com.openapi.app.fxlab.RendererKind.LOTTIE_LAYER
import com.openapi.app.fxlab.RendererKind.RIVE
import com.openapi.app.fxlab.RendererKind.THREE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertNull(cameraRendererFor(FxEngineId.LOTTIE))
        FxEngineCatalog.all.filter { it != FxEngineId.LOTTIE }.forEach {
            assertNotNull("camera needs its own renderer for $it", cameraRendererFor(it))
        }
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
