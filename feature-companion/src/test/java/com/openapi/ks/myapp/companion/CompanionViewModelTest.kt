package com.openapi.ks.myapp.companion

import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertFalse
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

    /**
     * 历史里的图片只有 upload_id，渲染前要逐张换成签名 URL（服务端只给 300 秒，所以不缓存）。
     * 这条钉的是「换到了」那一半：URL 进 images，附件 id 留在 attachmentIds（换源重试要用）。
     */
    @Test
    fun `a history image message gets its signed url`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            session = sessionWithChat(7L),
            historyItems = listOf(CompanionHistoryItem("user", "看这张", listOf("u1"))),
            attachmentUrls = mapOf("u1" to "https://signed/u1"),
        )
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 60_000)

        vm.start("linche")
        settleStartup()

        val message = vm.state.value.messages.single()
        assertEquals("看这张", message.text)
        assertEquals(listOf("u1"), message.attachmentIds)
        assertEquals("URL 要进渲染源", listOf("https://signed/u1"), message.images)
        assertNull(vm.state.value.error)
        vm.stopListening()
    }

    /**
     * Review Focus 5：换不到 URL（404 / 登录失效 / 响应坏了）只让那一张图退化成空路径，
     * 由 chatkit 的 surfaceVariant 底画出灰占位。这条消息的文字与这条消息本身都不能因此消失，
     * 也不能弹一个错误把整页历史变成报错态。
     */
    @Test
    fun `an image whose signed url cannot be fetched degrades to an empty path, keeping the text`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            session = sessionWithChat(7L),
            historyItems = listOf(CompanionHistoryItem("user", "照片在这儿", listOf("u-gone"))),
            attachmentUrls = mapOf("u-gone" to null),
        )
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 60_000)

        vm.start("linche")
        settleStartup()

        val message = vm.state.value.messages.single()
        assertEquals("文字不能因为一张图没了", "照片在这儿", message.text)
        assertEquals("换不到 URL 要留哨兵空串（渲染成灰占位），不是少一张", listOf(""), message.images)
        assertEquals(listOf("u-gone"), message.attachmentIds)
        assertNull("读图失败不是连接失败，不该报错", vm.state.value.error)
        assertEquals("房间照常连着", CompanionConnection.CONNECTED, vm.state.value.connection)
        vm.stopListening()
    }

    /** 一张成一张败：按 attachmentIds 的顺序逐个对应，败的那一格是哨兵，不能被挤掉。 */
    @Test
    fun `a failed url keeps its slot so the remaining images stay in order`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            session = sessionWithChat(7L),
            historyItems = listOf(CompanionHistoryItem("user", "两张", listOf("u1", "u2"))),
            attachmentUrls = mapOf("u1" to "https://signed/u1", "u2" to null),
        )
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 60_000)

        vm.start("linche")
        settleStartup()

        val message = vm.state.value.messages.single()
        assertEquals(listOf("https://signed/u1", ""), message.images)
        vm.stopListening()
    }

    /**
     * 她发一张不带字的照片（「对话」页能发，历史里就有这种行）。以前这种行在解析阶段就被
     * 丢掉了，所以看不到；现在它留在列表里，「对方正在输入…」就不能再当它是等待首字——
     * 图已经发完了，下面挂一条永远不消失的打字提示等于说她还在说话。
     */
    @Test
    fun `an assistant message that carries only an image does not fake a typing indicator`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            session = sessionWithChat(7L),
            historyItems = listOf(CompanionHistoryItem("assistant", "", listOf("u1"))),
            attachmentUrls = mapOf("u1" to "https://signed/u1"),
        )
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 60_000)

        vm.start("linche")
        settleStartup()

        val message = vm.state.value.messages.single()
        assertEquals("", message.text)
        assertEquals(listOf("https://signed/u1"), message.images)
        assertEquals("有图可画就不再是等待首字", false, vm.state.value.awaitingReply)
        vm.stopListening()
    }

    /** 换不到 URL 时那格是哨兵空串，仍然算「有内容可画」（灰占位），不退回打字提示。 */
    @Test
    fun `a failed image still counts as content so the typing indicator stays away`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            session = sessionWithChat(7L),
            historyItems = listOf(CompanionHistoryItem("assistant", "", listOf("u-gone"))),
            attachmentUrls = mapOf("u-gone" to null),
        )
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 60_000)

        vm.start("linche")
        settleStartup()

        assertEquals(listOf(""), vm.state.value.messages.single().images)
        assertEquals(false, vm.state.value.awaitingReply)
        vm.stopListening()
    }

    /** 没附件的历史一条 URL 都不该去换（老数据 + 纯文字是绝大多数）。 */
    @Test
    fun `text only history never asks for an attachment url`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            session = sessionWithChat(7L),
            historyItems = listOf(CompanionHistoryItem("user", "在吗"), CompanionHistoryItem("assistant", "在")),
        )
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 60_000)

        vm.start("linche")
        settleStartup()

        assertEquals(emptyList<String>(), api.attachmentUrlCalls)
        assertTrue(vm.state.value.messages.all { it.images.isEmpty() })
        vm.stopListening()
    }

    /**
     * 一页历史里的图要**并发**换 URL：一次 0.4–2 秒，串行做时五张图就是 2–10 秒才出图。
     * 同时同一个 id 在一页里只换一次。探针在途计数在虚拟时间下是确定值，串行时峰值必然是 1。
     */
    @Test
    fun `signed urls are fetched concurrently and each id only once`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            session = sessionWithChat(7L),
            historyItems = listOf(
                CompanionHistoryItem("user", "两张", listOf("u1", "u2")),
                CompanionHistoryItem("user", "再来一张", listOf("u3")),
                // 同一张图又出现在另一条消息里：不能再问一次
                CompanionHistoryItem("assistant", "看到了", listOf("u1")),
            ),
            attachmentUrls = mapOf("u1" to "https://signed/u1", "u2" to "https://signed/u2", "u3" to "https://signed/u3"),
            attachmentUrlDelayMillis = 1_000,
        )
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 60_000)

        vm.start("linche")
        settleStartup()

        assertEquals("三个不同 id 必须同时在途", 3, api.maxAttachmentUrlInFlight)
        assertEquals("同一个 id 只换一次", listOf("u1", "u2", "u3"), api.attachmentUrlCalls.sorted())
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

    /**
     * 长按动作轮盘的发送入口：动作文案**不经过输入框**直接发（输入框可能是空的，
     * 也不该被动作文案污染）。链路 = transport.sendText + 用户气泡 + 流式占位，
     * 与打字发送同一形态，她的回话才能落进同一条字幕流。
     */
    @Test
    fun `sendActionText sends straight to the transport without touching the input box`() = runTest(dispatcher) {
        val transport = SendingTransport()
        val vm = CompanionViewModel(api = null, transportFactory = { transport }, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()
        vm.onInputChange("草稿不要动")
        vm.sendActionText("摸了摸她的头")
        runCurrent()

        assertEquals("文案要直达传输层", listOf("摸了摸她的头"), transport.sentTexts)
        assertEquals("输入框不能被动作文案污染", "草稿不要动", vm.state.value.input)
        val user = vm.state.value.messages.last { it.role == MessageRole.USER }
        assertEquals("摸了摸她的头", user.text)
        val placeholder = vm.state.value.messages.last { it.role == MessageRole.ASSISTANT }
        assertEquals("要有流式占位等她的回话", true, placeholder.streaming)
        vm.stopListening()
    }

    /** 连线还没断时动作能发；连接不可用时不许把文案打进房间（与 send() 同一道闸）。 */
    @Test
    fun `sendActionText does nothing while the connection is down`() = runTest(dispatcher) {
        val transport = SendingTransport()
        val vm = CompanionViewModel(api = null, transportFactory = { transport }, inboxPollMillis = 60_000)

        vm.sendActionText("摸了摸她的头")
        runCurrent()

        assertEquals("没连上就不该发", emptyList<String>(), transport.sentTexts)
        assertEquals("没连上就不该加气泡", emptyList<MessageRole>(), vm.state.value.messages.map { it.role })
        vm.stopListening()
    }

    // ------------------------------------------------------ 图片消息：发送侧

    /**
     * 协议契约（设计 §5 时序）里最要命的一条就是**顺序**：`control.input` 的 attachments
     * 先到、文本后到，服务端才能把这两条挂成一条消息。反过来发，文本会先触发回合，
     * agent 开口时图片还没登记——表现是「她回了一句『图我看到了』但其实没有图」。
     *
     * 顺序在这里是可以确定断言的：`sendAttachments` 与 `sendText` 都是同步的接口方法，
     * 替身把每一次调用按到达顺序记进同一个 [calls] 序列，上传全成功时这条序列就是
     * 「房间里实际收到的先后」。
     */
    @Test
    fun `picked images are uploaded, then an attachments event goes out before the text`() = runTest(dispatcher) {
        val first = pendingFile("img_1.jpg")
        val second = pendingFile("img_2.jpg")
        val transport = AttachmentTransport()
        val uploader = FakeImageUploader()
        val vm = imageVm(transport, uploader)

        vm.start("linche")
        runCurrent()
        vm.addPendingImage(first)
        vm.addPendingImage(second)
        vm.onInputChange("看这两张")
        vm.send()
        runCurrent()

        assertEquals("两张图各传一次，按选定顺序", listOf("img_1.jpg", "img_2.jpg"), uploader.uploaded)
        assertEquals("attachments 必须先于文本（服务端靠这个把它们挂成一条消息）",
            listOf("sendAttachments", "sendText"), transport.calls)
        assertEquals(listOf("u1", "u2"), transport.attachmentIds)
        assertTrue(
            "client_msg_id 形状 att-<epochMillis>-<seq>，实际=${transport.clientMsgId}",
            Regex("^att-\\d+-\\d+$").matches(transport.clientMsgId),
        )
        assertEquals("文本照常走 lk.chat", listOf("看这两张"), transport.sentTexts)

        val optimistic = vm.state.value.messages.last { it.role == MessageRole.USER }
        assertEquals("乐观消息先用本地路径出图", listOf(first.absolutePath, second.absolutePath), optimistic.images)
        assertEquals("服务端 id 要留住（签名 URL 过期后靠它重试）", listOf("u1", "u2"), optimistic.attachmentIds)
        assertEquals("发完输入框要清空", "", vm.state.value.input)
        assertEquals("待发条要清掉", emptyList<PendingImage>(), vm.state.value.pendingImages)
        vm.stopListening()
    }

    /**
     * Review Focus 4：第二张传失败时**整条不发**。半发的形态是「她收到一句『看这张』
     * 却没有图」，比整条没发出去更糟，也更难解释。
     *
     * 三条都要钉住：文本没进房间、待发条原样留着（用户点一次发送就能重试）、
     * 错误提示出来了（否则用户以为发出去了，只会等她回话）。
     */
    @Test
    fun `a failed upload aborts the whole message and keeps the pending images`() = runTest(dispatcher) {
        val first = pendingFile("img_1.jpg")
        val second = pendingFile("img_2.jpg")
        val third = pendingFile("img_3.jpg")
        val transport = AttachmentTransport()
        val uploader = FakeImageUploader(failOn = "img_2.jpg")
        val vm = imageVm(transport, uploader)

        vm.start("linche")
        runCurrent()
        vm.addPendingImage(first)
        vm.addPendingImage(second)
        vm.addPendingImage(third)
        vm.onInputChange("看这三张")
        vm.send()
        runCurrent()

        // 第三张压根没被碰过：失败之后循环就断了，不是「继续传完再决定发不发」。
        assertEquals("第二张失败就该停住，第三张不必再传", listOf("img_1.jpg", "img_2.jpg"), uploader.uploaded)
        assertEquals("半发是不行的：一条都不许进房间", emptyList<String>(), transport.calls)
        assertEquals("待发图要原样留着待重发", 3, vm.state.value.pendingImages.size)
        assertTrue(
            "要给用户一条人话（实际 error=${vm.state.value.error}）",
            vm.state.value.error?.contains("部分图片上传失败") == true,
        )
        assertEquals("输入框不能白丢", "看这三张", vm.state.value.input)
        assertTrue("闸口要重新放开，否则重试按钮永远按不动", vm.state.value.canSend)
        vm.stopListening()
    }

    /**
     * **裁定**：纯图消息 P1 不做（设计 §5 时序）——companion 的回合由文本触发，
     * 只发图她不会回，用户会对着一条没有回音的消息等。所以只有图时发送键必须是死的。
     */
    @Test
    fun `images without any text are not sendable`() = runTest(dispatcher) {
        val vm = imageVm(AttachmentTransport(), FakeImageUploader())

        vm.start("linche")
        runCurrent()
        vm.addPendingImage(pendingFile("img_1.jpg"))

        assertEquals("房间是连着的，闸口关的不是连接", CompanionConnection.CONNECTED, vm.state.value.connection)
        assertTrue("只有图，不该能发", vm.state.value.pendingImages.isNotEmpty())
        assertFalse(vm.state.value.canSend)

        vm.onInputChange("   ")
        assertFalse("空白的文本也不算话", vm.state.value.canSend)

        vm.onInputChange("看这张")
        assertTrue("配上文本就能发了", vm.state.value.canSend)
        vm.stopListening()
    }

    /**
     * `media.image` 没谈成（老服务端、或还没握手到）时，「+」这个入口本身就是个谎：
     * 用户选了图、点了发送，最后什么都不会发生。所以入口的可见性由协商结果决定，
     * 界面只读这一个标志。两边都断言：没谈成 = false，谈成 = true。
     */
    @Test
    fun `the attachment entry follows the media image negotiation`() = runTest(dispatcher) {
        // 默认替身没有音频/协商能力，等价于「服务端还没跟上」。
        val plain = CompanionViewModel(
            api = null,
            transportFactory = { FakeCompanionTransport(reply = "") },
            mediaUploader = FakeImageUploader(),
            inboxPollMillis = 60_000,
        )
        plain.start("linche")
        runCurrent()
        assertFalse("没协商到 media.image 就不该出现「+」", plain.state.value.imageSupported)
        plain.stopListening()

        val negotiated = AttachmentTransport(negotiated = true)
        val vm = imageVm(negotiated, FakeImageUploader())
        vm.start("linche")
        runCurrent()
        assertTrue(vm.state.value.imageSupported)
        vm.stopListening()
    }

    /** 重发同一条消息时 client_msg_id 必须换一个（服务端拿它做幂等，重复会被当同一条丢掉）。 */
    @Test
    fun `each send gets its own client_msg_id`() = runTest(dispatcher) {
        val transport = AttachmentTransport()
        val uploader = FakeImageUploader()
        val vm = imageVm(transport, uploader)

        vm.start("linche")
        runCurrent()
        vm.addPendingImage(pendingFile("img_1.jpg"))
        vm.onInputChange("第一张")
        vm.send()
        runCurrent()
        val firstId = transport.clientMsgId

        vm.addPendingImage(pendingFile("img_2.jpg"))
        vm.onInputChange("第二张")
        vm.send()
        runCurrent()

        assertTrue("每条消息一个 id，实际=$firstId / ${transport.clientMsgId}",
            firstId != transport.clientMsgId)
        // 两次发送各自一条 attachments 事件、各自一组 id：id 不是一路累加下去的，
        // 否则第二次的图片会被服务端挂到第一次那条消息上。
        assertEquals(2, transport.attachmentEvents.size)
        assertEquals(listOf("u1"), transport.attachmentEvents[0].first)
        assertEquals(listOf("u2"), transport.attachmentEvents[1].first)
        assertEquals(firstId, transport.attachmentEvents[0].second)
        assertEquals(transport.clientMsgId, transport.attachmentEvents[1].second)
        vm.stopListening()
    }

    /**
     * 上传还在途时再按一次发送，不许把同一条消息发两遍：那样服务端会收到两组附件、
     * 她的回合被触发两次，界面上却是同一条气泡——用户看到「我发了一次，她回了两次」。
     *
     * 断言的是**出站次数**而不是 `canSend` 的取值：闸口按住了但代码还是走了一遍，
     * 在这里一样会露出来。
     */
    @Test
    fun `a second send while images are still uploading does not double-send`() = runTest(dispatcher) {
        val transport = AttachmentTransport()
        val uploader = FakeImageUploader(slowMillis = 1_000)
        val vm = imageVm(transport, uploader)

        vm.start("linche")
        runCurrent()
        vm.addPendingImage(pendingFile("img_1.jpg"))
        vm.onInputChange("看这张")
        vm.send()
        // 只推进 500ms：第一张还在传，上传协程没结束。
        advanceTimeBy(500)
        runCurrent()

        assertFalse("上传期间闸口必须关着", vm.state.value.canSend)
        vm.send()
        vm.send()
        runCurrent()

        assertEquals("一条都没发出去（都还没传完）", emptyList<String>(), transport.calls)
        // 让第一串跑完：只该发一次。
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals("传完之后只发一遍", listOf("sendAttachments", "sendText"), transport.calls)
        assertEquals(1, uploader.uploaded.size)
        vm.stopListening()
    }

    /**
     * 评审 Important 2：上传途中换角色（`start()` 重入取消 `sendJob`）之后，发送键必须还能用。
     *
     * `uploading` 的清零点全在被取消的那条协程**内部**（失败分支与 `deliver()`），取消本身
     * 到不了它们。这一条钉的是最干净的那个窗口：`send()` 刚把 `uploading` 置 true、协程还
     * 没轮到跑，`start()` 就把它取消了——协程一行都没执行，什么都不会把它归零，
     * `canSend` 从此恒为 false，发送区死到路由销毁这个 ViewModel 为止。
     */
    @Test
    fun `switching characters while an upload is in flight releases the send button`() = runTest(dispatcher) {
        val transport = AttachmentTransport()
        val uploader = FakeImageUploader(slowMillis = 1_000)
        val vm = imageVm(transport, uploader)

        vm.start("linche")
        runCurrent()
        vm.addPendingImage(pendingFile("img_1.jpg"))
        vm.onInputChange("看这张")
        vm.send()
        // 不给它跑的机会：uploading 已经 true，协程还排在队列里。
        assertTrue("先确认闸口已经关上了", vm.state.value.uploading)

        // 换角色：走 start() 的重入路径（同角色会因为已 CONNECTED 早退，绕不开）。
        vm.start("another")
        runCurrent()

        assertFalse("被取消的上传不能把闸口永久按住", vm.state.value.uploading)
        assertEquals("属于上一个房间的那条消息不许发出去", emptyList<String>(), transport.calls)
        vm.onInputChange("新角色的第一句话")
        assertTrue("闸口要重新放开，否则发送键再也按不动", vm.state.value.canSend)
        vm.stopListening()
    }

    /**
     * 评审 Important 2 的另一半：上传**已经跑起来**之后再换角色。
     *
     * 协程被取消时，图没传完不等于「上传失败」，此时报「部分图片上传失败，已保留待重发」
     * 是句谎话（用户没点失败、也没得可重试，他只是换了个角色）。这一条钉的是：
     * 取消既不留下闸口，也不留下错误提示。
     */
    @Test
    fun `cancelling an in-flight upload does not report a fake upload failure`() = runTest(dispatcher) {
        val transport = AttachmentTransport()
        val uploader = FakeImageUploader(slowMillis = 1_000)
        val vm = imageVm(transport, uploader)

        vm.start("linche")
        runCurrent()
        vm.addPendingImage(pendingFile("img_1.jpg"))
        vm.onInputChange("看这张")
        vm.send()
        advanceTimeBy(500)
        runCurrent()
        assertTrue("先确认上传真的已经在跑", vm.state.value.uploading)

        vm.start("another")
        runCurrent()

        assertFalse("闸口要放开", vm.state.value.uploading)
        assertNull(
            "换角色不是上传失败，别报一句谎（实际 error=${vm.state.value.error}）",
            vm.state.value.error,
        )
        assertEquals("取消掉的那条不许发出去", emptyList<String>(), transport.calls)
        vm.stopListening()
    }

    /**
     * 评审 Important 3：动作轮盘的发送不能绕过上传闸。
     *
     * `sendActionText` 只看连接状态，上传窗口期内它会发出一条纯文字回合，而 attachments
     * 事件还在路上——正是本任务要防的半条消息形状，服务端的 pending 槽还会把图挂错回合。
     */
    @Test
    fun `an action wheel send during an upload does not start a text-only turn`() = runTest(dispatcher) {
        val transport = AttachmentTransport()
        val uploader = FakeImageUploader(slowMillis = 1_000)
        val vm = imageVm(transport, uploader)

        vm.start("linche")
        runCurrent()
        vm.addPendingImage(pendingFile("img_1.jpg"))
        vm.onInputChange("看这张")
        vm.send()
        advanceTimeBy(500)
        runCurrent()

        assertTrue("先确认上传真的还在途", vm.state.value.uploading)
        vm.sendActionText("摸了摸她的头")
        runCurrent()

        assertEquals("上传途中不许有任何出站（动作文案也算）", emptyList<String>(), transport.calls)
        assertFalse("动作文案不该变成气泡", vm.state.value.messages.any { it.text == "摸了摸她的头" })
        // 上传跑完之后，原来那条带图的消息照常发出去，而且只发一遍。
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf("sendAttachments", "sendText"), transport.calls)
        assertEquals(listOf("看这张"), transport.sentTexts)
        vm.stopListening()
    }

    /**
     * 评审 Important 1：attachments 必须**真的发完了**，文本才许发出去。
     *
     * 「调用顺序正确」不够：真实实现如果像 [sendText] 那样把发布 `launch` 成独立协程，
     * 两条协程谁先落到房间由调度器决定，而服务端的 pending 槽只活到**下一次**用户回合——
     * 文本抢跑一次，图片就挂到下一条消息上去。所以契约钉的是「attachments 发布完成之前，
     * `sendText` 一次都不许被调用」。
     */
    @Test
    fun `text is not sent until the attachments publish has actually completed`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val transport = GatedAttachmentTransport(gate)
        val vm = CompanionViewModel(
            api = null,
            transportFactory = { transport },
            mediaUploader = FakeImageUploader(),
            inboxPollMillis = 60_000,
        )

        vm.start("linche")
        runCurrent()
        vm.addPendingImage(pendingFile("img_1.jpg"))
        vm.onInputChange("看这张")
        vm.send()
        runCurrent()

        assertEquals("attachments 要先发出去", listOf("sendAttachments"), transport.calls)
        assertEquals("发布还没完成时，文本一次都不许发", emptyList<String>(), transport.sentTexts)
        gate.complete(Unit)
        runCurrent()

        assertEquals("发布完成后文本才发", listOf("看这张"), transport.sentTexts)
        assertEquals(listOf("sendAttachments", "sendText"), transport.calls)
        assertFalse("发完闸口要放开", vm.state.value.uploading)
        vm.stopListening()
    }

    /**
     * 终评 I-3：attachments **没能落进房间**时，文本一次都不许发，图必须留在待发条里。
     *
     * 上传成功但发布失败（房间断了、协商在通话中间变了、编码失败）以前是静默的：
     * `sendAttachments` 返回 Unit，`deliver` 等完就往下走 `sendTextNow`，于是
     * 「她回了一句『图我看到了』而屏幕上根本没有图」——图既没发出去也没留着，
     * 与 [send] 那条 KDoc 声明的「全有或全无」正好相反。
     *
     * 三条一起钉：文本没进房间、待发条原样还在（再按一次就是完整的一条消息）、
     * 错误提示出来了（否则用户以为发出去了，只会等她回话）。
     */
    @Test
    fun `a failed attachments publish aborts the text and keeps the pending images`() = runTest(dispatcher) {
        val transport = AttachmentTransport(attachmentsSucceed = false)
        val uploader = FakeImageUploader()
        val vm = imageVm(transport, uploader)

        vm.start("linche")
        runCurrent()
        val image = pendingFile("img_1.jpg")
        vm.addPendingImage(image)
        vm.onInputChange("看这张")
        vm.send()
        runCurrent()

        assertEquals("attachments 试过一次", listOf("sendAttachments"), transport.calls)
        assertEquals("没能落进房间就不许发文本", emptyList<String>(), transport.sentTexts)
        assertEquals("图必须留在待发条里（用户再按一次就行）", listOf(image.absolutePath),
            vm.state.value.pendingImages.map { it.path })
        assertEquals("输入框里的话也要留着", "看这张", vm.state.value.input)
        assertNotNull("要告诉用户没发出去", vm.state.value.error)
        assertFalse("闸口要放开，否则发送键再也按不动", vm.state.value.uploading)
        assertFalse("不许落下乐观气泡", vm.state.value.messages.any { it.role == MessageRole.USER })
        vm.stopListening()
    }

    /**
     * 终评 I-3 的后半截：失败之后**再按一次发送**要能真的把这条消息发出去。
     *
     * 「已保留待重发」这句话只有在重发真的可行时才算数——图已经传好了，但 attachments
     * 那条事件要能重发一次才算数。所以这里在**同一个 VM、同一条连接**上翻掉发布结果：
     * 换第二个 VM 只会验证「第二次也是第一次」，验不到重发这条路。
     */
    @Test
    fun `retrying after a failed attachments publish sends the whole message`() = runTest(dispatcher) {
        val transport = AttachmentTransport(attachmentsSucceed = false)
        val vm = imageVm(transport, FakeImageUploader())

        vm.start("linche")
        runCurrent()
        vm.addPendingImage(pendingFile("img_1.jpg"))
        vm.onInputChange("看这张")
        vm.send()
        runCurrent()
        assertEquals("第一次没发出去", emptyList<String>(), transport.sentTexts)
        val attemptsBeforeRetry = transport.calls.size

        // 房间恢复了，用户再按一次发送：这条消息必须完整出去，而且图不重复。
        transport.attachmentsSucceed = true
        vm.send()
        runCurrent()

        assertEquals("重发是完整的一条消息", listOf("sendAttachments", "sendText"),
            transport.calls.drop(attemptsBeforeRetry))
        assertEquals("两次加起来只试过两次发布、发出一条文本", listOf("看这张"), transport.sentTexts)
        assertEquals("待发条要清空，不能留着一张已发出的图", emptyList<String>(), vm.state.value.pendingImages.map { it.path })
        vm.stopListening()
    }

    /**
     * 终评 I-2：「重新回答」不能绕过上传闸。
     *
     * 它和 [CompanionViewModel.sendActionText] 是同一个形状：发出去的都是**另起一条文本回合**，
     * 而 attachments 事件可能还在路上——服务端的 pending 槽只活到下一次用户回合，
     * 文本抢跑一次，图就落到这条「重新回答」上去。以前这里只查连接与 streaming。
     *
     * 形状要先造对：界面上**必须已经有一条用户气泡**（否则它会在「找不到 lastUser」那里
     * 提前 return，测不到闸）。所以先发一条纯文字、把她的占位回复删掉，再发起一条带图的
     * 消息——这正是「图还在传、用户顺手点了重新回答」的那个时刻。
     */
    @Test
    fun `regenerating during an upload does not start a text-only turn`() = runTest(dispatcher) {
        val transport = AttachmentTransport()
        val uploader = FakeImageUploader(slowMillis = 1_000)
        val vm = imageVm(transport, uploader)

        vm.start("linche")
        runCurrent()
        vm.onInputChange("在吗")
        vm.send()
        runCurrent()
        vm.deleteMessage(vm.state.value.messages.last().id) // 删掉她的占位回复：streaming 关掉
        runCurrent()

        vm.addPendingImage(pendingFile("img_1.jpg"))
        vm.onInputChange("看这张")
        vm.send()
        advanceTimeBy(500)
        runCurrent()
        assertTrue("先确认上传真的还在途", vm.state.value.uploading)
        assertTrue("界面上要有那条可重问的用户气泡，否则这条测试是空的",
            vm.state.value.messages.any { it.role == MessageRole.USER })
        // 「在吗」那条纯文本是同步发的，它已经在记录里了；后面比的都是它**之后**新增的出站。
        val baseline = transport.calls.toList()

        vm.regenerateLastReply()
        runCurrent()

        // 这一刻上传还在途，连 sendAttachments 都还没轮到：不许多出任何一次出站。
        assertEquals("上传途中不许有新的出站（重新回答也算）", baseline, transport.calls)
        assertNotNull("点了没反应是最糟的反馈，要说得出原因", vm.state.value.error)
        // 上传跑完之后，原来那条带图的消息照常发出去，而且只发一遍。
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals("重发之外不许多出一次文本回合", listOf("sendAttachments", "sendText"),
            transport.calls.drop(baseline.size))
        assertEquals("重问不能替那条带图的消息多发出一句", listOf("在吗", "看这张"), transport.sentTexts)
        vm.stopListening()
    }

    // ------------------------------------------------------ 历史图片：灰占位可重试（终评 I-4）

    /**
     * 灰占位上点一下要真的能救回来：签名 URL 只活 300 秒，而整页换 URL 一辈子只在
     * `restoreHistory` 里跑一次，所以「打开页面时恰好没换到」的那张图否则就永远是灰块——
     * 尽管 `attachmentIds` 恰恰是为了这一刻才留在消息上的。
     *
     * 假 API 的 URL 表是**活的** Map：第一次换的时候里面是 null（灰占位），点过重试才填上
     * 真地址——这正是「五分钟后 URL 过期 / 当时那次请求恰好失败」的形状。
     */
    @Test
    fun `retrying a failed history image re-fetches only that url and fills it in`() = runTest(dispatcher) {
        val urls = mutableMapOf<String, String?>("u-gone" to null)
        val api = FakeCompanionApi(
            session = sessionWithChat(7L),
            historyItems = listOf(CompanionHistoryItem("user", "照片在这儿", listOf("u-gone"))),
            attachmentUrls = urls,
        )
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 60_000)

        vm.start("linche")
        settleStartup()

        val message = vm.state.value.messages.single()
        assertEquals("第一次没换到就是灰占位", listOf(""), message.images)
        assertEquals("整页只换过一次", listOf("u-gone"), api.attachmentUrlCalls)

        // 用户点了灰块
        urls["u-gone"] = "https://signed/u-gone"
        vm.retryImage(message.id, 0)
        runCurrent()

        val retried = vm.state.value.messages.single()
        assertEquals("重试要把这一格填成真地址", listOf("https://signed/u-gone"), retried.images)
        assertEquals("附件 id 不能被重试弄丢", listOf("u-gone"), retried.attachmentIds)
        assertEquals("重试只重取那一张", listOf("u-gone", "u-gone"), api.attachmentUrlCalls)
        assertNull("读图失败不是错误态，不该弹提示", vm.state.value.error)
        vm.stopListening()
    }

    /** 重试还是换不到：灰占位原样留着（用户能再点一次），不许少一格、也不许报错。 */
    @Test
    fun `retrying an image that still cannot be fetched keeps the placeholder`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            session = sessionWithChat(7L),
            historyItems = listOf(CompanionHistoryItem("user", "照片在这儿", listOf("u-gone"))),
            attachmentUrls = mapOf("u-gone" to null),
        )
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 60_000)

        vm.start("linche")
        settleStartup()

        val message = vm.state.value.messages.single()
        vm.retryImage(message.id, 0)
        runCurrent()

        assertEquals("换不到就什么都不改", listOf(""), vm.state.value.messages.single().images)
        assertEquals("文字不能因为一次重试失败而丢", "照片在这儿", vm.state.value.messages.single().text)
        assertNull(vm.state.value.error)
        vm.stopListening()
    }

    /**
     * 下标语义：只有被点的那一张重取，同一行里**已经换到的**那张不许被一次失败换成灰的。
     * 组件传回来的 index 就是用户点的那一格，这里必须按它逐位对齐。
     */
    @Test
    fun `retrying one image does not touch the other images in the same message`() = runTest(dispatcher) {
        val urls = mutableMapOf<String, String?>("u1" to "https://signed/u1", "u2" to null)
        val api = FakeCompanionApi(
            session = sessionWithChat(7L),
            historyItems = listOf(CompanionHistoryItem("user", "两张", listOf("u1", "u2"))),
            attachmentUrls = urls,
        )
        val vm = CompanionViewModel(api = api, transportFactory = { FakeCompanionTransport(reply = "") }, inboxPollMillis = 60_000)

        vm.start("linche")
        settleStartup()
        assertEquals(listOf("https://signed/u1", ""), vm.state.value.messages.single().images)

        vm.retryImage(vm.state.value.messages.single().id, 1)
        runCurrent()

        assertEquals("只有第二格被重取，第一格原样不动", listOf("https://signed/u1", ""),
            vm.state.value.messages.single().images)
        assertEquals("只问了 u2 一次", listOf("u1", "u2", "u2"), api.attachmentUrlCalls.sorted())
        vm.stopListening()
    }
}

