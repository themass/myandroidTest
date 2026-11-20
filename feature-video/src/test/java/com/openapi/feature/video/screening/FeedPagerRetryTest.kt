package com.openapi.feature.video.screening

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * FeedPager 的取数失败策略：首屏失败自动重试一次，还不行才把错误条摆出来。
 *
 * 一次网络抖动不该逼用户去找「重试」按钮；但也不允许无限重试打服务端。
 * 翻页失败不自动重试——列表已经在屏上，失败了保留已加载的部分、说清楚就行。
 */
class FeedPagerRetryTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun page(items: List<RemoteEntry> = listOf(entry("一条")), nextStart: Int? = null) =
        ScreeningFeedStore.Page(items, nextStart, hasMore = nextStart != null)

    private fun entry(title: String) =
        remoteEntry(1L, 0, title, null, null, 0f, "video://x", needsLazyUrl = false, dataType = 0)!!

    @Test
    fun `首屏失败一次后自动重试，成功就不显示错误`() = runTest {
        val starts = mutableListOf<Int>()
        val pager = FeedPager(this) { start ->
            starts += start
            if (starts.size == 1) throw IOException("连不上") else page()
        }
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        pager.load()
        // 第一次已经失败，但自动重试的退避还没跑完：此时只该是「加载中」，不该是错误
        assertEquals(1, starts.size)
        assertTrue(pager.loading)
        assertNull(pager.error)

        advanceUntilIdle()
        assertEquals(2, starts.size)
        assertEquals(1, pager.items.size)
        assertNull(pager.error)
        assertFalse(pager.loading)
    }

    @Test
    fun `首屏连着两次都失败才报错，不会无限重试`() = runTest {
        val starts = mutableListOf<Int>()
        val pager = FeedPager(this) { start ->
            starts += start
            throw IOException("连不上")
        }
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        pager.load()
        advanceUntilIdle()

        assertEquals(2, starts.size)
        assertTrue(pager.error?.contains("没取到服务端数据") == true)
        assertFalse(pager.loading)
    }

    @Test
    fun `翻页失败不自动重试，已加载的列表原样保留`() = runTest {
        val starts = mutableListOf<Int>()
        val pager = FeedPager(this) { start ->
            starts += start
            if (start == 0) page(nextStart = 10) else throw IOException("连不上")
        }
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        pager.load()
        advanceUntilIdle()
        assertEquals(1, pager.items.size)

        pager.loadMore()
        advanceUntilIdle()

        assertEquals(2, starts.size)
        assertEquals(1, pager.items.size)
        assertTrue(pager.error?.contains("没取到服务端数据") == true)
    }

    @Test
    fun `首屏失败后触底判定不得替首屏无限重拉`() = runTest {
        val starts = mutableListOf<Int>()
        val pager = FeedPager(this) { start ->
            starts += start
            throw IOException("连不上")
        }
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        pager.load()
        advanceUntilIdle()
        // 首屏含自动补一次，共两次——到此为止
        assertEquals(2, starts.size)

        // 页面里的触底判定在空列表上恒真，会反复调 loadMore()；这里模拟连按 5 次
        repeat(5) { pager.loadMore() }
        advanceUntilIdle()

        // 首屏只归 load()/reload() 管：loadMore 一枪都不该补，否则就是无限拉取刷屏
        assertEquals(2, starts.size)
    }
}
