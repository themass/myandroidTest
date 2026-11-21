package com.openapi.ks.myapp.dh

import android.content.Context
import com.openapi.ks.dh.EngineAssets
import com.openapi.ks.myapp.avatar.AvatarCapability

/**
 * VRM（WebView + three.js + three-vrm）实验田适配器的可用性壳。
 *
 * 渲染在 WebView 场景页里跑（Task 4 落地 createView），这里先立可用性判定：
 * 模型没下好回「未下载」并标 [DhEngineAvailability.needsDownload]，菜单走下载闸门。
 * 能力位先报 MOUTH/EMOTION/GAZE——bridge（[VrmBridge]）已把这三条钉成线格式；
 * createView 没接通前不报它们以外的东西。
 */
class VrmDigitalHumanEngine : DigitalHumanEngine {

    override val id: DhEngineId = DhEngineId.VRM

    override val capabilities: Set<AvatarCapability> = setOf(
        AvatarCapability.MOUTH,
        AvatarCapability.EMOTION,
        AvatarCapability.GAZE,
    )

    override fun bind(spec: com.openapi.ks.myapp.avatar.AvatarSpec) = Unit

    override fun dispatch(command: com.openapi.ks.myapp.avatar.AvatarCommand) = Unit

    override fun onTouch(event: com.openapi.ks.myapp.avatar.AvatarTouch) =
        com.openapi.ks.myapp.avatar.TouchResult.Ignored

    override fun release() = Unit

    override fun availability(context: Context): DhEngineAvailability {
        val ready = DhLabBootstrap.assetStore(context).isReady(EngineAssets.VRM)
        return DhEngineAvailability(
            id = id,
            available = false, // WebView 场景页尚未接线（Task 4）；就绪前/后都先不可创建。
            reason = if (ready) "场景页未接线" else "未下载",
            needsDownload = !ready,
        )
    }
}
