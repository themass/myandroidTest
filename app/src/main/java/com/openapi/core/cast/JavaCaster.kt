package com.openapi.core.cast

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * [DlnaCaster] 的 Java 入口。
 *
 * 现有视频页是 View/Java 体系，Kotlin 的 suspend 函数在 Java 里要手写 Continuation，
 * 调用点会变得没法读。这里把协程与线程切换收在内部，对外只是普通方法 + 回调，
 * 结果统一在��线程回调。
 *
 * 页面销毁时调用 [close] 释放协程与本地 HTTP 服务。
 */
class JavaCaster(context: android.content.Context) {

    private val caster = DlnaCaster(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    val state: CastState get() = caster.state.value

    fun castRemote(device: DlnaDevice, url: String, title: String?, callback: CastCallback?) {
        scope.launch {
            caster.castRemote(device, url, title)
            notify(callback)
        }
    }

    fun castLocal(device: DlnaDevice, path: String, title: String?, callback: CastCallback?) {
        scope.launch {
            caster.castLocal(device, java.io.File(path), title)
            notify(callback)
        }
    }

    fun pause() = scope.launch { caster.pause() }

    fun resume() = scope.launch { caster.resume() }

    fun seekTo(seconds: Int) = scope.launch { caster.seekTo(seconds) }

    fun stop() = caster.stop()

    fun close() {
        caster.stop()
        scope.cancel()
    }

    private fun notify(callback: CastCallback?) {
        if (callback == null) return
        val error = (caster.state.value as? CastState.Error)?.message
        mainHandler.post { callback.onDone(error) }
    }
}
