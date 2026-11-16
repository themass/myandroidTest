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
        val vm = CompanionViewModel(api = null, transport = transport, inboxPollMillis = 60_000)

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
        val vm = CompanionViewModel(api = null, transport = transport, inboxPollMillis = 60_000)

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
        val vm = CompanionViewModel(api = null, transport = transport, inboxPollMillis = 60_000)

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
        val vm = CompanionViewModel(api = null, transport = transport, inboxPollMillis = 60_000)

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
}

/**
 * 记录麦克风意图的传输替身。连接状态可手动推进，用来验证映射这一段。
 */
class RecordingTransport : CompanionTransport {
    val micCalls = mutableListOf<Boolean>()
    val connectionState = MutableStateFlow(TransportConnection.CONNECTED)

    private val bus = MutableSharedFlow<CompanionEvent>(extraBufferCapacity = 8)

    override val connection = connectionState.asStateFlow()

    override suspend fun connect(session: SessionResponse) = Unit

    override fun sendText(text: String) = Unit

    override fun setMicEnabled(enabled: Boolean) {
        micCalls += enabled
    }

    override val events: Flow<CompanionEvent> = bus

    override suspend fun disconnect() = Unit
}
