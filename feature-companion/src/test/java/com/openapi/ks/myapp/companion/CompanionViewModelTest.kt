package com.openapi.ks.myapp.companion

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.openapi.ks.myapp.avatar.ProactiveKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 陪伴 ViewModel 的行为：主动消息怎么进对话、待确认动作怎么恢复与确认。
 * 只用假 API，不碰网络与 LiveKit。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompanionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `a due-event inbox message lands in the conversation as her proactive message`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            inboxBatches = mutableListOf(
                listOf(InboxMessage("m1", "linche", "面试加油，你准备好了吗？", "in_app", "2026-11-07T10:00:00Z", "event_due")),
            ),
        )
        val vm = CompanionViewModel(api = api, transport = FakeCompanionTransport(reply = ""), inboxPollMillis = 1_000)

        vm.start("linche")
        runCurrent()

        val proactive = vm.state.value.messages.single { it.proactive == ProactiveKind.EVENT }
        assertEquals("面试加油，你准备好了吗？", proactive.text)
        assertEquals(MessageRole.ASSISTANT, proactive.role)
        vm.stopListening()
    }

    @Test
    fun `the same inbox message is not shown twice`() = runTest(dispatcher) {
        val message = InboxMessage("m1", "linche", "记得吃药", "in_app", "2026-11-07T10:00:00Z", "event_due")
        val api = FakeCompanionApi(inboxBatches = mutableListOf(listOf(message), listOf(message)))
        val vm = CompanionViewModel(api = api, transport = FakeCompanionTransport(reply = ""), inboxPollMillis = 1_000)

        vm.start("linche")
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, vm.state.value.messages.count { it.text == "记得吃药" })
        vm.stopListening()
    }

    @Test
    fun `pending actions from the server are restored as confirmation cards`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            pending = mutableListOf(
                PendingAction("cfm-1", "linche", "create_reminder", "为你建一条提醒：吃药"),
            ),
        )
        val vm = CompanionViewModel(api = api, transport = FakeCompanionTransport(reply = ""), inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()

        assertEquals("cfm-1", vm.state.value.pendingConfirmation)
        vm.stopListening()
    }

    @Test
    fun `confirming an action removes the card and sends the decision to the server`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            pending = mutableListOf(PendingAction("cfm-1", "linche", "create_reminder", "建提醒")),
        )
        val vm = CompanionViewModel(api = api, transport = FakeCompanionTransport(reply = ""), inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()
        vm.confirmAction("cfm-1")
        runCurrent()

        assertNull(vm.state.value.pendingConfirmation)
        assertEquals(listOf("cfm-1" to true), api.confirmations)
        vm.stopListening()
    }

    @Test
    fun `declining an action sends accepted false`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            pending = mutableListOf(PendingAction("cfm-2", "linche", "create_reminder", "建提醒")),
        )
        val vm = CompanionViewModel(api = api, transport = FakeCompanionTransport(reply = ""), inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()
        vm.cancelAction("cfm-2")
        runCurrent()

        assertEquals(listOf("cfm-2" to false), api.confirmations)
        vm.stopListening()
    }

    @Test
    fun `a failed confirmation is shown as an error instead of silently succeeding`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            pending = mutableListOf(PendingAction("cfm-3", "linche", "create_reminder", "建提醒")),
            confirmFailure = CompanionApiException("登录已失效，请重新登录", statusCode = 401),
        )
        val vm = CompanionViewModel(api = api, transport = FakeCompanionTransport(reply = ""), inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()
        vm.confirmAction("cfm-3")
        runCurrent()

        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("登录"))
        vm.stopListening()
    }

    /**
     * 消息 id 必须稳定且唯一：界面的「删除这一条」按 id 定位，用列表下标会在删完之后删错人。
     * 这条测试同时锁住「用户消息与占位回复各有一个不同的 id」。
     */
    @Test
    fun `every message carries a distinct stable id`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            inboxBatches = mutableListOf(
                listOf(InboxMessage("m1", "linche", "记得吃药", "in_app", "2026-11-07T10:00:00Z", "event_due")),
            ),
        )
        val vm = CompanionViewModel(api = api, transport = FakeCompanionTransport(reply = ""), inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()
        vm.onInputChange("在吗")
        vm.send()
        runCurrent()

        val ids = vm.state.value.messages.map { it.id }
        assertEquals("id 必须唯一", ids.size, ids.distinct().size)
        assertTrue("id 必须已生成（0 是默认值）", ids.none { it == 0L })
        vm.stopListening()
    }

    /** 删除一条之后，其他消息的 id 不变——界面靠它继续定位。 */
    @Test
    fun `deleting a message removes exactly that one and leaves the rest`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            pending = mutableListOf(PendingAction("cfm-1", "linche", "create_reminder", "建提醒")),
        )
        val vm = CompanionViewModel(api = api, transport = FakeCompanionTransport(reply = ""), inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()
        val before = vm.state.value.messages
        val target = before.last().id

        vm.deleteMessage(target)
        runCurrent()

        val after = vm.state.value.messages
        assertEquals("只应删掉一条", before.size - 1, after.size)
        assertTrue(after.none { it.id == target })
        assertEquals("其余消息原样保留", before.dropLast(1).map { it.id }, after.map { it.id })
        vm.stopListening()
    }

    /**
     * 端到端的一环：房间里来的「需要确认」事件必须落成一条带 confirmationId 的消息。
     * 界面那一半由 :app 的 CompanionMessageAreaRenderTest 证明（带 confirmationId 的消息
     * 会渲染出「允许 / 先不用」）。两半合起来才是「用户真的能点」。
     */
    @Test
    fun `a needs-confirmation event from the transport becomes a confirmable message`() = runTest(dispatcher) {
        val transport = ScriptedTransport(
            listOf(
                CompanionEvent.NeedsConfirmation("cfm-9", "为你建一条提醒：周四面试"),
                CompanionEvent.TurnDone,
            ),
        )
        val vm = CompanionViewModel(api = null, transport = transport, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()

        val pending = vm.state.value.messages.last { it.confirmationId != null }
        assertEquals("cfm-9", pending.confirmationId)
        assertEquals("为你建一条提醒：周四面试", pending.text)
        assertEquals("cfm-9", vm.state.value.pendingConfirmation)
        vm.stopListening()
    }

    /** 主动消息事件必须带上来源类型，界面据此显示「她记得 / 她来找你」。 */
    @Test
    fun `a proactive event from the transport keeps its kind for the label`() = runTest(dispatcher) {
        val transport = ScriptedTransport(
            listOf(CompanionEvent.Proactive("面试加油", ProactiveKind.EVENT), CompanionEvent.TurnDone),
        )
        val vm = CompanionViewModel(api = null, transport = transport, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()

        val proactive = vm.state.value.messages.last { it.proactive == ProactiveKind.EVENT }
        assertEquals("面试加油", proactive.text)
        vm.stopListening()
    }

    /**
     * 「重新回答」必须真的能重问：删掉她的回复后 chatkit 会露出这个入口，
     * 所以它不能是个死按钮。这里验证它补了一个流式占位，且不重复添加用户气泡。
     */
    @Test
    fun `regenerating appends a streaming placeholder without duplicating the user message`() = runTest(dispatcher) {
        val transport = FakeCompanionTransport(reply = "")
        val vm = CompanionViewModel(api = null, transport = transport, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()
        vm.onInputChange("在吗")
        vm.send()
        runCurrent()
        // 删掉占位回复，模拟用户删完她的回答后点「重新回答」
        val placeholder = vm.state.value.messages.last()
        vm.deleteMessage(placeholder.id)
        runCurrent()

        val userCountBefore = vm.state.value.messages.count { it.role == MessageRole.USER }
        vm.regenerateLastReply()
        runCurrent()

        assertEquals("不应重复添加用户消息", userCountBefore, vm.state.value.messages.count { it.role == MessageRole.USER })
        val last = vm.state.value.messages.last()
        assertEquals(MessageRole.ASSISTANT, last.role)
        assertTrue("重新回答应补一个流式占位", last.streaming)
        vm.stopListening()
    }
}

/**
 * 按脚本吐事件的传输替身：用来验证「房间事件 → ViewModel state」这一段，
 * 不碰网络与 LiveKit。事件发完后保持打开（真实房间在一次会话里也是长连）。
 */
class ScriptedTransport(private val script: List<CompanionEvent>) : CompanionTransport {
    // replay = 脚本长度：ViewModel 是「先 connect 再 listen」，没有 replay 的话
    // 在 connect 里发出的这些事件会在收集者出现之前就丢掉。
    private val channel = kotlinx.coroutines.flow.MutableSharedFlow<CompanionEvent>(
        replay = script.size,
        extraBufferCapacity = 8,
    )

    override val events: kotlinx.coroutines.flow.Flow<CompanionEvent> = channel

    override suspend fun connect(session: SessionResponse) {
        script.forEach { channel.tryEmit(it) }
    }

    override fun sendText(text: String) = Unit

    override suspend fun disconnect() = Unit
}

/** 假 API：按脚本回放收件箱与待确认动作，记录确认决定。其余接口在这些测试里用不到。 */
class FakeCompanionApi(
    private val inboxBatches: MutableList<List<InboxMessage>> = mutableListOf(),
    private val pending: MutableList<PendingAction> = mutableListOf(),
    private val confirmFailure: Exception? = null,
) : CompanionApi {

    val confirmations = mutableListOf<Pair<String, Boolean>>()

    override suspend fun createSession(characterId: String) =
        SessionResponse("s1", "room", "token", "wss://example.test", "")

    override suspend fun inbox(): List<InboxMessage> =
        if (inboxBatches.isEmpty()) emptyList() else inboxBatches.removeAt(0)

    override suspend fun pendingActions(): List<PendingAction> = pending.toList()

    override suspend fun confirmAction(confirmationId: String, accepted: Boolean): String {
        confirmFailure?.let { throw it }
        confirmations += confirmationId to accepted
        pending.removeAll { it.confirmationId == confirmationId }
        return if (accepted) "executed" else "declined"
    }

    override suspend fun characters(limit: Int, offset: Int): CharacterPage = error("unused")
    override suspend fun character(id: String): CharacterDetail = error("unused")
    override suspend fun memory(roleId: String, limit: Int, offset: Int): FactPage = error("unused")
    override suspend fun deleteMemory(roleId: String, id: String) = error("unused")
    override suspend fun events(roleId: String, includePast: Boolean): List<TimelineEventItem> = error("unused")
    override suspend fun addEvent(roleId: String, title: String, atIso: String, kind: String): TimelineEventItem =
        error("unused")
    override suspend fun setEventStatus(id: String, status: String) = error("unused")
    override suspend fun deleteEvent(id: String) = error("unused")
    override suspend fun preferences(): CompanionPreferences = error("unused")
    override suspend fun updatePreferences(patch: Map<String, Any?>): CompanionPreferences = error("unused")
}
