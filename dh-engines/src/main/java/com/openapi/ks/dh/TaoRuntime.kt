package com.openapi.ks.dh

import android.content.Context
import android.util.Log
import android.view.View
import com.openapi.ks.dh.tao.MHConfig
import com.openapi.ks.dh.tao.a2bs.AudioBlendShapePlayer
import com.taobao.meta.avatar.a2bs.A2BSService
import com.taobao.meta.avatar.nnr.AvatarTextureView
import com.taobao.meta.avatar.nnr.NnrAvatarRender
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Call-page facade over the vendored Tao NNR runtime ([com.openapi.ks.dh.tao]).
 *
 * v1 acceptance is the idle NNR mesh on screen (mouth/A2BS not required), so the A2BS player is
 * an idle no-op stub. Assets live under [AssetStore] `cacheRoot(EngineAssets.TAO)`: the native
 * libs zip downloads there, and `MHConfig.BASE_DIR` is pointed at a directory containing
 * `TaoAvatar-NNR-MNN/` + `UniTalker-MNN/` (upstream names; adb-push those for now — upstream
 * ships them only via the ModelScope downloader SDK, no direct URL; see THIRD_PARTY.md).
 *
 * The JNI owner `libtaoavatar.so` is rebuilt from MnnTaoAvatar's cpp against the downloaded
 * libMNN/libnnrruntime (scripts/dh-lab/build-tao-native.sh) and pushed into the cache dir by
 * scripts/dh-lab/seed-assets.sh — it is **not** packaged by this module. Its symbols are the
 * upstream FQNs
 * `Java_com_taobao_meta_avatar_nnr_NnrAvatarRender_*`, and jni_utils.cpp hardcodes the field
 * descriptor `Lcom/taobao/meta/avatar/camera/Camera;`, so the vendored render + camera classes
 * keep the upstream package `com.taobao.meta.avatar.*` (see TaoNativeBridgeTest). A missing
 * native pack surfaces as [availability] = unavailable ([REASON_NATIVE_MISSING]) via
 * [TaoNativeLoader], never a crash.
 *
 * [createView] takes the host Context (the composition/activity context from
 * `DigitalHumanEngine.createView`); the TextureView attaches to it and drives rendering through
 * its own Choreographer callback.
 */
