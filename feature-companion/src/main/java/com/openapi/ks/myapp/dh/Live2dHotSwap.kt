package com.openapi.ks.myapp.dh

/**
 * Live2D 热换形象 vs 整引擎重建的决策（通话页 UX 2026-11-23）。
 *
 * 已在 GL 上且 View 已挂好时，只换模型目录，避免 Cubism Dispose/StartUp 连发闪黑。
 */
fun live2dHotSwapEligible(
    engineIsLive2d: Boolean,
    viewCreated: Boolean,
    released: Boolean,
    currentModel: String,
    targetModel: String,
): Boolean =
    engineIsLive2d && viewCreated && !released &&
        currentModel != targetModel && targetModel.isNotBlank()

/** ATRI 有全套换装；换形象后触点状态归零，与新建引擎一致。 */
fun live2dReactionAfterModelSwitch(modelDirName: String): Live2dTouchReactionState =
    Live2dTouchReactionState()

fun live2dFullWardrobeForModel(modelDirName: String): Boolean = modelDirName == "ATRI"
