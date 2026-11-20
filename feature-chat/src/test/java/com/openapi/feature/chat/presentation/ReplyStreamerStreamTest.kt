package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.ChatStreamEvent
import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流式回复的表征测试（整改清单 P1-12）：
 * 先钉住「Delta 缓冲 → 40ms 节拍逐段吐出 → Done 换正式 id」这套语义在
 * updateByKey 改定点更新前后都不许变，再钉住定点更新本身的新契约——
 * 没命中或内容没变时状态必须保持同一个实例（流式期 25 次/秒，白拷贝就是白唤醒整列表重组）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplyStreamerStreamTest {

    private class Harness(initial: ChatUiState = ChatUiState()) {
        val repo = FakeChatRepository()
        val state = MutableStateFlow(initial)
        val scope = TestScope(StandardTestDispatcher())
        val streamer = ReplyStreamer(
            repo, state, scope, SendMessageUseCase(repo),
            attachmentActive = { false }, reconcile = {}, syncSessions = {},
            strings = FakeChatStrings(),
        )

        /** 推进虚拟时钟 [ms] 并跑完到点任务（吐字节拍按这个走）。 */
        fun tick(ms: Long) {
            scope.advanceTimeBy(ms)
            scope.runCurrent()
        }
    }

    private fun userText(id: String, text: String) =
        ChatMessage(id = id, role = ChatRole.USER, text = text, createdAtMillis = 1L)

    /** 初始一条用户消息 + 注入好的 Delta/Done 流。 */
    private fun streamingHarness(full: String, assistantId: String = "a1") = Harness(
        ChatUiState(messages = listOf(userText("u1", "问题"))),
    ).also { h ->
        // 按 2 字一块喂 Delta，模拟服务端分帧
        h.repo.streamFlow = flowOf(
            *full.chunked(2).map { ChatStreamEvent.Delta("local-x", it) }.toTypedArray(),
            ChatStreamEvent.Done(assistantId = assistantId, userId = "u1", fullText = full),
        )
    }

    // ---- 表征：流式语义在性能重构前后必须保持不变 ----

    @Test
    fun streamRevealsAllTextThenDoneSwapsServerIds() {
        val full = "你好世界，测试流式。"
        val h = streamingHarness(full)
        h.streamer.stream(text = "问题", userMessageId = "u1", persistUser = false)
        h.scope.advanceUntilIdle()

        val msgs = h.state.value.messages
        assertEquals("用户气泡 + 一条助手气泡", 2, msgs.size)
        val asst = msgs[1]
        assertEquals(full, asst.text)
        assertEquals("done 帧的正式助手 id", "a1", asst.id)
        assertFalse("完成后不再是 pending 气泡", asst.pending)
        assertEquals(DeliveryStatus.SENT, msgs[0].delivery)
        assertFalse(h.state.value.sending)
        assertFalse(h.state.value.streaming)
        assertEquals(null, h.state.value.error)
    }

    @Test
    fun revealGrowsThePlaceholderPrefixByPrefix() {
        val full = "你好世界abcd" // 6 个 UTF-16 单元，2 字一块共 3 个 Delta
        val h = streamingHarness(full)
        h.streamer.stream(text = "问题", userMessageId = "u1", persistUser = false)

        // 节拍跑到一半：还没有任何文字（第一次 delay(40) 未到）
        h.tick(20)
        val placeholder = h.state.value.messages.last()
        assertTrue("思考中：pending 且无字", placeholder.pending && placeholder.text.isEmpty())

        // 跑过几个节拍：文字只会是全文的前缀，逐段长出来，不会一次全出
        var revealed = ""
        repeat(4) {
            h.tick(40)
            val t = h.state.value.messages.last().text
            assertTrue("每次都是全文前缀: <$t>", full.startsWith(t))
            assertTrue("单调增长", t.length >= revealed.length)
            revealed = t
        }
        assertTrue("节拍间只吐出了一部分，不是一整段", revealed.length < full.length)
        assertTrue(h.state.value.messages.last().pending)

        h.scope.advanceUntilIdle()
        assertEquals(full, h.state.value.messages.last().text)
    }

    @Test
    fun revealTicksDoNotRebuildUntouchedMessages() {
        val h = streamingHarness("你好世界")
        h.streamer.stream(text = "问题", userMessageId = "u1", persistUser = false)
        h.tick(60) // 至少吐过一次

        val userBefore = h.state.value.messages[0]
        h.tick(40)
        val after = h.state.value.messages
        assertSame("吐字节拍不得换掉用户气泡实例", userBefore, after[0])
    }

    // ---- 定点更新的新契约：无变化的更新不许产生新状态 ----

    @Test
    fun updateByKeyOnMissingKeyKeepsStateInstance() {
        val h = Harness(ChatUiState(messages = listOf(userText("u1", "文本"))))
        val before = h.state.value
        var transformed = 0
        h.streamer.updateByKey("没有这个 key") { transformed++; it }

        assertSame("没命中就原样返回：流式期每秒几十次空转必须零分配", before, h.state.value)
        assertEquals(0, transformed)
    }

    @Test
    fun updateByKeyWithUnchangedResultKeepsStateInstance() {
        val m = userText("u1", "文本")
        val h = Harness(ChatUiState(messages = listOf(m)))
        val before = h.state.value
        h.streamer.updateByKey(m.key) { it }

        assertSame("transform 返回等值内容也不能产生新状态", before, h.state.value)
    }

    @Test
    fun updateByKeyReplacesOnlyTheTargetElement() {
        val u = userText("u1", "文本")
        val asst = ChatMessage(id = "a0", role = ChatRole.ASSISTANT, text = "", createdAtMillis = 2L, pending = true)
        val h = Harness(ChatUiState(messages = listOf(u, asst)))
        h.streamer.updateByKey(asst.key) { it.copy(text = "吐") }

        val after = h.state.value.messages
        assertSame("其余元素保持原实例", u, after[0])
        assertEquals("吐", after[1].text)
    }

    @Test
    fun setDeliveryOnMissingIdKeepsStateInstance() {
        val h = Harness(ChatUiState(messages = listOf(userText("u1", "文本"))))
        val before = h.state.value
        h.streamer.setDelivery("没有这个 id", DeliveryStatus.FAILED)
        assertSame(before, h.state.value)
    }

    @Test
    fun setDeliveryWithUnchangedStatusKeepsStateInstance() {
        val m = userText("u1", "文本") // 默认 SENT
        val h = Harness(ChatUiState(messages = listOf(m)))
        val before = h.state.value
        h.streamer.setDelivery(m.id, DeliveryStatus.SENT)
        assertSame(before, h.state.value)
        assertNotSame(
            "换状态时必须真的产生新状态",
            run { h.streamer.setDelivery(m.id, DeliveryStatus.FAILED); before },
            h.state.value,
        )
    }
}
