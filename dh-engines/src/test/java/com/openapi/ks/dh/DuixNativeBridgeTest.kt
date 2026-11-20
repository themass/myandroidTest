package com.openapi.ks.dh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.openapi.ks.dh.duix.DuixNcnn
import org.junit.Test
import java.io.File

/**
 * Duix native binding bridge (Task-6 ruling: vendored Java keeps OUR package, the stock
 * libgjduix.so exports Java_ai_guiji_duix_DuixNcnn_* — a narrow upstream-FQN class carries
 * the real JNI surface, our DuixNcnn delegates to it).
 *
 * These tests pin the ABI mirror so the two classes can never drift apart: the stock .so
 * would otherwise die with UnsatisfiedLinkError on the first call (final review I4).
 */
class DuixNativeBridgeTest {

    // Non-null primitives (javaPrimitiveType is nullable in Kotlin's mirror types).
    private val I = Int::class.javaPrimitiveType!!
    private val L = Long::class.javaPrimitiveType!!
    private val S = String::class.java
    private val B = ByteArray::class.java
    private val IA = IntArray::class.java

    /** The 18 native methods the stock libgjduix.so actually exports. */
    private val nativeSurface: List<Pair<String, Array<Class<*>>>> = listOf(
        "alloc" to arrayOf<Class<*>>(I, I, I, I),
        "free" to arrayOf<Class<*>>(I),
        "initPcmex" to arrayOf<Class<*>>(I, I, I, I, I),
        "initWenet" to arrayOf<Class<*>>(S),
        "initMunet" to arrayOf<Class<*>>(S, S, S),
        "initMunetex" to arrayOf<Class<*>>(S, S, S, I),
        "newsession" to emptyArray(),
        "finsession" to arrayOf<Class<*>>(L),
        "consession" to arrayOf<Class<*>>(L),
        "allcnt" to arrayOf<Class<*>>(L),
        "readycnt" to arrayOf<Class<*>>(L),
        "pushpcm" to arrayOf<Class<*>>(L, B, I, I),
        "filerst" to arrayOf<Class<*>>(L, S, S, IA, S, I, B, B, I),
        "bufrst" to arrayOf<Class<*>>(L, IA, I, B, I),
        "fileload" to arrayOf<Class<*>>(S, S, I, I, B, B, I),
        "startgpg" to arrayOf<Class<*>>(S, S),
        "stopgpg" to emptyArray(),
        "processmd5" to arrayOf<Class<*>>(I, S, S),
    )

    private val upstream = Class.forName("ai.guiji.duix.DuixNcnn", false, javaClass.classLoader)

    @Test
    fun upstream_bridge_class_declares_every_native_symmetrically() {
        for ((name, params) in nativeSurface) {
            val m = upstream.getDeclaredMethod(name, *params)
            assertTrue(
                "ai.guiji.duix.DuixNcnn.$name must be native (the JNI surface the stock .so binds to)",
                java.lang.reflect.Modifier.isNative(m.modifiers),
            )
        }
    }

    @Test
    fun our_duix_ncnn_mirrors_the_bridge_publicly_and_delegates() {
        val ours = DuixNcnn::class.java
        for ((name, params) in nativeSurface) {
            val m = ours.getDeclaredMethod(name, *params)
            assertTrue("com.openapi.ks.dh.duix.DuixNcnn.$name must be public", java.lang.reflect.Modifier.isPublic(m.modifiers))
            assertFalse(
                "our $name must NOT be native anymore — binding lives in ai.guiji.duix.DuixNcnn",
                java.lang.reflect.Modifier.isNative(m.modifiers),
            )
        }
    }

    @Test
    fun cache_loader_reports_failure_without_a_native_dir() {
        // JVM/CI has no libgjduix anywhere; the loader must say false instead of throwing —
        // DuixRuntime turns that into 「native 库缺失」, never a crash (Review Focus: explain).
        assertFalse(DuixNcnnLoader.tryLoad(null))
    }

    @Test
    fun cache_loader_reports_failure_for_a_missing_directory() {
        assertFalse(DuixNcnnLoader.tryLoad(File("build/definitely-not-a-native-dir")))
    }

    @Test
    fun cache_load_order_puts_dependencies_before_gjduix() {
        // libgjduix.so NEEDED libncnn.so + libonnxruntime.so — dlopen order must match.
        assertEquals(
            listOf("libncnn.so", "libonnxruntime.so", "libgjduix.so"),
            DuixNcnnLoader.LOAD_ORDER,
        )
    }
}
