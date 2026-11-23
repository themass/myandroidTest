package com.openapi.ks.myapp.dh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 工程级护栏：`:dh-engines` 收编三家后，外部 native 外链（`-Pdh.lab.native`）与反射探测
 * 必须彻底删除——clone 本仓库、不依赖本机 `~/work/android` 就能编译通话页。
 */
class DhLabExternalIncludesRemovedTest {

    private val repoRoot: File by lazy {
        var dir = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "settings.gradle").isFile) {
            dir = dir.parentFile
        }
        checkNotNull(dir) { "cannot locate repo root (settings.gradle) from ${System.getProperty("user.dir")}" }
    }

    @Test
    fun settings_gradle_has_no_dh_lab_native_block() {
        val text = File(repoRoot, "settings.gradle").readText()
        assertFalse("settings.gradle must not reference dh.lab.native", text.contains("dh.lab.native"))
        assertFalse("settings.gradle must not include external dh-engine-*", text.contains("dh-engine-"))
    }

    @Test
    fun companion_build_has_no_external_engine_projects() {
        val text = File(repoRoot, "feature-companion/build.gradle").readText()
        assertFalse(text.contains("dh-engine-"))
        assertFalse(text.contains("dhDuixIncluded"))
        assertFalse(text.contains("dhLive2dIncluded"))
    }

    @Test
    fun root_build_has_no_dh_engine_hooks() {
        val text = File(repoRoot, "build.gradle").readText()
        assertFalse(text.contains("dh-engine-"))
    }

    @Test
    fun adapters_do_not_reflect_upstream_engine_classes() {
        val src = File(repoRoot, "feature-companion/src/main/java/com/openapi/ks/myapp/dh")
        val offenders = src.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                val t = f.readText()
                t.contains("ai.guiji") || t.contains("com.chatwaifu") || t.contains("com.taobao.meta.avatar")
            }
            .map { it.name }
            .toList()
        assertTrue("adapters must call :dh-engines directly, reflective upstream names found in $offenders",
            offenders.isEmpty())
    }
}
