package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
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

/**
 * 编辑态和发送的交互（整改清单 P0-3）：
 * 编辑一条消息后往草稿托盘加了图，点发送必须退出编辑态——
 * 曾经发出一条新消息而「编辑消息」横幅一直挂着，再点发送又走同一条错路。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SendActionsTest {

    private class Harness(initial: ChatUiState) {
        val repo = FakeChatRepository()
        val state = MutableStateFlow(initial)
        val scope = TestScope(StandardTestDispatcher())
        var submitted: Pair<String, String>? = null
        val attachmentSends = mutableListOf<ChatMessage>()
        val send: SendActions

        init {
            val streamer = ReplyStreamer(
                repo, state, scope, SendMessageUseCase(repo),
                attachmentActive = { false }, reconcile = {}, syncSessions = {},
                strings = FakeChatStrings(),
            )
            send = SendActions(
                repo, state, scope, ComposerActions(state), streamer,
                submitEdit = { id, text -> submitted = id to text },
                startAttachmentSend = { m -> attachmentSends += m },
                strings = FakeChatStrings(),
            )
        }
    }

    private fun draft(id: String) = DraftAttachment(
        id = id, kind = MessageKind.IMAGE,
        staged = StagedAttachment(file = java.io.File("/tmp/$id.jpg"), displayName = "$id.jpg", sizeBytes = 1L),
    )

    private fun userText(id: String, text: String) =
        ChatMessage(id = id, role = ChatRole.USER, text = text, createdAtMillis = 1L)

    @Test
    fun sendWithDraftsExitsEditMode() {
        val h = Harness(
            ChatUiState(
                messages = listOf(userText("u1", "原文")),
                input = "加张图",
                editingMessageId = "u1",
                drafts = listOf(draft("d1")),
            ),
        )
        h.send.onSend()
        h.scope.advanceUntilIdle()

        // 带草稿的不算编辑提交，走的必须是普通（附件）发送
        assertNull(h.submitted)
        assertEquals(1, h.attachmentSends.size)
        // 关键回归点：编辑横幅必须跟着清掉
        assertNull(h.state.value.editingMessageId)
        assertEquals("", h.state.value.input)
        assertTrue(h.state.value.drafts.isEmpty())
    }

    @Test
    fun plainEditSubmitStillGoesToSubmitEdit() {
        val h = Harness(
            ChatUiState(messages = listOf(userText("u1", "原文")), input = "改过了", editingMessageId = "u1"),
        )
        h.send.onSend()
        h.scope.advanceUntilIdle()
        assertEquals("u1" to "改过了", h.submitted)
        assertEquals(0, h.attachmentSends.size)
    }

    /** 编辑提交不带附件：进了编辑态就把草稿托盘清空，从源头杜绝「编辑+附件」的混合态。 */
    @Test
    fun enteringEditClearsDrafts() {
        val state = MutableStateFlow(
            ChatUiState(messages = listOf(userText("u1", "原文")), drafts = listOf(draft("d1"))),
        )
        ComposerActions(state).startEdit("u1")
        assertEquals("u1", state.value.editingMessageId)
        assertTrue("编辑态和附件草稿不能共存", state.value.drafts.isEmpty())
    }

    // ---------------------------------------------------------------- 位置 / 视频草稿（P2-13）

    private fun videoDraft(id: String) = DraftAttachment(
        id = id, kind = MessageKind.VIDEO,
        staged = StagedAttachment(
            file = java.io.File("/tmp/$id.mp4"), displayName = "$id.mp4", sizeBytes = 2L,
            durationSeconds = 42, thumbnailPath = "/tmp/${id}_thumb.jpg",
        ),
    )

    /** 只有位置（没打字、没附件）也必须能发：位置本身就是内容。 */
    @Test
    fun locationDraftAloneIsSendableAndBecomesLocationMessage() {
        val location = com.openapi.feature.chat.domain.model.GeoLocation(31.23, 121.47, 120)
        val h = Harness(ChatUiState(locationDraft = location))
        assertTrue(h.state.value.canSend)

        h.send.onSend()
        h.scope.advanceUntilIdle()

        assertEquals(1, h.attachmentSends.size)
        val message = h.attachmentSends.single()
        assertEquals(MessageKind.LOCATION, message.kind)
        assertEquals(location, message.location)
        // 发出去之后草稿必须清空，否则下一条消息会再带一次同一个位置
        assertEquals(null, h.state.value.locationDraft)
    }

    @Test
    fun videoDraftBecomesVideoMessageWithMetadata() {
        val h = Harness(ChatUiState(drafts = listOf(videoDraft("v1"))))
        h.send.onSend()
        h.scope.advanceUntilIdle()

        val message = h.attachmentSends.single()
        assertEquals(MessageKind.VIDEO, message.kind)
        val video = message.videos.single()
        assertEquals("/tmp/v1.mp4", video.path)
        assertEquals("v1.mp4", video.name)
        assertEquals(42, video.durationSeconds)
        assertEquals("/tmp/v1_thumb.jpg", video.thumbnailPath)
    }

    /** 位置 + 视频混发：一条消息、kind 以位置为主（与服务端字段各存各的对应）。 */
    @Test
    fun locationPlusVideoStaysOneMessageKindLocation() {
        val location = com.openapi.feature.chat.domain.model.GeoLocation(31.0, 121.0)
        val h = Harness(ChatUiState(drafts = listOf(videoDraft("v1")), locationDraft = location))
        h.send.onSend()
        h.scope.advanceUntilIdle()

        val message = h.attachmentSends.single()
        assertEquals(1, h.attachmentSends.size)
        assertEquals(MessageKind.LOCATION, message.kind)
        assertEquals(1, message.videos.size)
        assertEquals(location, message.location)
    }
}
