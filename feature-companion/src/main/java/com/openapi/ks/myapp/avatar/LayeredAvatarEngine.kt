package com.openapi.ks.myapp.avatar

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 一帧的姿态。位移与角度直接用于绘制：位移单位是参考宽度 [REF_WIDTH] 下的像素，
 * 绘制时按实际宽度等比换算，换屏幕尺寸不用改引擎。
 */
data class LayeredPose(
    val rootX: Float = 0f,
    val rootY: Float = 0f,
    val rootRot: Float = 0f,
    val torsoY: Float = 0f,
    val torsoSy: Float = 1f,
    val chestY: Float = 0f,
    val chestSy: Float = 1f,
    val bangsRot: Float = 0f,
    val hairSideRot: Float = 0f,
    val eyeL: String = "open",
    val eyeR: String = "open",
    /** `closed`、`A`、`I`、`U`、`E`、`O` 之一，与 manifest 的 `mouth` 分组变体名一致。 */
    val mouth: String = "closed",
    /** 刚被点中的部位，用于短暂的点击反馈；没有则为 null。 */
    val flashRegion: String? = null,
) {
    companion object {
        const val REF_WIDTH = 600f
    }
}

/**
 * 形象的六要素状态机（由浏览器原型移植）：呼吸、独立眨眼、嘴型、头发弹簧、命中区域、待机微动。
 *
 * 不持有任何 Compose 状态，只接收 [update] 的时间增量与 [mouthLevel]，输出 [LayeredPose]。
 * 嘴型由真实音频振幅决定：这不是音素识别，只是按响度选一个开口形状，诚实地表示「她在说话、声音有多大」。
 */
class LayeredAvatarEngine(private val random: Random = Random.Default) {

    /** 远端音频振幅 0..1。低于 [MOUTH_THRESHOLD] 闭嘴。 */
    var mouthLevel: Float = 0f

    private var t = 0f
    private var hairX = 0f
    private var hairV = 0f
    private val eyeL = BlinkTrack()
    private val eyeR = BlinkTrack()
    private var flashRegion: String? = null
    private var flashLeft = 0f

    init {
        eyeL.reschedule(random, first = true)
        eyeR.reschedule(random, first = true)
    }

    fun update(dt: Float): LayeredPose {
        val d = dt.coerceIn(0f, MAX_STEP)
        t += d

        // 待机微动：低频，作用于整体根节点。
        val idleAmp = 6f * IDLE
        val rootX = sin(t * TAU * 0.11f) * idleAmp * 0.35f
        val rootY = sin(t * TAU * 0.09f + 0.7f) * idleAmp * 0.12f
        val rootRot = sin(t * TAU * 0.1f + 0.3f) * idleAmp * 0.12f

        // 呼吸：较快，只作用于躯干和胸。
        val breath = sin(t * TAU * 0.28f)
        val breathAmp = 5.5f * BREATH
        val torsoY = breath * breathAmp * -0.35f
        val torsoSy = 1f + breath * 0.012f * BREATH
        val chestY = breath * breathAmp * -0.55f
        val chestSy = 1f + breath * 0.022f * BREATH

        // 头发：弹簧阻尼，由待机摆动与少量噪声驱动。
        val drive = rootRot * 0.08f + sin(t * 3.1f) * 0.015f * HAIR
        val acc = -HAIR_K * hairX - HAIR_D * hairV + drive * 40f * HAIR
        hairV += acc * d
        hairX += hairV * d

        eyeL.tick(d, random)
        eyeR.tick(d, random)

        flashLeft = (flashLeft - d).coerceAtLeast(0f)
        if (flashLeft == 0f) flashRegion = null

        return LayeredPose(
            rootX = rootX,
            rootY = rootY,
            rootRot = rootRot,
            torsoY = torsoY,
            torsoSy = torsoSy,
            chestY = chestY,
            chestSy = chestSy,
            bangsRot = hairX * 4.2f,
            hairSideRot = hairX * 2.6f,
            eyeL = eyeL.shape,
            eyeR = eyeR.shape,
            mouth = mouthFor(mouthLevel),
            flashRegion = flashRegion,
        )
    }

    /** 点中某部位：返回部位 id，并启动一次短反馈。未命中返回 null。 */
    fun tap(x: Float, y: Float): String? {
        val region = hitTest(x, y) ?: return null
        flashRegion = region
        flashLeft = FLASH_SECONDS
        return region
    }

    private fun mouthFor(level: Float): String = when {
        !level.isFinite() || level < MOUTH_THRESHOLD -> "closed"
        level >= 0.75f -> "A"
        level >= 0.6f -> "O"
        level >= 0.5f -> "E"
        else -> "I"
    }

    /** 归一化坐标 → 部位。后画的（列表靠后的）优先，与原型 DOM 叠放顺序一致。 */
    private fun hitTest(x: Float, y: Float): String? =
        HIT_ZONES.asReversed().firstOrNull { it.contains(x, y) }?.id

    /** 眨眼轨道：独立的随机间隔，半开 → 闭 → 半开 → 睁，与原型的节奏一致。 */
    private class BlinkTrack {
        var shape = "open"
        private var timer = 0f
        private var phase = Phase.IDLE
        private var step = 0

        fun reschedule(random: Random, first: Boolean = false) {
            val base = 2.2f - BLINK * 1.2f
            val spread = 1.8f + random.nextFloat() * 2.4f
            timer = (base + spread * (0.4f + random.nextFloat())).coerceAtLeast(0.35f)
            if (first) timer = (timer * random.nextFloat()).coerceAtLeast(0.6f)
            phase = Phase.IDLE
            step = 0
            shape = "open"
        }

        fun tick(dt: Float, random: Random) {
            timer -= dt
            if (phase == Phase.IDLE) {
                if (timer <= 0f) {
                    phase = Phase.BLINK
                    step = 0
                    timer = 0.045f
                    shape = BLINK_STEPS[0]
                }
                return
            }
            if (timer > 0f) return
            step += 1
            if (step >= BLINK_STEPS.size) {
                reschedule(random)
            } else {
                shape = BLINK_STEPS[step]
                timer = if (step == 1) 0.06f else 0.04f
            }
        }

        private enum class Phase { IDLE, BLINK }

        private companion object {
            val BLINK_STEPS = listOf("half", "closed", "half", "open")
        }
    }

    /** 点击命中区，坐标为 0..1 的画布比例（与原型的百分比热区相同）。 */
    private data class HitZone(val id: String, val left: Float, val top: Float, val width: Float, val height: Float) {
        fun contains(x: Float, y: Float) = x in left..(left + width) && y in top..(top + height)
    }

    companion object {
        private const val TAU = (2.0 * PI).toFloat()
        private const val MAX_STEP = 0.05f
        private const val IDLE = 0.4f
        private const val BREATH = 0.5f
        private const val BLINK = 0.5f
        private const val HAIR = 0.35f
        private const val HAIR_K = 28f
        private const val HAIR_D = 6.5f
        private const val MOUTH_THRESHOLD = 0.35f
        private const val FLASH_SECONDS = 0.42f

        private val HIT_ZONES = listOf(
            HitZone("head", 0.31f, 0.06f, 0.38f, 0.22f),
            HitZone("face", 0.34f, 0.16f, 0.32f, 0.16f),
            HitZone("chest", 0.34f, 0.40f, 0.32f, 0.18f),
            HitZone("hand_l", 0.08f, 0.52f, 0.18f, 0.14f),
            HitZone("hand_r", 0.74f, 0.52f, 0.18f, 0.14f),
        )
    }
}
