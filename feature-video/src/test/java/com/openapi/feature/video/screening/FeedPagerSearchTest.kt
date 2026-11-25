package com.openapi.feature.video.screening

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FeedPager 的搜索语义（用户点名 2026-11-25：「列表页的搜索功能不见了」）。
 *
 * 对齐旧列表基类 BasePullLoadbleFragment：提交才刷（打字不打接口）、刷回首屏、
 * 翻页继续带同一个词；清空＝取消搜索回到普通列表。四馆列表页只差接口，语义一份。
 */
class FeedPagerSearchTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun page(items: List<RemoteEntry> = listOf(entry("一条")), nextStart: Int? = null) =
        ScreeningFeedStore.Page(items, nextStart, hasMore = nextStart != null)

    private fun entry(title: String) =
        remoteEntry(1L, 0, title, null, null, 0f, "video://x", needsLazyUrl = false, dataType = 0)!!

    @Test
    fun `search 记住关键词、重取首屏，取数带当前词`() = runTest {
        val calls = mutableListOf<Pair<Int, String>>()
        val pager = FeedPager(this) { start, keyword ->
            calls += start to keyword
            page(listOf(entry("搜到的")), nextStart = 30)
        }
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        pager.load()
        advanceUntilIdle()
        assertEquals(0 to "", calls.last())

        pager.search(" 星际 ")
        advanceUntilIdle()
        // 提交即刷：重取第一页，关键词去空白后带上
        assertEquals(0 to "星际", calls.last())
        assertEquals("星际", pager.keyword)
        // 旧结果被搜索结果替换（append=false）
        assertEquals(listOf("搜到的"), pager.items.map { it.title })
    }

    @Test
    fun `翻页继续带当前搜索词`() = runTest {
        val calls = mutableListOf<Pair<Int, String>>()
        val pager = FeedPager(this) { start, keyword ->
            calls += start to keyword
            page(nextStart = if (start == 0) 30 else null)
        }
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        pager.search("音乐")
        advanceUntilIdle()
        pager.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(0 to "音乐", 30 to "音乐"), calls)
    }

    @Test
    fun `清空搜索词＝回到普通列表：重新取第一页不带词`() = runTest {
        val calls = mutableListOf<Pair<Int, String>>()
        val pager = FeedPager(this) { start, keyword ->
            calls += start to keyword
            page(nextStart = if (start == 0) 30 else null)
        }
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        pager.load()
        advanceUntilIdle()
        pager.search("词")
        advanceUntilIdle()
        pager.search("")
        advanceUntilIdle()

        assertEquals("", pager.keyword)
        assertEquals(0 to "", calls.last())
    }
}
