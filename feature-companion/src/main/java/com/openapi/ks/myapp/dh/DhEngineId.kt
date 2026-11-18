package com.openapi.ks.myapp.dh

/** Lab 里可切换的数字人引擎。P0 只有 [SIMPLE] 可创建，其余灰显占位。 */
enum class DhEngineId {
    SIMPLE,
    LIVE2D,
    DUIX,
    TAO_AVATAR,
}

data class DhEngineAvailability(
    val id: DhEngineId,
    val available: Boolean,
    val reason: String? = null,
)
