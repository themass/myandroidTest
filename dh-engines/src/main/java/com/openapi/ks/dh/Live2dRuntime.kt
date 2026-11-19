package com.openapi.ks.dh

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.View
import com.openapi.ks.dh.live2d.GLRenderer
import com.openapi.ks.dh.live2d.JniBridgeJava
import java.io.File

/**
 * Call-page facade over the vendored Live2D runtime ([com.openapi.ks.dh.live2d]).
 *
 * Models are expected under [AssetStore] `cacheRoot(EngineAssets.LIVE2D)` (adb-push for now;
 * the LIVE2D AssetManifest entry is empty until a public ATRI URL exists). The JNI wrapper
 * `libchatwaifu-live2d.so` ships as a stock arm64 prebuilt in the same cache layout as Duix's
 * native pack (`cacheRoot(LIVE2D)/native-libs/<abi>`) and is loaded by absolute path via
 * [Live2dNativeLoader]; a missing wrapper shows up as [availability] = unavailable
 * ([REASON_NATIVE_MISSING]).
 *
 * The stock .so keeps upstream symbols `Java_com_chatwaifu_live2d_JniBridgeJava_*` and
 * FindClass-es `com/chatwaifu/live2d/JniBridgeJava` inside JNI_OnLoad — the vendored mirror
 * class under that FQN carries the native surface, and our
 * [com.openapi.ks.dh.live2d.JniBridgeJava] delegates to it (Task-6 ruling, as for Duix).
 *
 * [createView] takes the host Context (typically the Activity / composition context from
 * `DigitalHumanEngine.createView`) — never the application context — so Activity resolution
 * and GLSurfaceView construction match the pre-facade adapter behaviour.
 */
