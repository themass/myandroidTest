package com.openapi.feature.video.screening

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「上滑加载更多」的触底判定（用户 2026-11-26 点名：所有子页上滑加载更多失效）。
 *
 * 根因：判定拿 LazyColumn 的 item 下标去比 pager 的数据条数——网格页把 N 条数据
 * chunked 成 N/3 行、外面还有头卡 item，下标永远够不到 `items.size - k`，loadMore()
 * 一次都不发。修法把判定收成一个纯函数：只认「列表不能继续向前滚」，不认条数算术。
 * 防空转：上次 append 取回后条数没涨，就不再对同一位置重复发请求。
 */
class FeedLoadMoreTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun page(items: List<RemoteEntry>, nextStart: Int?) =
        ScreeningFeedStore.Page(items, nextStart, hasMore = nextStart != null)

    private fun entry(tag: String) =
        remoteEntry(1L, 0, tag, null, null, 0f, "video://x", needsLazyUrl = false, dataType = 0)!!

    // ---- 纯函数：触发条件 ----

    @Test
    fun `到底（不能继续滚）且有下一页且不在取数＝该发 loadMore`() {
        assertTrue(feedShouldLoadMore(lastVisibleIndex = 2, totalItemsCount = 3, canScrollForward = false, hasMore = true, loading = false))
    }

    @Test
    fun `还能继续向前滚＝不发（提前预取不是这条的职责）`() {
        assertFalse(feedShouldLoadMore(2, 20, canScrollForward = true, hasMore = true, loading = false))
    }

    @Test
    fun `服务端说没有下一页＝不发，无论滚到哪`() {
        assertFalse(feedShouldLoadMore(2, 3, canScrollForward = false, hasMore = false, loading = false))
    }

    @Test
    fun `正在取数＝不发，判定重入不叠请求`() {
        assertFalse(feedShouldLoadMore(2, 3, canScrollForward = false, hasMore = true, loading = true))
    }

    @Test
    fun `网格页 chunked 成行后照样触发：下标2 总行3 与数据条数无关`() {
        // 现场复刻 Library 海报视图：30 条数据 chunked(3) 成 10 行 + 头卡 + 页脚，
        // 旧算术 2 >= 30-4 恒假；新判定只看「到底」。
        assertTrue(feedShouldLoadMore(lastVisibleIndex = 11, totalItemsCount = 12, canScrollForward = false, hasMore = true, loading = false))
    }

    // ---- 防空转：append 取回后条数没涨就不再发 ----

    @Test
    fun `append 取回后条数没涨，loadMore 不再发第二枪`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var calls = 0
        val pager = FeedPager(this) { start, _ ->
            calls++
            // 服务端回重复页：翻到 start=30 再要，回的是空页但 nextStart 原地不动——
            // append 后 items.size 不变，「到底」判定会反复重发同一枪，这就是空转。
            if (start == 0) page(List(5) { entry("t$it") }, nextStart = 30)
            else page(emptyList(), nextStart = 30)
        }
        pager.load()
        advanceUntilIdle()
        assertEquals(5, pager.items.size)

        pager.loadMore() // 第 1 枪 append：size 没涨
        advanceUntilIdle()
        assertEquals(2, calls)

        pager.loadMore() // 判定层重入：该被防空转闸挡下，不再打接口
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `append 真的涨了，之后仍照常发下一枪`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var calls = 0
        val pager = FeedPager(this) { start, _ ->
            calls++
            page(List(5) { entry("$start-$it") }, nextStart = start + 5)
        }
        pager.load()
        advanceUntilIdle()

        pager.loadMore()
        advanceUntilIdle()
        assertEquals(10, pager.items.size) // 涨了 → 不封闸

        pager.loadMore()
        advanceUntilIdle()
        assertEquals(3, calls)
    }

    @Test
    fun `reload 与 search 清空防空转闸，重试后翻页照常`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var calls = 0
        val pager = FeedPager(this) { start, _ ->
            calls++
            // 重复页：start=30 回空页但 nextStart 不动——append 后条数没涨，封闸。
            if (start == 0) page(List(5) { entry("t$it") }, nextStart = 30)
            else page(emptyList(), nextStart = 30)
        }
        pager.load()
        advanceUntilIdle()
        pager.loadMore() // append：size 没涨，封闸
        advanceUntilIdle()
        pager.loadMore()
        advanceUntilIdle()
        assertEquals(2, calls)

        pager.reload()
        advanceUntilIdle()
        assertEquals(3, calls)
        pager.loadMore()
        advanceUntilIdle()
        assertEquals(4, calls) // 闸已清

        pager.search("词")
        advanceUntilIdle()
        assertEquals(5, calls)
        pager.loadMore()
        advanceUntilIdle()
        assertEquals(6, calls) // 闸已清
    }

    // ---- 结构钉子：八个触发点统一走一个判定 ----

    @Test
    fun `screening 目录不再出现下标比数据条数的旧触底算术`() {
        val dir = File("src/main/java/com/openapi/feature/video/screening")
        for (name in listOf("ScreeningActivity.kt", "ScreeningGallery.kt", "ScreeningSound.kt", "ScreeningText.kt")) {
            val text = File(dir, name).readText()
            val stale = Regex("""index\s*>=\s*\w+\.items\.size\s*-""").findAll(text).toList()
            assertTrue("$name 仍有旧触底算术（item 下标比数据条数）：$stale", stale.isEmpty())
        }
    }

    @Test
    fun `每个列表层都用 feedShouldLoadMore 收口`() {
        val dir = File("src/main/java/com/openapi/feature/video/screening")
        val expected = mapOf(
            "ScreeningActivity.kt" to 2, // 频道 tab + 片单页
            "ScreeningGallery.kt" to 2,  // 封面瀑布流 + 册内网格
            "ScreeningSound.kt" to 2,    // 频道 hub + 曲目列表
            "ScreeningText.kt" to 2,     // 频道 hub + 文章列表
        )
        for ((name, count) in expected) {
            val text = File(dir, name).readText()
            val hits = Regex("""feedShouldLoadMore\(""").findAll(text).count()
            assertTrue("$name 应有 $count 处 feedShouldLoadMore，实际 $hits", hits >= count)
        }
    }
}
