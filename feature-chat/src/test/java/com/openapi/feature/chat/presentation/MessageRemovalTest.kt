package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 乐观删除（任务 3）：点击后界面**立刻**移除，服务端删除在后台补；
 * 服务端失败要把消息放回**原来的位置**并报错条。
 * 「多选立刻消失」「挂起期间 UI 已经空」这两条时序用挂起模式（[FakeChatRepository.deleteMessageHangs]）钉住。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageRemovalTest {

    private class Harness(messages: List<ChatMessage>) {
        val repo = FakeChatRepository()
        val state = MutableStateFlow(ChatUiState(messages = messages))
        val scope = TestScope(StandardTestDispatcher())
        val removal = MessageRemoval(
            repository = repo,
            state = state,
            scope = scope,
            strings = FakeChatStrings(),
            io = StandardTestDispatcher(scope.testScheduler),
        )
    }

    private fun msg(id: String, text: String = "消息$id") =
        ChatMessage(id = id, role = ChatRole.USER, text = text, createdAtMillis = id.hashCode().toLong())

    @Test
    fun serverDeleteSuccessRemovesTheMessageEverywhere() {
        val h = Harness(listOf(msg("10"), msg("11")))
        h.removal.deleteOne("10")
        h.scope.advanceUntilIdle()
        assertEquals(listOf("11"), h.state.value.messages.map { it.id })
        assertEquals(listOf("10"), h.repo.deletedMessages)
        assertEquals("缓存行也要删，否则切会话会把它画回来", listOf("10"), h.repo.locallyDeleted)
        assertNull(h.state.value.error)
    }

    /** 时序核心：服务端删除还挂着（慢/无响应），界面必须已经空出来——旧实现这里什么都没发生。 */
    @Test
    fun uiRemovesAtOnceWhileServerDeleteIsStillInFlight() {
        val h = Harness(listOf(msg("10"), msg("11")))
        h.repo.deleteMessageHangs = true
        h.removal.deleteOne("10")
        h.scope.runCurrent() // 跑到 deleteOnServer 的挂起点为止
        assertEquals(listOf("11"), h.state.value.messages.map { it.id })
        assertTrue("服务端那条还没发出去", h.repo.deletedMessages.isEmpty())
        assertEquals(listOf("10"), h.repo.locallyDeleted)
        // 墓碑挂着：这期间任何历史对齐都不能把它带回来（复活用例在 HistoryActionsTest）
        assertTrue(h.removal.pending.isPending(msg("10")))
    }

    @Test
    fun serverDeleteFailureRestoresTheMessageToItsOriginalPlace() {
        val h = Harness(listOf(msg("10"), msg("11"), msg("12")))
        h.repo.deleteMessageOk = false
        h.removal.deleteOne("11")
        h.scope.advanceUntilIdle()
        assertEquals("删失败必须放回原位（不是追加到尾部）", listOf("10", "11", "12"), h.state.value.messages.map { it.id })
        assertEquals("S:deleteFailed", h.state.value.error)
    }

    /** 多选的第一诉求：列表整批立刻消失，不等任何服务端往返。 */
    @Test
    fun deleteManyClearsTheWholeSelectionBeforeAnyServerRoundTrip() {
        val h = Harness(listOf(msg("10"), msg("11"), msg("12")))
        h.repo.deleteMessageHangs = true
        h.removal.deleteMany(listOf("10", "12"))
        h.scope.runCurrent()
        assertEquals(listOf("11"), h.state.value.messages.map { it.id })
        assertTrue(h.state.value.messages.none { it.id == "10" || it.id == "12" })
    }

    @Test
    fun deleteManyRestoresFailedEntriesInPlaceAndCountsThem() {
        val h = Harness(listOf(msg("10"), msg("11"), msg("12")))
        h.repo.deleteMessageOk = false
        h.removal.deleteMany(listOf("10", "12"))
        h.scope.advanceUntilIdle()
        assertEquals(listOf("10", "11", "12"), h.state.value.messages.map { it.id })
        assertEquals("S:deleteManyPartFailed:2", h.state.value.error)
    }

    @Test
    fun deleteManyRemovesLocalOnlyMessagesWithoutAnyServerCall() {
        val h = Harness(listOf(msg("10"), msg("local-2"), msg("12")))
        h.removal.deleteMany(listOf("10", "local-2", "12"))
        h.scope.advanceUntilIdle()
        // 乐观语义下本地临时行同样立刻消失（旧实现会留着它并提示「尚未同步」——
        // 它本来就只在本机，留着只会让人以为没删掉）
        assertTrue(h.state.value.messages.isEmpty())
        assertEquals(listOf("10", "12"), h.repo.deletedMessages)
        assertNull(h.state.value.error)
    }

    @Test
    fun localOnlyMessageIsRemovedWithoutAServerCall() {
        val h = Harness(listOf(msg("local-1")))
        h.removal.deleteOne("local-1")
        h.scope.advanceUntilIdle()
        assertTrue(h.state.value.messages.isEmpty())
        assertTrue(h.repo.deletedMessages.isEmpty())
        assertTrue(h.removal.pending.isPending(msg("local-1")))
    }

    /** 服务端有一键清空接口时：一次调用清掉整个会话，不再分页拉 id 逐条删（整改清单 P1-9）。 */
    @Test
    fun clearConversationUsesTheOneShotEndpointWhenTheRepositorySupportsIt() {
        val h = Harness(listOf(msg("10"), msg("11")))
        h.repo.history = listOf(msg("10"), msg("11"))
        h.repo.clearHistoryOk = true
        h.removal.clearConversation()
        h.scope.advanceUntilIdle()
        assertTrue(h.state.value.messages.isEmpty())
        assertNull(h.state.value.error)
        assertEquals(1, h.repo.clearHistoryCalls)
        assertTrue("一键清空成功后不应再逐条删服务端消息", h.repo.deletedMessages.isEmpty())
        assertEquals(1, h.repo.clearedLocal)
    }

    /** 旧服务端没有该接口（或调用失败）时：回退到原来的分页 + 逐条删除路径。 */
    @Test
    fun clearConversationFallsBackToPerMessageDeletesWithoutOneShotSupport() {
        val h = Harness(listOf(msg("10"), msg("11")))
        h.repo.history = listOf(msg("10"), msg("11"))
        h.repo.clearHistoryOk = false
        h.removal.clearConversation()
        h.scope.advanceUntilIdle()
        assertTrue(h.state.value.messages.isEmpty())
        assertNull(h.state.value.error)
        assertEquals(1, h.repo.clearHistoryCalls)
        assertEquals(listOf("10", "11"), h.repo.deletedMessages)
        assertEquals(1, h.repo.clearedLocal)
    }

    @Test
    fun clearConversationWithFailuresKeepsTheFailedOnesAndCountsThem() {
        val h = Harness(listOf(msg("10"), msg("11")))
        h.repo.history = listOf(msg("10"), msg("11"))
        h.repo.deleteMessageOk = false
        h.removal.clearConversation()
        h.scope.advanceUntilIdle()
        assertEquals(setOf("10", "11"), h.state.value.messages.map { it.id }.toSet())
        assertEquals("S:clearFailed:2", h.state.value.error)
        assertEquals(0, h.repo.clearedLocal)
    }
}
