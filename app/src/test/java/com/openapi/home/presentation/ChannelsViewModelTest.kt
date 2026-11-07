package com.openapi.home.presentation

import android.content.Context
import com.openapi.home.HomeDependencies
import com.openapi.home.domain.Channel
import com.openapi.home.domain.ChannelCategory
import com.openapi.home.domain.ChannelPage
import com.openapi.home.domain.HomeEntries
import com.openapi.home.domain.ProfileAction
import com.openapi.home.domain.ProfileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ChannelsViewModelTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var entries: FakeEntries

    @Before
    fun setUp() {
        // viewModelScope 在 Main 上启动协程；JVM 里没有真正的主线程循环，换成立即执行的调度器
        Dispatchers.setMain(Dispatchers.Unconfined)
        entries = FakeEntries()
        HomeDependencies.install(entries)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun resume_loadsTheFirstCategory() {
        val vm = ChannelsViewModel()
        vm.onResume(context)
        val s = awaitState(vm) { it.items.isNotEmpty() }
        assertEquals("a", s.selectedId)
        assertEquals(listOf("a-0", "a-1"), s.items.map { it.title })
        assertFalse(s.loading)
        assertFalse(s.error)
    }

    @Test
    fun resume_withContentAlreadyLoaded_doesNotReload() {
        val vm = ChannelsViewModel()
        vm.onResume(context)
        awaitState(vm) { it.items.isNotEmpty() }
        vm.onResume(context)
        vm.onResume(context)
        assertEquals(1, entries.requests.size)
    }

    @Test
    fun loadMore_usesTheStartTheServerReturned_notTheItemCount() {
        val vm = ChannelsViewModel()
        vm.onResume(context)
        awaitState(vm) { it.items.isNotEmpty() }
        vm.loadMore()
        val s = awaitState(vm) { it.items.size == 4 }
        // 第一页有 2 项，但服务端说下一页从 7 开始
        assertEquals(listOf("a" to 0, "a" to 7), entries.requests)
        assertEquals(listOf("a-0", "a-1", "a-7", "a-8"), s.items.map { it.title })
        assertFalse(s.hasMore)
    }

    @Test
    fun loadMore_stopsWhenThereIsNoMore() {
        val vm = ChannelsViewModel()
        vm.onResume(context)
        awaitState(vm) { it.items.isNotEmpty() }
        vm.loadMore()
        awaitState(vm) { it.items.size == 4 }
        vm.loadMore()
        assertEquals(2, entries.requests.size)
    }

    @Test
    fun switchingCategory_ignoresTheLateResponseOfTheOldOne() {
        val gate = CountDownLatch(1)
        entries.gates["a"] = gate
        val vm = ChannelsViewModel()
        vm.onResume(context)               // 分类 a 的请求被卡住
        awaitRequests(1)
        vm.select("b")                     // 用户切到 b
        val onB = awaitState(vm) { it.items.isNotEmpty() }
        assertEquals(listOf("b-0", "b-1"), onB.items.map { it.title })
        gate.countDown()                   // a 的响应现在才回来
        Thread.sleep(300)
        val s = vm.state.value
        assertEquals("b", s.selectedId)
        assertEquals(listOf("b-0", "b-1"), s.items.map { it.title })
    }

    @Test
    fun failedFirstLoad_showsErrorAndRetryRecovers() {
        entries.failing = true
        val vm = ChannelsViewModel()
        vm.onResume(context)
        val failed = awaitState(vm) { it.error }
        assertTrue(failed.items.isEmpty())
        assertFalse(failed.loading)

        entries.failing = false
        vm.retry()
        val ok = awaitState(vm) { it.items.isNotEmpty() }
        assertFalse(ok.error)
    }

    @Test
    fun failedLoadMore_keepsTheItemsAndIsNotAnError() {
        val vm = ChannelsViewModel()
        vm.onResume(context)
        awaitState(vm) { it.items.isNotEmpty() }
        entries.failing = true
        vm.loadMore()
        val s = awaitState(vm) { !it.loadingMore }
        assertEquals(2, s.items.size)
        assertFalse(s.error)
    }

    @Test
    fun categoryRemoved_fallsBackToTheFirstOne() {
        val vm = ChannelsViewModel()
        vm.onResume(context)
        vm.select("b")
        awaitState(vm) { it.selectedId == "b" && it.items.isNotEmpty() }
        entries.categories = listOf(ChannelCategory("a", 0))   // 设置里关掉了 b
        vm.onResume(context)
        val s = awaitState(vm) { it.selectedId == "a" && it.items.isNotEmpty() }
        assertEquals(listOf("a-0", "a-1"), s.items.map { it.title })
    }

    private fun awaitState(vm: ChannelsViewModel, predicate: (ChannelsUiState) -> Boolean): ChannelsUiState {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val s = vm.state.value
            if (predicate(s)) return s
            Thread.sleep(10)
        }
        throw AssertionError("等待状态超时，当前：${vm.state.value}")
    }

    private fun awaitRequests(count: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (entries.requests.size >= count) return
            Thread.sleep(10)
        }
        throw AssertionError("等待请求超时，已有：${entries.requests}")
    }

    /** 每个分类有两页：第一页 2 项、下一页从 7 开始；可让某个分类的请求卡住，或让所有请求失败。 */
    private class FakeEntries : HomeEntries {
        @Volatile var categories = listOf(ChannelCategory("a", 0), ChannelCategory("b", 0))
        @Volatile var failing = false
        val gates = HashMap<String, CountDownLatch>()
        private val log = java.util.Collections.synchronizedList(ArrayList<Pair<String, Int>>())
        val requests: List<Pair<String, Int>> get() = synchronized(log) { ArrayList(log) }

        override fun categories(context: Context) = categories

        override fun loadChannels(categoryId: String, start: Int): ChannelPage {
            log.add(categoryId to start)
            gates[categoryId]?.await(5, TimeUnit.SECONDS)
            if (failing) throw java.io.IOException("boom")
            return if (start == 0) {
                ChannelPage(listOf(channel("$categoryId-0"), channel("$categoryId-1")), nextStart = 7, hasMore = true)
            } else {
                ChannelPage(listOf(channel("$categoryId-7"), channel("$categoryId-8")), nextStart = 0, hasMore = false)
            }
        }

        private fun channel(title: String) = Channel(title, "", null, 1f, title)

        override fun openChannel(context: Context, channel: Channel) = Unit
        override fun profile(context: Context): ProfileInfo = error("not used")
        override fun perform(context: Context, action: ProfileAction) = Unit
        override fun onHomeStarted(context: Context) = Unit
    }
}
