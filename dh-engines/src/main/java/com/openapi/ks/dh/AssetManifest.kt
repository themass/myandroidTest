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

enum class EngineAssets { DUIX, LIVE2D, TAO, VRM }

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

    // TODO(Live2D): ATRI has no public download URL. Leave empty so ensure() fails with a clear
    //  reason (Task-3 gate). Final URL slot TBD — record it in THIRD_PARTY.md when chosen.
    //  Dev path: adb-push the model into AssetStore.cacheRoot(LIVE2D)/ATRI/; do NOT invent a URL
    //  and do NOT vendor the ATRI model pack into this repo.
    private val live2d = emptyList<AssetSpec>()

    // Tao models (NNR mesh + UniTalker A2BS) ship upstream only through the ModelScope
    // downloader SDK (repos "MNN/TaoAvatar-NNR-MNN", "MNN/UniTalker-MNN" — see THIRD_PARTY.md),
    // which has no stable single-zip URL. Dev path stays adb-push into
    // cacheRoot(TAO)/{TaoAvatar-NNR-MNN,UniTalker-MNN}/; TaoRuntime.modelsReady() checks those
    // directories directly. A mirror URL can pin them later without touching the runtime.
    private val tao = listOf(
        // zip contains app/src/main/libs/{MNN,NNR}/lib/arm64-v8a/*.so + jniLibs; libMNN.so and
        // libnnrruntime.so are what a rebuilt libtaoavatar.so links against.
        AssetSpec(
            id = "tao-native-libs-arm64-v8a",
            url = "https://meta.alicdn.com/data/mnn/avatar/native-libs-arm64-v8a.zip",
            relativePath = "native-libs",
        ),
    )

    // VRM 实验田（three.js + WebView，lab only）：pixiv/three-vrm 仓库自带的示例模型，
    // VRM 1.0 单文件。上游只有 raw 文件没有 zip，kind=FILE 直落 cache。
    // 仅供效果验证，模型逐件授权（personalNonProfit 默认），不进生产。
    private val vrm = listOf(
        AssetSpec(
            id = "vrm1-constraint-twist-sample",
            url = "https://raw.githubusercontent.com/pixiv/three-vrm/dev/packages/three-vrm/examples/models/VRM1_Constraint_Twist_Sample.vrm",
            relativePath = "models/VRM1_Constraint_Twist_Sample.vrm",
            kind = AssetSpec.Kind.FILE,
        ),
    )

    fun assetsFor(engine: EngineAssets): List<AssetSpec> = when (engine) {
        EngineAssets.DUIX -> duix
        EngineAssets.LIVE2D -> live2d
        EngineAssets.TAO -> tao
        EngineAssets.VRM -> vrm
    }
}
