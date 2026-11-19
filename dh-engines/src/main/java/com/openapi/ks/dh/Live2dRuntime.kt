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
 * `libchatwaifu-live2d.so` is **not** built by this module (no NDK in this task); only the
 * Cubism Core arm64 `.so` is packaged. A missing wrapper shows up as [availability] =
 * unavailable ([REASON_NATIVE_MISSING]).
 *
 * The native library must export JNI symbols for the renamed class, i.e.
 * `Java_com_openapi_ks_dh_live2d_JniBridgeJava_*` (upstream prebuilts export
 * `Java_com_chatwaifu_live2d_JniBridgeJava_*`), and its `FindClass` must target
 * `com/openapi/ks/dh/live2d/JniBridgeJava`.
 */
class Live2dRuntime internal constructor(
    private val env: Env,
    private val modelDirName: String,
    private val modelJsonName: String,
) {

    /** Everything [Live2dRuntime] needs from the outside world; tests inject a fake. */
    internal interface Env {
        fun modelsReady(): Boolean
        fun nativeLoadable(): Boolean
        fun cacheRoot(): File?
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

    /** A GLSurfaceView driving the avatar, or null when [availability] is not available. */
    fun createView(): View? {
        val context = appContext ?: return null
        if (!availability().available) return null
        val root = env.cacheRoot() ?: return null
        return try {
            val activity = context.findActivity()
            if (activity != null) {
                JniBridgeJava.SetActivityInstance(activity)
            }
            JniBridgeJava.SetContext(context.applicationContext)
            val view = GLSurfaceView(context).apply {
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
            view
        } catch (t: Throwable) {
            Log.e(TAG, "createView failed", t)
            null
        }
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
        /** JNI wrapper built by ChatWaifu's CMake; not packaged by this module. */
        private const val NATIVE_LIB = "chatwaifu-live2d"
        private const val TAG = "Live2dRuntime"

        /** JVM-test seam: no Context, no native library, availability from plain flags. */
        internal fun forTest(
            modelsReady: Boolean,
            nativeLoadable: Boolean = true,
            modelDirName: String = DEFAULT_MODEL_DIR,
            modelJsonName: String = "$DEFAULT_MODEL_DIR.model3.json",
        ): Live2dRuntime = Live2dRuntime(
            env = object : Env {
                override fun modelsReady() = modelsReady
                override fun nativeLoadable() = nativeLoadable
                override fun cacheRoot(): File? = null
            },
            modelDirName = modelDirName,
            modelJsonName = modelJsonName,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
