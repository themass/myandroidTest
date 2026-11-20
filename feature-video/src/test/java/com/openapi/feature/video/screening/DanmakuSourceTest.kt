package com.openapi.feature.video.screening

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 弹幕来源 + [DanmakuFeed] 的 JVM 单测。两边都不碰 Compose：
 * 来源是纯数据，Feed 的状态是普通快照，所以照 `DanmakuLaneTest` 的老路子直接跑。
 * 测试里一律用 `latencyMs = 0` 的假服务端，快且确定。
 */
class DanmakuSourceTest {

    private fun source(seed: List<TimedComment> = emptyList(), pageSize: Int = 200) =
        InMemoryDanmakuSource(TimelineKey.local("test"), seed, latencyMs = 0, pageSize = pageSize)

    private fun comment(at: Long, id: Int = at.toInt(), text: String = "t$id") = TimedComment(id, at, text)

    // ---------- 来源：本地样例 ----------

    @Test
    fun `样例源只返回落在窗口里的弹幕`() = runBlocking {
        val window = SampleDanmakuSource().loadWindow(1000, 3000, null)
        assertEquals(listOf(1400L, 1500L, 2600L), window.comments.map { it.atMs })
        assertNull("样例一页取完", window.nextCursor)
    }

    @Test
    fun `样例源发送的弹幕立刻出现在下一次拉取里`() = runBlocking {
        val sample = SampleDanmakuSource()
        val posted = sample.post("c1", 1234, "  发一条  ").getOrThrow()
        assertEquals("发送会去掉首尾空格", "发一条", posted.text)
        assertTrue(sample.loadWindow(0, 10_000, null).comments.any { it.text == "发一条" })
    }

    // ---------- 来源：假服务端 ----------

    @Test
    fun `服务端源按窗口过滤并排序`() = runBlocking {
        val seeded = source(listOf(comment(5000, 5), comment(100, 1), comment(9500, 9), comment(8800, 8)))
        val window = seeded.loadWindow(0, 10_000, null)
        assertEquals(listOf(100L, 5000L, 8800L, 9500L), window.comments.map { it.atMs })
        assertTrue("下一个窗口是空的", seeded.loadWindow(10_000, 20_000, null).comments.isEmpty())
    }

    @Test
    fun `服务端源一页装不下就给游标，取完为止`() = runBlocking {
        val seeded = source((1..5).map { comment(it * 1000L, it) }, pageSize = 2)
        val page1 = seeded.loadWindow(0, 10_000, null)
        assertEquals(listOf(1000L, 2000L), page1.comments.map { it.atMs })
        assertEquals("2", page1.nextCursor)
        // 游标语义由来源自己定，调用方把上一页给的 token 原样带回来即可
        assertEquals("4", seeded.loadWindow(0, 10_000, page1.nextCursor).nextCursor)
        val page3 = seeded.loadWindow(0, 10_000, "4")
        assertEquals(listOf(5000L), page3.comments.map { it.atMs })
        assertNull("最后一页没有下一页", page3.nextCursor)
    }

    @Test
    fun `重试同一个幂等 ID 不会重复上屏`() = runBlocking {
        val server = source()
        val first = server.post("abc", 500, "同一句").getOrThrow()
        val retry = server.post("abc", 500, "同一句").getOrThrow()
        assertEquals("两次 post 是服务端同一条", first.id, retry.id)
        assertEquals(1, server.storedSnapshot.size)
    }

    @Test
    fun `服务端源校验文本长度并拒绝空文本`() = runBlocking {
        val server = source()
        assertTrue(server.post("a", 0, "   ").isFailure)
        assertTrue(server.post("b", 0, "字".repeat(41)).isFailure)
        assertTrue(server.post("c", 0, "字".repeat(40)).isSuccess)
    }

    @Test
    fun `撤回后再次拉取不再返回，重复撤回报错`() = runBlocking {
        val server = source(listOf(comment(1000, 7)))
        assertTrue(server.retract(7).isSuccess)
        assertTrue(server.loadWindow(0, 2000, null).comments.isEmpty())
        assertTrue("同一条不能撤两次", server.retract(7).isFailure)
    }

    @Test
    fun `可注入的发送失败`() = runBlocking {
        val server = source()
        server.failPost = true
        assertTrue(server.post("a", 0, "够不着服务端").isFailure)
        server.failPost = false
        assertTrue(server.post("a", 0, "够不着服务端").isSuccess)
    }

    // ---------- DanmakuFeed ----------

