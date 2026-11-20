package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.model.FileAttachment
import com.openapi.feature.chat.domain.model.MessageKind
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerAndFeedbackTest {

    private fun msg(id: String, role: ChatRole, text: String, files: List<FileAttachment> = emptyList()) =
        ChatMessage(id = id, role = role, text = text, createdAtMillis = 1L, files = files, delivery = DeliveryStatus.SENT)

    @Test
    fun inputChangeIsKept() {
        val state = MutableStateFlow(ChatUiState())
        ComposerActions(state).setInput("你好")
        assertEquals("你好", state.value.input)
    }

    @Test
    fun editStartsOnlyForPlainUserText() {
        val state = MutableStateFlow(
            ChatUiState(
                messages = listOf(
                    msg("u1", ChatRole.USER, "原文"),
                    msg("a1", ChatRole.ASSISTANT, "回复"),
                    msg("u2", ChatRole.USER, "带文件", files = listOf(FileAttachment(path = "/tmp/a.txt", name = "a.txt", sizeBytes = 1L))),
                ),
            ),
        )
        val composer = ComposerActions(state)
        composer.startEdit("a1") // 助手消息不能编辑
        assertNull(state.value.editingMessageId)
        composer.startEdit("u2") // 带文件的不能编辑
        assertNull(state.value.editingMessageId)
        composer.startEdit("u1")
        assertEquals("u1", state.value.editingMessageId)
        assertEquals("原文", state.value.input)
        composer.cancelEdit()
        assertNull(state.value.editingMessageId)
        assertEquals("", state.value.input)
    }

    @Test
    fun dismissErrorClearsIt() {
        val state = MutableStateFlow(ChatUiState(error = "出错了"))
        ComposerActions(state).dismissError()
        assertNull(state.value.error)
    }

    @Test
    fun draftsCanBeAddedAndRemoved() {
        val state = MutableStateFlow(ChatUiState())
        val composer = ComposerActions(state)
        composer.addDraft(MessageKind.IMAGE, StagedAttachment(file = java.io.File("/tmp/a.jpg"), displayName = "a.jpg", sizeBytes = 1L))
        composer.addDraft(MessageKind.IMAGE, StagedAttachment(file = java.io.File("/tmp/b.jpg"), displayName = "b.jpg", sizeBytes = 1L))
        assertEquals(2, state.value.drafts.size)
        val firstId = state.value.drafts.first().id
        composer.removeDraft(firstId)
        assertEquals(1, state.value.drafts.size)
        assertFalse(state.value.drafts.any { it.id == firstId })
    }

    @Test
    fun likeAndDislikeAreMutuallyExclusive() {
        val state = MutableStateFlow(ChatUiState())
        val feedback = FeedbackActions(state)
        assertTrue(feedback.toggleLike("a1"))
        assertEquals(setOf("a1"), state.value.likedIds)
        feedback.disliked("a1")
        assertEquals(setOf("a1"), state.value.dislikedIds)
        assertTrue(state.value.likedIds.isEmpty())
        // 再点赞：取消点踩，返回 true（现在是已赞）
        assertTrue(feedback.toggleLike("a1"))
        assertTrue(state.value.dislikedIds.isEmpty())
        // 再点一次：取消点赞，返回 false
        assertFalse(feedback.toggleLike("a1"))
        assertTrue(state.value.likedIds.isEmpty())
    }
}
