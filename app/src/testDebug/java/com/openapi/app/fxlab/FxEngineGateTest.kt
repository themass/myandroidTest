package com.openapi.app.fxlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FxEngineGateTest {

    @Test
    fun agslUnavailableBelowApi33WithReasonNamingBothVersions() {
        val o = FxEngineGate.decide(FxEngineId.AGSL_SHADER, FxScene.DH_INTERACT, 28, true, "")
        assertTrue(o is GateOutcome.Unavailable)
        val r = (o as GateOutcome.Unavailable).reason
        assertTrue(r.contains("33") && r.contains("28"))
    }

    @Test
    fun agslAvailableOn33() {
        assertEquals(
            GateOutcome.Enabled,
            FxEngineGate.decide(FxEngineId.AGSL_SHADER, FxScene.DH_INTERACT, 33, true, ""),
        )
    }

    @Test
    fun missingAssetReportsGivenReasonNotGenericFailure() {
        val o = FxEngineGate.decide(FxEngineId.RIVE, FxScene.DH_INTERACT, 37, false, "缺 .riv 资产")
        assertEquals("缺 .riv 资产", (o as GateOutcome.Unavailable).reason)
    }

    @Test
    fun lottieAlwaysEnabledEveryScene() {
        FxScene.entries.forEach {
            assertEquals(GateOutcome.Enabled, FxEngineGate.decide(FxEngineId.LOTTIE, it, 28, true, ""))
        }
    }

    @Test
    fun everyEngineHasMetaAndChipOrderIsFixed() {
        assertEquals(
            listOf(FxEngineId.LOTTIE, FxEngineId.RIVE, FxEngineId.GL_PARTICLE, FxEngineId.THREE_WEBGL, FxEngineId.AGSL_SHADER),
            FxEngineCatalog.all,
        )
        FxEngineCatalog.all.forEach { id ->
            assertEquals(id, FxEngineCatalog.meta.getValue(id).id)
        }
    }
}