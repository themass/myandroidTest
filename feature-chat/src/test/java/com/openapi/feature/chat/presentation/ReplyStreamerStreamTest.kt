package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.ChatStreamEvent
import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流式回复语义表征（P1-12）：打字机释放、最终文本、delivery、停止。
 * 重构 updateByKey 后这些断言必须保持全绿。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplyStreamerStreamTest {

    private fun user(id: String = "user-1", text: String = "问") =
        ChatMessage(id = id, role = ChatRole.USER, text = text, createdAtMillis = 1L)

    private fun fillerMessages(count: Int): List<ChatMessage> =
        (0 until count).map { i ->
            ChatMessage(
                id = "hist-$i",
                role = if (i % 2 == 0) ChatRole.USER else ChatRole.ASSISTANT,
                text = "m$i",
                createdAtMillis = i.toLong(),
            )
        }

    private class Harness(
        messages: List<ChatMessage> = emptyList(),
        streamFlow: Flow<ChatStreamEvent>,
    ) {
        val repo = FakeChatRepository().apply { this.streamFlow = streamFlow }
        val state = MutableStateFlow(ChatUiState(messages = messages, sessionId = 1L))
        val scope = TestScope(StandardTestDispatcher())
        val streamer = ReplyStreamer(
            repo, state, scope, SendMessageUseCase(repo),
            attachmentActive = { false }, reconcile = {}, syncSessions = {},
            strings = FakeChatStrings(),
        )
    }

    @Test
    fun streamingRevealsTextAndFinishesWithServerIds() {
        val flow = flow {
            emit(ChatStreamEvent.Delta("mid", "Hel"))
            emit(ChatStreamEvent.Delta("mid", "lo"))
            emit(ChatStreamEvent.Done("asst-99", "user-99", "Hello"))
        }
        val h = Harness(listOf(user()), flow)
        h.streamer.stream("问", userMessageId = "user-1", persistUser = true)
        h.scope.advanceUntilIdle()
        val assistant = h.state.value.messages.last()
        assertEquals(ChatRole.ASSISTANT, assistant.role)
        assertEquals("Hello", assistant.text)
        assertFalse(assistant.pending)
        assertEquals("asst-99", assistant.id)
        assertEquals(DeliveryStatus.SENT, h.state.value.messages.first().delivery)
        assertFalse(h.state.value.streaming)
    }

    @Test
    fun stopKeepsPartialTextAndMarksUserFailedWhenNothingRevealed() {
        val flow = flow<ChatStreamEvent> {
            // 故意不 emit：缓冲区为空时停止 → 用户消息可重试
            kotlinx.coroutines.awaitCancellation()
        }
        val h = Harness(listOf(user()), flow)
        h.streamer.stream("问", userMessageId = "user-1", persistUser = true)
        h.scope.runCurrent()
        h.streamer.cancel()
        h.scope.advanceUntilIdle()
        assertEquals(DeliveryStatus.FAILED, h.state.value.messages.first().delivery)
        // A43-#9：停止后不再弹底部提示——失败的可重试入口在气泡上的 delivery 标（FAILED→重试），
        // 底部那条「已停止，消息未发出，可点重试」是重复提示，删掉。
        org.junit.Assert.assertNull("停止后不得再有底部提示", h.state.value.error)
    }

    // -------------------------------------------------- A43-3 / 设计 20261126 §2 D4：取消乐观化

    /**
     * 点停止的这一刻本地就收尾：发送键复位（sending=false）、生成中的占位气泡定格
     * （pending=false）、等待队列保留——不等网络。`cancelTurn` 在后台发，失败也不影响本地。
     * 旧实现要等 catch 分支跑完（含一次 suspend 的 saveMessage）才更新这几个标志，
     * 慢网络下「按了停止但界面还在生成」。这里用 runCurrent（不 advance）钉住同步性。
     */
    @Test
    fun stopFinalizesLocallySynchronouslyWithoutWaitingForNetwork() {
        val flow = flow<ChatStreamEvent> {
            emit(ChatStreamEvent.Delta("mid", "已经出来的半截"))
            kotlinx.coroutines.awaitCancellation()
        }
        val h = Harness(listOf(user()), flow)
        h.streamer.stream("问", userMessageId = "user-1", persistUser = true)
        h.scope.runCurrent()
        assertTrue("前置：正在生成", h.state.value.streaming)

        h.streamer.cancel()
        // 只跑当前已排队的恢复任务（catch 分支同步跑完、停在 persistStopped 挂起点前都算）。
        // 故意**不** advanceUntilIdle：只看停止那一击本身落定了什么，不等后台网络任务。
        h.scope.runCurrent()
        assertFalse("停止后发送态必须当场复位", h.state.value.sending)
        assertFalse("停止后生成态必须当场复位", h.state.value.streaming)
        val assistant = h.state.value.messages.lastOrNull { it.role == ChatRole.ASSISTANT }
        assertTrue("占位气泡必须当场定格（不再是 pending）", assistant != null && !assistant.pending)
        assertTrue("已显示的文字不能丢", assistant != null && assistant.text.isNotEmpty())
        // 后台那条服务端取消照常发出（不阻塞本地收尾）
        h.scope.advanceUntilIdle()
        assertEquals("仍要请服务端取消这一轮", listOf(1L), h.repo.cancelledTurns)
    }

    /** 普通停止保留等待输入，队列不会自行启动。 */
    @Test
    fun stopPreservesQueueSynchronously() {
        val flow = flow<ChatStreamEvent> { kotlinx.coroutines.awaitCancellation() }
        val h = Harness(listOf(user()), flow)
        h.streamer.stream("问", userMessageId = "user-1", persistUser = true)
        h.scope.runCurrent()
        h.state.value = h.state.value.copy(queued = listOf(QueuedText("q-0", "排队的")))

        h.streamer.cancel()
        assertEquals(listOf("排队的"), h.state.value.queued.map { it.text })
    }

    @Test
    fun stopAfterSomeDeltasKeepsAssistantTextAndUserSent() {        val flow = flow {
            emit(ChatStreamEvent.Delta("mid", "ABCXYZ"))
            kotlinx.coroutines.awaitCancellation()
        }
        val h = Harness(listOf(user()), flow)
        h.streamer.stream("问", userMessageId = "user-1", persistUser = true)
        repeat(5) {
            h.scope.advanceTimeBy(ReplyStreamerRevealTestSupport.TICK_MS)
            h.scope.runCurrent()
        }
        h.streamer.cancel()
        h.scope.advanceUntilIdle()
        val assistant = h.state.value.messages.lastOrNull { it.role == ChatRole.ASSISTANT }
        assertTrue(assistant != null && assistant.text.isNotEmpty())
        assertTrue("停止后的部分回复应有明确状态", assistant?.interrupted == true)
        assertEquals(DeliveryStatus.SENT, h.state.value.messages.first().delivery)
    }

    @Test
    fun updateByKeyOnLongHistoryOnlyChangesTailMessageInstance() {
        val flow = flow {
            emit(ChatStreamEvent.Delta("mid", "abcdefghijklmnopqrstuvwxyz"))
            kotlinx.coroutines.awaitCancellation()
        }
        val history = fillerMessages(20)
        val h = Harness(history + user(), flow)
        h.streamer.stream("问", userMessageId = "user-1", persistUser = true)
        val fillerRefs = h.state.value.messages.take(20)
        repeat(3) {
            h.scope.advanceTimeBy(ReplyStreamerRevealTestSupport.TICK_MS)
            h.scope.runCurrent()
        }
        val during = h.state.value.messages
        assertTrue(during.size >= 22)
        fillerRefs.indices.forEach { i ->
            assertSame("流式 tick 期间前缀不应被整表 map 替换", fillerRefs[i], during[i])
        }
        h.streamer.cancel()
        h.scope.advanceUntilIdle()
    }

    // ---------------------------------------------------------------- followup 队列排空

    /**
     * 回合**自然结束**后排空队列：取队首续发，保持 Enter 顺序（FIFO），
     * 排空后 queued 清空。每轮都用注入的假流，第二轮、第三轮同样正常收尾。
     */
    @Test
    fun naturalEndDrainsQueueHeadFirstInOrder() {
        val flow = flow {
            emit(ChatStreamEvent.Delta("mid", "答"))
            emit(ChatStreamEvent.Done("asst-1", "user-1", "答"))
        }
        val h = Harness(listOf(user()), flow)
        // 生成中用户按了两次 Enter（SendActions.sendText 的落点就是这里）
        h.state.value = h.state.value.copy(queued = listOf(QueuedText("q-2", "第二条"), QueuedText("q-3", "第三条")))
        h.streamer.stream("问", userMessageId = "user-1", persistUser = true)
        h.scope.advanceUntilIdle()

        // 首条 + 队首 + 第二条队首：三轮都发给了仓库，顺序与 Enter 顺序一致
        assertEquals(listOf("问", "第二条", "第三条"), h.repo.sentTexts)
        assertTrue("排空后队列必须清空", h.state.value.queued.isEmpty())
        val userTexts = h.state.value.messages.filter { it.role == ChatRole.USER }.map { it.text }
        assertEquals(listOf("问", "第二条", "第三条"), userTexts)
        assertFalse("最后一轮也已正常收尾", h.state.value.streaming)
        // A43-#10：排空续发的气泡带「排队发送」标记（回合结束后才冒出来，不标用户以为丢了）
        assertTrue(h.state.value.messages.first { it.text == "问" }.fromQueue.not())
        assertTrue(h.state.value.messages.filter { it.text == "第二条" || it.text == "第三条" }
            .all { it.fromQueue })
    }

    // ---------------------------------------------------------------- 停止必须停两端（任务 2）

    /**
     * 点停止除了杀本地 Job，还必须请服务端取消这一轮——
     * 否则服务端回合继续跑，下一条吃 409「上一轮回复还没结束」。
     */
    @Test
    fun stopAlsoCancelsTheTurnOnTheServer() {
        val flow = flow<ChatStreamEvent> { kotlinx.coroutines.awaitCancellation() }
        val h = Harness(listOf(user()), flow)
        h.streamer.stream("问", userMessageId = "user-1", persistUser = true)
        h.scope.runCurrent()
        h.streamer.cancel()
        h.scope.advanceUntilIdle()
        assertEquals("停止要带上当前会话请服务端取消", listOf(1L), h.repo.cancelledTurns)
    }

    /**
     * 用户点停止只终止当前回复：排队文字保留，但不自动起下一回合
     * （排空只挂在流事件收尾；本地停止的 catch 分支 rethrow，走不到那里）。
     */
    @Test
    fun stopClearsQueueAndDoesNotStartNextTurn() {
        val flow = flow<ChatStreamEvent> { kotlinx.coroutines.awaitCancellation() }
        val h = Harness(listOf(user()), flow)
        h.streamer.stream("问", userMessageId = "user-1", persistUser = true)
        h.scope.runCurrent()
        h.state.value = h.state.value.copy(
            queued = listOf(QueuedText("q-x", "第一条等待"), QueuedText("q-y", "第二条等待")),
        )
        h.streamer.cancel()
        h.scope.advanceUntilIdle()

        assertEquals("停止必须保留排队输入", listOf("第一条等待", "第二条等待"), h.state.value.queued.map { it.text })
        assertEquals("停止后不得自动起新回合", listOf("问"), h.repo.sentTexts)
        assertEquals("队列文字不能变成用户气泡", listOf("问"), h.state.value.messages.map { it.text })
    }
}

/** 测试里需要 reveal 节拍常量；与 ReplyStreamer 内 40ms 一致。 */
internal object ReplyStreamerRevealTestSupport {
    const val TICK_MS = 40L
}
