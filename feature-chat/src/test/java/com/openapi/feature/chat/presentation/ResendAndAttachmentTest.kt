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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
            strings = FakeChatStrings(),
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
            strings = FakeChatStrings(),
        )
        var sentAttachments: List<OutgoingAttachment>? = null
        var sentLocation: com.openapi.feature.chat.domain.model.GeoLocation? = null
        var streamCalls = 0
        val sender = AttachmentSender(
            repo, state, streamer,
            streamWithAttachments = { _, _, _, attachments, location ->
                streamCalls++
                sentAttachments = attachments
                sentLocation = location
            },
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

    /**
     * 整改清单 P1-11：附件发送失败后，草稿托盘和输入框文字必须回到输入栏，
     * 用户才能「改一下再发」。气泡的重试保留，但只够原样重发，不够改。
     * 此刻 onSend 已把托盘和输入清空，deliver 失败时才该把它们放回去。
     */
    @Test
    fun failedAttachmentSendRestoresTheDraftTrayAndText() {
        val h = SenderHarness()
        val file = java.io.File.createTempFile("img", ".jpg")
        file.deleteOnExit()
        h.repo.uploadImageResult = null // 上传拿不到 upload_id → 失败
        val message = imageMsg(images = listOf(ImageAttachment(path = file.absolutePath)))

        runBlocking { h.sender.deliver(message, restoreDraftsOnFailure = true) }

        assertEquals(DeliveryStatus.FAILED, h.repo.saved.single().delivery)
        val restored = h.state.value.drafts.single()
        assertEquals(MessageKind.IMAGE, restored.kind)
        assertEquals(file.absolutePath, restored.staged.file.absolutePath)
        assertEquals("看图", h.state.value.input)
    }

    /** 本机附件文件已经没了（没有字节可恢复）：不把空托盘硬塞回去，输入框也不动。 */
    @Test
    fun failedAttachmentSendRestoresNothingWhenTheLocalFilesAreGone() {
        val h = SenderHarness()
        val message = imageMsg(images = listOf(ImageAttachment(path = "/nonexistent/lost.jpg")))

        runBlocking { h.sender.deliver(message, restoreDraftsOnFailure = true) }

        assertTrue(h.state.value.drafts.isEmpty())
        assertEquals("", h.state.value.input)
    }

    /** 失败后用户已经在输入框打了新字：草稿照常回来，但不拿旧文字盖掉他刚打的。 */
    @Test
    fun failedAttachmentSendDoesNotOverwriteWhatTheUserAlreadyRetyped() {
        val h = SenderHarness()
        val file = java.io.File.createTempFile("img", ".jpg")
        file.deleteOnExit()
        h.repo.uploadImageResult = null
        val message = imageMsg(images = listOf(ImageAttachment(path = file.absolutePath)))
        h.state.value = h.state.value.copy(input = "换成这句话")

        runBlocking { h.sender.deliver(message, restoreDraftsOnFailure = true) }

        assertEquals("换成这句话", h.state.value.input)
        assertEquals(1, h.state.value.drafts.size)
    }

    // ---------------------------------------------------------------- 停止语义（P3-20）

    /**
     * 整改清单 P3-20：用户在传输途中点「停止」。
     * 旧实现靠 `coroutineContext[Job]!!` 猜「是不是被停了」，而这行只在
     * 「仓库吞掉了取消异常」时才会被执行——真实仓库恰好 `runCatching` 吞了取消
     * 所以侥幸能用，协程取消语义上是错的，而且假仓库不吞、发送就直接以取消结束：
     * 消息永远停在「发送中」、没有失败记录、界面没有反馈。
     * 正确契约：deliver 自己捕获 CancellationException，落「已停止」的失败状态，
     * 然后把取消重新抛出（不许吞协程取消）。
     */
    @Test
    fun stoppingMidUploadMarksTheMessageStoppedAndRethrowsCancellation() {
        val h = SenderHarness()
        h.repo.uploadImageHangs = true
        val file = java.io.File.createTempFile("img", ".jpg")
        file.deleteOnExit()
        val message = imageMsg(images = listOf(ImageAttachment(path = file.absolutePath)))

        var rethrown = false
        val job = h.scope.launch {
            try {
                h.sender.deliver(message)
            } catch (c: kotlinx.coroutines.CancellationException) {
                rethrown = true
            }
        }
        h.scope.runCurrent() // 启动并挂进 uploadImage（不能用 advanceUntilIdle：会先把 120s 超时烧完）
        job.cancel()
        h.scope.advanceUntilIdle() // 让收尾真的跑完

        assertTrue("停止必须以取消结束 deliver（协程取消不许被吞）", rethrown)
        assertEquals("停止也要留下可重试的失败记录", DeliveryStatus.FAILED, h.repo.saved.single().delivery)
        // 这条文案出自 AttachmentSender（未列入 P2-14b 的注入范围），仍是字面量。
        // A43-#9 裁决（协调会话）：**保留**——#9 反对的是「停止后底部弹提示」的打扰模式，
        // 附件这条是状态说明（消息确实没发出、重试是真实可点动作），语义正确。
        assertEquals("已停止，消息未发出，可点重试", h.state.value.error)
        assertTrue("收尾后 streaming 要撤掉", h.state.value.streaming.not())
        assertTrue("停止后这条消息不再是「发送中」", h.sender.isInFlight(message.id).not())
    }

    /** 普通失败（超时/服务端拒绝，没有任何取消）说的必须是「发送失败」而不是「已停止」。 */
    @Test
    fun aPlainFailureSaysRetryNotStopped() {
        val h = SenderHarness()
        h.repo.uploadImageResult = null
        val message = imageMsg(images = listOf(ImageAttachment(path = "/nonexistent/lost.jpg")))

        runBlocking { h.sender.deliver(message) }

        assertEquals("发送失败，请稍后重试", h.state.value.error)
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
            strings = FakeChatStrings(),
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
    // ---------------------------------------------------------------- 视频 / 位置（P2-13）

    private fun videoMsg(
        videos: List<com.openapi.feature.chat.domain.model.VideoAttachment>,
        text: String = "",
        location: com.openapi.feature.chat.domain.model.GeoLocation? = null,
    ) = ChatMessage(
        id = "20", role = ChatRole.USER, text = text, createdAtMillis = 100L,
        kind = if (location != null) MessageKind.LOCATION else MessageKind.VIDEO,
        videos = videos, location = location,
    )

    @Test
    fun videoWithFileOnDiskReuploadsAndCarriesDuration() {
        val h = SenderHarness()
        val file = java.io.File.createTempFile("clip", ".mp4")
        file.deleteOnExit()
        h.repo.uploadVideoResult = "vid-id"
        val message = videoMsg(
            listOf(com.openapi.feature.chat.domain.model.VideoAttachment(
                path = file.absolutePath, name = "clip.mp4", durationSeconds = 42, uploadId = "stale",
            )),
        )

        runBlocking { h.sender.deliver(message) }

        // 本机有字节 → 重新上传（与图片同一规矩）；duration_s 随附件引用发出
        assertEquals(1, h.repo.uploadVideoCalls.size)
        val sent = h.sentAttachments?.single()
        assertEquals("vid-id", sent?.uploadId)
        assertEquals(MessageKind.VIDEO, sent?.kind)
        assertEquals(42, sent?.durationSeconds)
        val saved = h.repo.saved.single()
        assertEquals("vid-id", saved.videos.single().uploadId)
        assertEquals(DeliveryStatus.SENT, saved.delivery)
    }

    @Test
    fun videoZeroDurationOmitsDurationSeconds() {
        val h = SenderHarness()
        val file = java.io.File.createTempFile("clip", ".mp4")
        file.deleteOnExit()
        val message = videoMsg(
            listOf(com.openapi.feature.chat.domain.model.VideoAttachment(path = file.absolutePath, name = "clip.mp4")),
        )

        runBlocking { h.sender.deliver(message) }

        // 0 = 没读到时长：不带这个字段而不是声称 0 秒
        assertEquals(null, h.sentAttachments?.single()?.durationSeconds)
    }

    @Test
    fun videoWithoutFileReusesStoredUploadId() {
        val h = SenderHarness()
        val message = videoMsg(
            listOf(com.openapi.feature.chat.domain.model.VideoAttachment(
                path = "/nonexistent/lost.mp4", name = "lost.mp4", uploadId = "stored-vid",
            )),
        )

        runBlocking { h.sender.deliver(message) }

        assertEquals(0, h.repo.uploadVideoCalls.size)
        assertEquals("stored-vid", h.sentAttachments?.single()?.uploadId)
        assertEquals(DeliveryStatus.SENT, h.repo.saved.single().delivery)
    }

    @Test
    fun videoUploadFailureMarksFailedWithoutAnyRelayFallback() {
        val h = SenderHarness()
        val file = java.io.File.createTempFile("clip", ".mp4")
        file.deleteOnExit()
        h.repo.uploadVideoResult = null // 直传挂了：视频没有中转降级，只能整条失败
        val message = videoMsg(
            listOf(com.openapi.feature.chat.domain.model.VideoAttachment(path = file.absolutePath, name = "clip.mp4")),
        )

        runBlocking { h.sender.deliver(message) }

        // 没有任何一条附件引用发出去（不走 sendAttachment 中转，也不空手发消息）
        assertEquals(null, h.sentAttachments)
        assertEquals(0, h.streamCalls)
        assertEquals(DeliveryStatus.FAILED, h.repo.saved.single().delivery)
    }

    @Test
    fun locationOnlyMessageStreamsOneTurnWithoutAttachments() {
        val h = SenderHarness()
        val location = com.openapi.feature.chat.domain.model.GeoLocation(31.23, 121.47, 120)
        val message = videoMsg(emptyList(), text = "", location = location)

        runBlocking { h.sender.deliver(message) }

        // 只有位置也是完整一条消息：一个回合、零附件、坐标原样带过去
        assertEquals(1, h.streamCalls)
        assertEquals(emptyList<OutgoingAttachment>(), h.sentAttachments)
        assertEquals(location, h.sentLocation)
        assertEquals(DeliveryStatus.SENT, h.repo.saved.single().delivery)
    }

    @Test
    fun failedVideoSendRestoresVideoDraftWithMetadata() {
        val h = SenderHarness()
        val file = java.io.File.createTempFile("clip", ".mp4")
        file.deleteOnExit()
        h.repo.uploadVideoResult = null
        val message = videoMsg(
            listOf(com.openapi.feature.chat.domain.model.VideoAttachment(
                path = file.absolutePath, name = "clip.mp4", durationSeconds = 42,
                thumbnailPath = file.absolutePath,
            )),
        )

        runBlocking { h.sender.deliver(message, restoreDraftsOnFailure = true) }

        val restored = h.state.value.drafts.single()
        assertEquals(MessageKind.VIDEO, restored.kind)
        assertEquals(42, restored.staged.durationSeconds)
        assertEquals(file.absolutePath, restored.staged.thumbnailPath)
    }

    /** 失败的位置消息重发：坐标在气泡里就有，不需要任何字节，走同一条附件通道。 */
    @Test
    fun retryRoutesLocationOnlyMessageToAttachmentSender() {
        val messages = listOf(
            videoMsg(emptyList(), location = com.openapi.feature.chat.domain.model.GeoLocation(31.0, 121.0)),
        )
        val h = RegenerateHarness(messages)
        h.resend.retry("20")
        h.scope.advanceUntilIdle()
        assertEquals(1, h.attachmentStarts)
    }

    /** 失败的视频消息重发同样要进附件通道（以前只有图片/文件会进，视频会被当成文字发送）。 */
    @Test
    fun retryRoutesVideoMessageToAttachmentSender() {
        val messages = listOf(
            videoMsg(listOf(com.openapi.feature.chat.domain.model.VideoAttachment(path = "/tmp/a.mp4", name = "a.mp4"))),
        )
        val h = RegenerateHarness(messages)
        h.resend.retry("20")
        h.scope.advanceUntilIdle()
        assertEquals(1, h.attachmentStarts)
    }
}
