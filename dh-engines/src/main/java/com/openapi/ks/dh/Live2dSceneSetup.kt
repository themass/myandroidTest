package com.openapi.ks.dh

import com.chatwaifu.live2d.JniBridgeJava

/** Run as one GL-thread operation: native consumes the scene flags on its next frame. */
internal fun prepareLive2dScene(
    modelPath: String,
    modelJson: String,
    selectModel: (String, String) -> Unit = JniBridgeJava::nativeProjectChangeTo,
    enableBlink: (Boolean) -> Unit = JniBridgeJava::nativeAutoBlinkEyes,
    applyExpression: (String) -> Unit = JniBridgeJava::nativeApplyExpression,
) {
    selectModel(modelPath, modelJson)
    enableBlink(true)
    // Amadeus needs base's upstream logo=0.01 gate even before the first user interaction.
    // Queue it with model selection so LAppView consumes both flags in the same frame.
    applyExpression("base")
}
