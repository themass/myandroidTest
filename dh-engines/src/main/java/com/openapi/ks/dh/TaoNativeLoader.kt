package com.openapi.ks.dh

import android.util.Log
import java.io.File

/**
 * Loads the Tao native chain from the AssetStore cache by absolute path — same shape as
 * [DuixNcnnLoader] for the same reason (final review I3: `System.loadLibrary` never sees the
 * downloaded `tao/native-libs/<abi>` pack).
 *
 * The cache dir additionally carries `libc++_shared.so` (an NDK sysroot copy pushed by
 * scripts/dh-lab/seed-assets.sh): `libtaoavatar.so` NEEDEDs it and Android ships no system
 * libc++_shared, so without it every lib in the chain fails to dlopen.
 *
 * dlopen order is the NEEDED closure (verified with llvm-readelf on the local build):
 * `libtaoavatar.so → {libnnrruntime, libmnn_a2bs, libmnn_tts_SDK, libMNN, libc++_shared}` and
 * `{a2bs, tts} → {libMNN, libc++_shared}`, so shared first, JNI owner last.
 */
object TaoNativeLoader {

    private const val TAG = "TaoNativeLoader"

    /** dlopen order: dependencies before the JNI owner. */
    val LOAD_ORDER = listOf(
        "libc++_shared.so",
        "libMNN.so",
        "libnnrruntime.so",
        "libmnn_a2bs.so",
        "libmnn_tts_SDK.so",
        "libtaoavatar.so",
    )

    private var loaded = false

    /**
     * True once the whole chain is loaded. [nativeDir] is the dir containing the `.so` files
     * (AssetStore `cacheRoot(TAO)/native-libs/<abi>`); null tries the APK search path only.
     * Failed attempts are remembered, so availability checks never re-throw.
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
            // must explain, never crash.
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

    /** Whether the cache dir for this device holds the Tao native pack. */
    fun nativeDirFor(cacheRoot: File, abi: String): File =
        File(cacheRoot, "native-libs/$abi")
}
