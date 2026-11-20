package com.openapi.ks.dh

import android.util.Log
import java.io.File

/**
 * Loads the Live2D JNI wrapper from the AssetStore cache by absolute path — the same
 * pattern as [DuixNcnnLoader] (System.loadLibrary would only see APK-bundled libs).
 *
 * `libchatwaifu-live2d.so` statically links Cubism Core (verified: no undefined `csm*`
 * symbols), so its only NEEDED entries are system libs — a single-lib load order. Loading
 * it runs JNI_OnLoad, which FindClass-es `com.chatwaifu.live2d.JniBridgeJava` and resolves
 * the Java bridge method IDs there: that mirror class must be on the classpath (it is —
 * vendored in this module) or the load throws.
 */
object Live2dNativeLoader {

    private const val TAG = "Live2dNativeLoader"

    /** Only the JNI owner; its deps are platform libs the linker resolves. */
    val LOAD_ORDER = listOf("libchatwaifu-live2d.so")

    private var loaded = false

    /**
     * True once the wrapper is loaded. [nativeDir] is the dir holding the `.so` (AssetStore
     * `cacheRoot(LIVE2D)/native-libs/<abi>`); null tries the APK search path only. Attempts
     * are memoised and every failure (UnsatisfiedLinkError **and** its cause chain — JNI_OnLoad
     * FindClass failures surface as ExceptionInInitializerError/Throwable) becomes false,
     * never a crash.
     */
    @Synchronized
    fun tryLoad(nativeDir: File?): Boolean {
        if (loaded) return true
        val ok = try {
            for (name in LOAD_ORDER) {
                val file = nativeDir?.let { File(it, name) }
                if (file != null && file.isFile) {
                    System.load(file.absolutePath)
                } else {
                    System.loadLibrary(name.removePrefix("lib").removeSuffix(".so"))
                }
            }
            true
        } catch (t: Throwable) {
            logFailure(t)
            false
        }
        if (ok) loaded = true
        return ok
    }

    private fun logFailure(t: Throwable) {
        try {
            Log.i(TAG, "native load failed", t)
        } catch (_: RuntimeException) {
            // android.util.Log is unmocked on plain JVM unit tests.
        }
    }

    /** The cache dir for this device's abi, mirroring seed-assets.sh's push layout. */
    fun nativeDirFor(cacheRoot: File, abi: String): File =
        File(cacheRoot, "native-libs/$abi")
}
