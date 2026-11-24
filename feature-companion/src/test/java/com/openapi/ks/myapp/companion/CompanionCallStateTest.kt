package com.openapi.ks.myapp.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 通话形态的状态机：进/退通话、静音，以及「房间连接到了哪一步」怎么进到界面。
 *
 * 以前这些全是硬编码：界面自己写死 isMuted=false、connection 只有「连着/没连着」两档，
 * 掉线重连时用户看到的是一个永远显示「已连接」的页面。这里把那段映射钉死。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompanionCallStateTest {
    init {
        // JVM 单测没有资源环境：VM 的错误文案走标记串（cleanup 项 2）。
        // 装在这里而不是逐个构造点传参——VM 的 `strings` 有默认值，38 处构造点不必各自记一次。
        CompanionDependencies.installStrings(FakeCompanionStrings())
    }

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `entering a call without mic permission still opens the call, muted`() = runTest(dispatcher) {
        val transport = RecordingTransport()
        val vm = CompanionViewModel(api = null, transportFactory = { transport }, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()

        vm.startCall(micAllowed = false)
        runCurrent()

        // 拒绝权限不该把人挡在通话外：她还在说，字幕还在来，只是自己不出声。
        assertTrue(vm.state.value.callActive)
        assertTrue(vm.state.value.isMuted)
        assertEquals(listOf(false), transport.micCalls)
        vm.stopListening()
    }

    @Test
    fun `mute switches both the ui state and the transport`() = runTest(dispatcher) {
        val transport = RecordingTransport()
        val vm = CompanionViewModel(api = null, transportFactory = { transport }, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()
        vm.startCall(micAllowed = true)

        vm.toggleMute()
        runCurrent()
        assertTrue("第一下应静音", vm.state.value.isMuted)

        vm.toggleMute()
        runCurrent()
        assertFalse("第二下应回到开麦", vm.state.value.isMuted)

        // 界面状态和真实发布必须同步下发，否则按钮和耳朵听到的对不上。
        assertEquals(listOf(true, false, true), transport.micCalls)
        vm.stopListening()
    }

    @Test
    fun `hanging up leaves the call and turns the microphone off`() = runTest(dispatcher) {
        val transport = RecordingTransport()
        val vm = CompanionViewModel(api = null, transportFactory = { transport }, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()
        vm.startCall(micAllowed = true)
        vm.endCall()
        runCurrent()

        assertFalse(vm.state.value.callActive)
        assertFalse("挂断后不该留着静音标记", vm.state.value.isMuted)
        assertEquals(listOf(true, false), transport.micCalls)
        vm.stopListening()
    }

    @Test
    fun `room reconnecting and disconnecting reach the ui instead of staying connected`() = runTest(dispatcher) {
        val transport = RecordingTransport()
        val vm = CompanionViewModel(api = null, transportFactory = { transport }, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()
        assertEquals(CompanionConnection.CONNECTED, vm.state.value.connection)

        transport.connectionState.value = TransportConnection.RECONNECTING
        runCurrent()
        assertEquals(CompanionConnection.RECONNECTING, vm.state.value.connection)

        transport.connectionState.value = TransportConnection.DISCONNECTED
        runCurrent()
        // 断开不是「空闲」：界面要给出原因，所以归到错误态。
        assertEquals(CompanionConnection.ERROR, vm.state.value.connection)
        vm.stopListening()
    }

    /**
     * 401（未登录点通话）必须和普通的断线分开：界面横幅是「请先登录」+「去登录」，
     * 这时候「重新连接」是误导——再点一百次也一样 401。真机验这条要 logout，
     * 凭证不在验收会话手里（可能不可恢复），所以这条由单测钉死、真机列 BLOCKED。
     */
    @Test
    fun `a 401 at session creation marks loginRequired instead of a plain disconnect`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            sessionFailure = CompanionApiException("登录后才能使用通话", statusCode = 401),
        )
        val transport = RecordingTransport()
        val vm = CompanionViewModel(api = api, transportFactory = { transport }, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()

        assertEquals(CompanionConnection.ERROR, vm.state.value.connection)
        assertTrue("401 必须置 loginRequired（横幅走「去登录」分支）", vm.state.value.callLoginRequired)
        vm.stopListening()
    }

    /** 非 401 的建连失败不走登录分支：横幅该给「重新连接」。 */
    @Test
    fun `a non-401 start failure keeps loginRequired false so the retry button applies`() = runTest(dispatcher) {
        val api = FakeCompanionApi(
            sessionFailure = CompanionApiException("服务 unavailable", statusCode = 503),
        )
        val transport = RecordingTransport()
        val vm = CompanionViewModel(api = api, transportFactory = { transport }, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()

        assertEquals(CompanionConnection.ERROR, vm.state.value.connection)
        assertFalse("503 不该出现「去登录」", vm.state.value.callLoginRequired)
        vm.stopListening()
    }

    /**
     * 「重新连接」按钮按下 = retryConnection：先断开旧房间，再按同一角色重建。
     * 断线横幅的整条点击链路（disconnect → start → 重新连上 → 横幅消失）到传输层
     * 边界为止都在 ViewModel 里，这一段必须不依赖真机与额度。
     */
    @Test
    fun `retry after disconnect rebuilds the same room and the error state clears`() = runTest(dispatcher) {
        val transport = RecordingTransport()
        val vm = CompanionViewModel(api = null, transportFactory = { transport }, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()
        transport.connectionState.value = TransportConnection.DISCONNECTED
        runCurrent()
        assertEquals(CompanionConnection.ERROR, vm.state.value.connection)

        vm.retryConnection("linche")
        runCurrent()
        assertEquals("重试必须先干净断开旧房间", 1, transport.disconnectCalls.size)

        // 替身不会自己把连接状态推回来（真机是 LiveKit 事件驱动）：手动推它连上，
        // 断言的是「重建后的房间连上 → ERROR 解除 → 横幅的前提消失」。
        transport.connectionState.value = TransportConnection.CONNECTED
        runCurrent()
        assertEquals("重建成会话并连上后，横幅前提（ERROR 态）解除", CompanionConnection.CONNECTED, vm.state.value.connection)
        assertFalse(vm.state.value.callLoginRequired)
        vm.stopListening()
    }
}

/**
 * 记录麦克风意图的传输替身。连接状态可手动推进，用来验证映射这一段。
 */
class RecordingTransport : CompanionTransport {
    val micCalls = mutableListOf<Boolean>()
    val disconnectCalls = mutableListOf<Unit>()
    val connectionState = MutableStateFlow(TransportConnection.CONNECTED)

    private val bus = MutableSharedFlow<CompanionEvent>(extraBufferCapacity = 8)

    override val connection = connectionState.asStateFlow()

    override suspend fun connect(session: SessionResponse) = Unit

    override fun sendText(text: String) = Unit

    override fun setMicEnabled(enabled: Boolean) {
        micCalls += enabled
    }

    override val events: Flow<CompanionEvent> = bus

    override suspend fun disconnect() {
        disconnectCalls += Unit
    }
}
