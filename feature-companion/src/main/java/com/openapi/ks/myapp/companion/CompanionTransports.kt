package com.openapi.ks.myapp.companion

import android.content.Context
import android.util.Log

/**
 * RTC 线路的唯一分发点：服务端 grant 里的 `provider` → [CompanionTransport] 实现。
 *
 * 协议见 docs/companion-rtc-provider-switching.md §2：切换权威在服务端
 * （建会话时定 provider 随 grant 下发），客户端只照着分发，不做网络探测也不自选。
 * 以后接火山 RTC 容灾，就是在这里加一个分支 + 一个 VolcCompanionTransport，
 * ViewModel/UI/测试都不动。
 */
object CompanionTransports {
    const val PROVIDER_LIVEKIT = "livekit"
    const val PROVIDER_VOLCENGINE = "volcengine"

    private const val TAG = "CompanionTransports"

    /**
     * 生产工厂。[provider] 来自 [SessionResponse.provider]，空串按 livekit 解释
     * （老服务端不认识这个字段，响应里就没有它）。
     *
     * **认不出来的 provider 不崩溃、不报错**：降级按 livekit 尝试并打日志。
     * 这是「新服务端 + 旧客户端」共存窗口里唯一合理的行为——
     * 服务端灰度先发新值，旧客户端没有对应实现，房间凭据本身（livekit_url/token）
     * 在降级窗口内仍然可用；真连不上，走的是既有的连接失败 UI，不是崩溃。
     */
    fun create(context: Context, provider: String?): CompanionTransport {
        val known = provider?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: PROVIDER_LIVEKIT
        return when (known) {
            PROVIDER_LIVEKIT -> LiveKitCompanionTransport(context)
            PROVIDER_VOLCENGINE -> {
                // 火山实现尚未接入：先按 livekit 降级。真机容灾上线时替换这一行。
                Log.w(TAG, "provider=$PROVIDER_VOLCENGINE 尚无实现，降级按 $PROVIDER_LIVEKIT 连接")
                LiveKitCompanionTransport(context)
            }
            else -> {
                Log.w(TAG, "不认识 provider=$known，降级按 $PROVIDER_LIVEKIT 连接")
                LiveKitCompanionTransport(context)
            }
        }
    }
}
