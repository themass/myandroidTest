package com.openapi.ks.myapp.dh

/** Lab 里可切换的数字人引擎。P0 只有 [SIMPLE] 可创建，其余灰显占位。 */
enum class DhEngineId {
    SIMPLE,
    LIVE2D,
    DUIX,
    TAO_AVATAR,

    /** WebView + three.js + VRM 实验田（lab only，不进生产）。 */
    VRM,
}

data class DhEngineAvailability(
    val id: DhEngineId,
    val available: Boolean,
    val reason: String? = null,
    /** true：现在不能用，但素材下好后就能用（菜单仍可点，点了走下载闸门）。 */
    val needsDownload: Boolean = false,
)
