package com.openapi.feature.video.screening

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * [FeedPager] 的失败处理。
 *
 * 钉的是真机上出现过的风暴：内页列表为空时，「最后一个可见项」恒 ≥ size-4，
 * snapshotFlow 会一直再触发 loadMore；首屏失败后如果不把取数闸门关掉，
 * 一次失败会被自动重试成几百次请求（logcat 里 200ms 一条 items.json 就是它）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedPagerTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private val scope get() = CoroutineScope(mainDispatcher)

    @Before fun setMain() = Dispatchers.setMain(mainDispatcher)
    @After fun clearMain() = Dispatchers.resetMain()

    @Test
    fun `首屏失败后 loadMore 不再自动重试，只剩 reload 一条路`() {
        var calls = 0
        val pager = FeedPager(scope, fetch = { _ -> calls++; error("服务端报错") })
        pager.load()
        assertEquals("首屏失败只发一次请求", 1, calls)
        // 空列表下页面会按「快到底」反复回调 loadMore：调多少次都不该再发请求
        repeat(50) { pager.loadMore() }
        assertEquals(1, calls)
        // 唯一能重新发起的是重试按钮
        pager.reload()
        assertEquals(2, calls)
    }

    @Test
    fun `翻页失败保留已加载内容`() {
        var calls = 0
        val entry = remoteEntry(1L, 0, "片", null, null, 0f, "video://x", false, 0)!!
        val pager = FeedPager(scope, fetch = { start ->
            calls++
            if (start == 0) ScreeningFeedStore.Page(listOf(entry), 1, true) else error("翻页炸了")
        })
        pager.load()
        assertEquals(1, pager.items.size)
        pager.loadMore()
        assertEquals(2, calls)
        assertEquals("翻页失败保留第一页", 1, pager.items.size)
        // 翻页失败不禁用闸门（页面只在真的滑到底时才再调 loadMore，不会自己循环），
        // 这里只钉「不清空、不吞错」。
        assertEquals("条目还是那条", "srv:1", pager.items.single().key)
    }
}
