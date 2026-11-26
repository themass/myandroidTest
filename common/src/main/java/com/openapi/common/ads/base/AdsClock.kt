package com.openapi.common.ads.base

/**
 * 可注入时钟：广告门控不直接读 `System.currentTimeMillis()`，
 * JVM 单测里要能推进时间（静默期、最小间隔、窗口复位都要跨时间验证）。
 */
fun interface AdsClock {
    fun now(): Long
}
