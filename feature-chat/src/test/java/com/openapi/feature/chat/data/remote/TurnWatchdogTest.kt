package com.openapi.feature.chat.data.remote

import com.openapi.feature.chat.domain.model.ChatStreamEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 帧间看门狗（任务 1）：「连接活着、服务端照常心跳、模型不再出字」这一种死法。
 *
 * 事件流的 readTimeout 是 0（回复多久吐完看模型），而 15 秒一次的心跳注释行在解析阶段
 * 就被丢掉、根本不产生帧——旧实现里这种回合会永远挂在 collect 上：不超时、不抛异常、
 * 不重连，「正在思考…」永久挂着，sending 一直为真。看门狗的判据是
 * 「距离上一帧静默了 frameTimeoutMs」（每帧重新计时），测试直接注入小阈值走虚拟时间。
 * 注入自己的 parse（纯字符串协议）：折叠逻辑不碰 org.json。
 * 跑在 Robolectric 下是因为 [ChatStreamEvent.Error] 的 init 会写 android.util.Log
 * （与 [TurnEventCollectorTest] 同一个理由）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class TurnWatchdogTest {

    private fun frame(id: String?, event: String, data: String) = AgentSseFrame(id, event, data)

    private class Harness(
        frameTimeoutMs: Long,
        private val streams: (attempt: Int) -> Flow<AgentSseFrame>,
    ) {
        val events = mutableListOf<ChatStreamEvent>()
        val persisted = mutableListOf<String>()
        val waits = mutableListOf<Long>()
        var opens = 0

        val collector = TurnEventCollector(
            open = { _ ->
                opens++
                streams(opens)
            },
            parse = { frame ->
                when (frame.event) {
                    "d" -> ParsedTurnEvent.Delta("t1", frame.data)
                    "done" -> ParsedTurnEvent.Done("t1", 55L)
                    else -> null
                }
            },
            backoffMs = listOf(1_000L),
            wait = { waits += it },
            frameTimeoutMs = frameTimeoutMs,
        )

        suspend fun run() = collector.collect(
            emit = { events += it },
            expectedTurnId = null,
            assistantLocalId = "a1",
            userMessageId = "u1",
            persist = { persisted += it },
        )
    }

    @Test
    fun anIdleForeverStreamFiresTheWatchdogOnceAndSettles() = runTest {
        // 「只发心跳、永不出内容」在服务端事件流里的真实形状 = 一条永不产出帧的流
        //（心跳注释不产生帧）。看门狗必须：报一次带机读 code 的错、不重连、不 persist。
        val h = Harness(frameTimeoutMs = 200) {
            flow { awaitCancellation() }
        }
        h.run()
        val e = h.events.single() as ChatStreamEvent.Error
        assertEquals(ChatStreamEvent.Error.CODE_TURN_TIMEOUT, e.code)
        assertTrue(e.message, e.message.contains("等不到新的内容"))
        assertEquals("看门狗不重连（服务端认为这一轮还活着）", 1, h.opens)
        assertTrue("看门狗不走退避", h.waits.isEmpty())
        assertTrue("错误收尾不落盘半截文字", h.persisted.isEmpty())
    }

    @Test
    fun framesKeepArrivingWithinBudgetAreNotCutOff() = runTest {
        // 正常慢回复：帧持续到达（间隔远小于阈值），看门狗每帧重新计时，不能误伤。
        val h = Harness(frameTimeoutMs = 200) {
            flow {
                emit(frame("1-0", "d", "前半"))
                delay(150)
                emit(frame("1-1", "d", "后半"))
                delay(150)
                emit(frame(null, "done", ""))
            }
        }
        h.run()
        assertEquals(listOf("前半", "后半"), h.events.filterIsInstance<ChatStreamEvent.Delta>().map { it.text })
        val d = h.events.last() as ChatStreamEvent.Done
        assertEquals("55", d.assistantId)
        assertEquals(1, h.opens)
        assertTrue(h.waits.isEmpty())
    }

    @Test
    fun silenceAfterPartialTextEndsTheTurnWithErrorKeepingDeltas() = runTest {
        // 出了两个字然后彻底安静：半截 Delta 已经 emit（界面保留内容），
        // 看门狗触发后这一轮以错误收尾——不再退避重连、不再补 Done。
        val h = Harness(frameTimeoutMs = 100) {
            flow {
                emit(frame("1-0", "d", "AB"))
                awaitCancellation()
            }
        }
        h.run()
        assertEquals(listOf("AB"), h.events.filterIsInstance<ChatStreamEvent.Delta>().map { it.text })
        val e = h.events.last() as ChatStreamEvent.Error
        assertEquals(ChatStreamEvent.Error.CODE_TURN_TIMEOUT, e.code)
        assertEquals(1, h.opens)
    }

    @Test
    fun unparseableFramesStillResetTheWatchdog() = runTest {
        // 判据是「有没有帧」，不是「帧能不能解析」：连 parse 返回 null 的帧也重新计时。
        // （真实流里这等价于服务端还在说话——哪怕说的是本轮不认识的 event。）
        val h = Harness(frameTimeoutMs = 200) {
            flow {
                repeat(4) {
                    emit(frame(null, "unknown-event", "x"))
                    delay(150)
                }
                emit(frame(null, "done", ""))
            }
        }
        h.run()
        val d = h.events.last() as ChatStreamEvent.Done
        assertEquals("55", d.assistantId)
    }
}