class TaoRuntime internal constructor(
    private val env: Env,
    private val viewKit: ViewKit? = null,
) {

    /** Everything [TaoRuntime] needs from the outside world; tests inject a fake. */
    internal interface Env {
        fun modelsReady(): Boolean
        fun nativeLoadable(): Boolean
        fun cacheRoot(): File?
    }

    /**
     * Builds the Tao view from the host + model dir passed to [createView]; JVM tests inject a
     * recorder. [Built.boot] is the render-boot kick (see [createView]) — null means "nothing to
     * start", which is what the JVM fakes use.
     */
    internal fun interface ViewKit {
        fun build(host: Context, modelDir: String): Built
    }

    /** [view] goes into the composition; [boot] starts the async NNR init when invoked. */
    internal class Built(val view: View?, val boot: (() -> Unit)? = null)

    constructor(context: Context) :
        this(AndroidEnv(AssetStore(context.applicationContext)))

    constructor(assetStore: AssetStore) : this(AndroidEnv(assetStore))

    private var view: AvatarTextureView? = null
    private var render: NnrAvatarRender? = null

    /**
     * Process-wide A2BS owner. Its constructor runs `nativeCreateA2BS`, which installs the
     * C++ `A2BSService::instance_` that UpdateNNRScene derefs every frame — deliberately never
     * destroyed (upstream MainActivity keeps theirs alive the same way; nativeDestroy would
     * re-null the singleton a live render loop still uses).
     */
    private var a2bs: A2BSService? = null
    private var released = false
    private val bootScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun availability(): EngineStatus = when {
        released -> EngineStatus(false, REASON_RELEASED)
        !env.modelsReady() -> EngineStatus(false, REASON_NOT_DOWNLOADED)
        !env.nativeLoadable() -> EngineStatus(false, REASON_NATIVE_MISSING)
        else -> EngineStatus(true, null)
    }

    /**
     * The Tao avatar view, or null when [availability] is not available.
     *
     * Mirrors upstream MainActivity wiring: point [MHConfig.BASE_DIR] at the model root, build
     * [AvatarTextureView], attach [NnrAvatarRender] on `NNR_MODEL_DIR`, and — the part the
     * stripped MainActivity used to own — kick the async render boot (`waitForInitComplete`,
     * which creates the native NNR instance once the surface exists). Without that kick the
     * mesh can never render. A boot failure is logged, not thrown.
     */
    fun createView(host: Context): View? {
        if (!availability().available) return null
        val root = env.cacheRoot() ?: return null
        return try {
            MHConfig.BASE_DIR = root.absolutePath
            val built = (viewKit ?: defaultKit()).build(host, MHConfig.NNR_MODEL_DIR)
            built.boot?.invoke()
            built.view
        } catch (t: Throwable) {
            Log.e(TAG, "createView failed", t)
            null
        }
    }

    private fun defaultKit(): ViewKit = ViewKit { host, modelDir ->
        // Before any render boot: the C++ UpdateNNRScene derefs A2BSService::GetActiveInstance()
        // every frame, and that pointer is only set by the Kotlin constructor (nativeCreateA2BS).
        if (a2bs == null) a2bs = com.taobao.meta.avatar.a2bs.A2BSService()
        val textureView = AvatarTextureView(host, null)
        val nnrRender = NnrAvatarRender(textureView, modelDir)
        nnrRender.setAudioBlendShapePlayer(AudioBlendShapePlayer())
        view = textureView
        render = nnrRender
        Built(textureView) {
            bootScope.launch {
                runCatching { nnrRender.waitForInitComplete() }
                    .onFailure { Log.e(TAG, "NNR boot failed", it) }
            }
        }
    }

    /**
     * Drops our references and cancels a pending boot. The NNR native instance itself is
     * destroyed by the view's surface lifecycle (upstream behaviour) once it leaves the
     * composition; safe to call twice.
     */
    fun release() {
        if (released) return
        released = true
        view = null
        render = null
        bootScope.cancel()
    }

    private class AndroidEnv(private val store: AssetStore) : Env {
        private val nativeOk: Boolean by lazy {
            // Same lesson as Duix (final review I3): the APK-path loader never sees the
            // cache-dir pack — it must go through TaoNativeLoader by absolute path.
            val root = store.cacheRoot(EngineAssets.TAO)
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull { it.startsWith("arm64") }
                ?: android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            TaoNativeLoader.tryLoad(TaoNativeLoader.nativeDirFor(root, abi))
        }

        override fun cacheRoot(): File = store.cacheRoot(EngineAssets.TAO)

        override fun modelsReady(): Boolean {
            // The download manifest pins native libs only; the NNR/UniTalker model trees come
            // from the ModelScope SDK upstream (no direct URL yet), so the dev path is
            // adb-push into cacheRoot(TAO)/{TaoAvatar-NNR-MNN,UniTalker-MNN}/.
            val root = cacheRoot()
            return File(root, NNR_MODEL_DIR_NAME).isDirectory &&
                File(root, A2BS_MODEL_DIR_NAME).isDirectory
        }

        override fun nativeLoadable(): Boolean = nativeOk
    }

    companion object {
        const val REASON_NOT_DOWNLOADED = "未下载"
        const val REASON_NATIVE_MISSING = "native 库缺失"
        private const val REASON_RELEASED = "已释放"
        private const val NNR_MODEL_DIR_NAME = "TaoAvatar-NNR-MNN"
        private const val A2BS_MODEL_DIR_NAME = "UniTalker-MNN"
        private const val TAG = "TaoRuntime"

        /** JVM-test seam: no native library and no real GL. */
        internal fun forTest(
            modelsReady: Boolean,
            nativeLoadable: Boolean = true,
            cacheRoot: File? = null,
            viewKit: ViewKit? = null,
        ): TaoRuntime = TaoRuntime(
            env = object : Env {
                override fun modelsReady() = modelsReady
                override fun nativeLoadable() = nativeLoadable
                override fun cacheRoot(): File? = cacheRoot
            },
            viewKit = viewKit,
        )
    }
}
