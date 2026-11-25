package com.openapi.ks.myapp.agent

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A39：「角色对话（新）」的 `agent 一直不结束、一直在生成`。
 *
 * 这条路径的三件套此前合起来正好构成永挂形状：
 * - `AgentEventSource` readTimeout 60s > 服务端 15s 心跳：「连接活着但模型不出字」
 *   不会抛任何异常，重连（只由异常触发）永远不触发；
 * - `active` 回合气泡只被 done/cancelled/failed 清除，连接死了这些事件不会来；
 * - 重连耗尽只发 `Connection(OFFLINE)`，`reduce` 只改连接字段，`active` 原样挂着。
 *
 * 修复两层：reconnector 补帧间看门狗（只在回合进行中武装，空闲会话不受影响），
 * OFFLINE 到达时 `reduce` 把半截回合收尾落回列表。这里钉：
 * 1. 回合进行中只发心跳的流 → 看门狗按一次失败退避重连（注入的 open 计数可验）；
 * 2. 空闲会话静默 → 不掐线（不产生失败/重连）；
 * 3. OFFLINE + 挂着 active → 气泡落回、active 清空、给出可行动提示。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AgentReconnectorWatchdogTest {

    private fun frame(id: String?, event: String, data: String) = SseFrame(id, event, data)

    private class Harness(
        private val frameGapMs: Long,
        // 放最后一个参数：尾部 lambda 字面量绑到它（与 TurnEventCollectorTest 同一约定）。
        private val streams: (attempt: Int) -> Flow<SseFrame>,
    ) {
        val updates = mutableListOf<StreamUpdate>()
        var opens = 0
        val waits = mutableListOf<Long>()

        fun flow(): Flow<StreamUpdate> = AgentEventReconnector(
            open = { _, _ ->
                opens++
                streams(opens)
            },
            maxFailures = 3,
            waitFn = { waits += it },
            frameGapMs = frameGapMs,
        ).updates("s1", null)
    }

    @Test
    fun anActiveTurnWithOnlyHeartbeatsFiresTheWatchdogAndBacksOff() = runTest {
        // 第 1 次连接：started 进来（回合进行中），之后只有心跳（不产生帧）→
        // 看门狗必须掐线并按一次失败退避；第 2 次连接给 done 收尾，测试到此为止收集。
        val h = Harness(frameGapMs = 200) { attempt ->
            if (attempt == 1) {
                flow {
                    emit(frame("1-0", "turn.started", """{"turn_id":"T","user_message_ids":[]}"""))
                    awaitCancellation()
                }
            } else {
                flow {
                    emit(frame("2-0", "message.done", """{"turn_id":"T","assistant_message_id":"9"}"""))
                }
            }
        }
        // 只取到 done 为止，避免 done 后进入空闲常态无限重连。
        h.flow().takeWhile { update ->
            !(update is StreamUpdate.Event && update.event is SessionEvent.Done)
        }.collect { h.updates += it }
        // 至少经历：CONNECTED → (RECONNECTING…) → 第二次 CONNECTED → Done
        assertEquals(2, h.opens)
        assertEquals(listOf(1_000L), h.waits) // 一次失败：恰好一次退避
    }

    @Test
    fun anIdleSessionIsNotStalledByTheWatchdog() = runTest {
        // 没有回合进行中：静默是常态，绝不能掐线。让流空转远超阈值（虚拟时间 5 个周期），
        // 期间看门狗若误武装，会看到失败退避的 waits——必须一个都没有。
        val h = Harness(frameGapMs = 200) { flow { awaitCancellation() } }
        val job = launch { h.flow().collect { /* 空闲流什么都不发 */ } }
        delay(1_000) // = 5 个阈值周期
        job.cancel()
        assertEquals(1, h.opens) // 只开过一次：没有被看门狗掐线重连
        assertEquals(emptyList<Long>(), h.waits)
    }

    @Test
    fun offlineSettlesTheLingeringActiveTurn() {
        var s = AgentChatState()
        s = reduce(s, StreamUpdate.Event(SessionEvent.TurnStarted("1-0", "T", listOf("7"))))
        s = reduce(s, StreamUpdate.Event(SessionEvent.Delta("2-0", "T", "说了一半")))
        // 重连耗尽：传输层发 OFFLINE。旧 reduce 只改连接字段，active 永挂。
        s = reduce(s, StreamUpdate.Connection(ConnectionState.OFFLINE))
        assertNull(s.active)
        assertEquals(ConnectionState.OFFLINE, s.connection)
        val last = s.bubbles.last()
        assertEquals("assistant", last.role)
        assertEquals("说了一半", last.content)
        assertEquals(true, s.lastError?.contains("连接已断开"))
    }

    @Test
    fun offlineWithNoActiveTurnJustFlipsConnection() {
        val s = reduce(AgentChatState(), StreamUpdate.Connection(ConnectionState.OFFLINE))
        assertNull(s.active)
        assertNull(s.lastError) // 没有回合被牺牲，别凭空报错
        assertEquals(ConnectionState.OFFLINE, s.connection)
    }
}
