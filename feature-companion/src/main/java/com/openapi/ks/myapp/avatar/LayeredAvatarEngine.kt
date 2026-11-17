package com.openapi.ks.myapp.avatar

import kotlin.math.PI
import kotlin.math.exp
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
    /** 脸红程度 0..1（点脸、点胸后出现，几秒内淡去）。 */
    val blush: Float = 0f,
    /** 左右手被点后的上抬量（参考像素，向上为负）。 */
    val handLY: Float = 0f,
    val handRY: Float = 0f,
    /** 最近一次点击：画布比例坐标与反馈进度 0..1（1 = 反馈结束）。 */
    val tapX: Float = 0f,
    val tapY: Float = 0f,
    val tapProgress: Float = 1f,
    /** 最近一次点中的部位；反馈结束后为 null。 */
    val tapRegion: String? = null,
) {
    companion object {
        const val REF_WIDTH = 600f
    }
}

/**
 * 形象的六要素状态机（由浏览器原型移植）：呼吸、独立眨眼、嘴型、头发弹簧、命中区域、待机微动，
 * 外加「被碰到」的本地反应。
 *
 * 本地反应只是肢体上的即时回应（摸头时头发晃、眯眼；点脸脸红；点手手抬一下），让触摸
 * 立刻有落点；她说什么、什么情绪，仍由 agent 收到触摸事件后决定，客户端不替她说话。
 *
 * 嘴型由真实音频振幅决定：不是音素识别，只是按响度选一个开口形状。
 */
class LayeredAvatarEngine(private val random: Random = Random.Default) {

    /** 远端音频振幅 0..1。低于 [MOUTH_THRESHOLD] 闭嘴。 */
    var mouthLevel: Float = 0f

    private var t = 0f
    private var hairX = 0f
    private var hairV = 0f
    /** 额外的点头/后仰：独立弹簧，被触摸时给一个冲量。 */
    private var nodX = 0f
    private var nodV = 0f
    private val eyeL = BlinkTrack()
    private val eyeR = BlinkTrack()
    private var squintLeft = 0f
    private var blush = 0f
    private var blushHold = 0f
    private var handL = 0f
    private var handR = 0f
    private var tapX = 0f
    private var tapY = 0f
    private var tapAge = TAP_FEEDBACK_SECONDS
    private var tapRegion: String? = null

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

        // 触摸带来的点头/后仰：弹簧阻尼，冲量来自 [tap]。
        val nodAcc = -NOD_K * nodX - NOD_D * nodV
        nodV += nodAcc * d
        nodX += nodV * d
        val rootRot = sin(t * TAU * 0.1f + 0.3f) * idleAmp * 0.12f + nodX

        // 呼吸：较快，只作用于躯干和胸。
        val breath = sin(t * TAU * 0.28f)
        val breathAmp = 5.5f * BREATH
        val torsoY = breath * breathAmp * -0.35f
        val torsoSy = 1f + breath * 0.012f * BREATH
        val chestY = breath * breathAmp * -0.55f
        val chestSy = 1f + breath * 0.022f * BREATH

        // 头发：弹簧阻尼，由待机摆动、点头与少量噪声驱动。
        val drive = rootRot * 0.08f + sin(t * 3.1f) * 0.015f * HAIR
        val acc = -HAIR_K * hairX - HAIR_D * hairV + drive * 40f * HAIR
        hairV += acc * d
        hairX += hairV * d

        eyeL.tick(d, random)
        eyeR.tick(d, random)
        squintLeft = (squintLeft - d).coerceAtLeast(0f)
        val squint = squintLeft > 0f

        // 脸红：保持一段时间后指数淡出。
        if (blushHold > 0f) blushHold = (blushHold - d).coerceAtLeast(0f) else blush *= exp(-d / 0.9f)

        // 手：点中后抬起再落下（半个正弦），独立衰减。
        handL = (handL - d / HAND_SECONDS).coerceAtLeast(0f)
        handR = (handR - d / HAND_SECONDS).coerceAtLeast(0f)

        tapAge = (tapAge + d).coerceAtMost(TAP_FEEDBACK_SECONDS)
        if (tapAge >= TAP_FEEDBACK_SECONDS) tapRegion = null

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
            eyeL = if (squint && eyeL.shape == "open") "half" else eyeL.shape,
            eyeR = if (squint && eyeR.shape == "open") "half" else eyeR.shape,
            mouth = mouthFor(mouthLevel),
            blush = blush.coerceIn(0f, 1f),
            handLY = -sin(handL * PI.toFloat()) * HAND_LIFT,
            handRY = -sin(handR * PI.toFloat()) * HAND_LIFT,
            tapX = tapX,
            tapY = tapY,
            tapProgress = tapAge / TAP_FEEDBACK_SECONDS,
            tapRegion = tapRegion,
        )
    }

    /**
     * 点中某部位：返回部位 id，并做出即时的肢体反应。未命中任何热区返回 null。
     * 坐标为画布比例 0..1，y 向下。
     */
    fun tap(x: Float, y: Float): String? {
        val region = hitTest(x, y) ?: return null
        tapX = x
        tapY = y
        tapAge = 0f
        tapRegion = region
        when (region) {
            // 摸头：头发被拨动、微微低头、眯眼。
            "head" -> {
                hairV += 2.6f
                nodV += 9f
                squintLeft = 0.7f
            }
            // 点脸：脸红、眯一下眼。
            "face" -> {
                blush = 1f
                blushHold = 1.6f
                squintLeft = 0.35f
                nodV -= 5f
            }
            // 点胸口：往后一缩，脸红。
            "chest" -> {
                nodV -= 12f
                blush = 0.8f
                blushHold = 1.0f
            }
            "hand_l" -> handL = 1f
            "hand_r" -> handR = 1f
        }
        return region
    }

    private fun mouthFor(level: Float): String = when {
        !level.isFinite() || level < MOUTH_THRESHOLD -> "closed"
        level >= 0.75f -> "A"
        level >= 0.6f -> "O"
        level >= 0.5f -> "E"
        else -> "I"
    }

    /** 归一化坐标 → 部位。后画的（列表靠后的）优先：脸在头里面，点脸算脸。 */
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

    /** 点击命中区，坐标为 0..1 的画布比例。按这套 512×768 素材的实际位置校准。 */
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
        private const val NOD_K = 60f
        private const val NOD_D = 9f
        private const val MOUTH_THRESHOLD = 0.35f
        private const val HAND_SECONDS = 0.6f
        private const val HAND_LIFT = 22f
        const val TAP_FEEDBACK_SECONDS = 0.9f

        /**
         * 热区按这套 512×768 素材的 alpha 包围盒实测（画布比例）：头发 y0.145–0.333、
         * 脸 x0.346–0.654 / y0.180–0.421、眼睛 y0.288–0.323、胸 x0.396–0.604 / y0.479–0.587、
         * 左手 x0.207–0.326 / 右手 x0.674–0.793，y0.646–0.711。
         * 头部取眼睛以上的头顶；热区比图形外扩约 0.03，手指好点中。
         */
        private val HIT_ZONES = listOf(
            HitZone("head", 0.27f, 0.12f, 0.46f, 0.17f),
            HitZone("face", 0.33f, 0.27f, 0.34f, 0.17f),
            HitZone("chest", 0.37f, 0.46f, 0.26f, 0.15f),
            HitZone("hand_l", 0.17f, 0.61f, 0.19f, 0.14f),
            HitZone("hand_r", 0.64f, 0.61f, 0.19f, 0.14f),
        )
    }
}