    private class RecordingSource(
        override val timelineKey: TimelineKey = TimelineKey.local("feed"),
        private val failTimes: Int = 0,
    ) : TimedCommentSource {
        var loads = 0
        var posts = 0
        private var calls = 0
        val posted = ArrayList<Pair<String, String>>()

        override suspend fun loadWindow(startMs: Long, endMs: Long, cursor: String?): CommentWindow {
            loads++
            return CommentWindow(startMs, endMs, listOf(TimedComment(1000 + startMs.toInt(), startMs + 100, "窗口$startMs")))
        }

        override suspend fun post(clientMessageId: String, atMs: Long, text: String): Result<TimedComment> {
            posts++
            calls++
            posted.add(clientMessageId to text)
            if (calls <= failTimes) return Result.failure(IllegalStateException("boom"))
            return Result.success(TimedComment(500 + posts, atMs, text, author = "我"))
        }

        override suspend fun retract(serverId: Int): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun `发出的弹幕先以发送中回显，成功后换成已发送`() = runBlocking {
        var stateDuringPost: DanmakuState? = null
        lateinit var feed: DanmakuFeed
        val probe = object : TimedCommentSource {
            override val timelineKey = TimelineKey.local("probe")
            override suspend fun loadWindow(startMs: Long, endMs: Long, cursor: String?) = CommentWindow(startMs, endMs, emptyList())
            override suspend fun post(clientMessageId: String, atMs: Long, text: String): Result<TimedComment> {
                // post 还在挂起：回显必须已经贴上时间轴
                stateDuringPost = feed.state
                return Result.success(TimedComment(900, atMs, text, author = "我"))
            }
            override suspend fun retract(serverId: Int) = Result.success(Unit)
        }
        feed = DanmakuFeed(probe)
        val outgoing = feed.send(1234, "回显测试")

        val during = stateDuringPost!!
        val echo = during.outgoing.single()
        assertEquals(SendState.Sending, echo.state)
        assertTrue("等待服务端期间就在时间轴上", during.comments.any { it.text == "回显测试" })

        assertEquals(SendState.Sent, outgoing.state)
        val sent = feed.state.outgoing.single()
        assertNotNull("成功后有服务端 id", sent.serverId)
        assertTrue(feed.state.comments.any { it.id == sent.serverId && it.text == "回显测试" })
    }

    @Test
    fun `发送失败可重试，重试复用同一个幂等 ID`() = runBlocking {
        val source = RecordingSource(failTimes = 1)
        val feed = DanmakuFeed(source)
        val first = feed.send(2000, "重试我")
        assertEquals(SendState.Failed, first.state)
        assertEquals("失败原因显示出来", "boom", first.error)
        assertEquals("失败的那条仍然贴在时间轴上", "重试我", feed.state.comments.single().text)

        val retried = feed.retry(first.clientMessageId)!!
        assertEquals(SendState.Sent, retried.state)
        assertEquals("两次请求共用一个幂等 ID", 1, source.posted.map { it.first }.distinct().size)
        assertEquals(2, source.posts)
    }

    @Test
    fun `窗口缓存：同一窗口反复 sync 不重复请求`() = runBlocking {
        val source = RecordingSource()
        val feed = DanmakuFeed(source, windowMs = 8000)
        feed.sync(1000) // 取当前窗 [0,8000) 和预取窗 [8000,16000)
        val afterFirst = source.loads
        feed.sync(1200); feed.sync(7000)
        assertEquals("还在同一窗口里，一次都不该重新拉", afterFirst, source.loads)
        feed.sync(9000) // 进到第二窗：补预取 [16000,24000)
        assertEquals(afterFirst + 1, source.loads)
    }

    @Test
    fun `seek 清空窗口缓存，新位置重新排队`() = runBlocking {
        val source = RecordingSource()
        val feed = DanmakuFeed(source, windowMs = 8000)
        feed.sync(1000)
        assertTrue(feed.state.comments.isNotEmpty())
        feed.seek(30_000)
        assertTrue("seek 之后旧弹幕立即清屏", feed.state.comments.isEmpty())
        feed.sync(30_000)
        assertTrue("新位置的弹幕排上来了", feed.state.comments.any { it.atMs >= 30_000 })
    }

    @Test
    fun `在途回包回来之前 seek，旧窗口的数据不会贴回时间轴`() = runBlocking {
        val slow = object : TimedCommentSource {
            override val timelineKey = TimelineKey.local("slow")
            override suspend fun loadWindow(startMs: Long, endMs: Long, cursor: String?): CommentWindow {
                delay(30)
                return CommentWindow(startMs, endMs, listOf(TimedComment(startMs.toInt(), startMs, "旧窗口")))
            }
            override suspend fun post(clientMessageId: String, atMs: Long, text: String) =
                Result.failure<TimedComment>(UnsupportedOperationException())
            override suspend fun retract(serverId: Int) = Result.success(Unit)
        }
        val feed = DanmakuFeed(slow, windowMs = 8000)
        coroutineScope {
            val loading = launch { feed.sync(1000) }
            delay(5) // 让 loadWindow 挂起在 delay 里
            feed.seek(50_000)
            loading.join()
        }
        assertTrue("旧代回包被丢弃", feed.state.comments.isEmpty())
    }

    @Test
    fun `服务端确认后不重复画：回显和窗口数据按 id 去重`() = runBlocking {
        val server = source()
        val feed = DanmakuFeed(server, windowMs = 8_000)
        feed.sync(0)
        val sent = feed.send(500, "只画一遍")
        // 模拟下一次窗口刷新把服务端定稿的那条拉回来
        feed.seek(0)
        feed.sync(0)
        val matches = feed.state.comments.filter { it.text == "只画一遍" }
        assertEquals("只出现一条", listOf(sent.serverId), matches.map { it.id })
    }

    @Test
    fun `撤回后弹幕从时间轴消失`() = runBlocking {
        val server = source()
        val feed = DanmakuFeed(server, windowMs = 8_000)
        val sent = feed.send(500, "要撤回的")
        assertTrue(feed.state.comments.any { it.id == sent.serverId })
        assertTrue(feed.retract(sent.clientMessageId))
        assertFalse("撤回后不再渲染", feed.state.comments.any { it.text == "要撤回的" })
        assertEquals(SendState.Retracted, feed.state.outgoing.single().state)
    }
}
