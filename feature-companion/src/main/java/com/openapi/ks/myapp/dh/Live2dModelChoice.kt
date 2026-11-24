package com.openapi.ks.myapp.dh

/**
 * Live2D 多形象切换的纯决策（用户 2026-11-23「demo里有好几个形象，是否可以都集成建立可切换」）。
 *
 * 上游 ChatWaifu_Mobile 带 ATRI / Amadeus / Yuuka 三个模型（8.6M / 6.6M / 7.1M，
 * 推设备毫无压力）。白名单钉死目录名——偏好里出现别的字符串一律回落 ATRI，
 * 防止把 cacheRoot 下的杂目录当模型加载。可用 = 白名单 ∩ cacheRoot 里真实存在
 * 的模型目录，另含 debug-only OriginalDemo；切换在可用列表里循环，只有一个可用时原地不动（不换 = 不闪屏）。
 */
object Live2dModels {
    val ALLOWED = listOf("ATRI", "Amadeus", "Yuuka", OriginalAvatarDemo.MODEL_DIR)

    fun sanitize(raw: String?): String = raw?.takeIf { it in ALLOWED } ?: "ATRI"

    /** 当前模型 → 下一个可用模型（ALLOWED 顺序循环；current 不在 present 里也照样接入轮盘）。 */
    fun next(current: String, present: List<String>): String {
        val ordered = ALLOWED.filter { it in present }
        if (ordered.isEmpty()) return current
        val idx = ordered.indexOf(sanitize(current))
        return ordered[(idx + 1) % ordered.size]
    }
}

/**
 * 设备上真实可用的模型（cacheRoot(LIVE2D)/<name> 目录存在且非空——与
 * Live2dRuntime.AndroidEnv.modelsReady 同一判据，**再过 [com.openapi.ks.dh.Live2dModelGate]
 * 版本闸**）。托盘「换形象」用它决定能不能切、切到谁；只有一个可用时置灰而不是假装能点。
 *
 * 版本闸不能省：目录存在但 moc3 比端侧 Core 新时，切过去就是下一帧 SIGSEGV
 * （真机 2026-11-24 就是 Core r4 × OriginalDemo r5；Core 升 5-r.5 后上限 r6，
 * 闸本身保留，见 Live2dModelGate）。json 名沿用切模型的同一约定
 * （<目录名>.model3.json，见 Live2dDigitalHumanEngine.modelJsonName / 设备缓存实测）。
 */
fun live2dPresentModels(context: android.content.Context): List<String> {
    val root = com.openapi.ks.dh.AssetStore(context.applicationContext)
        .cacheRoot(com.openapi.ks.dh.EngineAssets.LIVE2D)
    return Live2dModels.ALLOWED.filter { name ->
        java.io.File(root, name).let { it.isDirectory && it.list()?.isNotEmpty() == true } &&
            com.openapi.ks.dh.Live2dModelGate.rejectReason(root, name) == null
    }
}
