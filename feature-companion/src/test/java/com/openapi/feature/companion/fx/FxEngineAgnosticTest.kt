package com.openapi.feature.companion.fx

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 硬验收：fx 包不得 import / 调用任何引擎符号（DhEngineId / dh-engines 等）。
 * 扫的是代码行（import 与标识符），注释里的禁词不算——文档要能写「不要用 X」。
 * Spine 上线零改动靠这条护栏钉死。
 */
class FxEngineAgnosticTest {

    private val repoRoot: File by lazy {
        var dir = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "settings.gradle").isFile) {
            dir = dir.parentFile
        }
        checkNotNull(dir) { "cannot locate repo root from ${System.getProperty("user.dir")}" }
    }

    private fun codeLines(text: String): List<String> =
        text.lines().filter { line ->
            val t = line.trim()
            t.isNotEmpty() && !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*")
        }

    @Test
    fun fxPackage_hasNoEngineImports() {
        val src = File(repoRoot, "feature-companion/src/main/java/com/openapi/feature/companion/fx")
        assertTrue("fx package missing at $src", src.isDirectory)
        val bannedImports = listOf(
            "import com.openapi.ks.myapp.dh.",
            "import com.openapi.dh",
            "DhEngineId",
            "DigitalHumanEngine",
            "Live2dDigitalHumanEngine",
            "DuixDigitalHumanEngine",
            "VrmDigitalHumanEngine",
            "StageFxBus",
        )
        val offenders = src.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { f ->
                val lines = codeLines(f.readText())
                bannedImports.filter { needle -> lines.any { it.contains(needle) } }
                    .map { "${f.name}: $it" }
            }
            .toList()
        assertTrue("fx package must stay engine-agnostic, found $offenders", offenders.isEmpty())
    }

    @Test
    fun fxPackage_hasNoPointerInput() {
        val src = File(repoRoot, "feature-companion/src/main/java/com/openapi/feature/companion/fx")
        val offenders = src.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                val lines = codeLines(f.readText())
                lines.any {
                    it.contains("pointerInput(") ||
                        it.contains("detectTapGestures") ||
                        (f.name == "LottieFxLayer.kt" && it.contains(".clickable"))
                }
            }
            .map { it.name }
            .toList()
        assertTrue("fx layer must not consume touch, found $offenders", offenders.isEmpty())
    }
}
