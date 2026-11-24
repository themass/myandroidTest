package com.openapi.app.fxlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 粒子**模拟**是行为被钉死的地方（纯 JVM）；GL 只负责把它画出来。
 */
class FxParticleSimTest {

    @Test
    fun burstEmitsParticlesThenDiesWithinLifetime() {
        val sim = FxParticleSim(ParticleKind.PAT_HEAD_BURST, seed = 42L)
        assertTrue(sim.step(0.016f).count > 0)
        var t = 0.016f
        var guard = 0
        while (sim.alive() && guard++ < 600) {
            sim.step(0.016f)
            t += 0.016f
        }
        assertTrue("burst should end in <10s, ended at ${t}s", t < 10f)
    }

    @Test
    fun sameSeedGivesSameTraceSoScreensAreReproducible() {
        val a = FxParticleSim(ParticleKind.PAT_HEAD_BURST, 7L)
        val b = FxParticleSim(ParticleKind.PAT_HEAD_BURST, 7L)
        repeat(20) {
            assertEquals(a.step(0.016f).positions.toList(), b.step(0.016f).positions.toList())
        }
    }

    @Test
    fun ambientLoopStaysAliveUntilStopped() {
        val sim = FxParticleSim(ParticleKind.AMBIENT_LOOP, 1L)
        repeat(120) { sim.step(0.016f) }
        assertTrue(sim.alive())
    }

    @Test
    fun waveArcFadesOutAndEnds() {
        val sim = FxParticleSim(ParticleKind.WAVE_ARC, seed = 3L)
        assertTrue(sim.step(0.016f).count > 0)
        var t = 0.016f
        var guard = 0
        while (sim.alive() && guard++ < 600) {
            sim.step(0.016f)
            t += 0.016f
        }
        assertTrue("arc should end in <10s, ended at ${t}s", t < 10f)
    }

    @Test
    fun burstEndsAtCapSoSmallWindowCannotSpike() {
        val sim = FxParticleSim(ParticleKind.PAT_HEAD_BURST, seed = 9L)
        repeat(30) {
            assertTrue(
                "count must stay <= 512",
                sim.step(0.016f).count <= 512,
            )
        }
    }

    @Test
    fun positionsAreNormalizedInViewSpace() {
        val sim = FxParticleSim(ParticleKind.PAT_HEAD_BURST, seed = 11L)
        repeat(40) {
            val frame = sim.step(0.016f)
            for (i in 0 until frame.count) {
                val x = frame.positions[i * 2]
                val y = frame.positions[i * 2 + 1]
                assertTrue("x out of 0..1: $x", x.isFinite() && x in 0f..1f)
                assertTrue("y out of 0..1: $y", y.isFinite() && y in 0f..1f)
            }
        }
    }

    @Test
    fun stoppedAmbientLoopDies() {
        val sim = FxParticleSim(ParticleKind.AMBIENT_LOOP, 1L)
        repeat(10) { sim.step(0.016f) }
        sim.stop()
        repeat(3) { sim.step(0.016f) }
        assertEquals(0, sim.step(0.016f).count)
        assertTrue(!sim.alive())
    }
}
