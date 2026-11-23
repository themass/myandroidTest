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
        assertTrue(h.state.value.error?.contains("未发出") == true)
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
}

/** 测试里需要 reveal 节拍常量；与 ReplyStreamer 内 40ms 一致。 */
internal object ReplyStreamerRevealTestSupport {
    const val TICK_MS = 40L
}
