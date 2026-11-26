package com.openapi.app.architecture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 拍照解题排版守卫（用户反馈「字体太大」的根因守卫，5ace4ae3）。
 *
 * 守的是**结构**而不是截图数字（Robolectric 字体度量与真机差得多，见同目录渲染测试
 * 先例）：字号漂移是这次问题的根因——结果页排版修复（3b01deae）没覆盖历史详情页，
 * 详情页自己那套 20/16/20sp 一直留在线上。现在两处共用 `SolveResultSections`，
 * 字号只允许在 `PhotoResultScreen.kt` 一处定义；谁再给某个页面单独开字号或
 * 用营销页大标题，这里直接红。
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

    /** MathText 的字号只允许在 PhotoResultScreen 的 SolveResultSections 里定义。 */
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
        // 反向也钉住：那一处确实存在，且就是修后的四档
        val result = read("PhotoResultScreen.kt")
        for (size in listOf("17.sp", "15.sp", "14.sp", "18.sp")) {
            assertTrue("SolveResultSections 应定义 $size 一档", result.contains("fontSize = $size"))
        }
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
