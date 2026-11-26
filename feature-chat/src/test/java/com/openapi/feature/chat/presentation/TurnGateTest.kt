package com.openapi.feature.chat.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChatTurnGate]：「这一轮占不占着会话」的读侧判定表（最小收敛）。
 * 每条都对应散在 SendActions/HistoryActions/ResendActions 里的一处旧闸。
 */
class TurnGateTest {

    private fun gate(
        sending: Boolean = false,
        loadingOlder: Boolean = false,
        hasMore: Boolean = true,
        editing: String? = null,
        drafts: Boolean = false,
        location: Boolean = false,
        sendMode: com.openapi.feature.chat.presentation.SendMode =
            com.openapi.feature.chat.presentation.SendMode.Queue,
    ) = ChatTurnGate(
        sending = sending,
        loadingOlder = loadingOlder,
        hasMoreHistory = hasMore,
        editingMessageId = editing,
        hasAttachmentDrafts = drafts,
        hasLocationDraft = location,
        sendMode = sendMode,
    )

    @Test
    fun idleSendGoesDirect() {
        assertTrue(gate().sendPath() == ChatTurnGate.SendPath.Direct)
    }

    @Test
    fun plainTextWhileTurnRunningQueues() {
        val g = gate(sending = true)
        assertTrue(g.sendPath() == ChatTurnGate.SendPath.Queue)
        assertTrue(g.textSendPath() == ChatTurnGate.SendPath.Queue)
    }

    // D1（设计 20261126 §2 D1）：默认 Immediate——生成中纯文字不再本地排队，直发交给服务端裁决。

    @Test
    fun immediateModeSendsPlainTextDirectlyWhileTurnRunning() {
        val g = gate(sending = true, sendMode = com.openapi.feature.chat.presentation.SendMode.Immediate)
        assertTrue(g.sendPath() == ChatTurnGate.SendPath.Direct)
        assertTrue(g.textSendPath() == ChatTurnGate.SendPath.Direct)
    }

    @Test
    fun gateDefaultSendModeIsImmediate() {
        // ChatUiState 的默认值就是 Immediate（用户不设置就是即时发送）——
        // 从 of() 走一遍钉住，防默认值哪天被翻过去测试还全绿。
        val g = ChatTurnGate.of(ChatUiState(sending = true))
        assertTrue(g.sendMode == com.openapi.feature.chat.presentation.SendMode.Immediate)
        assertTrue(g.sendPath() == ChatTurnGate.SendPath.Direct)
    }

    @Test
    fun immediateModeStillBlocksEditDraftsAndLocation() {
        // 开关只管纯文字：编辑/附件/位置在两种模式下都被同一个闸挡住。
        val immediate = com.openapi.feature.chat.presentation.SendMode.Immediate
        assertTrue(gate(sending = true, editing = "m1", sendMode = immediate).sendPath() == ChatTurnGate.SendPath.BlockedTurnRunning)
        assertTrue(gate(sending = true, drafts = true, sendMode = immediate).sendPath() == ChatTurnGate.SendPath.BlockedTurnRunning)
        assertTrue(gate(sending = true, location = true, sendMode = immediate).sendPath() == ChatTurnGate.SendPath.BlockedTurnRunning)
    }

    @Test
    fun editingAttachmentAndLocationAreBlockedWhileTurnRunning() {
        assertTrue(gate(sending = true, editing = "m1").sendPath() == ChatTurnGate.SendPath.BlockedTurnRunning)
        assertTrue(gate(sending = true, drafts = true).sendPath() == ChatTurnGate.SendPath.BlockedTurnRunning)
        assertTrue(gate(sending = true, location = true).sendPath() == ChatTurnGate.SendPath.BlockedTurnRunning)
    }

    @Test
    fun attachmentPickIsBlockedOnlyWhileTurnRunning() {
        assertTrue(gate().allowAttachmentPick)
        assertFalse(gate(sending = true).allowAttachmentPick)
    }

    @Test
    fun resendIsBlockedOnlyWhileTurnRunning() {
        assertTrue(gate().allowResend)
        assertFalse(gate(sending = true).allowResend)
    }

    @Test
    fun loadOlderIgnoresTheTurnAndOnlyRespectsItsOwnTwoFlags() {
        // 读历史与回合无关：sending 不再挡（任务 4 删掉手动按钮后，滚到顶必须还有反应）
        assertTrue(gate(sending = true).allowLoadOlder)
        assertFalse(gate(loadingOlder = true).allowLoadOlder)
        assertFalse(gate(hasMore = false).allowLoadOlder)
    }

    @Test
    fun ofDerivesEverythingFromTheStateSnapshot() {
        val state = ChatUiState(
            sending = true,
            loadingOlder = true,
            hasMoreHistory = true,
            editingMessageId = "e1",
            drafts = listOf(DraftAttachment("d", com.openapi.feature.chat.domain.model.MessageKind.IMAGE, StagedAttachment(java.io.File("/tmp/d.jpg"), "d.jpg", 1L))),
            locationDraft = com.openapi.feature.chat.domain.model.GeoLocation(1.0, 2.0),
        )
        val g = ChatTurnGate.of(state)
        assertTrue(g.sending && g.loadingOlder && g.hasMoreHistory)
        assertTrue(g.hasAttachmentDrafts && g.hasLocationDraft)
        assertTrue(g.editingMessageId == "e1")
    }
}
