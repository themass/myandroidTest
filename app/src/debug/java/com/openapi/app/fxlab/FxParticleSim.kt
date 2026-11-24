package com.openapi.app.fxlab

import java.util.Random

enum class ParticleKind { PAT_HEAD_BURST, WAVE_ARC, AMBIENT_LOOP }

/** Interleaved x,y pairs in normalized view space (0..1); only the first [count] are live. */
data class ParticleFrame(val positions: FloatArray, val count: Int)

/**
 * Deterministic fixed-timestep particle simulation. All behaviour lives here (pure JVM);
 * the GL host only draws what this returns. [seed] makes two runs byte-identical so a
 * comparison screenshot of an engine is reproducible.
 */
class FxParticleSim(val kind: ParticleKind, val seed: Long) {

    private val random = Random(seed)
    private val x = FloatArray(MAX_COUNT)
    private val y = FloatArray(MAX_COUNT)
    private val vx = FloatArray(MAX_COUNT)
    private val vy = FloatArray(MAX_COUNT)
    private val life = FloatArray(MAX_COUNT)
    private var count = 0
    private var stopped = false
    private var ageSeconds = 0f

    fun stop() {
        stopped = true
        count = 0
    }

    fun alive(): Boolean = !stopped && (kind == ParticleKind.AMBIENT_LOOP || count > 0)

    fun step(dtSeconds: Float): ParticleFrame {
        if (stopped) return ParticleFrame(FloatArray(0), 0)
        val dt = dtSeconds.coerceIn(0f, MAX_DT)
        ageSeconds += dt
        // Seeding happens exactly once, on the first step after construction.
        if (ageSeconds <= dt + 1e-6f) seedParticles()
        integrate(dt)
        if (kind == ParticleKind.AMBIENT_LOOP) refillAmbient()
        val out = FloatArray(count * 2)
        for (i in 0 until count) {
            out[i * 2] = x[i]
            out[i * 2 + 1] = y[i]
        }
        return ParticleFrame(out, count)
    }

    private fun seedParticles() {
        when (kind) {
            ParticleKind.PAT_HEAD_BURST -> {
                // Radial puff centred on the head slot (mirrors the Lottie head anchor).
                val originX = 0.5f
                val originY = 0.28f
                for (i in 0 until BURST_COUNT) {
                    val angle = random.nextFloat() * TAU
                    val speed = 0.15f + random.nextFloat() * 0.35f
                    x[i] = originX
                    y[i] = originY
                    vx[i] = kotlin.math.cos(angle) * speed
                    vy[i] = kotlin.math.sin(angle) * speed
                    life[i] = 1.0f + random.nextFloat()
                }
                count = BURST_COUNT
            }
            ParticleKind.WAVE_ARC -> {
                // Outward arc across the hand side, then fade with life.
                val cx = 0.5f
                val cy = 0.45f
                for (i in 0 until ARC_COUNT) {
                    val t = i.toFloat() / (ARC_COUNT - 1)
                    val angle = ARC_START + (ARC_SWEEP * t)
                    val radius = 0.22f + random.nextFloat() * 0.06f
                    x[i] = cx + kotlin.math.cos(angle) * radius
                    y[i] = cy + kotlin.math.sin(angle) * radius
                    vx[i] = kotlin.math.cos(angle) * 0.12f
                    vy[i] = kotlin.math.sin(angle) * 0.12f
                    life[i] = 0.9f + random.nextFloat() * 0.6f
                }
                count = ARC_COUNT
            }
            ParticleKind.AMBIENT_LOOP -> {
                for (i in 0 until AMBIENT_COUNT) spawnAmbient(i)
                count = AMBIENT_COUNT
            }
        }
    }

    private fun spawnAmbient(i: Int) {
        x[i] = random.nextFloat()
        y[i] = random.nextFloat()
        vx[i] = (random.nextFloat() - 0.5f) * 0.04f
        vy[i] = -0.02f - random.nextFloat() * 0.03f
        life[i] = 1.5f + random.nextFloat() * 1.5f
    }

    private fun integrate(dt: Float) {
        var write = 0
        for (read in 0 until count) {
            if (life[read] <= 0f) continue
            if (kind == ParticleKind.PAT_HEAD_BURST) vy[read] += GRAVITY * dt
            x[read] = (x[read] + vx[read] * dt).coerceIn(0f, 1f)
            y[read] = (y[read] + vy[read] * dt).coerceIn(0f, 1f)
            life[read] -= dt
            if (write != read) {
                x[write] = x[read]; y[write] = y[read]
                vx[write] = vx[read]; vy[write] = vy[read]
                life[write] = life[read]
            }
            write++
        }
        count = write
    }

    private fun refillAmbient() {
        // Respawn-on-expire: live count stays constant until stop().
        while (count < AMBIENT_COUNT) {
            spawnAmbient(count)
            count++
        }
    }

    private companion object {
        const val MAX_COUNT = 512
        const val BURST_COUNT = 180
        const val ARC_COUNT = 140
        const val AMBIENT_COUNT = 96
        const val GRAVITY = 0.9f
        const val MAX_DT = 0.05f
        const val TAU = 6.2831855f
        const val ARC_START = -2.1f
        const val ARC_SWEEP = 1.2f
    }
}
