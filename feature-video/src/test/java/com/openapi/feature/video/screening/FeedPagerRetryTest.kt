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
        val pager = FeedPager(this) { start, _ ->
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
        val pager = FeedPager(this) { start, _ ->
            starts += start
            throw IOException("连不上")
        }
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        pager.load()
        advanceUntilIdle()

        assertEquals(2, starts.size)
        assertTrue(pager.error?.contains("内容没加载出来") == true)
        assertFalse(pager.loading)
    }

    @Test
    fun `翻页失败不自动重试，已加载的列表原样保留`() = runTest {
        val starts = mutableListOf<Int>()
        val pager = FeedPager(this) { start, _ ->
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
        assertTrue(pager.error?.contains("内容没加载出来") == true)
    }

    @Test
    fun `首屏失败后触底判定不得替首屏无限重拉`() = runTest {
        val starts = mutableListOf<Int>()
        val pager = FeedPager(this) { start, _ ->
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

    @Test
    fun `下拉刷新重取第一页，旧内容先留在屏上，成功后才换`() = runTest {
        val starts = mutableListOf<Int>()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val pager = FeedPager(this) { start, _ ->
            starts += start
            if (starts.size == 1) page(listOf(entry("旧一条")), nextStart = 10)
            else {
                // 挂到闸开为止：这样「刷新在路上」才真的可观察（Unconfined 会把不挂起的取数当场跑完）
                gate.await()
                page(listOf(entry("新一条"), entry("新二条")), nextStart = 20)
            }
        }
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        pager.load()
        advanceUntilIdle()
        assertEquals(1, pager.items.size)

        pager.refresh()
        // 刷新在路上：旧列表不能先清空（清空就是「一进列表页先白一遍」的观感来源）
        assertEquals(1, pager.items.size)
        assertEquals("旧一条", pager.items.first().title)
        assertTrue(pager.loading)
        assertTrue(pager.refreshing)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("新一条", "新二条"), pager.items.map { it.title })
        assertEquals(2, starts.size)
        assertFalse(pager.refreshing)
        // 刷新拿回的新页带着新的翻页游标：翻下一页从新页尾续，不该续旧游标
        assertTrue(pager.hasMore)
    }

    @Test
    fun `刷新失败不清已加载的列表，只报错误`() = runTest {
        val starts = mutableListOf<Int>()
        val pager = FeedPager(this) { start, _ ->
            starts += start
            if (starts.size == 1) page(listOf(entry("旧一条")), nextStart = 10)
            else throw IOException("连不上")
        }
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        pager.load()
        advanceUntilIdle()

        pager.refresh()
        advanceUntilIdle()

        // 刷新失败不自动补枪（列表已在屏上），也绝不把已有内容清掉
        assertEquals(2, starts.size)
        assertEquals(1, pager.items.size)
        assertEquals("旧一条", pager.items.first().title)
        assertTrue(pager.error?.contains("内容没加载出来") == true)
    }

    @Test
    fun `返回列表不重新取数：load 只发一次，重复调不再打接口`() = runTest {
        val starts = mutableListOf<Int>()
        val pager = FeedPager(this) { start, _ ->
            starts += start
            page(listOf(entry("一条")), nextStart = 10)
        }
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        pager.load()
        advanceUntilIdle()
        // 页面组合被转场拆掉又重建（返回列表页）时会再调一次 load()：
        // 首屏已经取过，这一声必须哑掉——用户点名的「不是进入列表页都要刷新」
        pager.load()
        pager.load()
        advanceUntilIdle()

        assertEquals(1, starts.size)
    }
}
