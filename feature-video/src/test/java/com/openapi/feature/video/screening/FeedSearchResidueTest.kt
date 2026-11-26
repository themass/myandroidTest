package com.openapi.feature.video.screening

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索残余问题（用户 2026-11-26 点名；入口/翻页带词已在 f74e9003 修好，这里钉剩下四条）：
 * a) 图集走到尾续（tailItemUrl 置位）后新搜索被静默丢词——带词必须走回 albumImages；
 * b) 提交搜索后列表不回顶（只有清空词才回顶）；
 * c) 0 结果空态文案是列表口径——搜索中要说「没有找到「X」，换个词试试」；
 * d) keyword 拼 URL 前必须过 HttpUtils.encoderParam（中文/&/= 原样进 URL 会打坏请求）。
 */
class FeedSearchResidueTest {

    // ---- a) 尾续 vs 搜索的路由 ----

    @Test
    fun `图集取数路由：无词且已尾续走 item-page，任何非空词都走回 albumImages`() {
        assertTrue(albumImagesUseTail(tailItemUrl = "video://shot", keyword = ""))
        assertFalse(albumImagesUseTail(tailItemUrl = "video://shot", keyword = "词"))
        assertFalse(albumImagesUseTail(tailItemUrl = null, keyword = ""))
        assertFalse(albumImagesUseTail(tailItemUrl = null, keyword = "词"))
    }

    // ---- c) 空态文案按搜索态分支 ----

    @Test
    fun `空列表文案：未搜索保留列表口径`() {
        assertEquals(
            "这个频道服务端还没上片单。",
            feedEmptyCopy(keyword = "", fallback = "这个频道服务端还没上片单。"),
        )
        assertEquals(
            "这个频道还没有文章。",
            feedEmptyCopy(keyword = "   ", fallback = "这个频道还没有文章。"),
        )
    }

    @Test
    fun `空列表文案：搜索中给「没有找到「X」换个词」`() {
        assertEquals("没有找到「星际」，换个词试试。",
            feedEmptyCopy(keyword = "星际", fallback = "这个频道服务端还没上片单。"))
        assertEquals("没有找到「音乐」，换个词试试。",
            feedEmptyCopy(keyword = "音乐", fallback = "这个频道还没有音频。"))
    }

    // ---- d) keyword 进 URL 前编码 ----

    @Test
    fun `keyword 过 encoderParam：中文与保留字符不会原样进 URL`() {
        assertEquals("%E6%98%9F%E9%99%85", screeningKeywordParam("星际"))
        assertEquals("a%26b%3Dc+d", screeningKeywordParam("a&b=c d"))
        assertEquals("", screeningKeywordParam(""))
        assertEquals("", screeningKeywordParam("   "))
    }

    // ---- 结构钉子：b/c/d 接在真实页面上 ----

    @Test
    fun `FeedListShell 提交任何搜索都回顶，不再只在清空时回顶`() {
        val shell = File("src/main/java/com/openapi/feature/video/screening/ScreeningFeedUi.kt").readText()
        assertFalse("清空词才回顶的旧写法还在",
            Regex("""if \(q\.isEmpty\(\)\) scrollScope""").containsMatchIn(shell))
        val onSubmit = Regex("""onSubmit = \{[\s\S]{0,400}?onClear""").find(shell)
        assertTrue("onSubmit 里必须无条件 animateScrollToItem(0)",
            onSubmit != null && onSubmit.value.contains("animateScrollToItem(0)"))
    }

    @Test
    fun `四个可搜索列表的空态都走 feedEmptyCopy(keyword, 列表口径)`() {
        val dir = File("src/main/java/com/openapi/feature/video/screening")
        val expected = mapOf(
            "ScreeningActivity.kt" to 1, // Library 片单空态
            "ScreeningText.kt" to 1,     // 文章列表空态
            "ScreeningSound.kt" to 1,    // 曲目列表空态
            "ScreeningGallery.kt" to 1,  // 册内图片空态
        )
        for ((name, count) in expected) {
            val text = File(dir, name).readText()
            val hits = Regex("""feedEmptyCopy\(""").findAll(text).count()
            assertTrue("$name 应有 $count 处 feedEmptyCopy，实际 $hits", hits >= count)
        }
    }

    @Test
    fun `store 的 keyword 参数全部过 screeningKeywordParam`() {
        val store = File("src/main/java/com/openapi/feature/video/screening/ScreeningFeedStore.kt").readText()
        // 带 keyword 的六个取数接口：videos/videoUsers/videoUserItems/textItemsPage/soundItemsPage/albumImages
        val hits = Regex("""screeningKeywordParam\(""").findAll(store).count()
        assertTrue("store 里至少 6 处 keyword 要过编码，实际 $hits", hits >= 6)
    }
}