/** 发送侧测试共用的 ViewModel：一条会记调用顺序的房间 + 一个假上传。 */
private fun imageVm(transport: AttachmentTransport, uploader: FakeImageUploader) = CompanionViewModel(
    api = null,
    transportFactory = { transport },
    mediaUploader = uploader,
    inboxPollMillis = 60_000,
)

/** 一个真实存在的待发图片文件（上传替身不读内容，只要路径与名字）。 */
private fun pendingFile(name: String) =
    java.io.File(System.getProperty("java.io.tmpdir"), name).apply { writeText("fake-jpeg") }

/** 假上传：按文件名回一个稳定的 id，`failOn` 那张抛异常（模拟服务端拒绝/断网）。 */
private class FakeImageUploader(
    private val failOn: String? = null,
    /** 每张挂起多久：配合 `advanceTimeBy` 造出「上传还在途」的那个时刻。 */
    private val slowMillis: Long = 0,
) : CompanionViewModel.ImageUploader {
    val uploaded = mutableListOf<String>()
    override suspend fun upload(file: java.io.File, mime: String): String {
        uploaded += file.name
        if (slowMillis > 0) kotlinx.coroutines.delay(slowMillis)
        if (file.name == failOn) throw com.openapi.common.media.MediaUploadException("上传失败（HTTP 503）", 503)
        return "u${uploaded.size}"
    }
}

