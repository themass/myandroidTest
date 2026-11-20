package com.openapi.ks.dh.tao

/**
 * Minimal MHConfig surface for the idle NNR path.
 * Upstream also owns LLM/ASR/TTS/A2BS dirs; those stay out of this module.
 * [TaoRuntime] points [BASE_DIR] at the AssetStore TAO cache root before createView.
 */
object MHConfig {
    private var baseDir: String = ""

    var BASE_DIR: String
        get() = baseDir
        set(value) {
            baseDir = value
        }

    val NNR_MODEL_DIR: String
        get() = "$BASE_DIR/TaoAvatar-NNR-MNN/"

    /** Idle mesh still references UniTalker idle_speech_slices.json via JNI. */
    val A2BS_MODEL_DIR: String
        get() = "$BASE_DIR/UniTalker-MNN/"
}
