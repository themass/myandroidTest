package com.openapi.cast

import com.openapi.cast.soap.DeviceDescription
import com.openapi.cast.ssdp.SsdpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 发现局域网里的可投屏设备。
 *
 * 两步：SSDP 广播找设备 → 逐个取描述文档解析出 AVTransport 控制地址。
 * 第二步必须串行做（一次性开几十个连接容易触发路由器的连接数限制，反而更慢）。
 */
class DlnaDiscovery {

    suspend fun discover(timeoutMs: Int = 4000): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val responses = SsdpClient.search(timeoutMs)
        responses.keys.mapNotNull { location ->
            runCatching {
                val parsed = DeviceDescription.fetch(location) ?: return@runCatching null
                DlnaDevice(
                    friendlyName = parsed.friendlyName.ifBlank { location },
                    location = location,
                    controlUrl = parsed.controlUrl,
                    modelName = parsed.modelName,
                    manufacturer = parsed.manufacturer,
                )
            }.getOrNull()
        }
            // 同一台电视常常对三条 ST 各回一次，按 controlUrl 去重
            .distinctBy { it.controlUrl }
            .sortedBy { it.friendlyName }
    }
}
