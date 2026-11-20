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
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 1_000)

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
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 1_000)

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
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 60_000)

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
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 60_000)

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
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 60_000)

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
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 60_000)

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
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 60_000)

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
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 60_000)

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
        val vm = CompanionViewModel(api = null, transportFactory = { transport }, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()

        val pending = vm.state.value.messages.last { it.confirmationId != null }
        assertEquals("cfm-9", pending.confirmationId)
        assertEquals("为你建一条提醒：周四面试", pending.text)
        assertEquals("cfm-9", vm.state.value.pendingConfirmation)
        vm.stopListening()
    }

    /**
     * B3 的一环：`AvatarJson`（emotion cue）必须落进 `state.emotion`——这条
     * cue→UI state 的链路以前在 VM 层一个用例都没有。界面那一跳由 mapper 的用例钉住。
     */
    @Test
    fun `an emotion cue from the transport lands in ui state`() = runTest(dispatcher) {
        val transport = ScriptedTransport(
            listOf(CompanionEvent.AvatarJson("""{"v":1,"type":"emotion","emotion":"Joy"}""")),
        )
        val vm = CompanionViewModel(api = null, transportFactory = { transport }, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()

        assertEquals("joy", vm.state.value.emotion)
        vm.stopListening()
    }

    /**
     * B3：降级进 L2 后 cue 全被端侧丢掉，卡在脸上的表情等不到下一条 emotion。
     * 传输层在升档那条边发 [CompanionEvent.ClearAvatarEmotion]，ViewModel 必须把
     * state.emotion 收回 null——若这条指令不生效，上一条 cue 留下的 "joy" 会留在这里。
     */
    @Test
    fun `a degrade clear wipes the stuck emotion out of ui state`() = runTest(dispatcher) {
        val transport = ScriptedTransport(
            listOf(
                CompanionEvent.AvatarJson("""{"v":1,"type":"emotion","emotion":"Joy"}"""),
                CompanionEvent.ClearAvatarEmotion,
            ),
        )
        val vm = CompanionViewModel(api = null, transportFactory = { transport }, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()

        assertNull("降级清除后 state 里不能还留着 joy", vm.state.value.emotion)
        vm.stopListening()
    }

    /** 主动消息事件必须带上来源类型，界面据此显示「她记得 / 她来找你」。 */
    @Test
    fun `a proactive event from the transport keeps its kind for the label`() = runTest(dispatcher) {
        val transport = ScriptedTransport(
            listOf(CompanionEvent.Proactive("面试加油", ProactiveKind.EVENT), CompanionEvent.TurnDone),
        )
        val vm = CompanionViewModel(api = null, transportFactory = { transport }, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()

        val proactive = vm.state.value.messages.last { it.proactive == ProactiveKind.EVENT }
        assertEquals("面试加油", proactive.text)
        vm.stopListening()
    }

    /**
     * 语音通话的字幕回归：语音链路里没有人先发 send()，所以**没有**流式占位，
     * 服务端推来的 TextDelta 以前全被 appendToStreaming 丢掉——字幕永远是空的，
     * 「字幕开/关」看起来毫无效果。第一个 delta 必须自己开一条流式消息。
     */
    @Test
    fun `a text delta without a placeholder starts a streaming caption`() = runTest(dispatcher) {
        val transport = ScriptedTransport(
            listOf(
                CompanionEvent.TextDelta("你好，"),
                CompanionEvent.TextDelta("今天过得怎么样？"),
                CompanionEvent.TurnDone,
            ),
        )
        val vm = CompanionViewModel(api = null, transportFactory = { transport }, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()

        val reply = vm.state.value.messages.single { it.role == MessageRole.ASSISTANT }
        assertEquals("你好，今天过得怎么样？", reply.text)
        assertEquals("TurnDone 之后不再是流式", false, reply.streaming)
        vm.stopListening()
    }

    /**
     * 「重新回答」必须真的能重问：删掉她的回复后 chatkit 会露出这个入口，
     * 所以它不能是个死按钮。这里验证它补了一个流式占位，且不重复添加用户气泡。
     */
    @Test
    fun `regenerating appends a streaming placeholder without duplicating the user message`() = runTest(dispatcher) {
        val transport = FakeCompanionTransport(reply = "")
        val vm = CompanionViewModel(api = null, transportFactory = { transport }, inboxPollMillis = 60_000)

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

    // ------------------------------------------------------ provider 分发

    /**
     * 线路切换的客户端一侧（docs/companion-rtc-provider-switching.md §2）：
     * provider 由服务端在建会话时定、随 grant 下发；ViewModel 只把 grant 里的
     * provider 递给工厂，自己不认识任何具体 RTC 实现。
     */
    @Test
    fun `the factory gets the provider the server chose in the grant`() = runTest(dispatcher) {
        val requested = mutableListOf<String>()
        val api = FakeCompanionApi(
            session = SessionResponse("s1", "room", "token", "wss://example.test", "", provider = "volcengine"),
        )
        val vm = CompanionViewModel(
            api = api,
            transportFactory = { provider -> requested += provider; RecordingProbe() },
            inboxPollMillis = 60_000,
        )

        vm.start("linche")
        runCurrent()

        // 构造时按默认线路装配一次，拿到 grant 后发现服务端指定了别的线路再换。
        assertEquals(listOf(CompanionTransports.PROVIDER_LIVEKIT, "volcengine"), requested)
        vm.stopListening()
    }

    /** grant 里没有 provider（老服务端）→ 按 livekit 装配，行为与以前一致。 */
    @Test
    fun `a grant without a provider means livekit`() = runTest(dispatcher) {
        val requested = mutableListOf<String>()
        val vm = CompanionViewModel(
            api = FakeCompanionApi(), // 默认 SessionResponse：provider 取默认值 livekit
            transportFactory = { provider -> requested += provider; RecordingProbe() },
            inboxPollMillis = 60_000,
        )

        vm.start("linche")
        runCurrent()

        assertEquals(listOf(CompanionTransports.PROVIDER_LIVEKIT), requested)
        vm.stopListening()
    }

    /** 服务端没切线路（provider 没变）→ 不得重建 transport，否则会白白换掉替身。 */
    @Test
    fun `the same provider does not rebuild the transport`() = runTest(dispatcher) {
        val probe = RecordingProbe()
        var created = 0
        val vm = CompanionViewModel(
            api = FakeCompanionApi(),
            transportFactory = { created++; probe },
            inboxPollMillis = 60_000,
        )

        vm.start("linche")
        runCurrent()
        vm.start("linche") // 重进同一角色（重连路径）
        runCurrent()

        assertEquals("provider 没变就不该再向工厂要实现", 1, created)
        assertEquals(1, probe.connects)
        vm.stopListening()
    }

    /** 线路换了 → 旧实现先断开、新实现接上并连上新会话（容灾换线的客户端形态）。 */
    @Test
    fun `a provider change swaps the transport and connects the new one`() = runTest(dispatcher) {
        val livekit = RecordingProbe()
        val volc = RecordingProbe()
        val api = FakeCompanionApi(
            session = SessionResponse("s1", "room", "token", "wss://example.test", "", provider = "livekit"),
        )
        val vm = CompanionViewModel(
            api = api,
            transportFactory = { provider -> if (provider == "volcengine") volc else livekit },
            inboxPollMillis = 60_000,
        )

        vm.start("linche")
        runCurrent()
        // 服务端把这个会话切到了火山线路：再发起一次通话必须换实现。
        // 换一个角色 id 绕过「同角色不重连」的守卫，模拟的就是新通话。
        api.session = SessionResponse("s2", "room2", "t2", "wss://example.test", "", provider = "volcengine")
        vm.start("linche2")
        runCurrent()

        assertEquals("旧线路要先断开", 1, livekit.disconnects)
        assertEquals("新线路要连上新会话", 1, volc.connects)
        vm.stopListening()
    }
}

/** 记录 connect/disconnect 次数的探针，用于验证 provider 换线时的实现交接。 */
private class RecordingProbe : CompanionTransport {
    var connects = 0
    var disconnects = 0
    override val events: kotlinx.coroutines.flow.Flow<CompanionEvent> =
        kotlinx.coroutines.flow.MutableSharedFlow()
    override suspend fun connect(session: SessionResponse) { connects++ }
    override fun sendText(text: String) = Unit
    override suspend fun disconnect() { disconnects++ }
}


/**
 * 按脚本吐事件的传输替身：用来验证「房间事件 → ViewModel state」这一段，
 * 不碰网络与 LiveKit。事件发完后保持打开（真实房间在一次会话里也是长连）。
 */
class ScriptedTransport(private val script: List<CompanionEvent>) : CompanionTransport {
    // replay = 脚本长度：ViewModel 统一「先 listen 再 connect」，这里保留 replay
    // 只是为了对订阅时序不敏感（connect 前发出的事件也能被补上）。
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
    /** 可以带 provider：验证「服务端在 grant 里指定线路 → ViewModel 按它取实现」。 */
    var session: SessionResponse = SessionResponse("s1", "room", "token", "wss://example.test", ""),
) : CompanionApi {

    val confirmations = mutableListOf<Pair<String, Boolean>>()

    override suspend fun createSession(characterId: String) = session

    override suspend fun createScriptSession(characterId: String, caseId: String) = session

    override suspend fun history(sessionId: Long, limit: Int): List<CompanionHistoryItem> = emptyList()

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
