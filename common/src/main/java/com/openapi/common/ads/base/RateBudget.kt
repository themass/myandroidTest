package com.openapi.common.ads.base

/**
 * 插屏概率的**窗口化**预算 —— 取代 `AdsContext.showCount`。
 *
 * 旧实现是 `if (showCount++ > 8) return false;`，而 `showCount` 是
 * `private static int`、全仓没有任何复位点：进程启动后第 9 次 `showRand` 起，
 * **整个进程生命周期内所有插屏永久返回 false**。用户报「广告原先有、现在全没了」，
 * 这是其中唯一在 release 包同样发生的一条（台账
 * docs/design/20261125-admob-ads-ledger.md §0.2）。
 *
 * 限流语义保留（防止连弹），但预算按窗口滚动：窗口过期后重新计数。
 */
class RateBudget @JvmOverloads constructor(
    private val clock: AdsClock = AdsClock { System.currentTimeMillis() },
    private val budget: Int = DEFAULT_BUDGET,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
) {
    private var windowStart = Long.MIN_VALUE
    private var used = 0

    /** true = 本次允许进入概率判定。 */
    @Synchronized
    fun take(): Boolean {
        val now = clock.now()
        if (windowStart == Long.MIN_VALUE) {
            windowStart = now
        } else if (used >= budget) {
            if (now - windowStart < windowMs) return false
            windowStart = now
            used = 0
        }
        used++
        return true
    }

    companion object {
        /** 与旧 `showCount > 8` 同量级的窗口配额。 */
        const val DEFAULT_BUDGET = 8
        /** 窗口长度：10 分钟。旧实现没有这一维，所以「8 次」是终身的。 */
        const val DEFAULT_WINDOW_MS = 600_000L
    }
}
