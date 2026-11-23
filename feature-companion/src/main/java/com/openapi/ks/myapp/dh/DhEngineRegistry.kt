package com.openapi.ks.myapp.dh

import android.content.Context

/**
 * 显式注册表：各引擎模块在启动时 [register]，Lab UI 只问这里。
 * 未注册的 id 在 [available] 里灰显，原因固定为「未编入」，[create] 直接抛。
 */
object DhEngineRegistry {
    private val factories = linkedMapOf<DhEngineId, (Context) -> DigitalHumanEngine>()

    fun register(id: DhEngineId, factory: (Context) -> DigitalHumanEngine) {
        factories[id] = factory
    }

    fun available(context: Context): List<DhEngineAvailability> =
        DhEngineId.entries.map { id ->
            val factory = factories[id]
            if (factory == null) {
                DhEngineAvailability(id, available = false, reason = "未编入")
            } else {
                // 探测可用性时创建的实例不会挂到 UI：立刻 release，避免 Simple 眨眼协程泄漏。
                val probe = factory(context)
                try {
                    probe.availability(context)
                } finally {
                    probe.release()
                }
            }
        }

    fun create(context: Context, id: DhEngineId): DigitalHumanEngine {
        val factory = factories[id]
            ?: throw IllegalStateException("引擎未编入: $id")
        return factory(context)
    }

    /** 单测隔离：清掉注册表。生产路径不要调用。 */
    fun resetForTests() {
        factories.clear()
    }
}
