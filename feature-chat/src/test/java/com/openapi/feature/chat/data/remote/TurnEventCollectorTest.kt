package com.openapi.feature.chat.data.remote

import com.openapi.feature.chat.domain.model.ChatStreamEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「回复没有完整收到」的真相：服务端把每轮回复写进 Redis Stream，重订阅会先补发
 * `turn.snapshot`，还能按 Last-Event-ID 断点续传——**掉线本来一个字都不该丢**。
 * 但客户端以前只订阅一次：移动网络切一下、代理掐掉长连接，用户就被告知"请重试"。
 *
 * 这里守住续传的三条命：
 * 1. 流断了没 done → 带 Last-Event-ID 重连，snapshot 补回来的只有缺失的尾巴（不重复出字）；
 * 2. 退避递增、次数有限，用完后半截按半截收尾（半截要 persist，上层靠它保留内容）；
 * 3. 401 这类重连也不会好的错误不浪费退避，立刻报错。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class TurnEventCollectorTest {

    private fun frame(id: String?, event: String, data: String) = AgentSseFrame(id, event, data)

    private class Harness(
        waits: MutableList<Long> = mutableListOf(),
        isFatal: (Throwable) -> Boolean = { false },
        /** submit 回执里的服务端用户消息 id；0 = 没有（重新生成）。 */
        private val userServerId: Long = 0L,
        /** 每次 open 递增计数；返回这一轮要吐的流（可 throw）。放最后一个参数：尾部 lambda 字面量绑到它。 */
        private val streams: (attempt: Int, lastEventId: String?) -> Flow<AgentSseFrame>,
    ) {
        val events = mutableListOf<ChatStreamEvent>()
        val persisted = mutableListOf<String>()
        val waits = waits
        var opens = 0

        val collector = TurnEventCollector(
            open = { lastEventId ->
                opens++
                streams(opens, lastEventId)
            },
            parse = ::parseAgentEvent,
            backoffMs = listOf(1_000L, 2_000L, 4_000L),
            wait = { waits += it },
            isFatal = isFatal,
        )

        suspend fun run() = collector.collect(
            emit = { events += it },
            expectedTurnId = null,
            assistantLocalId = "a1",
            userMessageId = userServerId.takeIf { it > 0 }?.toString(),
            persist = { persisted += it },
        )

        fun deltas() = events.filterIsInstance<ChatStreamEvent.Delta>().map { it.text }
    }

    private fun snapshot(turn: String, text: String) =
        frame(null, "turn.snapshot", """{"turn_id":"$turn","text":"$text"}""")
    private fun delta(turn: String, d: String, id: String? = null) =
        frame(id, "message.delta", """{"turn_id":"$turn","delta":"$d"}""")
    private fun done(turn: String, assistantId: Long = 55L) =
        frame(null, "message.done", """{"turn_id":"$turn","assistant_message_id":$assistantId}""")

    @Test
    fun aDropMidTurnReconnectsAndOnlyTheMissingTailReappears() = runTest {
        // 第 1 条流吐了"你好"就掉线；重连带上最后见过的帧 id；
        // 服务端先 snapshot 全文（含已有部分！）再补尾、再 done。
        val h = Harness { attempt, lastEventId ->
            if (attempt == 1) flow {
                emit(delta("t1", "你", id = "7-0"))
                emit(delta("t1", "好", id = "7-1"))
                throw java.io.IOException("connection reset")
            } else {
                assertEquals("重连必须带断点 id", "7-1", lastEventId)
                flowOf(
                    snapshot("t1", "你好，世"),
                    delta("t1", "界"),
                    done("t1"),
                )
            }
        }
        h.run()
        assertEquals("断线处不能重复出字", listOf("你", "好", "，世", "界"), h.deltas())
        assertEquals(listOf(1_000L), h.waits)
        assertEquals(2, h.opens)
        val d = h.events.last() as ChatStreamEvent.Done
        assertEquals("55", d.assistantId)
        assertEquals("你好，世界", d.fullText)
        assertEquals("正常 done 要落盘完整文字", listOf("你好，世界"), h.persisted)
    }

    @Test
    fun reconnectSucceedsWithoutSnapshotWhenNoTextWasLost() = runTest {
        // 掉线发生在两条 delta 之间，且服务端没有再发 snapshot：直接续 delta 也要能接上。
        val h = Harness { attempt, _ ->
            if (attempt == 1) flow {
                emit(delta("t1", "前半", id = "9-0"))
                throw java.io.IOException("premature EOF")
            } else flowOf(delta("t1", "后半"), done("t1"))
        }
        h.run()
        assertEquals(listOf("前半", "后半"), h.deltas())
        assertEquals(1, h.persisted.size)
    }

    @Test
    fun reconnectsAreBackoffLimitedAndAPartialTurnStillPersists() = runTest {
        // 每条流都吐一截就断：4 次尝试（首连 + 3 次退避重连）之后放弃，
        // 半截文字必须 persist（界面靠它保留内容）并发 assistantId=null 的 Done。
        val h = Harness { attempt, _ ->
            flow {
                emit(delta("t1", "段$attempt", id = "1-$attempt"))
                throw java.io.IOException("connection reset")
            }
        }
        h.run()
        assertEquals("1s、2s、4s 三轮退避", listOf(1_000L, 2_000L, 4_000L), h.waits)
        assertEquals(4, h.opens)
        assertEquals(listOf("段1", "段2", "段3", "段4"), h.deltas())
        assertEquals(listOf("段1段2段3段4"), h.persisted)
        val d = h.events.last() as ChatStreamEvent.Done
        assertEquals(null, d.assistantId)
    }

    @Test
    fun aStreamThatNeverEmitsAnythingAndKeepsDroppingReportsAnError() = runTest {
        val h = Harness { _, _ -> flow { throw java.io.IOException("connection refused") } }
        h.run()
        val e = h.events.single() as ChatStreamEvent.Error
        assertTrue(e.message, e.message.contains("没有收到回复"))
        assertEquals(3, h.waits.size)
        assertTrue(h.persisted.isEmpty())
    }

    @Test
    fun aFatalErrorStopsRetryingImmediately() = runTest {
        class Unauthorized(msg: String) : Exception(msg)
        val h = Harness(
            streams = { _, _ -> flow { throw Unauthorized("登录已过期") } },
            isFatal = { it is Unauthorized },
        )
        h.run()
        assertEquals("重连也不会好的错误不浪费退避", 1, h.opens)
        assertTrue(h.waits.isEmpty())
        val e = h.events.single() as ChatStreamEvent.Error
        assertTrue(e.message, e.message.contains("登录已过期"))
    }

    @Test
    fun errorFrameFromOurTurnReportsTheTranslatedCodeAndStops() = runTest {
        val h = Harness { _, _ ->
            flowOf(frame(null, "error", """{"turn_id":"t1","code":"content_policy"}"""))
        }
        h.run()
        val e = h.events.single() as ChatStreamEvent.Error
        assertEquals("这条消息不符合内容规范", e.message)
        assertEquals("服务端明确报错就不该重连", 1, h.opens)
    }

    @Test
    fun doneCarriesTheUserMessageIdFromSubmitSoEditingWorksAtOnce() = runTest {
        // 编辑/删除用户气泡要用服务端正式 id。submit 回执里的 message_id 必须
        // 一路带进 Done.userId，否则刚发完的气泡是本地 id，编辑会报「还没同步到服务端」，
        // 直到重启 App 重新拉历史才能编辑。
        val h = Harness(userServerId = 91L) { _, _ ->
            flowOf(delta("t1", "你好"), done("t1"))
        }
        h.run()
        val d = h.events.last() as ChatStreamEvent.Done
        assertEquals("91", d.userId)
        assertEquals("55", d.assistantId)
    }

    @Test
    fun userMessageIdStaysNullOnRegenerate() = runTest {
        // 重新生成没有新的用户消息（submit 回执 id 为 0），不能把 0 当成正式 id。
        val h = Harness(userServerId = 0L) { _, _ ->
            flowOf(delta("t1", "再说一遍"), done("t1"))
        }
        h.run()
        val d = h.events.last() as ChatStreamEvent.Done
        assertEquals(null, d.userId)
    }

    @Test
    fun framesFromAnotherTurnAreIgnored() = runTest {
        // 语义（沿用迁移实现）：第一次拿到非空 turn_id 就固定这一轮，之后只认它。
        // snapshot 先锁轮，别的轮混进来的 delta 与 done 都不该混进本轮的气泡。
        val h = Harness { _, _ ->
            flowOf(
                snapshot("t1", ""),
                delta("other", "别人的回合"),
                delta("t1", "我的", id = "3-0"),
                done("t1"),
            )
        }
        h.run()
        assertEquals(listOf("我的"), h.deltas())
        assertEquals("别人的 done 不能提前收尾本轮", 1, h.opens)
        val d = h.events.last() as ChatStreamEvent.Done
        assertEquals("55", d.assistantId)
    }
}