/**
 * 记录出站调用**顺序**的替身：`calls` 就是「房间按什么顺序收到了什么」，
 * 发送顺序这条契约只有在这里才看得见。
 *
 * 默认按「`media.image` 已谈成」装配——它是发送路径的替身，不协商图片的话测的就不是
 * 发送路径了。协商位本身那条（谈不成时「+」不出现）单独有用例。
 *
 * [attachmentsSucceed] 模拟发布这一步的结果（终评 I-3）：false = 房间没了 / 协商变了 /
 * 编码或发布失败，`sendAttachments` 回 false，调用方必须**不发文本**。
 */
private class AttachmentTransport(
    negotiated: Boolean = true,
    /** 可翻：`false` 模拟这一次没落进房间（房间断了/协商变了/编码或发布失败），翻回 `true` 就是用户再按一次发送时房间恢复了。 */
    var attachmentsSucceed: Boolean = true,
) : CompanionTransport {
    val calls = mutableListOf<String>()
    val sentTexts = mutableListOf<String>()
    /** 每一次 sendAttachments 原样记下来：只留最后一次的值会让「id 累加」这种错看不出来。 */
    val attachmentEvents = mutableListOf<Pair<List<String>, String>>()
    var attachmentIds: List<String> = emptyList()
    var clientMsgId: String = ""

    override val imageSupported: kotlinx.coroutines.flow.StateFlow<Boolean> =
        kotlinx.coroutines.flow.MutableStateFlow(negotiated)
    override val events: kotlinx.coroutines.flow.Flow<CompanionEvent> =
        kotlinx.coroutines.flow.MutableSharedFlow()
    override suspend fun connect(session: SessionResponse) = Unit
    override fun sendText(text: String) {
        calls += "sendText"
        sentTexts += text
    }

    override suspend fun sendAttachments(uploadIds: List<String>, clientMsgId: String): Boolean {
        calls += "sendAttachments"
        attachmentIds = uploadIds
        this.clientMsgId = clientMsgId
        attachmentEvents += uploadIds to clientMsgId
        return attachmentsSucceed
    }

    override suspend fun disconnect() = Unit
}

