package com.openapi.app.architecture

import androidx.compose.ui.unit.sp
import com.openapi.feature.photosolve.SolveTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 拍照解题排版守卫（用户反馈「字体太大」的根因守卫，5ace4ae3；A42-#1 收口到 token）。
 *
 * 守的是**结构**而不是截图数字（Robolectric 字体度量与真机差得多，见同目录渲染测试
 * 先例）：字号漂移是这次问题的根因——结果页排版修复（3b01deae）没覆盖历史详情页，
 * 详情页自己那套 20/16/20sp 一直留在线上；A42 取证进一步发现 17/15/14/18 四个值
 * 虽然只在一处定义，但仍是**调用点硬编码**，MathText 默认值 16sp 和公式 ×1.15 各算
 * 一套事实源。现在四档值全部定义在 `SolveTypography`（token），页面只引用名字；
 * 谁再给某个页面单独开字号、给 MathText 传字面量 sp、或在 MathText 里另定默认值/系数，
 * 这里直接红。
 *
 * 源码扫描先例：ModuleGraphTest（同样读文件断言规则）。
 */
class PhotosolveTypographyGuardTest {

    private val uiDir = File(
        File("..").canonicalFile,
        "feature-photosolve/src/main/java/com/openapi/feature/photosolve/ui",
    )

    private fun read(name: String): String = File(uiDir, name).readText()

    /** 历史详情页必须走共享的 SolveResultSections，不许自带字号。 */
    @Test
    fun historyDetailDelegatesToSharedResultSections() {
        val detail = read("SolveHistoryDetailScreen.kt")
        assertTrue(
            "历史详情页应复用 SolveResultSections（字号单一事实源），不要再写一份正文",
            detail.contains("SolveResultSections("),
        )
        assertFalse(
            "历史详情页不许自定义 MathText 字号——20/16sp 旧字号就是在这里漂移出来的（5ace4ae3 修的就是它）",
            detail.contains("fontSize"),
        )
    }

    /** MathText 的字号只允许在 PhotoResultScreen 的 SolveResultSections 里定义，且必须是 token 引用。 */
    @Test
    fun mathTextFontSizesDefinedInOneFileOnly() {
        // 只看 MathText(...) 调用里带 fontSize 的；普通 Text 的小标签
        // （裁剪页 12sp、弹窗走 AppTypography token）不在本守卫范围内。
        val pattern = Regex("""MathText\([^)]*fontSize""", RegexOption.DOT_MATCHES_ALL)
        val offenders = uiDir.listFiles()
            .orEmpty()
            .filter { it.name.endsWith(".kt") && it.name != "PhotoResultScreen.kt" }
            .filter { pattern.containsMatchIn(it.readText()) }
            .map { it.name }
        assertTrue(
            "MathText 字号只允许在 PhotoResultScreen.kt 定义；这些文件给 MathText 自定义了字号: $offenders",
            offenders.isEmpty(),
        )
        // 反向钉住（A42-#1）：那一处确实存在，且引用的是 SolveTypography 的四档 token，
        // 而不是字面量 sp——「页面级不得出现字面量 sp」。
        val result = read("PhotoResultScreen.kt")
        for (token in listOf("question", "step", "thinking", "answer")) {
            assertTrue(
                "SolveResultSections 应引用 SolveTypography.$token，不要写回字面量",
                result.contains("fontSize = SolveTypography.$token"),
            )
        }
        val literalInResult = Regex("""fontSize\s*=\s*[0-9]+(\.[0-9]+)?\.sp""")
        assertFalse(
            "PhotoResultScreen.kt 的 MathText 字号必须是 token 引用，不得出现字面量 sp（A42-#1 收口）",
            literalInResult.containsMatchIn(result),
        )
    }

    /** A42-#1：MathText 的默认字号与公式系数也只认 token，不留第二套事实源。 */
    @Test
    fun mathTextDefaultsComeFromTokens() {
        val mathText = File(
            File("..").canonicalFile,
            "feature-photosolve/src/main/java/com/openapi/feature/photosolve/markdown/MathText.kt",
        ).readText()
        assertTrue(
            "MathText 默认字号应引用 SolveTypography.step（默认路径也要受 token 控制）",
            mathText.contains("fontSize: TextUnit = SolveTypography.step"),
        )
        assertTrue(
            "公式系数应引用 SolveTypography.LATEX_SIZE_FACTOR，不在 MathText 里手写",
            mathText.contains("SolveTypography.LATEX_SIZE_FACTOR"),
        )
    }

    /** A42-#1：token 的取值钉死——改字号必须过这条测试（写注释说明为什么改）。 */
    @Test
    fun solveTypographyTokensPinned() {
        assertEquals(16.sp, SolveTypography.question)
        assertEquals(15.sp, SolveTypography.step)
        assertEquals(14.sp, SolveTypography.thinking)
        assertEquals(16.sp, SolveTypography.answer)
        assertEquals(1.05f, SolveTypography.LATEX_SIZE_FACTOR, 1e-9f)
    }

    /** 拍照解题页面不许用 headline 档（用户反馈「title 太大」：首页 28sp → titleLarge 22sp）。 */
    @Test
    fun photosolveScreensAvoidHeadlineStyles() {
        // 只匹配真实的样式引用，注释里的说明文字不算
        val pattern = Regex("""MaterialTheme\.typography\.headline(Large|Medium|Small)""")
        val offenders = uiDir.listFiles()
            .orEmpty()
            .filter { it.name.endsWith(".kt") }
            .filter { pattern.containsMatchIn(it.readText()) }
            .map { it.name }
        assertTrue(
            "拍照解题界面不应使用 headline 大标题（应用内页用 title/body 档）: $offenders",
            offenders.isEmpty(),
        )
    }
}
