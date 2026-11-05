package com.openapi.core.cast

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * [DlnaDiscovery] 的 Java 友好入口。
 *
 * 现有视频页是 View/Java 体系，没有现成的 lifecycle scope 可挂，而 Kotlin 的
 * suspend 函数在 Java 里只能用 Continuation 手写——那样调用点会很难读。
 * 这里用一个短生命周期协程 + 主线程回调，把复杂度收在模块内部。
 *
 * 用完记得 [close]，否则搜索协程会一直挂着（虽然它自己会超时结束）。
 */
class DlnaDiscoveryCallback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    // 不用 @JvmOverloads：它和 SAM 接口参数组合会触发 Kotlin 编译器后端崩溃
    // (Backend Internal error during IR lowering)。Java 调用方显式传 timeoutMs 即可。
    fun discover(onResult: OnDevicesFound, timeoutMs: Int) {
        scope.launch {
            val devices = runCatching { DlnaDiscovery().discover(timeoutMs) }
                .getOrElse { emptyList() }
            mainHandler.post { onResult.onFound(devices) }
        }
    }

    /** 默认 4 秒超时的便捷重载。 */
    fun discover(onResult: OnDevicesFound) = discover(onResult, 4000)

    fun close() {
        scope.cancel()
    }
}
