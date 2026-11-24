package com.openapi.ks.myapp.dh

import android.content.Context
import com.openapi.ks.myapp.avatar.AvatarCapability

/**
 * WebView + TalkingHead（met4citizen/TalkingHead，MIT 源码 vendored）+ CC0 mpfb 头像
 * （lab only）。
 *
 * 素材随 APK assets 走（`dh-talkinghead/`）：vendored modules 打进 bundle、mpfb.glb
 * (36.8MB, ARKit/Oculus blendshape, CC0) 与 audio worklet 按白名单供文件流。没有下载包。
 * 场景桥见 [TalkingHeadSceneBridge]：情绪走内置 mood 词表，口型写 jawOpen 固定值通道，
 * 注视写 eyesRotateX/Y 聚合通道（x 右正 → eyesRotateY 正；y 上正 → eyesRotateX 负）。
 * 不接任何在线 TTS：页面 lipsyncModules 置空，禁用运行时动态 import。
 */
class TalkingHeadDigitalHumanEngine : WebViewSceneEngine(
    host = HOST,
    startPage = "index.html",
    bridge = TalkingHeadSceneBridge()::sceneCommand,
) {
    override val id: DhEngineId = DhEngineId.TALKING_HEAD

    override val capabilities: Set<AvatarCapability> = setOf(
        AvatarCapability.MOUTH,
        AvatarCapability.EMOTION,
        AvatarCapability.GAZE,
    )

    override fun resolveAsset(path: String): String? = assetFor(path)

    // 素材随 APK assets 走：缺文件是构建期问题，不是运行时状态（同 Spine/VRM 纪律）。
    override fun availability(context: Context): DhEngineAvailability =
        DhEngineAvailability(id = id, available = true)

    companion object {
        const val HOST = "dh-talkinghead.local"

        /** 白名单：worklet 必须放行——vendored talkinghead.mjs 顶层用 import.meta.url 定位它。 */
        private val FILES = setOf(
            "index.html",
            "talkinghead.bundle.js",
            "mpfb.glb",
            "playback-worklet.js",
        )

        fun assetFor(path: String): String? = if (path in FILES) "dh-talkinghead/$path" else null
    }
}
