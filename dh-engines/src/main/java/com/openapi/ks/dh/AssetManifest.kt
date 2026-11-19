package com.openapi.ks.dh

/** One downloadable pack that an engine needs before it can run. */
data class AssetSpec(
    val id: String,
    /** Absolute URL, or a path relative to [AssetStore]'s `baseUrlOverride`. */
    val url: String,
    /** Extraction directory (ZIP) or destination file (FILE), under the engine cache root. */
    val relativePath: String,
    /** Lowercase hex SHA-256 of the downloaded file. `null` skips verification. */
    val sha256: String? = null,
    val kind: Kind = Kind.ZIP,
) {
    enum class Kind { ZIP, FILE }
}

enum class EngineAssets { DUIX, LIVE2D, TAO }

/** Pure data: which packs each engine needs. No I/O. */
object AssetManifest {

    private val duix = listOf(
        AssetSpec(
            id = "gj_dh_res",
            url = "https://github.com/GuijiAI/duix.ai/releases/download/v1.0.0/gj_dh_res.zip",
            relativePath = "model/gj_dh_res",
        ),
        AssetSpec(
            id = "Oliver",
            url = "https://github.com/duixcom/Duix.mobile/releases/download/v2.0.1/Oliver.zip",
            relativePath = "model/Oliver",
        ),
    )

    // TODO(Live2D): the ATRI model has no public download URL yet. Add its AssetSpec here once a
    //  URL is chosen, and record it in THIRD_PARTY.md. Until then the engine is never "ready".
    private val live2d = emptyList<AssetSpec>()

    // TODO(Tao, Task 6): add the NNR model spec once its URL is chosen (record it in
    //  THIRD_PARTY.md). Only the native libs are pinned so far.
    private val tao = listOf(
        AssetSpec(
            id = "tao-native-libs-arm64-v8a",
            url = "https://meta.alicdn.com/data/mnn/avatar/native-libs-arm64-v8a.zip",
            relativePath = "native-libs",
        ),
    )

    fun assetsFor(engine: EngineAssets): List<AssetSpec> = when (engine) {
        EngineAssets.DUIX -> duix
        EngineAssets.LIVE2D -> live2d
        EngineAssets.TAO -> tao
    }
}
