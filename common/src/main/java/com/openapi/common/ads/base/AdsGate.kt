package com.openapi.common.ads.base

/**
 * 广告总开关：**debug 构建一律不加载、不展示广告**。
 *
 * 真机验收踩到（P2-13，2026-11-21）：chat debug 包冷启动即弹全屏开屏/插屏
 * （GMA SDK 的 AdActivity），误触点下去会跳 googleadservices 证书报警页。
 * 测试机、开发机、CI 跑的签名都是 debug——广告在这些包里只有负面作用。
 *
 * 判定与 [com.openapi.common.util.android.SystemUtils.isApkDebugable] 同源
 * （调用方传入，保持本对象纯函数可单测，见 AdsGateTest）。release 行为不变。
 */
object AdsGate {
    /** 构建期 debug 标记，由 MyApplication 在 onCreate 最早处写入。 */
    @JvmStatic
    @Volatile
    var debugBuild: Boolean = false

    /** true = 允许加载与展示广告。@JvmOverloads：AdsManager 是 Java，要用无参重载。 */
    @JvmStatic
    @JvmOverloads
    fun enabled(isDebugBuild: Boolean = debugBuild): Boolean = !isDebugBuild
}
