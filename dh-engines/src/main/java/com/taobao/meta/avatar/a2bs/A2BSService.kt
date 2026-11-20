package com.taobao.meta.avatar.a2bs

/**
 * Mirror of upstream `com.taobao.meta.avatar.a2bs.A2BSService` with two deliberate edits:
 *
 * - **No `System.loadLibrary("mnn_a2bs")`** — the native pack lives in the AssetStore cache
 *   dir and is dlopen'd by absolute path through [com.openapi.ks.dh.TaoNativeLoader] before
 *   this class is ever constructed (same ruling as the NnrAvatarRender mirror, TaoNativeBridgeTest).
 * - **No TTS/init-deferred machinery** — the upstream coroutine plumbing belongs to
 *   AudioBlendShapePlayer/MainActivity, which this v1 does not vendor.
 *
 * The FQN is load-bearing twice: `nativeProcessBuffer` FindClass-es
 * `com/taobao/meta/avatar/a2bs/A2BSService`'s sibling data class by string, and — critically —
 * the **constructor** runs `nativeCreateA2BS`, which `new`s the C++ A2BSService and installs
 * it as `A2BSService::instance_`. `nnr_avatar_render.cpp` UpdateNNRScene derefs
 * `A2BSService::GetActiveInstance()` **every frame**; without exactly one live Kotlin instance
 * here, that deref is null and the first Tao frame SIGSEGVs (device 2026-11-20 00:31:49,
 * fault addr 0x20 in GetActiveFrame). Upstream keeps it alive via MainActivity.getA2bsService();
 * [com.openapi.ks.dh.TaoRuntime] owns ours process-wide.
 */
class A2BSService {

    private var a2bsServiceNative: Long = 0

    fun process(index: Int, audioData: ShortArray, sampleRate: Int): AudioToBlendShapeData {
        return nativeProcessBuffer(a2bsServiceNative, index, audioData, sampleRate)
    }

    /** Loads the UniTalker A2BS models; only needed once real lipsync drives process(). */
    fun loadA2bsResources(resourceDir: String?, tempDir: String): Boolean =
        nativeLoadA2bsResources(a2bsServiceNative, resourceDir, tempDir)

    private external fun nativeCreateA2BS(): Long
    private external fun nativeDestroy(nativePtr: Long)
    private external fun nativeLoadA2bsResources(
        nativePtr: Long,
        resourceDir: String?,
        tempDir: String,
    ): Boolean

    private external fun nativeProcessBuffer(
        nativePtr: Long,
        index: Int,
        audioData: ShortArray,
        sampleRate: Int,
    ): AudioToBlendShapeData

    init {
        a2bsServiceNative = nativeCreateA2BS()
    }
}
