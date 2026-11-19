package com.openapi.ks.dh

import android.content.Context
import android.util.Log
import android.view.View
import com.openapi.ks.dh.duix.sdk.client.Callback
import com.openapi.ks.dh.duix.sdk.client.DUIX
import com.openapi.ks.dh.duix.sdk.client.DuixHome
import com.openapi.ks.dh.duix.sdk.client.render.DUIXRenderer
import com.openapi.ks.dh.duix.sdk.client.render.DUIXTextureView
import java.io.File

/** Whether an engine can run right now; [reason] says why not when [available] is false. */
data class EngineStatus(val available: Boolean, val reason: String?)

/**
 * Call-page facade over the vendored Duix runtime ([com.openapi.ks.dh.duix]).
 *
 * Models come from [AssetStore] (`cacheRoot(EngineAssets.DUIX)/model/{gj_dh_res,<modelName>}`);
 * nothing is read from the upstream `getExternalFilesDir("duix")` location. The native
 * `libgjduix.so` is loaded lazily and never throws: a missing library shows up as
 * [availability] = unavailable ([REASON_NATIVE_MISSING]).
 *
 * The native library must export JNI symbols for the renamed class, i.e.
 * `Java_com_openapi_ks_dh_duix_DuixNcnn_*` (upstream prebuilts export `Java_ai_guiji_duix_DuixNcnn_*`).
 *
 * PCM only drives the mouth: [setVolume] defaults to 0 so the speaker stays with LiveKit.
 */
class DuixRuntime internal constructor(
    private val env: Env,
    private val modelName: String,
) {

    /** Everything [DuixRuntime] needs from the outside world; tests inject a fake. */
    internal interface Env {
        fun modelsReady(): Boolean
        fun nativeLoadable(): Boolean
        fun cacheRoot(): File?
    }

    constructor(context: Context) : this(context, AssetStore(context.applicationContext))

    constructor(
        context: Context,
        assetStore: AssetStore,
        modelName: String = DEFAULT_MODEL,
    ) : this(AndroidEnv(assetStore, modelName), modelName) {
        appContext = context.applicationContext
    }

    private var appContext: Context? = null
    private var duix: DUIX? = null
    private var renderer: DUIXRenderer? = null
    private var volume = 0f
    private var pushing = false
    private var released = false

    fun availability(): EngineStatus = when {
        released -> EngineStatus(false, REASON_RELEASED)
        !env.modelsReady() -> EngineStatus(false, REASON_NOT_DOWNLOADED)
        !env.nativeLoadable() -> EngineStatus(false, REASON_NATIVE_MISSING)
        else -> EngineStatus(true, null)
    }

    /** A GL texture view driving the avatar, or null when [availability] is not available. */
    fun createView(): View? {
        val context = appContext ?: return null
        if (!availability().available) return null
        val root = env.cacheRoot() ?: return null
        return try {
            DuixHome.setRoot(root)
            val view = DUIXTextureView(context)
            val sink = DUIXRenderer(context, view)
            view.setEGLContextClientVersion(2)
            view.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            view.isOpaque = false
            view.setRenderer(sink)
            view.renderMode = DUIXTextureView.RENDERMODE_WHEN_DIRTY
            val callback = Callback { event, msg, _ -> Log.i(TAG, "duix event=$event msg=$msg") }
            val sdk = DUIX(context, modelName, sink, callback)
            sdk.setVolume(volume)
            sdk.init()
            duix = sdk
            renderer = sink
            view
        } catch (t: Throwable) {
            Log.e(TAG, "createView failed", t)
            null
        }
    }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        duix?.setVolume(volume)
    }

    /** 16 kHz mono PCM16LE. Starts the push session on first use. */
    fun pushPcm(pcm16leMono16k: ByteArray) {
        val sdk = duix ?: return
        if (!pushing) {
            sdk.startPush()
            pushing = true
        }
        sdk.pushPcm(pcm16leMono16k)
    }

    /**
     * Plays the motion region named `id.toString()` from the model's motion list; a negative [id]
     * picks a random motion. [loop] maps to the SDK's `now` flag (interrupt the idle loop and play
     * immediately instead of queueing after it).
     */
    fun startMotion(id: Int, loop: Boolean) {
        val sdk = duix ?: return
        if (id < 0) sdk.startRandomMotion(loop) else sdk.startMotion(id.toString(), loop)
    }

    fun release() {
        if (released) return
        released = true
        val sdk = duix
        val sink = renderer
        duix = null
        renderer = null
        runCatching {
            if (pushing) sdk?.stopPush()
            sdk?.release()
            sink?.release()
        }
        pushing = false
    }

    private class AndroidEnv(
        private val store: AssetStore,
        private val modelName: String,
    ) : Env {
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

        override fun cacheRoot(): File = store.cacheRoot(EngineAssets.DUIX)

        override fun modelsReady(): Boolean {
            if (!store.isReady(EngineAssets.DUIX)) return false
            // Custom model names need their own dir + marker next to the manifest packs.
            val root = cacheRoot()
            return File(root, "model/$modelName").isDirectory && File(root, "model/tmp/$modelName").exists()
        }

        override fun nativeLoadable(): Boolean = nativeOk
    }

    companion object {
        const val DEFAULT_MODEL = "Oliver"
        const val BASE_MODEL = "gj_dh_res"
        const val REASON_NOT_DOWNLOADED = "未下载"
        const val REASON_NATIVE_MISSING = "native 库缺失"
        private const val REASON_RELEASED = "已释放"
        private const val NATIVE_LIB = "gjduix"
        private const val TAG = "DuixRuntime"

        /** JVM-test seam: no Context, no native library, availability from plain flags. */
        internal fun forTest(
            modelsReady: Boolean,
            nativeLoadable: Boolean = true,
            modelName: String = DEFAULT_MODEL,
        ): DuixRuntime = DuixRuntime(
            env = object : Env {
                override fun modelsReady() = modelsReady
                override fun nativeLoadable() = nativeLoadable
                override fun cacheRoot(): File? = null
            },
            modelName = modelName,
        )
    }
}
