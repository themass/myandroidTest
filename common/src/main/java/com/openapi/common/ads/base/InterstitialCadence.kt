package com.openapi.common.ads.base

/**
 * 插屏节律（进程级）：启动静默期 + 两次插屏最小间隔。
 *
 * 规则移植自 `4ff9d4e5 广告时机闸门…`（仅在 origin/global-vpn 上的 `AdGate.java`），
 * 主干从来没有这道闸——恢复插屏必须带上，否则「进页面就弹全屏」会被 Play 按
 * 「广告干扰核心功能」处理。判定本体在 [AdsShowPolicy.allowInterstitial]（纯函数），
 * 本对象只持有时间戳，方便 JVM 注入时钟测试（AdsShowPolicyTest）。
 */
object InterstitialCadence {

    /** 可注入时钟：测试推进时间验证静默期/间隔。 */
    @Volatile
    @JvmStatic
    var clock: AdsClock = AdsClock { System.currentTimeMillis() }

    private var startupAtMs = 0L
    private var lastShownAtMs = 0L

    /** 进程启动锚点（MyApplication.onCreate 调一次）。 */
    @JvmStatic
    fun noteStartup() {
        startupAtMs = clock.now()
    }

    /** 真正展示过一屏插屏之后调用（决定下一次的最小间隔）。 */
    @JvmStatic
    fun noteShown() {
        lastShownAtMs = clock.now()
    }

    /** 当前时刻是否允许弹插屏。 */
    @JvmStatic
    fun allow(): Boolean =
        AdsShowPolicy.allowInterstitial(clock.now(), startupAtMs, lastShownAtMs)
}
