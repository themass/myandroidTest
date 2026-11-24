package com.openapi.feature.video.screening

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 列表工具条计数的纯函数钉子 + 「翻页列表不再自带内嵌返回行」的结构钉子。
 *
 * 用户点名（2026-11-24）：所有翻页列表（电影/图集/音频/文章）要共用一个基类形状——
 * 返回箭头 + title 的 toolbar 吸附在顶部（滚动容器外），翻很多页后退得出去；
 * 列表离开顶部后出现浮动回顶按钮。返回行如果还留在 LazyColumn 首 item 里就是回归。
 */
class FeedListShellTest {

    @Test
    fun `工具条计数：取数中一个文案，取回后 N 加量词`() {
        assertEquals("正在取数据…", feedToolbarDetail(true, 0, "篇"))
        assertEquals("60 篇", feedToolbarDetail(false, 60, "篇"))
        assertEquals("7 个频道", feedToolbarDetail(false, 7, "个频道"))
    }

    @Test
    fun `翻页列表层都走 FeedListShell，下拉刷新只在壳里出现一次`() {
        val dir = File("src/main/java/com/openapi/feature/video/screening")
        for (name in listOf("ScreeningText.kt", "ScreeningSound.kt", "ScreeningGallery.kt", "ScreeningActivity.kt")) {
            val text = File(dir, name).readText()
            assertFalse("$name 列表层不该自己包 PullToRefreshBox（壳里已有）",
                text.contains("PullToRefreshBox"))
        }
        // 壳自己那份刷新在 ScreeningFeedUi 里，只有一处。
        val shell = File(dir, "ScreeningFeedUi.kt").readText()
        assertEquals("PullToRefreshBox 只该出现在 FeedListShell 一处",
            1, Regex("""PullToRefreshBox\(""").findAll(shell).count())
    }

    @Test
    fun `列表层不再在列表首 item 里画返回行`() {
        val dir = File("src/main/java/com/openapi/feature/video/screening")
        // 返回箭头改由壳的 toolbar 提供（onBack 参数）；列表层里再出现内嵌返回按钮就是回退到旧写法。
        // 正文/阅览这类非列表页的返回按钮不在名单里（ScreeningText 的 reader、Gallery 的 Lightbox）。
        val listLayers = mapOf(
            "ScreeningText.kt" to listOf("fun TextHubPicker", "fun TextArticleList"),
            "ScreeningSound.kt" to listOf("fun SoundHubPicker", "fun SoundTrackHub"),
            "ScreeningGallery.kt" to listOf("fun AlbumCovers", "fun AlbumContents"),
            "ScreeningActivity.kt" to listOf("fun Library("),
        )
        for ((name, fns) in listLayers) {
            val text = File(dir, name).readText()
            for (fn in fns) {
                val body = bodyOf(text, fn)
                assertTrue("$fn 必须走 FeedListShell", body.contains("FeedListShell("))
                assertFalse("$fn 还残留内嵌返回行",
                    Regex("""IconButton\(onClick = (onBack|back|onExit)\)""").containsMatchIn(body))
            }
        }
    }

    /** 取函数体：从签名处到下一个同缩进的顶层声明为止（够用即可，不求解析器）。 */
    private fun bodyOf(text: String, signature: String): String {
        val start = text.indexOf(signature)
        assertTrue("找不到 $signature", start >= 0)
        val next = Regex("\n(internal |private |@)\\S|\\nobject |\\nclass ").find(text, start + signature.length)
        return text.substring(start, next?.range?.first ?: text.length)
    }
}
