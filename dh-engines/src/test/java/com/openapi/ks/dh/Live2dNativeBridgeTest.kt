package com.openapi.ks.dh

import com.openapi.ks.dh.live2d.JniBridgeJava
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Live2D native binding bridge — same ruling as Duix (Task-6): vendored Java keeps OUR
 * package, the stock `libchatwaifu-live2d.so` keeps its upstream symbols
 * `Java_com_chatwaifu_live2d_JniBridgeJava_*`.
 *
 * The stricter half: this .so's JNI_OnLoad does `FindClass("com/chatwaifu/live2d/JniBridgeJava")`
 * and caches GetStaticMethodID for LoadFile/MoveTaskToBack/OnLoadError/OnLoadDone/
 * OnLoadOneMotion/OnLoadOneExpression *inside* JNI_OnLoad. So the upstream-FQN class must
 * exist (and declare those Java bridge methods) BEFORE [Live2dNativeLoader] System.loads
 * the .so by absolute path — hence the mirror is the real native owner and our vendored
 * JniBridgeJava delegates to it.
 */
class Live2dNativeBridgeTest {

    private val F = Float::class.javaPrimitiveType!!
    private val Z = Boolean::class.javaPrimitiveType!!
    private val I = Int::class.javaPrimitiveType!!
    private val S = String::class.java

    /**
     * The 20 native methods the fork-built libchatwaifu-live2d.so exports: the stock 19
     * plus [live2d-fork] `nativeSetBackgroundColor` (背景色注入，见
     * scripts/dh-lab/build-live2d-native.sh + live2d-native-fork.patch).
     */
    private val nativeSurface: List<Pair<String, Array<Class<*>>>> = listOf(
        "nativeOnStart" to emptyArray(),
        "nativeOnPause" to emptyArray(),
        "nativeOnStop" to emptyArray(),
        "nativeOnDestroy" to emptyArray(),
        "nativeOnSurfaceCreated" to emptyArray(),
        "nativeOnSurfaceChanged" to arrayOf<Class<*>>(I, I),
        "nativeOnDrawFrame" to emptyArray(),
        "nativeOnTouchesBegan" to arrayOf<Class<*>>(F, F),
        "nativeOnTouchesEnded" to arrayOf<Class<*>>(F, F),
        "nativeOnTouchesMoved" to arrayOf<Class<*>>(F, F),
        "nativeProjectChangeTo" to arrayOf<Class<*>>(S, S),
        "nativeApplyExpression" to arrayOf<Class<*>>(S),
        "needRenderBack" to arrayOf<Class<*>>(Z),
        "nativeProjectScale" to arrayOf<Class<*>>(F),
        "nativeProjectTransformX" to arrayOf<Class<*>>(F),
        "nativeProjectTransformY" to arrayOf<Class<*>>(F),
        "nativeAutoBlinkEyes" to arrayOf<Class<*>>(Z),
        "nativeProjectMouthForm" to arrayOf<Class<*>>(F),
        "nativeProjectMouthOpenY" to arrayOf<Class<*>>(F),
        "nativeSetBackgroundColor" to arrayOf<Class<*>>(F, F, F),
    )

    /** Java bridge methods JNI_OnLoad resolves on the upstream class — missing = load throws. */
    private val bridgeSurface: List<Pair<String, Array<Class<*>>>> = listOf(
        "LoadFile" to arrayOf<Class<*>>(S),
        "MoveTaskToBack" to emptyArray(),
        "OnLoadError" to emptyArray(),
        "OnLoadDone" to emptyArray(),
        "OnLoadOneMotion" to arrayOf<Class<*>>(S, I, S),
        "OnLoadOneExpression" to arrayOf<Class<*>>(S, I),
    )

    private val upstream = Class.forName("com.chatwaifu.live2d.JniBridgeJava", false, javaClass.classLoader)

    @Test
    fun upstream_mirror_class_declares_every_native() {
        for ((name, params) in nativeSurface) {
            val m = upstream.getDeclaredMethod(name, *params)
            assertTrue(
                "com.chatwaifu.live2d.JniBridgeJava.$name must be native (the JNI surface the stock .so binds to)",
                java.lang.reflect.Modifier.isNative(m.modifiers),
            )
        }
    }

    @Test
    fun upstream_mirror_class_declares_the_java_bridge_methods_jni_onload_needs() {
        for ((name, params) in bridgeSurface) {
            val m = upstream.getDeclaredMethod(name, *params)
            assertTrue("com.chatwaifu.live2d.JniBridgeJava.$name must be static", java.lang.reflect.Modifier.isStatic(m.modifiers))
            assertFalse("com.chatwaifu.live2d.JniBridgeJava.$name is a plain-Java bridge, not native", java.lang.reflect.Modifier.isNative(m.modifiers))
        }
    }

    @Test
    fun vendored_bridge_delegates_without_own_native_or_load_block() {
        for ((name, params) in nativeSurface) {
            val m = JniBridgeJava::class.java.getDeclaredMethod(name, *params)
            assertTrue("our JniBridgeJava.$name must stay public", java.lang.reflect.Modifier.isPublic(m.modifiers))
            assertFalse(
                "our $name must NOT be native anymore — binding lives in com.chatwaifu.live2d.JniBridgeJava",
                java.lang.reflect.Modifier.isNative(m.modifiers),
            )
        }
    }

    @Test
    fun cache_loader_reports_failure_without_a_native_dir() {
        // JVM/CI has no libchatwaifu-live2d anywhere; the loader must say false, never throw —
        // Live2dRuntime turns that into 「native 库缺失」.
        assertFalse(Live2dNativeLoader.tryLoad(null))
    }

    @Test
    fun cache_loader_reports_failure_for_a_missing_directory() {
        assertFalse(Live2dNativeLoader.tryLoad(File("build/definitely-not-a-native-dir")))
    }

    @Test
    fun cache_load_order_is_the_single_jni_owner() {
        // Cubism Core is statically linked into libchatwaifu-live2d.so; only system libs
        // (GLESv2/log/m/dl/c) remain, which the linker resolves itself.
        assertEquals(listOf("libchatwaifu-live2d.so"), Live2dNativeLoader.LOAD_ORDER)
    }

    @Test
    fun cache_dir_layout_matches_seed_scripts_push_target() {
        val dir = Live2dNativeLoader.nativeDirFor(File("/x/live2d"), "arm64-v8a")
        assertEquals(File("/x/live2d/native-libs/arm64-v8a"), dir)
    }

    @Test
    fun live2d_native_ui_assets_are_packaged() {
        // nativeOnSurfaceChanged 无条件 LoadFile 这三张 assets（"" 相对路径 → APK assets 根）。
        // 缺任何一张：LoadFile 回 null → native 对 null 调 GetArrayLength → JNI abort 杀进程
        // （设备实测 SIGABRT "java_array == null in call to GetArrayLength"）。
        var root = File(System.getProperty("user.dir"))
        while (root != null && !File(root, "settings.gradle").isFile) root = root.parentFile
        val assets = File(checkNotNull(root), "dh-engines/src/main/assets")
        for (name in listOf("back_class_normal.png", "icon_gear.png", "close.png")) {
            assertTrue("$name missing from $assets (native crashes without it)", File(assets, name).isFile)
        }
    }
}
