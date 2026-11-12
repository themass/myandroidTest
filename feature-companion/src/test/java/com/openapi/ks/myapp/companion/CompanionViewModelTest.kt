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