/**
 * attachments 发布**不会立刻完成**的替身：[sendAttachments] 挂起直到 [gate] 被 complete，
 * 模拟真实实现里那次 `publishData` 往返。
 *
 * 它存在的唯一理由是把「调用过」和「发完了」这两件事分开：如果实现只是顺序调两个方法
 * （或把发布 `launch` 出去不等它），这条用例会在 `gate.complete` 之前就看见 `sendText`。
 */
private class GatedAttachmentTransport(
    private val gate: CompletableDeferred<Unit>,
    /** 发布完成后回给调用方的结果（终评 I-3）：false = 没能落进房间。 */
    private val result: Boolean = true,
) : CompanionTransport {
    val calls = mutableListOf<String>()
    val sentTexts = mutableListOf<String>()

    override val imageSupported: kotlinx.coroutines.flow.StateFlow<Boolean> =
        kotlinx.coroutines.flow.MutableStateFlow(true)
    override val events: kotlinx.coroutines.flow.Flow<CompanionEvent> =
        kotlinx.coroutines.flow.MutableSharedFlow()
    override suspend fun connect(session: SessionResponse) = Unit
    override suspend fun sendAttachments(uploadIds: List<String>, clientMsgId: String): Boolean {
        calls += "sendAttachments"
        gate.await()
        return result
    }

    override fun sendText(text: String) {
        calls += "sendText"
        sentTexts += text
    }

    override suspend fun disconnect() = Unit
}

