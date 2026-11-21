package com.openapi.ks.dh

import android.util.Log
import java.io.File

/**
 * Loads the Duix native chain from the AssetStore cache by absolute path.
 *
 * `System.loadLibrary` only searches the APK's native dir, so the downloaded
 * `duix/native-libs/<abi>` pack would never be found otherwise (final review I3).
 * Order matters: libgjduix.so NEEDED libncnn.so + libonnxruntime.so, so dependencies load
 * first. Every attempt is memoised — [tryLoad] is cheap after the first call.
 */
object DuixNcnnLoader {

    private const val TAG = "DuixNcnnLoader"

    /** dlopen order: dependencies before the JNI owner. */
    val LOAD_ORDER = listOf("libncnn.so", "libonnxruntime.so", "libgjduix.so")

    private var loaded = false

    /**
     * True once all three libs are loaded. [nativeDir] is the dir containing the `.so`
     * files (AssetStore `cacheRoot(DUIX)/native-libs/<abi>`); null tries the APK search
     * path only. Failed attempts are remembered, so availability checks never re-throw.
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
                    // Fall back to the APK search path for this lib (e.g. a future bundled build).
                    System.loadLibrary(name.removePrefix("lib").removeSuffix(".so"))
                }
            }
            true
        } catch (t: Throwable) {
            // UnsatisfiedLinkError AND its cause chain (bad ELF, missing deps) — availability
            // must explain, never crash (final review I4).
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

    /** Whether the cache dir for this device holds the Duix native pack. */
    fun nativeDirFor(cacheRoot: File, abi: String): File =
        File(cacheRoot, "native-libs/$abi")
}
