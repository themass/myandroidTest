package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 编辑态和新消息的边界。
 *
 * 原来的问题：编辑一条消息 → 加一张图 → 发送。`onSend()` 只在「编辑中且草稿为空」时
 * 走编辑分支，带图就落到普通发送分支，而那个分支清输入框、清草稿，**不清
 * `editingMessageId`**。结果是一条新消息发出去了，「编辑消息」横幅还挂着，
 * 再点一次发送又走同一条错路。状态必须和实际发生的事一致。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SendWhileEditingTest {

    private class Harness(state: ChatUiState) {
        val repo = FakeChatRepository()
        val ui = MutableStateFlow(state)
        val scope = TestScope(StandardTestDispatcher())
        val composer = ComposerActions(ui)
        var submitEditCalls = mutableListOf<Pair<String, String>>()
        var attachmentStarts = 0
        val streamer = ReplyStreamer(
            repo, ui, scope, SendMessageUseCase(repo),
            attachmentActive = { false }, reconcile = {}, syncSessions = {},
        )
        val send = SendActions(
            repo, ui, scope, composer, streamer,
            submitEdit = { id, text -> submitEditCalls += id to text },
            startAttachmentSend = { attachmentStarts++ },
        )
    }

    private fun editingState(drafts: List<DraftAttachment> = emptyList()) = ChatUiState(
        messages = listOf(ChatMessage(id = "u1", role = ChatRole.USER, text = "原文",
            createdAtMillis = 1L, delivery = DeliveryStatus.SENT)),
        input = "改过的字",
        editingMessageId = "u1",
        drafts = drafts,
    )

    private fun imageDraft() = DraftAttachment(
        id = "draft-1", kind = MessageKind.IMAGE,
        staged = StagedAttachment(File("/tmp/a.jpg"), "a.jpg", 1L),
    )

    @Test
    fun sendingADraftWhileEditingEndsTheEdit() {
        val h = Harness(editingState(drafts = listOf(imageDraft())))
        h.send.onSend()
        h.scope.advanceUntilIdle()

        assertNull("带上附件发送走的是新消息，编辑态必须一起结束", h.ui.value.editingMessageId)
        assertEquals("", h.ui.value.input)
        assertTrue(h.ui.value.drafts.isEmpty())
        assertEquals(1, h.attachmentStarts)
    }

    @Test
    fun sendingADraftWhileEditingDoesNotSubmitAnEdit() {
        val h = Harness(editingState(drafts = listOf(imageDraft())))
        h.send.onSend()
        h.scope.advanceUntilIdle()
        assertTrue("附件不参与编辑，不该混进 editMessage", h.submitEditCalls.isEmpty())
    }

    @Test
    fun editingPlainTextStillSubmitsTheEdit() {
        // 回归保护：没有草稿时仍然是编辑，不能因为这个修复被改掉。
        val h = Harness(editingState())
        h.send.onSend()
        h.scope.advanceUntilIdle()
        assertEquals(listOf("u1" to "改过的字"), h.submitEditCalls)
        assertEquals(0, h.attachmentStarts)
    }

    @Test
    fun aVoiceMessageAlsoEndsTheEdit() {
        // 语音走 sendText，不经过 onSend 的清空那一行——同一个 bug 还有第二个入口。
        val h = Harness(editingState())
        h.send.sendText("语音转出来的文字")
        h.scope.advanceUntilIdle()
        assertNull(h.ui.value.editingMessageId)
    }
}
