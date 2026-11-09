package com.aimath.paizhao.photosolve

/** Normalized (0..1) rectangle relative to a bitmap's width/height — the shared coordinate type
 *  every [com.aimath.paizhao.photosolve.cropassist.CropAssistProvider] returns a suggestion in. */
data class CropNorm(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        /** Centered box covering ~84% of width and ~50% of height — a reasonable default for a single question. */
        val Default = CropNorm(left = 0.08f, top = 0.25f, right = 0.92f, bottom = 0.75f)
    }
}