class Live2dRuntime internal constructor(
    private val env: Env,
    private val modelDirName: String,
    private val modelJsonName: String,
    private val viewBuilder: ViewBuilder? = null,
) {

    /** Everything [Live2dRuntime] needs from the outside world; tests inject a fake. */
    internal interface Env {
        fun modelsReady(): Boolean
        fun nativeLoadable(): Boolean
        fun cacheRoot(): File?
    }

    /**
     * Builds the GL view from the host passed to [createView]. Production leaves this null and uses
     * the real GLSurfaceView path; JVM tests inject a recorder so they can assert which host
     * Context and resolved Activity the GL path receives, without touching GL or JNI.
     */
    internal fun interface ViewBuilder {
        fun build(host: Context, activity: Activity?): View?
    }

    constructor(context: Context) : this(context, AssetStore(context.applicationContext))

    constructor(
        context: Context,
        assetStore: AssetStore,
        modelDirName: String = DEFAULT_MODEL_DIR,
        modelJsonName: String = "$DEFAULT_MODEL_DIR.model3.json",
    ) : this(AndroidEnv(assetStore, modelDirName), modelDirName, modelJsonName) {
        appContext = context.applicationContext
    }

    private var appContext: Context? = null
    private var surface: GLSurfaceView? = null
    private var started = false
    private var released = false

    fun availability(): EngineStatus = when {
        released -> EngineStatus(false, REASON_RELEASED)
        !env.modelsReady() -> EngineStatus(false, REASON_NOT_DOWNLOADED)
        !env.nativeLoadable() -> EngineStatus(false, REASON_NATIVE_MISSING)
        else -> EngineStatus(true, null)
    }

    /**
     * A GLSurfaceView driving the avatar, or null when [availability] is not available.
     *
     * [host] must be the Activity-bearing context from the call site (the argument of
     * `DigitalHumanEngine.createView`). The application context cannot resolve an Activity, so
     * `JniBridgeJava.SetActivityInstance` would never run.
     */
    fun createView(host: Context): View? {
        if (!availability().available) return null
        val root = env.cacheRoot() ?: return null
        return try {
            val activity = host.findActivity()
            val builder = viewBuilder
            if (builder != null) builder.build(host, activity) else buildGlView(host, activity, root)
        } catch (t: Throwable) {
            Log.e(TAG, "createView failed", t)
            null
        }
    }

    private fun buildGlView(host: Context, activity: Activity?, root: File): View {
        if (activity != null) {
            JniBridgeJava.SetActivityInstance(activity)
        }
        JniBridgeJava.SetContext(appContext ?: host.applicationContext)
        val view = GLSurfaceView(host).apply {
            setEGLContextClientVersion(2)
            setRenderer(GLRenderer())
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        JniBridgeJava.nativeOnStart()
        // Absolute path so LoadFile uses FileInputStream, not assets.
        val modelPath = File(root, modelDirName).absolutePath + File.separator
        JniBridgeJava.nativeProjectChangeTo(modelPath, modelJsonName)
        JniBridgeJava.nativeAutoBlinkEyes(true)
        started = true
        surface = view
        return view
    }

    /** Mouth openness 0..1 → Cubism ParamMouthOpenY. */
    fun setMouth(v: Float) {
        if (!started || released) return
        runCatching { JniBridgeJava.nativeProjectMouthOpenY(v.coerceIn(0f, 1f)) }
    }

    /** Applies a named expression (e.g. "Angry", "Smile") if the model defines it. */
    fun setExpression(name: String) {
        if (!started || released || name.isEmpty()) return
        runCatching { JniBridgeJava.nativeApplyExpression(name) }
    }

    fun release() {
        if (released) return
        released = true
        runCatching {
            if (started) {
                JniBridgeJava.nativeOnStop()
                JniBridgeJava.nativeOnDestroy()
            }
        }
        surface = null
        started = false
    }

    private class AndroidEnv(
        private val store: AssetStore,
        private val modelDirName: String,
    ) : Env {
        // Same ruling as DuixRuntime: the stock .so lives in the AssetStore cache
        // (cacheRoot(LIVE2D)/native-libs/<abi>) and is dlopen'd by absolute path —
        // loadLibrary would only see APK-bundled libs. Its JNI_OnLoad FindClass-es
        // com/chatwaifu/live2d/JniBridgeJava, vendored in this module with that FQN.
        private val nativeOk: Boolean by lazy {
            val root = cacheRoot()
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull {
                Live2dNativeLoader.nativeDirFor(root, it).isDirectory
            } ?: android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            Live2dNativeLoader.tryLoad(Live2dNativeLoader.nativeDirFor(root, abi))
        }

        override fun cacheRoot(): File = store.cacheRoot(EngineAssets.LIVE2D)

        override fun modelsReady(): Boolean {
            // Manifest is empty until a public ATRI URL lands, so isReady stays false.
            // Dev path: adb-push the model directory into cacheRoot(LIVE2D)/<modelDirName>.
            if (store.isReady(EngineAssets.LIVE2D)) return true
            val dir = File(cacheRoot(), modelDirName)
            return dir.isDirectory && dir.list()?.isNotEmpty() == true
        }

        override fun nativeLoadable(): Boolean = nativeOk
    }

    companion object {
        const val DEFAULT_MODEL_DIR = "ATRI"
        const val REASON_NOT_DOWNLOADED = "未下载"
        const val REASON_NATIVE_MISSING = "native 库缺失"
        private const val REASON_RELEASED = "已释放"
        private const val TAG = "Live2dRuntime"

        /**
         * JVM-test seam: no native library and no real GL. [viewBuilder] records the host Context
         * and resolved Activity that [createView] hands to the GL path.
         */
        internal fun forTest(
            modelsReady: Boolean,
            nativeLoadable: Boolean = true,
            modelDirName: String = DEFAULT_MODEL_DIR,
            modelJsonName: String = "$DEFAULT_MODEL_DIR.model3.json",
            cacheRoot: File? = null,
            viewBuilder: ViewBuilder? = null,
        ): Live2dRuntime = Live2dRuntime(
            env = object : Env {
                override fun modelsReady() = modelsReady
                override fun nativeLoadable() = nativeLoadable
                override fun cacheRoot(): File? = cacheRoot
            },
            modelDirName = modelDirName,
            modelJsonName = modelJsonName,
            viewBuilder = viewBuilder,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
