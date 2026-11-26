package com.openapi.feature.video.screening

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A40「进入声音馆曲目列表是空的」（用户 2026-11-26）。
 *
 * 取证：`val tracks = remember(tracksPager.items) { … }` —— [FeedPager.items]
 * 是同一个 `mutableStateListOf` 实例，取数/翻页都在原地增删（快照系统靠内容
 * 失效，不靠实例换）。`remember` 的 key 按 equals/identity 比，永远相等，
 * 派生表第一次算完就再也不重算——列表空屏。
 *
 * 这里钉两层：① FeedPager 的 items 实例在 load 前后必须不换（钉死「key 按
 * 实例比必然失效不了」这个前提）；② ScreeningSound 里不得再出现
 * `remember(tracksPager.items)`。
 */
class SoundTracksDeriveTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `FeedPager items 是原地改的同一个列表实例，remember 按它做 key 必然不刷新`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val pager = FeedPager(CoroutineScope(Dispatchers.Unconfined)) { _, _ ->
            ScreeningFeedStore.Page(
                items = (0 until 3).mapNotNull {
                    remoteEntry(
                        id = it + 1L, index = it, title = "t$it", description = "",
                        imageUrl = null, rate = 0f, actionUrl = "https://x/$it",
                        needsLazyUrl = false, dataType = 0,
                    )
                },
                nextStart = null,
                hasMore = false,
            )
        }
        val before = pager.items
        pager.load()
        advanceUntilIdle()
        assertSame("items 必须仍是同一实例（原地增删是 FeedPager 的既有契约）", before, pager.items)
        assertFalse("取数后实例内容应已填充", before.isEmpty())
    }

    @Test
    fun `曲目派生不得用 remember 包 pager 的 items 做 key`() {
        val src = File("src/main/java/com/openapi/feature/video/screening/ScreeningSound.kt").readText()
        assertFalse(
            "items 是同实例原地改，remember 按它做 key 永不重算（A40 空列表根因）",
            Regex("""remember\(\s*tracksPager\.items\s*\)""").containsMatchIn(src),
        )
        // 派生仍然要在：就地 mapIndexedNotNull，别删成别的形态。
        assertTrue(src.contains("tracksPager.items.mapIndexedNotNull"))
    }
}
