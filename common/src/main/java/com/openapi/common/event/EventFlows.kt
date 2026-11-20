package com.openapi.common.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/*
 * 应用内事件的基础设施（替代 EventBus）。
 *
 * 每个模块在自己的包里声明事件的 flow（见 [eventFlow]），发布方调用 tryEmit，订阅方用 [observe] 收集。
 * 语义和 EventBus 的默认行为一致：没有订阅者时发布的事件直接丢弃（不缓存，不回放）。
 */

/** 创建一条事件 flow：不回放，缓冲 16 个事件，缓冲满时丢弃最旧的（与 EventBus 一样不阻塞发布方）。 */
fun <T> eventFlow(): MutableSharedFlow<T> = MutableSharedFlow(
    replay = 0,
    extraBufferCapacity = 16,
    onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
)

/** 只读视图，供订阅方使用。 */
fun <T> MutableSharedFlow<T>.readOnly(): SharedFlow<T> = asSharedFlow()

/**
 * 在 [scope] 里收集事件，每个事件交给 [action]。
 * 界面订阅者传 lifecycleScope 或 viewModelScope：作用域结束时自动取消，不用手动 unregister。
 */
fun <T> observe(scope: CoroutineScope, flow: Flow<T>, action: (T) -> Unit) = scope.launch {
    flow.collect { action(it) }
}

/** 进程级的协程作用域：用于启动时注册一次、随进程存活的订阅（主线程收集）。 */
val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/** Java 调用方用的收集入口（Java 不便于传 Kotlin 的泛型函数类型）。 */
fun <T> observeJava(scope: CoroutineScope, flow: Flow<T>, consumer: java.util.function.Consumer<T>) = scope.launch {
    flow.collect { consumer.accept(it) }
}
