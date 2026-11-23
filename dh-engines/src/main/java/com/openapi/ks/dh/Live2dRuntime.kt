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
    private var gazeCaptured = false

    /**
     * 改 Cubism 场景的 JNI 调用（expression / mouth / gaze 触点）必须与
     * `nativeOnDrawFrame` **同线程**执行：vendored `libchatwaifu-live2d.so` 无内部锁，
     * 跨线程改场景真机直接 SIGSEGV（2026-11-22 通话页 GLThread drawFrame fault addr 0x14；
     * 2026-11-20 引擎切换 release 崩溃同族——那次只修了拆除路径）。
     *
     * 生产实现 = `GLSurfaceView.queueEvent`（块排队进 GL 线程事件队列，与逐帧绘制天然
     * 串行）；JVM 测试注入 [JniDispatcher] 记录器验证投递语义，不触真 JNI。
     */
    private var dispatcher: JniDispatcher? = null

    /** 投递「改场景的 JNI 调用」到 GL 线程；见 [Live2dRuntime.dispatcher]。 */
    internal fun interface JniDispatcher {
        fun post(block: () -> Unit)
    }

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
        // view 还没挂上就允许 setExpression 等调用（先建 view 后触发的顺序在通话页成立），
        // 此刻起所有场景变更走 GL 队列；queueEvent 在 surface 未创建时也会排队给 GL 线程。
        dispatcher = JniDispatcher { block -> view.queueEvent(block) }
        started = true
        surface = view
        return view
    }

    /** Mouth openness 0..1 → Cubism ParamMouthOpenY. */
    fun setMouth(v: Float) {
        if (!started || released) return
        val at = v.coerceIn(0f, 1f)
        val d = dispatcher ?: return
        runCatching {
            d.post { runCatching { JniBridgeJava.nativeProjectMouthOpenY(at) } }
        }
    }

    /**
     * Head/eye/body follow. The Cubism sample treats a touch drag as "look here": it feeds
     * ParamAngleX/Y, ParamEyeBallX/Y and ParamBodyAngleX. We drive that same path with a
     * synthetic move event so the head turns without a finger on the screen.
     * [nx], [ny] are -1..1 (0,0 = looking straight at the camera).
     */
    fun setGaze(nx: Float, ny: Float) {
        if (!started || released) return
        val v = surface ?: return
        val d = dispatcher ?: return
        val w = v.width.toFloat()
        val h = v.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val x = (0.5f + nx.coerceIn(-1f, 1f) * 0.5f) * w
        val y = (0.5f - ny.coerceIn(-1f, 1f) * 0.5f) * h
        // 像素换算留在调用线程（读 view 尺寸是安全的），JNI 进 GL 队列。
        // began 与 moved 在同一个投递块里，保序不依赖两次排队的相对次序。
        val needCapture = !gazeCaptured
        runCatching {
            d.post {
                runCatching {
                    // Cubism only tracks a drag after a press ("captured"); press once and never release
                    // (a release would run the tap hit-test and fire motions).
                    if (needCapture) {
                        gazeCaptured = true
                        JniBridgeJava.nativeOnTouchesBegan(x, y)
                    }
                    JniBridgeJava.nativeOnTouchesMoved(x, y)
                }
            }
        }
    }

    /** Applies a named expression (e.g. "Angry", "Smile") if the model defines it. */
    fun setExpression(name: String) {
        if (!started || released || name.isEmpty()) return
        val d = dispatcher ?: return
        runCatching {
            d.post { runCatching { JniBridgeJava.nativeApplyExpression(name) } }
        }
    }

    /**
     * 舞台背景色（0..1 sRGB）→ fork .so 的每帧 `glClearColor`（上游写死黑，主题色
     * 永远透不出来——真机验收 2026-11-22「换背景色没效果」）。
     *
     * 只有 [Live2dNativeLoader] 加载的 fork 构建（scripts/dh-lab/build-live2d-native.sh）
     * 导出该符号；误装回上游库存 .so 时 JNI 是 UnsatisfiedLinkError，runCatching 吞掉
     * 保持黑底，不影响其余渲染。写入与逐帧读取都在 GL 线程（dispatcher 串行），无竞争。
     */
    fun setBackground(r: Float, g: Float, b: Float) {
        if (!started || released) return
        val d = dispatcher ?: return
        runCatching {
            d.post {
                runCatching { JniBridgeJava.nativeSetBackgroundColor(r, g, b) }
                    .onFailure { t ->
                        Log.w(TAG, "nativeSetBackgroundColor failed — 舞台会保持黑底（检查 fork .so）", t)
                    }
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        // 切引擎（选 Tao 那一刻，2026-11-20 00:10:31 设备 SIGSEGV）：主线程直接 nativeOnDestroy
        // 拆 native 场景，而 GLThread 还在 guardedRun 里跑 egl createSurface/onSurfaceChanged，
        // libchatwaifu-live2d.so 里 null 解引用。GLSurfaceView.onPause() 会阻塞到 GL 线程退出
        // 渲染循环并释放 EGL surface —— 任何 native 拆除之前必须先执行它。
        runCatching { surface?.onPause() }
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
         * and resolved Activity that [createView] hands to the GL path. [started] pre-arms the
         * runtime without buildGlView; [jniDispatcher] captures posted scene-mutation blocks so
         * threading can be asserted without JNI ([Live2dRuntimeThreadingTest]).
         */
        internal fun forTest(
            modelsReady: Boolean,
            nativeLoadable: Boolean = true,
            modelDirName: String = DEFAULT_MODEL_DIR,
            modelJsonName: String = "$DEFAULT_MODEL_DIR.model3.json",
            cacheRoot: File? = null,
            viewBuilder: ViewBuilder? = null,
            started: Boolean = false,
            jniDispatcher: JniDispatcher? = null,
        ): Live2dRuntime = Live2dRuntime(
            env = object : Env {
                override fun modelsReady() = modelsReady
                override fun nativeLoadable() = nativeLoadable
                override fun cacheRoot(): File? = cacheRoot
            },
            modelDirName = modelDirName,
            modelJsonName = modelJsonName,
            viewBuilder = viewBuilder,
        ).apply {
            this.started = started
            this.dispatcher = jniDispatcher
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
