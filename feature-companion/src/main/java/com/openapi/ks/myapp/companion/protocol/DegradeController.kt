package com.openapi.ks.myapp.companion.protocol

/**
 * 端侧降级阶梯 L0–L4（§12.3）。
 *
 * 纯 Kotlin、假时钟可测：所有时间由调用方以单调毫秒传入。判定只看本地传感器，
 * 不发新消息、不改形象渲染——接线层用 [level] 挡 AvatarJson、写 `telemetry.health`。
 *
 * 升档立刻；降档要连续好窗口 ≥ [hysteresisMs]，避免抖动。
 */
class DegradeController(
    private val windowSize: Int = 20,
    private val cueSilenceMs: Long = 5_000L,
    private val dataDeadMs: Long = 6_000L,
    private val pcmAliveMs: Long = 2_000L,
    private val pcmDeadMs: Long = 6_000L,
    private val hysteresisMs: Long = 2_500L,
    private val lateRatioThreshold: Double = 0.3,
    private val gapStreakThreshold: Int = 3,
) {
    enum class Conn { CONNECTED, RECONNECTING, DISCONNECTED }

    /** 手动注入（验收每一档 / 单测）。非 null 时压过自动判定。 */
    var forceLevel: Int? = null

    // 环形窗口：true = expired，false = applied。只计 decide 结果，不算 CANCELLED。
    private val decisions = BooleanArray(windowSize)
    private var decisionCount = 0
    private var decisionWrite = 0
    private var expiredInWindow = 0

    private var cueGapStreak = 0
    private var lastCueAt: Long? = null
    private var lastDataAt: Long? = null
    private var lastPcmAt: Long? = null
    private var connection: Conn = Conn.CONNECTED

    private var stickyLevel = 0
    private var recoverSince: Long? = null

    fun onCueApplied(lateMs: Long) {
        pushDecision(expired = false)
    }

    fun onCueExpired() {
        pushDecision(expired = true)
    }

    fun onCueGap() {
        cueGapStreak++
    }

    /** cue 到达：刷新静默计时。无 gap 的干净到达才会清零 streak。 */
    fun onCueAccepted(now: Long, clearGapStreak: Boolean = true) {
        if (clearGapStreak) cueGapStreak = 0
        lastCueAt = now
        lastDataAt = now
    }

    fun onDataAccepted(now: Long) {
        lastDataAt = now
    }

    fun onPcm(now: Long) {
        lastPcmAt = now
    }

    fun onConnection(state: Conn) {
        connection = state
        if (state == Conn.CONNECTED) {
            // 重连后窗口从干净状态再计；sticky 仍走 hysteresis，避免立刻抖回。
            clearWindow()
            cueGapStreak = 0
        }
    }

    fun level(now: Long): Int {
        forceLevel?.let { return it.coerceIn(0, 4) }
        val desired = desiredLevel(now)
        if (desired > stickyLevel) {
            stickyLevel = desired
            recoverSince = null
            return stickyLevel
        }
        if (desired < stickyLevel) {
            val since = recoverSince
            if (since == null) {
                recoverSince = now
                return stickyLevel
            }
            if (now - since >= hysteresisMs) {
                stickyLevel = desired
                recoverSince = null
                return stickyLevel
            }
            return stickyLevel
        }
        recoverSince = null
        return stickyLevel
    }

    private fun desiredLevel(now: Long): Int {
        if (connection == Conn.RECONNECTING || connection == Conn.DISCONNECTED) return 4

        val pcmAt = lastPcmAt
        if (pcmAt != null && now - pcmAt > pcmDeadMs) return 4

        val dataAt = lastDataAt
        if (dataAt != null && now - dataAt > dataDeadMs) {
            if (pcmAt != null && now - pcmAt <= pcmAliveMs) return 3
        }

        val cueAt = lastCueAt
        if (cueAt != null && now - cueAt > cueSilenceMs) return 2

        if (lateRatio() > lateRatioThreshold || cueGapStreak >= gapStreakThreshold) return 1

        return 0
    }

    private fun lateRatio(): Double {
        if (decisionCount == 0) return 0.0
        return expiredInWindow.toDouble() / decisionCount.toDouble()
    }

    private fun pushDecision(expired: Boolean) {
        if (decisionCount == windowSize) {
            if (decisions[decisionWrite]) expiredInWindow--
        } else {
            decisionCount++
        }
        decisions[decisionWrite] = expired
        if (expired) expiredInWindow++
        decisionWrite = (decisionWrite + 1) % windowSize
    }

    private fun clearWindow() {
        decisionCount = 0
        decisionWrite = 0
        expiredInWindow = 0
    }

    companion object {
        /**
         * Apply 闸：L0 全放；L1 `state` 全放、`emotion` 约 1/2、其它丢；
         * L2+ 全部 `presentation.*` 丢。口型不经这条路径。
         */
        fun shouldEmit(eventType: String, level: Int, emotionSeq: Int): Boolean {
            if (level <= 0) return true
            if (level >= 2) return false
            // L1
            return when (eventType) {
                "presentation.state" -> true
                "presentation.emotion" -> emotionSeq % 2 == 0
                else -> false
            }
        }
    }
}
