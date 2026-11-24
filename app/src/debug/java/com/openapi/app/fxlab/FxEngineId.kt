package com.openapi.app.fxlab

/**
 * 特效引擎对比页的引擎标识。**debug-only**（`app/src/debug`）：生产路径没有这些
 * 后端可选，默认后端始终是 Lottie（见 specs §11）。
 */
enum class FxEngineId {
    LOTTIE,
    RIVE,
    GL_PARTICLE,
    THREE_WEBGL,
    AGSL_SHADER,
}

/** 对比页的四个场景，口径由用户给定：数字人有互动/教学，相机有全屏/小窗。 */
enum class FxScene {
    DH_INTERACT,
    DH_TEACH,
    CAMERA_FULL,
    CAMERA_PIP,
}

/**
 * 引擎元数据——页面上那行状态文字的数据源。措辞只写事实：依赖坐标、资产来源、
 * API 门槛、是否吃现有的 Lottie JSON 资产。不写未实测的体积数字（specs §11）。
 */
data class FxEngineMeta(
    val id: FxEngineId,
    val label: String,
    val dependency: String,
    val assetNote: String,
    val minApi: Int,
    val readsLottieAssets: Boolean,
)

/** 引擎目录。[all] 的顺序就是页面上引擎按钮的顺序，测试钉死它，避免顺序漂移。 */
object FxEngineCatalog {

    val meta: Map<FxEngineId, FxEngineMeta> = mapOf(
        FxEngineId.LOTTIE to FxEngineMeta(
            id = FxEngineId.LOTTIE,
            label = "Lottie",
            dependency = "lottie-compose 6.7.1（已在包里）",
            assetNote = "仓库自制 fx_pat_head / fx_wave / fx_connected",
            minApi = 28,
            readsLottieAssets = true,
        ),
        FxEngineId.RIVE to FxEngineMeta(
            id = FxEngineId.RIVE,
            label = "Rive",
            dependency = "app.rive:rive-android:11.16.3",
            assetNote = "官方样例 .riv（授权不确定，debug 对比用）",
            minApi = 28,
            readsLottieAssets = false,
        ),
        FxEngineId.GL_PARTICLE to FxEngineMeta(
            id = FxEngineId.GL_PARTICLE,
            label = "GL 粒子",
            dependency = "平台 GLES3（无依赖）",
            assetNote = "纯代码生成粒子，无资产",
            minApi = 28,
            readsLottieAssets = false,
        ),
        FxEngineId.THREE_WEBGL to FxEngineMeta(
            id = FxEngineId.THREE_WEBGL,
            label = "Three.js",
            dependency = "three 0.187.0（离线 bundle）",
            assetNote = "自制场景，不加载外部模型",
            minApi = 28,
            readsLottieAssets = false,
        ),
        FxEngineId.AGSL_SHADER to FxEngineMeta(
            id = FxEngineId.AGSL_SHADER,
            label = "AGSL",
            dependency = "平台 RuntimeShader（无依赖）",
            assetNote = "自写 AGSL 透明光环，不采样画面像素",
            minApi = 33,
            readsLottieAssets = false,
        ),
    )

    val all: List<FxEngineId> = meta.keys.toList()
}
