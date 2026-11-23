package com.openapi.feature.camerafx

/**
 * 相机预览上的叠加策略（纯数据，JVM 可测）。
 *
 * 与舞台 [com.openapi.feature.companion.fx.StageEffect] 解耦：相机用像素/CanvasMap，
 * 舞台用视口 Norm；**共用 asset 文件名**（fx_pat_head 等）通过 [StickerAssetIds]。
 */
data class CameraFxOverlayPolicy(
    val drawWireframe: Boolean = true,
    val drawStickers: Boolean = true,
    /** `assets/fx/<id>.json`，须在 [StickerAssetIds.ALLOWED] 内。 */
    val stickerAssetId: String = StickerAssetIds.PAT_HEAD,
    val showDebugHud: Boolean = true,
) {
    init {
        require(stickerAssetId in StickerAssetIds.ALLOWED) {
            "unknown sticker asset: $stickerAssetId"
        }
    }

    val lottieAssetPath: String get() = "fx/$stickerAssetId.json"
}

/** 与 companion `FxAssets` 词表对齐的 id（不依赖 companion 模块）。 */
object StickerAssetIds {
    const val PAT_HEAD = "fx_pat_head"
    const val WAVE = "fx_wave"

    val ALLOWED: Set<String> = setOf(PAT_HEAD, WAVE)
}
