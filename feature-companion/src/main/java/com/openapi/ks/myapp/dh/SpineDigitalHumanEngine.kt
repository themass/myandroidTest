package com.openapi.ks.myapp.dh

import android.content.Context
import com.openapi.ks.myapp.avatar.AvatarCapability

/**
 * WebView + spine-webgl 官方运行时 + spineboy 官方样例（lab only，内部评估）。
 *
 * 素材随 APK assets 走（`dh-spine/`，esotericsoftware.com 官方示例 spineboy-pro +
 * spine-webgl 4.3 打包；Spine Runtime 为专有许可，内部评估用，生产化需书面许可确认）。
 * 没有下载包：[availability] 只查 assets 齐不齐，菜单永远直接换舞台。
 * 场景桥见 [SpineSceneBridge]：spineboy 没有表情/口型参数，只接官方动作组。
 */
class SpineDigitalHumanEngine : WebViewSceneEngine(
    host = HOST,
    startPage = "index.html",
    bridge = SpineSceneBridge()::sceneCommand,
) {
    override val id: DhEngineId = DhEngineId.SPINE

    override val capabilities: Set<AvatarCapability> = setOf(AvatarCapability.GESTURE)

    override fun resolveAsset(path: String): String? = assetFor(path)

    // 素材随 APK assets 走：缺文件是构建期问题（assemble 就会红），不是运行时状态；
    // 与 VrmDigitalHumanEngine 同纪律——页面在不在 assets 从不运行时查。注册即编入即能用。
    override fun availability(context: Context): DhEngineAvailability =
        DhEngineAvailability(id = id, available = true)

    companion object {
        const val HOST = "dh-spine.local"

        /** 白名单：伪源只放行这几个文件，其余一律空响应（不联网、不放行任意 assets 读取）。 */
        private val FILES = setOf(
            "index.html",
            "spine.bundle.js",
            "spineboy-pro.json",
            "spineboy-pma.atlas",
            "spineboy-pma.png",
        )

        /** [FILES] 里的页面文件名 → assets 路径；不在白名单回 null。 */
        fun assetFor(path: String): String? = if (path in FILES) "dh-spine/$path" else null
    }
}
