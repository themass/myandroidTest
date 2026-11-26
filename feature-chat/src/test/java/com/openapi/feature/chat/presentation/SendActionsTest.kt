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
import kotlinx.coroutines.test.runCurrent
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

    // ---------------------------------------------------------------- followup 排队（生成中也能发下一条）

    /** 新语义（取代旧 `canSend == … && !sending` 的闸）：生成中输入非空就能按发送键。 */
    @Test
    fun canSendStaysTrueWhileSending() {
        val h = Harness(ChatUiState(input = "下一条", sending = true, streaming = true))
        assertTrue("生成中输入框有文字，发送键必须可用（走排队）", h.state.value.canSend)
    }

    /** 生成中 sendText（**排队模式**）：不直发、不插用户气泡，按 Enter 顺序进队列。 */
    @Test
    fun sendTextWhileSendingQueuesInOrderWithoutStartingStream() {
        val h = Harness(ChatUiState(sending = true, streaming = true, sendMode = SendMode.Queue))
        h.send.sendText("第一条")
        h.send.sendText("第二条")
        h.scope.advanceUntilIdle()

        assertEquals(listOf("第一条", "第二条"), h.state.value.queued.map { it.text })
        // 没有直发：没有新的用户气泡/占位气泡，仓库一次流都没被调用
        assertTrue(h.state.value.messages.isEmpty())
        assertTrue(h.repo.sentTexts.isEmpty())
    }

    /** 生成中点发送（纯文字，**排队模式**）：立即清空 input 并入队，不走附件发送路径。 */
    @Test
    fun onSendWhileSendingQueuesPlainTextAndClearsInput() {
        val h = Harness(ChatUiState(input = " 追加的问题 ", sending = true, streaming = true, sendMode = SendMode.Queue))
        h.send.onSend()
        h.scope.advanceUntilIdle()

        assertEquals(listOf("追加的问题"), h.state.value.queued.map { it.text })
        assertEquals("", h.state.value.input)
        assertEquals(0, h.attachmentSends.size)
        assertTrue(h.state.value.messages.isEmpty())
    }

    // ---------------------------------------------------------------- D1 即时模式（默认）

    /**
     * D1（设计 20261126 §2 D1）：默认 Immediate——生成中的纯文字**不进本地队列**，
     * 气泡立即上屏并直接 submit（clientMessageId = 气泡 id，与 A40「插话」同一套 id 语义）。
     * 关键：不走 streamer.stream——那条路带 MigrationGuard，同会话在飞回合会 409。
     */
    @Test
    fun immediateModeMidTurnSendSubmitsDirectlyWithoutQueueing() {
        val h = Harness(ChatUiState(sending = true, streaming = true)) // 默认就是 Immediate
        h.repo.steerResult = com.openapi.feature.chat.domain.model.SteerResult(
            true, com.openapi.feature.chat.domain.model.SteerDisposition.MERGED, messageId = 7L, turnId = "t1",
        )
        h.send.sendText("生成中发的")
        h.scope.advanceUntilIdle()

        assertTrue("即时模式绝不进本地队列", h.state.value.queued.isEmpty())
        assertEquals("必须走 submit（steer 管道），一次", 1, h.repo.steerCalls.size)
        assertEquals("生成中发的", h.repo.steerCalls[0].second)
        // 气泡与提交同一个 id：服务端按 clientMessageId 去重，回执换 id 不错位
        val bubble = h.state.value.messages.single { it.role == ChatRole.USER }
        assertEquals(h.repo.steerCalls[0].third, bubble.id)
        // 绝不能再起一条本地流（MigrationGuard 会对同会话 409）
        assertTrue(h.repo.sentTexts.isEmpty())
    }

    /** 即时发送失败（网络/旧服务端）：气泡撤掉、文字回输入框——任何路径不丢一条用户输入。 */
    @Test
    fun immediateModeSendFailureReturnsTextToInputAndRemovesBubble() {
        val h = Harness(ChatUiState(sending = true, streaming = true))
        // steerResult 默认就是失败：显式走这条
        h.send.sendText("发不出去的")
        // 提交前气泡已乐观上屏
        assertEquals(1, h.state.value.messages.count { it.role == ChatRole.USER })
        h.scope.advanceUntilIdle()

        assertTrue(h.state.value.messages.none { it.role == ChatRole.USER })
        assertEquals("发不出去的", h.state.value.input)
        assertEquals("S:steerSendFailed", h.state.value.error)
    }

    /**
     * 裁决 queued：当前流收尾后接住服务端排空开的新回合——即时模式原样复用 A40 的
     * noteCaughtTurn/takeCaughtTurn 路径（协调会话：别自己再造），气泡 id 被 Done
     * 带回的正式服务端 id 原位替换。
     */
    @Test
    fun immediateModeQueuedDispositionDrainsServerTurnAfterCurrentTurnEnds() {
        val h = Harness(
            ChatUiState(
                sending = true, streaming = true,
                messages = listOf(userText("user-1", "正在答")),
            ),
        )
        h.repo.steerResult = com.openapi.feature.chat.domain.model.SteerResult(
            true, com.openapi.feature.chat.domain.model.SteerDisposition.QUEUED, messageId = 42L, turnId = "t-old",
        )
        // 当前回合慢一拍：提交必须在它结束前登记成功
        h.repo.streamFlow = kotlinx.coroutines.flow.flow {
            emit(com.openapi.feature.chat.domain.model.ChatStreamEvent.Delta("mid", "当前"))
            kotlinx.coroutines.delay(100)
            emit(com.openapi.feature.chat.domain.model.ChatStreamEvent.Delta("mid", "回复"))
            emit(com.openapi.feature.chat.domain.model.ChatStreamEvent.Done("asst-1", "server-user-9", "当前回复"))
        }
        h.repo.caughtFlow = kotlinx.coroutines.flow.flow {
            emit(com.openapi.feature.chat.domain.model.ChatStreamEvent.Delta("mid", "排空回复"))
            emit(com.openapi.feature.chat.domain.model.ChatStreamEvent.Done("asst-2", "server-user-42", "排空回复"))
        }

        h.streamer.stream("正在答", userMessageId = "user-1", persistUser = true)
        h.scope.runCurrent()
        h.send.sendText("生成中排的")
        h.scope.runCurrent()
        h.scope.advanceUntilIdle()

        val userTexts = h.state.value.messages.filter { it.role == ChatRole.USER }.map { it.text }
        assertEquals(listOf("正在答", "生成中排的"), userTexts)
        val assistantTexts = h.state.value.messages.filter { it.role == ChatRole.ASSISTANT }.map { it.text }
        assertEquals("两轮回复都在，顺序正确", listOf("当前回复", "排空回复"), assistantTexts)
        val bubble = h.state.value.messages.first { it.text == "生成中排的" }
        assertEquals("Done 后气泡换成正式服务端 id", "server-user-42", bubble.id)
    }

    /** 队列只收纯文字：生成中编辑/附件/位置维持旧的闸，不入队也不清输入。 */
    @Test
    fun onSendWhileSendingKeepsExistingGatesForEditDraftsAndLocation() {
        val editing = Harness(ChatUiState(
            messages = listOf(userText("u1", "原文")), input = "改过的字",
            editingMessageId = "u1", sending = true,
        ))
        editing.send.onSend()
        editing.scope.advanceUntilIdle()
        assertNull("生成中不能提交编辑", editing.submitted)
        assertTrue(editing.state.value.queued.isEmpty())
        assertEquals("改过的字", editing.state.value.input)

        val withDraft = Harness(ChatUiState(input = "带图", drafts = listOf(draft("d1")), sending = true))
        withDraft.send.onSend()
        withDraft.scope.advanceUntilIdle()
        assertTrue("生成中附件不入队", withDraft.state.value.queued.isEmpty())
        assertEquals(0, withDraft.attachmentSends.size)
        assertEquals("带图", withDraft.state.value.input)

        val withLocation = Harness(
            ChatUiState(input = "带位置", locationDraft = com.openapi.feature.chat.domain.model.GeoLocation(31.0, 121.0), sending = true),
        )
        withLocation.send.onSend()
        withLocation.scope.advanceUntilIdle()
        assertTrue("生成中位置不入队", withLocation.state.value.queued.isEmpty())
        assertEquals(0, withLocation.attachmentSends.size)
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
