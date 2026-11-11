package com.openapi.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 模块依赖的分层规则（阶段 9 的架构测试）。
 *
 * 规则：
 * 1. 没有模块依赖 :app（:app 是组合根）。
 * 2. :commonutil 不依赖任何项目模块。
 * 3. :core、:media、:ads、:chatkit 不依赖 feature-*。
 * 4. feature-* 之间不互相依赖；只允许 :feature-chat → :feature-roles（已批准）。
 *
 * 新增依赖前先看这里的规则，不要直接改测试来通过。
 */
class ModuleGraphTest {

    private val root: File = File("..").canonicalFile

    private val approvedFeatureEdges = setOf("feature-chat" to "feature-roles")

    private fun projectDependencies(module: String): Set<String> {
        val gradle = File(root, "$module/build.gradle")
        if (!gradle.exists()) return emptySet()
        return Regex("""project\(':([^']+)'\)""")
            .findAll(gradle.readText())
            .map { it.groupValues[1] }
            .toSet()
    }

    private fun allModules(): List<String> = root.listFiles { f -> File(f, "build.gradle").exists() }
        .orEmpty()
        .map { it.name }
        .filter { it != "build" && !it.startsWith(".") }
        .sorted()

    @Test
    fun layeringRulesHold() {
        val violations = mutableListOf<String>()
        val modules = allModules()
        val featureModules = modules.filter { it.startsWith("feature-") }.toSet()

        modules.forEach { module ->
            val deps = projectDependencies(module)

            if ("app" in deps) {
                violations += "$module depends on :app (the composition root)"
            }
            if (module == "common" && deps.isNotEmpty()) {
                violations += "common depends on $deps (it must not depend on project modules)"
            }
            if (module in setOf("core", "media", "ads", "chatkit")) {
                deps.filter { it in featureModules }.forEach {
                    violations += "$module depends on feature module :$it"
                }
            }
            if (module in featureModules) {
                deps.filter { it in featureModules }.forEach { other ->
                    if (module to other !in approvedFeatureEdges) {
                        violations += "$module depends on feature module :$other"
                    }
                }
            }
        }

        assertTrue("Module layering violations:\n" + violations.joinToString("\n"), violations.isEmpty())
    }
}
