package com.openapi.core.cast

/**
 * 发现到的一个可投屏设备。
 *
 * [controlUrl] 是 AVTransport 服务的控制端点（SOAP 请求发到这里），
 * 不是设备根描述地址——根描述要先 GET 一次才能解析出它，见 [DlnaDiscovery]。
 */
data class DlnaDevice(
    val friendlyName: String,
    val location: String,
    val controlUrl: String,
    val modelName: String?,
    val manufacturer: String?,
) {
    /** 设备唯一标识，用于列表去重与"上次投过的设备"。 */
    val id: String get() = location
}

/** 投屏状态，UI 只认这个。 */
sealed interface CastState {
    data object Idle : CastState
    data object Connecting : CastState
    data class Playing(val device: DlnaDevice, val positionSec: Int, val durationSec: Int) : CastState
    data class Paused(val device: DlnaDevice) : CastState
    data class Error(val message: String) : CastState
}
