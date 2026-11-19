package com.openapi.ks.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tao native binding contract for the locally rebuilt `libtaoavatar.so`
 * (MnnTaoAvatar cpp, built with NDK 27 against upstream libMNN/libnnrruntime).
 *
 * Unlike the stock Live2D/Duix .so (where only a thin bridge class needs the upstream FQN),
 * here the whole vendored render class + camera data classes must KEEP their upstream package
 * `com.taobao.meta.avatar.*`:
 *
 * 1. The .so exports only `Java_com_taobao_meta_avatar_nnr_NnrAvatarRender_*` — a renamed
 *    package yields UnsatisfiedLinkError on first call (no JNI_OnLoad/RegisterNatives to help).
 * 2. jni/jni_utils.cpp CopyToNativeCameraControlData does
 *    `GetFieldID(cls, "camera", "Lcom/taobao/meta/avatar/camera/Camera;")` — the field
 *    descriptor hardcodes the upstream camera FQN, so CameraControlData AND Camera must live
 *    at that exact FQN or the per-frame GetFieldID aborts.
 * 3. Every field jni_utils.cpp reads (name + JNI signature) must exist on those classes.
 *
 * The mirror classes are NOT in feature-companion, so DhLabExternalIncludesRemovedTest
 * (which guards the app-side dh/ package) does not apply to them.
 */
class TaoNativeBridgeTest {

    private val F = Float::class.javaPrimitiveType!!
    private val Z = Boolean::class.javaPrimitiveType!!
    private val J = Long::class.javaPrimitiveType!!
    private val S = String::class.java
    private val Surface = android.view.Surface::class.java

    private fun upstream(name: String): Class<*> =
        Class.forName("com.taobao.meta.avatar.$name", false, javaClass.classLoader)

    /** The 8 JNI symbols libtaoavatar.so exports for NnrAvatarRender (all static). */
    private val nativeSurface: List<Pair<String, Array<Class<*>>>> = listOf(
        "nativeCreateNNR" to emptyArray(),
        "nativeInitNNR" to arrayOf<Class<*>>(J, Surface, S, S),
        "nativeDestroy" to arrayOf<Class<*>>(J),
        "nativeRender" to arrayOf<Class<*>>(J),
        "nativeReset" to arrayOf<Class<*>>(J),
        "nativeIsNNRReady" to arrayOf<Class<*>>(J),
        "nativeUpdateNNRScene" to arrayOf<Class<*>>(
            J, upstream("camera.CameraControlData"), Z, J, J, Z, F, F, J,
        ),
        "nativeLoadNnrResources" to arrayOf<Class<*>>(J, S, S, S, S, S, S),
    )

    @Test
    fun upstream_fqn_render_class_declares_every_native() {
        val render = upstream("nnr.NnrAvatarRender")
        for ((name, params) in nativeSurface) {
            val m = render.getDeclaredMethod(name, *params)
            assertTrue(
                "com.taobao.meta.avatar.nnr.NnrAvatarRender.$name must be native (libtaoavatar.so binds it by FQN)",
                java.lang.reflect.Modifier.isNative(m.modifiers),
            )
        }
    }

    @Test
    fun camera_control_data_matches_jni_utils_field_surface() {
        val cls = upstream("camera.CameraControlData")
        // jni_utils.cpp GetFieldID(name, signature) — private Kotlin backing fields are fine,
        // JNI reads them regardless of access; the name+type must match exactly.
        val scalar = mapOf(
            "curScale" to F, "lastScale" to F, "rotateSpeed" to F,
            "isFirstFrame" to Z, "distanceOnScreen" to F,
        )
        for ((n, t) in scalar) assertEquals("CameraControlData.$n type", t, cls.getDeclaredField(n).type)
        for (n in listOf("rotateX", "rotateY", "scaleMatrix", "modelMatrix")) {
            assertEquals("CameraControlData.$n must be float[16]-compatible", FloatArray::class.java, cls.getDeclaredField(n).type)
        }
    }

    @Test
    fun camera_field_descriptor_is_the_upstream_fqn() {
        // The load-bearing one: the descriptor "Lcom/taobao/meta/avatar/camera/Camera;" is
        // hardcoded in jni_utils.cpp, so the field type must be exactly this FQN.
        val field = upstream("camera.CameraControlData").getDeclaredField("camera")
        assertEquals("com.taobao.meta.avatar.camera.Camera", field.type.name)
    }

    @Test
    fun camera_matches_jni_utils_field_surface() {
        val cls = upstream("camera.Camera")
        for (n in listOf("position", "front", "up", "right", "worldUp")) {
            assertEquals("Camera.$n must be float[3]-compatible", FloatArray::class.java, cls.getDeclaredField(n).type)
        }
        for (n in listOf("yaw", "pitch", "movementSpeed", "mouseSensitivity", "zoom", "nearPlane", "farPlane")) {
            assertEquals("Camera.$n type", F, cls.getDeclaredField(n).type)
        }
    }

    @Test
    fun loader_order_is_dependency_first_jni_owner_last() {
        // NEEDED chain (llvm-readelf): taoavatar → {nnrruntime, mnn_a2bs, mnn_tts_SDK, MNN, c++_shared};
        // a2bs/tts → {MNN, c++_shared}. libc++_shared is NOT on the Android system image — the
        // NDK copy must ship in the cache dir (the Duix cache has none; nothing else loads one).
        assertEquals(
            listOf(
                "libc++_shared.so",
                "libMNN.so",
                "libnnrruntime.so",
                "libmnn_a2bs.so",
                "libmnn_tts_SDK.so",
                "libtaoavatar.so",
            ),
            TaoNativeLoader.LOAD_ORDER,
        )
    }

    @Test
    fun cache_loader_reports_failure_without_a_native_dir() {
        // JVM/CI has no libtaoavatar anywhere; the loader must say false, never throw —
        // TaoRuntime turns that into 「native 库缺失」.
        assertFalse(TaoNativeLoader.tryLoad(null))
    }

    @Test
    fun cache_loader_reports_failure_for_a_missing_directory() {
        assertFalse(TaoNativeLoader.tryLoad(File("build/definitely-not-a-native-dir")))
    }

    @Test
    fun cache_dir_layout_matches_seed_scripts_push_target() {
        val dir = TaoNativeLoader.nativeDirFor(File("/x/tao"), "arm64-v8a")
        assertEquals(File("/x/tao/native-libs/arm64-v8a"), dir)
    }

    @Test
    fun tao_runtime_loads_from_cache_instead_of_apk_path() {
        // Source guard (same style as CompanionCallStageLayoutTest): the cache-dir pack is
        // invisible to System.loadLibrary (final review I3 lesson from Duix), so TaoRuntime
        // must go through TaoNativeLoader with the per-abi native dir.
        var dir = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "settings.gradle").isFile) dir = dir.parentFile
        val src = File(checkNotNull(dir), "dh-engines/src/main/java/com/openapi/ks/dh/TaoRuntime.kt").readText()
        assertTrue("TaoRuntime must call TaoNativeLoader.tryLoad", src.contains("TaoNativeLoader.tryLoad"))
        assertTrue("TaoRuntime must resolve the cache native dir", src.contains("TaoNativeLoader.nativeDirFor"))
        assertFalse("System.loadLibrary cannot see cache-dir libs", src.contains("System.loadLibrary"))
    }
}
