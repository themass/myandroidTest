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
        // P2-14b：文案走 ChatStrings 端口，JVM 单测里断言取到了哪条标记串；
        // 真实措辞「未发出」由 :app 的 ResourceChatStringsTest（Robolectric）钉住
        assertEquals("S:stoppedNoReply", h.state.value.error)
    }

    @Test
    fun stopAfterSomeDeltasKeepsAssistantTextAndUserSent() {
        val flow = flow {
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
        h.state.value = h.state.value.copy(queued = listOf("第二条", "第三条"))
        h.streamer.stream("问", userMessageId = "user-1", persistUser = true)
        h.scope.advanceUntilIdle()

        // 首条 + 队首 + 第二条队首：三轮都发给了仓库，顺序与 Enter 顺序一致
        assertEquals(listOf("问", "第二条", "第三条"), h.repo.sentTexts)
        assertTrue("排空后队列必须清空", h.state.value.queued.isEmpty())
        val userTexts = h.state.value.messages.filter { it.role == ChatRole.USER }.map { it.text }
        assertEquals(listOf("问", "第二条", "第三条"), userTexts)
        assertFalse("最后一轮也已正常收尾", h.state.value.streaming)
    }

    /**
     * 用户点停止 = 中断意图明确：排队中的文字必须清空，不能接着起新回合
     * （排空只挂在自然结束的收尾；停止的 catch 分支 rethrow，走不到那里）。
     */
    @Test
    fun stopClearsQueueAndDoesNotStartNextTurn() {
        val flow = flow<ChatStreamEvent> { kotlinx.coroutines.awaitCancellation() }
        val h = Harness(listOf(user()), flow)
        h.streamer.stream("问", userMessageId = "user-1", persistUser = true)
        h.scope.runCurrent()
        h.state.value = h.state.value.copy(queued = listOf("没发出去的"))
        h.streamer.cancel()
        h.scope.advanceUntilIdle()

        assertTrue("停止必须清空排队", h.state.value.queued.isEmpty())
        assertEquals("停止后不得再起新回合", listOf("问"), h.repo.sentTexts)
        assertEquals("队列文字不能变成用户气泡", listOf("问"), h.state.value.messages.map { it.text })
    }
}

/** 测试里需要 reveal 节拍常量；与 ReplyStreamer 内 40ms 一致。 */
internal object ReplyStreamerRevealTestSupport {
    const val TICK_MS = 40L
}
