package com.openapi.ks.myapp.dh

/** 引擎下拉切换的纯决策，便于单测（LabHost 只负责执行）。 */
internal object DhEngineSwapPolicy {
    /**
     * @param requested 用户（或持久偏好）当前请求的引擎
     * @param lastRequested 上一次已处理的请求；相同 = 无变化（含首次组合）
     * @param isQuarantined 清除隔离**之后**该引擎是否仍被熔断
     * @return 要切换到的引擎；null = 不切换
     */
    fun target(
        requested: DhEngineId,
        lastRequested: DhEngineId,
        isQuarantined: (DhEngineId) -> Boolean,
    ): DhEngineId? {
        if (requested == lastRequested) return null
        return if (isQuarantined(requested)) DhEngineId.SIMPLE else requested
    }
}
