package com.openapi.app.fxlab

/**
 * 某个引擎在某个场景下能不能跑，以及为什么不能。
 *
 * 把「API 门槛」「缺资产」两件事从 UI 里拆出来钉死（specs §8）：低版本设备不猜、
 * 不假回退，页面上直接显示原因，而不是黑屏或悄悄换个引擎。
 */
sealed interface GateOutcome {

    data object Enabled : GateOutcome

    data class Unavailable(val reason: String) : GateOutcome
}

object FxEngineGate {

    /**
     * 判定顺序固定：先看 API 门槛（硬件/系统能力，用户改不了），再看资产。
     * [reasonWhenMissing] 由调用方给出具体缺什么（例如「缺 .riv 资产」），
     * gate 只负责何时用它，不自造含糊的失败文案。
     */
    fun decide(
        engine: FxEngineId,
        scene: FxScene,
        sdkInt: Int,
        assetReady: Boolean,
        reasonWhenMissing: String,
    ): GateOutcome {
        val required = FxEngineCatalog.meta.getValue(engine).minApi
        if (sdkInt < required) {
            return GateOutcome.Unavailable("需要 API $required+，本机 API $sdkInt")
        }
        if (!assetReady) {
            return GateOutcome.Unavailable(reasonWhenMissing)
        }
        return GateOutcome.Enabled
    }
}
