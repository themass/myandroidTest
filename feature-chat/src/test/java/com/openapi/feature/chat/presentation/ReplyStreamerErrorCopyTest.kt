package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.ChatStreamEvent
import com.openapi.feature.chat.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 事件流错误 → 用户文案的映射（任务 1/2 的收尾半边）：带机读 code 的错误
 * （看门狗 turn_timeout、取消后仍 409 的 turn_still_running）必须由 presentation
 * 按 [ChatStrings] 端口出当前语言的文案，状态必须收尾（sending/streaming 落地）。
 *
 * 单独一个 Robolectric 类的原因：[ChatStreamEvent.Error] 的 init 写 android.util.Log
 * （线上行为，查真实原因用的），纯 JVM 单测里构造它就会炸（Log 未 mock）。
 * [TurnEventCollectorTest] 同一个理由。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ReplyStreamerErrorCopyTest {

    private fun user(id: String = "user-1", text: String = "问") =
        ChatMessage(id = id, role = ChatRole.USER, text = text, createdAtMillis = 1L)

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

    /**
     * 帧间看门狗报错：状态收尾（sending=false、streaming=false），文案走
     * ChatStrings.turnTimeout()——不能是 data 层写死的那句兜底；已经显示的内容保留。
     */
    @Test
    fun watchdogErrorSettlesStateWithLocalizedCopy() {
        val flow = flow<ChatStreamEvent> {
            emit(ChatStreamEvent.Delta("mid", "只出了一半"))
            emit(
                ChatStreamEvent.Error(
                    "等不到新的内容，回复可能已经中断",
                    code = ChatStreamEvent.Error.CODE_TURN_TIMEOUT,
                ),
            )
        }
        val h = Harness(listOf(user()), flow)
        h.streamer.stream("问", userMessageId = "user-1", persistUser = true)
        h.scope.advanceUntilIdle()
        assertFalse("看门狗后 sending 必须落地", h.state.value.sending)
        assertFalse(h.state.value.streaming)
        assertEquals("S:turnTimeout", h.state.value.error)
        val assistant = h.state.value.messages.lastOrNull { it.role == ChatRole.ASSISTANT }
        assertTrue("已显示的内容不能因为看门狗被吞掉", assistant != null && assistant.text.contains("只出了一半"))
    }

    /** 取消后仍然 409：文案走 ChatStrings.previousTurnRunning()（机读 code 的另一半）。 */
    @Test
    fun previousTurnErrorUsesLocalizedCopy() {
        val flow = flow<ChatStreamEvent> {
            emit(ChatStreamEvent.Error("上一轮回复还没结束", code = ChatStreamEvent.Error.CODE_PREVIOUS_TURN_RUNNING))
        }
        val h = Harness(listOf(user()), flow)
        h.streamer.stream("问", userMessageId = "user-1", persistUser = true)
        h.scope.advanceUntilIdle()
        assertEquals("S:previousTurnRunning", h.state.value.error)
        assertFalse(h.state.value.sending)
    }

    /** 没有 code 的错误（服务端 error 帧、重连耗尽）：保持 data 层给的原话，不被端口覆盖。 */
    @Test
    fun plainErrorKeepsTheDataLayerMessage() {
        val flow = flow<ChatStreamEvent> {
            emit(ChatStreamEvent.Error("这条消息不符合内容规范"))
        }
        val h = Harness(listOf(user()), flow)
        h.streamer.stream("问", userMessageId = "user-1", persistUser = true)
        h.scope.advanceUntilIdle()
        assertEquals("这条消息不符合内容规范", h.state.value.error)
        assertFalse(h.state.value.sending)
    }
}