/** 记录 sendText 文案的传输替身：动作发送路径的唯一证据就在「房间收到了什么」。 */
private class SendingTransport : CompanionTransport {
    val sentTexts = mutableListOf<String>()
    override val events: kotlinx.coroutines.flow.Flow<CompanionEvent> =
        kotlinx.coroutines.flow.MutableSharedFlow()
    override suspend fun connect(session: SessionResponse) = Unit
    override fun sendText(text: String) { sentTexts.add(text) }
    override suspend fun disconnect() = Unit
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
    /** 历史脚本：`restoreHistory` 按这个顺序回放（服务端已按新→旧翻转正序）。 */
    private val historyItems: List<CompanionHistoryItem> = emptyList(),
    /** 换签名 URL 的脚本：值为 null 表示「换不到」（404 / 登录失效 / 响应坏了）。没列出的 id 同样算换不到。 */
    private val attachmentUrls: Map<String, String?> = emptyMap(),
    /** 每次换 URL 挂起多久：配合在途计数证明并发而不是串行。 */
    private val attachmentUrlDelayMillis: Long = 0,
) : CompanionApi {

    val confirmations = mutableListOf<Pair<String, Boolean>>()

    /** 换 URL 的调用顺序与并发峰值：串行时峰值必然是 1。 */
    val attachmentUrlCalls = mutableListOf<String>()
    var maxAttachmentUrlInFlight = 0
        private set
    private var inFlight = 0

    override suspend fun createSession(characterId: String) = session

    override suspend fun createScriptSession(characterId: String, caseId: String) = session

    override suspend fun history(sessionId: Long, limit: Int): List<CompanionHistoryItem> = historyItems

    override suspend fun attachmentUrl(uploadId: String): String? {
        inFlight++
        maxAttachmentUrlInFlight = maxOf(maxAttachmentUrlInFlight, inFlight)
        attachmentUrlCalls += uploadId
        if (attachmentUrlDelayMillis > 0) kotlinx.coroutines.delay(attachmentUrlDelayMillis)
        val url = attachmentUrls[uploadId]
        inFlight--
        return url
    }

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

/**
 * 把「进页 → 建房 → 读历史 → 换图片 URL」这条启动链跑到稳定。
 *
 * 这里**不能**用 `advanceUntilIdle()`：收件箱轮询是 `delay(pollMillis)` 的无限循环，
 * 虚拟时间永远追不到「空闲」，测试会挂死（不是慢，是永远不返回）。所以只推进到轮询间隔
 * 之前——30 秒远够启动链和挂起 1 秒的换 URL 跑完，又踩不到 60 秒那一跳。
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun kotlinx.coroutines.test.TestScope.settleStartup() {
    advanceTimeBy(30_000)
    runCurrent()
}

/**
 * 带 chatSessionId 的会话：历史回放只在服务端绑定了文字会话时才做
 * （没有它连读什么都读不到），所以读路径的测试都要先给它一个。
 */
private fun sessionWithChat(chatSessionId: Long) = SessionResponse(
    sessionId = "s1",
    roomName = "room",
    token = "token",
    livekitUrl = "wss://example.test",
    expiresAt = "",
    chatSessionId = chatSessionId,
)
