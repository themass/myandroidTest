package com.openapi.app.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 架构规则（见 docs/design/refactor-round2.md 第 3 节）。新增依赖前先看这里，不要为了通过而改测试。
 *
 * 1. 分层：只依赖下层。同层只允许 [SAME_LAYER_OK] 里的几条边。
 *    第 0 层 :common :network :uikit
 *    第 1 层 :mediaview :speech :subtitle :imaging :cast :ads :chatkit :retromusic（可复用能力）
 *    第 2 层 :core
 *    第 3 层 :feature-*（互相不依赖）
 *    第 4 层 :app（组合根，没有模块依赖它）
 * 2. 包名 = 模块根包：每个源文件的包名以模块根包开头（:retromusic、:feature-companion 除外）。
 * 3. 第 0、1 层不 import 业务代码（com.openapi.core / feature / app），拿到别的项目也能用。
 * 4. 声明的项目依赖必须真的用到（:app 作为组合根除外），避免借道和残留依赖。
 */
class ModuleGraphTest {

    private val root: File = File("..").canonicalFile

    private val layerOf = mapOf(
        "common" to 0, "network" to 0, "uikit" to 0,
        "mediaview" to 1, "speech" to 1, "subtitle" to 1, "imaging" to 1, "cast" to 1,
        "ads" to 1, "chatkit" to 1, "retromusic" to 1,
        "core" to 2,
        "app" to 4,
    )

    private val SAME_LAYER_OK = setOf(
        "network" to "common",
        "uikit" to "common",
        "chatkit" to "mediaview",
        "subtitle" to "speech",
    )

    private val packageRuleExempt = setOf("retromusic", "feature-companion")

    private fun modules(): List<String> =
        File(root, "settings.gradle").readLines()
            .mapNotNull { Regex("""^include\s+['"]:([\w-]+)['"]""").find(it)?.groupValues?.get(1) }

    private fun layer(module: String): Int =
        if (module.startsWith("feature-")) 3 else layerOf[module] ?: error("模块 :$module 没有分层，先在测试里登记")

    private fun rootPackage(module: String): String = when {
        module.startsWith("feature-") -> "com.openapi.feature." + module.removePrefix("feature-")
        else -> "com.openapi.$module"
    }

    /** (配置名, 依赖的模块) */
    private fun projectDependencies(module: String): List<Pair<String, String>> =
        Regex("""^\s*(\w+)\s*\(?\s*project\(['"]:([\w-]+)['"]\)""", RegexOption.MULTILINE)
            .findAll(File(root, "$module/build.gradle").readText())
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()

    private fun mainSources(module: String): List<File> =
        File(root, "$module/src/main").walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .toList()

    private fun packageOf(text: String): String =
        Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1).orEmpty()

    @Test
    fun layeringRulesHold() {
        val violations = mutableListOf<String>()
        val modules = modules()
        modules.forEach { module ->
            projectDependencies(module).forEach { (_, dep) ->
                when {
                    dep == "app" -> violations += ":$module 依赖 :app（组合根）"
                    module.startsWith("feature-") && dep.startsWith("feature-") ->
                        violations += ":$module 依赖 :$dep（feature 之间不能互相依赖，用路由）"
                    layer(dep) > layer(module) ->
                        violations += ":$module（第 ${layer(module)} 层）依赖上层 :$dep（第 ${layer(dep)} 层）"
                    layer(dep) == layer(module) && (module to dep) !in SAME_LAYER_OK ->
                        violations += ":$module 依赖同层的 :$dep（未批准）"
                }
            }
        }
        assertTrue("分层违规:\n" + violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun packagesMatchModules() {
        val violations = mutableListOf<String>()
        modules().filter { it !in packageRuleExempt }.forEach { module ->
            val rootPkg = rootPackage(module)
            mainSources(module).forEach { f ->
                val pkg = packageOf(f.readText())
                if (pkg != rootPkg && !pkg.startsWith("$rootPkg.")) {
                    violations += "${f.relativeTo(root)}：包名 $pkg 不在 $rootPkg 下"
                }
            }
        }
        assertTrue("包名违规:\n" + violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun reusableModulesDoNotImportBusinessCode() {
        val violations = mutableListOf<String>()
        val importRx = Regex("""^\s*import\s+(com\.openapi\.(?:core|feature|app)\b[\w.]*)""", RegexOption.MULTILINE)
        modules().filter { layer(it) <= 1 }.forEach { module ->
            mainSources(module).forEach { f ->
                importRx.findAll(f.readText()).forEach {
                    violations += "${f.relativeTo(root)} import ${it.groupValues[1]}"
                }
            }
        }
        assertTrue("第 0、1 层引用了业务代码:\n" + violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun declaredProjectDependenciesAreUsed() {
        val violations = mutableListOf<String>()
        val modules = modules()
        modules.filter { it != "app" }.forEach { module ->
            val main = File(root, "$module/src/main")
            val text = main.walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "java", "xml") }
                .joinToString("\n") { it.readText() }
            projectDependencies(module)
                .filter { (conf, dep) -> !conf.startsWith("test") && dep in modules }
                .forEach { (_, dep) ->
                    val pkg = if (dep == "retromusic") "com.openapi.retromusic" else rootPackage(dep)
                    if (!Regex("""(?<![\w.])""" + Regex.escape(pkg) + """\b""").containsMatchIn(text)) {
                        violations += ":$module 声明了 :$dep 但没有用到"
                    }
                }
        }
        assertTrue("多余的项目依赖:\n" + violations.joinToString("\n"), violations.isEmpty())
    }
}
