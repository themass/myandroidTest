package com.openapi.ks.myapp.dh

/** Lab 里可切换的数字人引擎。P0 只有 [SIMPLE] 可创建，其余灰显占位。 */
enum class DhEngineId {
    SIMPLE,
    LIVE2D,
    DUIX,
    TAO_AVATAR,

    /** WebView + three.js + VRM 实验田（lab only，不进生产）。 */
    VRM,

    /** WebView + spine-webgl 官方运行时 + spineboy 官方样例（lab only，内部评估）。 */
    SPINE,

    /** WebView + TalkingHead（MIT vendored）+ CC0 mpfb 头部（lab only）。 */
    TALKING_HEAD,

    /**
     * Rive 官方 Android 运行时（app.rive:rive-android）。引擎类在 debug 源集，
     * release 变体里类不存在 → 注册表里「未编入」；样本 .riv 按许可结论只进 debug
     * （见 app/src/debug/raw-notes/README-fxlab-rive.md）。
     */
    RIVE,
}

data class DhEngineAvailability(
    val id: DhEngineId,
    val available: Boolean,
    val reason: String? = null,
    /** true：现在不能用，但素材下好后就能用（菜单仍可点，点了走下载闸门）。 */
    val needsDownload: Boolean = false,
)
