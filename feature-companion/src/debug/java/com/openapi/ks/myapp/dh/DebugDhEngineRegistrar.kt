package com.openapi.ks.myapp.dh

/**
 * debug 源集的引擎注册器——main 的 [DhLabBootstrap] 按类名反射调 `registerDebugEngines`。
 * release 变体不编本目录（rive-android 也只挂 debugImplementation），release 里
 * [Class.forName] 找不到这个类 = 静默跳过 = RIVE「未编入」。
 */
object DebugDhEngineRegistrar {
    @JvmStatic
    fun registerDebugEngines() {
        DhEngineRegistry.register(DhEngineId.RIVE) { RiveDigitalHumanEngine() }
    }
}
