package com.openapi.ks.dh

import android.content.Context
import android.util.Log
import android.view.View
import com.openapi.ks.dh.tao.MHConfig
import com.openapi.ks.dh.tao.a2bs.AudioBlendShapePlayer
import com.openapi.ks.dh.tao.nnr.AvatarTextureView
import com.openapi.ks.dh.tao.nnr.NnrAvatarRender
import java.io.File

/**
 * Call-page facade over the vendored Tao NNR runtime ([com.openapi.ks.dh.tao]).
 *
 * v1 acceptance is the idle NNR mesh on screen (mouth/A2BS not required), so the A2BS player is
 * an idle no-op stub. Assets live under [AssetStore] `cacheRoot(EngineAssets.TAO)`: the native
 * libs zip downloads there, and `MHConfig.BASE_DIR` is pointed at a directory containing
 * `TaoAvatar-NNR-MNN/` + `UniTalker-MNN/` (upstream names; adb-push those for now — upstream
 * ships them only via the ModelScope downloader SDK, no direct URL; see THIRD_PARTY.md).
 *
 * The JNI owner `libtaoavatar.so` is built from MnnTaoAvatar's cpp against the downloaded
 * libMNN/libnnrruntime and is **not** packaged or rebuilt by this module. It must export
 * `Java_com_openapi_ks_dh_tao_nnr_NnrAvatarRender_*` symbols (upstream prebuilts export
 * `Java_com_taobao_meta_avatar_nnr_NnrAvatarRender_*`), so a missing/rebuilt-native situation
 * surfaces as [availability] = unavailable ([REASON_NATIVE_MISSING]) instead of a crash.
 *
 * [createView] takes the host Context (the composition/activity context from
 * `DigitalHumanEngine.createView`); the TextureView attaches to it and drives rendering through
 * its own Choreographer callback.
 */
class TaoRuntime internal constructor(
    private val env: Env,
    private val viewBuilder: ViewBuilder? = null,
) {

    /** Everything [TaoRuntime] needs from the outside world; tests inject a fake. */
    internal interface Env {
        fun modelsReady(): Boolean
        fun nativeLoadable(): Boolean
        fun cacheRoot(): File?
    }

    /** Builds the Tao view from the host passed to [createView]; JVM tests inject a recorder. */
    internal fun interface ViewBuilder {
        fun build(host: Context): View?
    }

    constructor(context: Context) : this(context, AssetStore(context.applicationContext))

    constructor(context: Context, assetStore: AssetStore) :
        this(AndroidEnv(assetStore), viewBuilder = null) {
        appContext = context.applicationContext
    }

    private var appContext: Context? = null
    private var view: AvatarTextureView? = null
    private var render: NnrAvatarRender? = null
    private var released = false

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
     * [AvatarTextureView], attach [NnrAvatarRender] on `NNR_MODEL_DIR`, and kick the async
     * resource load. Destruction is driven by the view's own surface lifecycle.
     */
    fun createView(host: Context): View? {
        if (!availability().available) return null
        val root = env.cacheRoot() ?: return null
        return try {
            MHConfig.BASE_DIR = root.absolutePath
            val builder = viewBuilder
            if (builder != null) {
                builder.build(host)
            } else {
                val textureView = AvatarTextureView(host, null)
                val nnrRender = NnrAvatarRender(textureView, MHConfig.NNR_MODEL_DIR)
                nnrRender.setAudioBlendShapePlayer(AudioBlendShapePlayer())
                view = textureView
                render = nnrRender
                textureView
            }
        } catch (t: Throwable) {
            Log.e(TAG, "createView failed", t)
            null
        }
    }

    /**
     * Drops our references. The NNR native instance itself is destroyed by the view's surface
     * lifecycle (upstream behaviour) once it leaves the composition; safe to call twice.
     */
    fun release() {
        if (released) return
        released = true
        view = null
        render = null
    }

    private class AndroidEnv(private val store: AssetStore) : Env {
        private val nativeOk: Boolean by lazy {
            try {
                System.loadLibrary(NATIVE_LIB)
                true
            } catch (_: UnsatisfiedLinkError) {
                false
            } catch (_: SecurityException) {
                false
            }
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
        /** JNI owner built from MnnTaoAvatar cpp; not packaged by this module. */
        private const val NATIVE_LIB = "taoavatar"
        private const val NNR_MODEL_DIR_NAME = "TaoAvatar-NNR-MNN"
        private const val A2BS_MODEL_DIR_NAME = "UniTalker-MNN"
        private const val TAG = "TaoRuntime"

        /** JVM-test seam: no native library and no real GL. */
        internal fun forTest(
            modelsReady: Boolean,
            nativeLoadable: Boolean = true,
            cacheRoot: File? = null,
            viewBuilder: ViewBuilder? = null,
        ): TaoRuntime = TaoRuntime(
            env = object : Env {
                override fun modelsReady() = modelsReady
                override fun nativeLoadable() = nativeLoadable
                override fun cacheRoot(): File? = cacheRoot
            },
            viewBuilder = viewBuilder,
        )
    }
}
