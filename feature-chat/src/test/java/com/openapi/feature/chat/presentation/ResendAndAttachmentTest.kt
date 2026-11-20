package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.DeliveryStatus
import com.openapi.feature.chat.domain.model.ImageAttachment
import com.openapi.feature.chat.domain.model.ImageSource
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.domain.model.OutgoingAttachment
import com.openapi.feature.chat.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 重发/重新生成的两条规则：
 * - 带图片的消息重跑走服务端 regenerate（服务端从消息 meta 里重新读图），不再整条重发；
 *   带文件的仍整条重发（服务端历史里没有文件引用）。
 * - 附件发送只在「本机文件已丢、无字节可传」时复用已存的 upload_id；
 *   文件还在就重新上传（服务端只认 10 分钟内签发的直传 id）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResendAndAttachmentTest {

    private fun imageMsg(
        id: String = "10",
        images: List<ImageAttachment>,
        files: List<com.openapi.feature.chat.domain.model.FileAttachment> = emptyList(),
    ) = ChatMessage(id = id, role = ChatRole.USER, text = "看图", createdAtMillis = 100L,
        kind = MessageKind.IMAGE, images = images, files = files)

    private fun assistant(id: String = "11") =
        ChatMessage(id = id, role = ChatRole.ASSISTANT, text = "回复", createdAtMillis = 200L)

    // ---------------------------------------------------------------- regenerate

    private class RegenerateHarness(messages: List<ChatMessage>) {
        val repo = FakeChatRepository()
        val state = MutableStateFlow(ChatUiState(messages = messages))
        val scope = TestScope(StandardTestDispatcher())
        var attachmentStarts = 0
        val streamer = ReplyStreamer(
            repo, state, scope, SendMessageUseCase(repo),
            attachmentActive = { false }, reconcile = {}, syncSessions = {},
        )
        val resend = ResendActions(
            repo, state, scope, streamer,
            startAttachments = { attachmentStarts++ },
        )
    }

    @Test
    fun regenerateOnImageMessageGoesThroughServerNotReupload() {
        val remote = ImageAttachment(path = "", source = ImageSource.REMOTE, uploadId = "u1")
        val h = RegenerateHarness(listOf(imageMsg(images = listOf(remote)), assistant()))
        h.resend.regenerate("11")
        h.scope.advanceUntilIdle()
        // 走服务端重跑：regenerateStream 被调用，而不是把整条消息重新上传发送
        assertEquals(1, h.repo.regenerateStreamCalls)
        assertEquals(0, h.attachmentStarts)
    }

    @Test
    fun regenerateOnFileMessageStillReuploads() {
        val file = com.openapi.feature.chat.domain.model.FileAttachment("/tmp/a.pdf", "a.pdf", 1L)
        val h = RegenerateHarness(listOf(imageMsg(files = listOf(file), images = emptyList()), assistant()))
        h.resend.regenerate("11")
        h.scope.advanceUntilIdle()
        // 文件没有服务端引用，只能截断后整条重发
        assertEquals(0, h.repo.regenerateStreamCalls)
        assertEquals(1, h.attachmentStarts)
        assertEquals(listOf("10"), h.state.value.messages.map { it.id })
    }

    // ---------------------------------------------------------------- AttachmentSender

    private class SenderHarness {
        val repo = FakeChatRepository()
        val state = MutableStateFlow(ChatUiState(sessionId = 1L))
        val scope = TestScope(StandardTestDispatcher())
        val streamer = ReplyStreamer(
            repo, state, scope, SendMessageUseCase(repo),
            attachmentActive = { false }, reconcile = {}, syncSessions = {},
        )
        var sentAttachments: List<OutgoingAttachment>? = null
        val sender = AttachmentSender(
            repo, state, streamer,
            streamWithAttachments = { _, _, _, attachments -> sentAttachments = attachments },
        )
    }

    @Test
    fun resendWithFileOnDiskReuploadsInsteadOfReusingUploadId() {
        val h = SenderHarness()
        val file = java.io.File.createTempFile("img", ".jpg")
        file.deleteOnExit()
        h.repo.uploadImageResult = "fresh-id"
        val message = imageMsg(images = listOf(ImageAttachment(path = file.absolutePath, uploadId = "stale-id")))

        runBlocking { h.sender.deliver(message) }

        // 本机有字节：必须重新上传，不复用旧 id（服务端只认 10 分钟内签发的直传 id）
        assertEquals(1, h.repo.uploadImageCalls.size)
        assertEquals("fresh-id", h.sentAttachments?.single()?.uploadId)
        // 新 id 记回消息并持久化，供文件丢失后的下一次重发兜底
        val saved = h.repo.saved.single()
        assertEquals("fresh-id", saved.images.single().uploadId)
        assertEquals(DeliveryStatus.SENT, saved.delivery)
    }

    @Test
    fun resendWithoutFileOnDiskReusesStoredUploadId() {
        val h = SenderHarness()
        val gone = java.io.File("/nonexistent/chat_attachments/img_gone.jpg")
        val message = imageMsg(
            images = listOf(ImageAttachment(path = gone.absolutePath, source = ImageSource.REMOTE, uploadId = "stored-id")),
        )

        runBlocking { h.sender.deliver(message) }

        // 没有字节可传：复用已存的 upload_id 是唯一能把图带上的路
        assertEquals(0, h.repo.uploadImageCalls.size)
        assertEquals("stored-id", h.sentAttachments?.single()?.uploadId)
        assertEquals(DeliveryStatus.SENT, h.repo.saved.single().delivery)
    }

    @Test
    fun imageWithoutFileAndWithoutUploadIdFailsAsRetryable() {
        val h = SenderHarness()
        val message = imageMsg(images = listOf(ImageAttachment(path = "/nonexistent/lost.jpg")))

        runBlocking { h.sender.deliver(message) }

        assertEquals(null, h.sentAttachments)
        val saved = h.repo.saved.single()
        assertEquals(DeliveryStatus.FAILED, saved.delivery)
        assertTrue(h.state.value.sending.not())
    }

    // ---------------------------------------------------------------- submitEdit

    private class EditHarness {
        val repo = FakeChatRepository()
        val user = ChatMessage(id = "42", role = ChatRole.USER, text = "原文字", createdAtMillis = 1L)
        val assistant = ChatMessage(id = "43", role = ChatRole.ASSISTANT, text = "旧回答", createdAtMillis = 2L)
        val state = MutableStateFlow(
            ChatUiState(
                sessionId = 1L,
                messages = listOf(user, assistant),
                input = "改过的字",
                editingMessageId = "42",
            ),
        )
        val scope = TestScope(StandardTestDispatcher())
        val streamer = ReplyStreamer(
            repo, state, scope, SendMessageUseCase(repo),
            attachmentActive = { false }, reconcile = {}, syncSessions = {},
        )
        val resend = ResendActions(
            repo, state, scope, streamer,
            startAttachments = {},
        )
    }

    @Test
    fun failedEditKeepsTheEditedTextAndEditMode() {
        // 服务端改写失败：用户辛苦改的那段字必须原样还在——输入框、编辑横幅、后续气泡都不动，
        // 再点一次发送就是重试。以前的实现先乐观改写再 refresh 拉回旧文字，编辑内容直接丢了。
        val h = EditHarness()
        h.repo.editOk = false
        h.resend.submitEdit("42", "改过的字")
        h.scope.advanceUntilIdle()
        assertEquals("改过的字", h.state.value.input)
        assertEquals("42", h.state.value.editingMessageId)
        assertEquals(listOf("42", "43"), h.state.value.messages.map { it.id })
        assertEquals("原文字", h.state.value.messages.first().text)
        org.junit.Assert.assertNotNull(h.state.value.error)
        assertEquals("编辑失败不该触发重新生成", 0, h.repo.regenerateStreamCalls)
    }

    @Test
    fun successfulEditAppliesTextTruncatesAndRegenerates() {
        // 成功路径：气泡截断到这条并落新文字，输入框清空、退出编辑，然后走服务端 regenerate。
        val h = EditHarness()
        h.repo.editOk = true
        h.resend.submitEdit("42", "改过的字")
        h.scope.advanceUntilIdle()
        assertEquals("", h.state.value.input)
        assertEquals(null, h.state.value.editingMessageId)
        assertEquals(listOf("42"), h.state.value.messages.map { it.id })
        assertEquals("改过的字", h.state.value.messages.single().text)
        assertEquals(1, h.repo.regenerateStreamCalls)
    }
}
