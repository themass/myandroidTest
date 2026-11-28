package com.openapi.ks.myapp.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 通话页举手键的一键两义语义（C7，OLLMV `use-footer.ts:35-44`）：
 * 她在说 = 打断；空闲 = 催她主动说话。
 *
 * 键只管两个出口中的一个决定：按下去做什么。这里钉 UI 决策与 ViewModel 转发，
 * 打断/催话的**执行**分别在传输层（sendBargeIn / control.nudge）与去重闸
 * （[com.openapi.ks.myapp.companion.protocol.NudgeGateTest]）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompanionHandKeyTest {
    init {
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
    fun `the hand key label follows whether she is speaking`() {
        assertEquals("打断", callHandActionLabel(isAgentSpeaking = true))
        assertEquals("叫她说话", callHandActionLabel(isAgentSpeaking = false))
    }

    @Test
    fun `the label never mixes the two meanings in one phrase`() {
        // 标签必须单一语义：混着写（"打断/催话"）会让用户在说话中误以为按了催话。
        val speaking = callHandActionLabel(isAgentSpeaking = true)
        val idle = callHandActionLabel(isAgentSpeaking = false)
        assertEquals(false, speaking.contains("叫"))
        assertEquals(false, idle.contains("打断"))
    }

    @Test
    fun `raiseHand is forwarded to the transport once per press`() = runTest(dispatcher) {
        val transport = HandRecordingTransport()
        val vm = CompanionViewModel(api = null, transportFactory = { transport }, inboxPollMillis = 60_000)

        vm.start("linche")
        runCurrent()

        vm.raiseHand()
        runCurrent()
        vm.raiseHand()
        runCurrent()

        assertEquals(2, transport.raiseHandCalls)
        vm.stopListening()
    }
}

/** 记录举手键按次的传输替身。 */
private class HandRecordingTransport : CompanionTransport {
    var raiseHandCalls = 0

    override suspend fun connect(session: SessionResponse) = Unit

    override fun sendText(text: String) = Unit

    override fun raiseHand() {
        raiseHandCalls++
    }

    override val events: Flow<CompanionEvent> = MutableSharedFlow()

    override suspend fun disconnect() = Unit
}
