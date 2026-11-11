package com.openapi.common.net

import com.openapi.common.net.request.InfoListVo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

/*
 * 给 Java 调用方用的 CompletableFuture 版本（Java 不能直接调用挂起函数）。
 * 请求在 Dispatchers.IO 上执行，结果或异常通过 future 交给调用方，调用方不用阻塞后台线程。
 */

private val asyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** 分页列表；失败时 future 以 ExecutionException 完成。 */
fun <T> BaseService.infoListAsync(url: String, clazz: Class<T>, tag: String): CompletableFuture<InfoListVo<T>> =
    launchFuture { awaitInfoList(url, clazz, tag) }

/** 单个对象；失败时 future 以 ExecutionException 完成。 */
fun <T> BaseService.getAsync(url: String, clazz: Class<T>, tag: String): CompletableFuture<T> =
    launchFuture { awaitGet(url, clazz, tag) }

private fun <T> launchFuture(block: suspend () -> T): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    asyncScope.launch {
        try {
            future.complete(block())
        } catch (e: Throwable) {
            future.completeExceptionally(e)
        }
    }
    return future
}
