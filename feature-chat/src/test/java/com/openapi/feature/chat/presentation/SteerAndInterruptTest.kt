package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.ChatStreamEvent
import com.openapi.feature.chat.domain.model.SteerDisposition
import com.openapi.feature.chat.domain.model.SteerResult
import com.openapi.feature.chat.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A40：排队消息的两个「现在就要答案」出口。
 *
 * - 插话（steerQueued）：本地队列摘出 → 气泡立即上屏（id = clientMessageId）→ 仓库裁决；
 *   失败必须文字回输入框、气泡撤掉——用户的一条输入在任何路径下都不许丢。
 * - 打断重发（interruptQueued）：插到队首 + 请服务端取消当前回合；当前流以
 *   cancelled→Done 自然收尾后立即重发这条。没有活跃回合的竞态直接发。
 * - 接住回合（noteCaughtTurn + takeCaughtTurn）：裁决 queued/started 时，本轮收尾后
 *   服务端排空开的新回合照常流式展示。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SteerAndInterruptTest {

    private class Harness(initial: ChatUiState) {
        val repo = FakeChatRepository()
        val state = MutableStateFlow(initial)
        val scope = TestScope(StandardTestDispatcher())
        val streamer: ReplyStreamer
        val send: SendActions

        init {
            streamer = ReplyStreamer(
                repo, state, scope, SendMessageUseCase(repo),
                attachmentActive = { false }, reconcile = {}, syncSessions = {},
                strings = FakeChatStrings(),
            )
            send = SendActions(
                repo, state, scope, ComposerActions(state), streamer,
                submitEdit = { _, _ -> },
                startAttachmentSend = {},
                strings = FakeChatStrings(),
            )
        }
    }

    private fun queued(vararg texts: String) =
        texts.mapIndexed { i, t -> QueuedText(id = "q-$i", text = t) }

    private fun doneFlow(text: String) = flow<ChatStreamEvent> {
        emit(ChatStreamEvent.Delta("mid", text))
        emit(ChatStreamEvent.Done("asst-1", "server-user-9", text))
    }

    // ---------------------------------------------------------------- 插话

    @Test
    fun steerRemovesFromQueueInsertsBubbleAndSubmitsWithEntryId() {
        val h = Harness(ChatUiState(sending = true, streaming = true, queued = queued("第一条", "第二条")))
        h.repo.steerResult = SteerResult(true, SteerDisposition.MERGED, messageId = 41L, turnId = "t1")

        h.send.steerQueued(1)
        h.scope.advanceUntilIdle()

        assertEquals("队列只摘掉这一条", listOf("第一条"), h.state.value.queued.map { it.text })
        // 气泡立刻上屏，id = 队列条目 id（= clientMessageId）：Done 带回正式 id 前也能定位
        val bubble = h.state.value.messages.single { it.role == ChatRole.USER }
        assertEquals("q-1", bubble.id)
        assertEquals("第二条", bubble.text)
        assertEquals(
            "提交必须带 (会话, 文字, clientMessageId)",
            listOf(Triple(1L, "第二条", "q-1")),
            h.repo.steerCalls,
        )
    }

    @Test
    fun steerFailurePutsTextBackInInputAndRemovesBubble() {
        val h = Harness(ChatUiState(sending = true, streaming = true, queued = queued("要插的话")))
        h.repo.steerResult = SteerResult(false) // 默认就是失败：显式写出来表明走的就是这条

        h.send.steerQueued(0)
        h.scope.advanceUntilIdle()

        assertTrue("失败后气泡必须撤掉", h.state.value.messages.none { it.role == ChatRole.USER })
        assertEquals("文字回到输入框，一条都不能丢", "要插的话", h.state.value.input)
        assertEquals("S:steerSendFailed", h.state.value.error)
        assertTrue(h.state.value.queued.isEmpty())
    }

    @Test
    fun steerQueuedDispositionDrainsServerTurnAfterCurrentTurnEnds() {
        // 裁决 queued：当前流收尾后接住服务端排空开的新回合（takeCaughtTurn 的流），
        // 排空回合的文字照常流式出现；「插话」登记的锁扣参数也要钉住——
        // queued 带的是当时在跑的回合 id（要跳过它），不是新回合 id。
        val h = Harness(
            ChatUiState(
                sending = true, streaming = true, queued = queued("排到下一轮"),
                messages = listOf(
                    com.openapi.feature.chat.domain.model.ChatMessage(
                        id = "user-1", role = ChatRole.USER, text = "正在答", createdAtMillis = 1L,
                    ),
                ),
            ),
        )
        h.repo.steerResult = SteerResult(true, SteerDisposition.QUEUED, messageId = 42L, turnId = "t-old")
        // 当前回合故意慢一拍：插话必须在它自然结束**之前**登记成功。
        h.repo.streamFlow = flow {
            emit(ChatStreamEvent.Delta("mid", "当前"))
            kotlinx.coroutines.delay(100)
            emit(ChatStreamEvent.Delta("mid", "回复"))
            emit(ChatStreamEvent.Done("asst-1", "server-user-9", "当前回复"))
        }
        h.repo.caughtFlow = flow {
            emit(ChatStreamEvent.Delta("mid", "排空回复"))
            emit(ChatStreamEvent.Done("asst-2", "server-user-42", "排空回复"))
        }

        h.streamer.stream("正在答", userMessageId = "user-1", persistUser = true)
        h.scope.runCurrent() // 当前流已在跑（停在 delay 上）
        h.send.steerQueued(0)
        h.scope.runCurrent() // steer 回执落定：noteCaughtTurn 已登记
        h.scope.advanceUntilIdle()

        val userTexts = h.state.value.messages.filter { it.role == ChatRole.USER }.map { it.text }
        assertEquals("插话气泡必须还在", listOf("正在答", "排到下一轮"), userTexts)
        val assistantTexts = h.state.value.messages.filter { it.role == ChatRole.ASSISTANT }.map { it.text }
        assertEquals("两轮回复都在，顺序正确", listOf("当前回复", "排空回复"), assistantTexts)
        assertFalse("接住回合收尾后不能还在生成", h.state.value.streaming)
        // 排空回合的 Done 带回正式 user id：插话气泡原位换 id（可编辑/删除）
        val bubble = h.state.value.messages.first { it.text == "排到下一轮" }
        assertEquals("server-user-42", bubble.id)
    }

    // ---------------------------------------------------------------- 打断重发

    @Test
    fun interruptMovesToFrontAndCancelsServerTurnWhileReplyActive() {
        val h = Harness(ChatUiState(sending = true, streaming = true, queued = queued("甲", "乙", "丙")))
        // 有一个活跃回合在跑
        h.repo.streamFlow = flow<ChatStreamEvent> { kotlinx.coroutines.awaitCancellation() }
        h.streamer.stream("正在答", userMessageId = "user-1", persistUser = true)
        h.scope.runCurrent()

        h.send.interruptQueued(2) // 丙 抢队首
        // 注意：这里**不能** advanceUntilIdle——流故意永不结束，而 reveal 显示节奏
        // 每 40ms 自我续排（ReplyStreamer.runTurn 的 reveal 协程），虚拟时间会无限推进，
        // 测试直接挂死。本用例只关心「插队首 + 已请服务端取消」这两件同步落定的事，
        // runCurrent 处理当前已排队的任务即可。
        h.scope.runCurrent()

        assertEquals("丙必须插到最前", listOf("丙", "甲", "乙"), h.state.value.queued.map { it.text })
        assertEquals("要请服务端取消当前回合", listOf(1L), h.repo.cancelledTurns)
    }

    @Test
    fun interruptWithoutActiveTurnSendsDirectly() {
        // 竞态：点打断的这一刻根本没有回合在跑——不需要取消，直接按正常路径发。
        val h = Harness(ChatUiState(queued = queued("就这条")))
        h.repo.streamFlow = doneFlow("好的")

        h.send.interruptQueued(0)
        h.scope.advanceUntilIdle()

        assertTrue(h.state.value.queued.isEmpty())
        assertEquals(listOf("就这条"), h.repo.sentTexts)
        assertEquals("打断路径不请服务端取消", 0, h.repo.cancelledTurns.size)
        // 直接发时提交带的 clientMessageId = 队列条目 id（与插话同一套语义；
        // 气泡 id 之后会被 Done 的正式服务端 id 原位替换，那是既有规则）
        assertEquals(listOf("q-0"), h.repo.sentClientMessageIds)
    }

    @Test
    fun interruptUsesLatestQueuedMessageEvenIfCallbackIndexIsStale() {
        val h = Harness(ChatUiState(sending = true, streaming = true, queued = queued("甲", "乙", "丙")))
        h.repo.streamFlow = flow<ChatStreamEvent> { kotlinx.coroutines.awaitCancellation() }
        h.streamer.stream("正在答", userMessageId = "user-1", persistUser = true)
        h.scope.runCurrent()

        h.send.interruptQueued(9)
        h.scope.runCurrent()

        assertEquals(listOf("丙", "甲", "乙"), h.state.value.queued.map { it.text })
        assertEquals(listOf(1L), h.repo.cancelledTurns)
    }

    // ---------------------------------------------------------------- 队列排空用条目 id

    @Test
    fun drainedQueueHeadReusesEntryIdAsBubbleId() {
        // 排空续发的提交带 clientMessageId = QueuedText.id：与「插话」同一套 id 语义，
        // 服务端按它去重；Done 带回正式 id 后气泡原位换 id（可编辑/删除）。
        val h = Harness(ChatUiState())
        h.repo.streamFlow = doneFlow("答")
        h.state.value = h.state.value.copy(queued = queued("排队发的"))
        h.streamer.stream("问", userMessageId = "user-1", persistUser = true)
        h.scope.advanceUntilIdle()

        assertEquals(listOf("问", "排队发的"), h.repo.sentTexts)
        assertEquals(listOf("user-1", "q-0"), h.repo.sentClientMessageIds)
        val bubble = h.state.value.messages.first { it.text == "排队发的" }
        assertEquals("Done 后气泡换成正式服务端 id", "server-user-9", bubble.id)
    }
}
