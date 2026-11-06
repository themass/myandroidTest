package com.openapi.ks.myapp.companion

import com.openapi.ks.myapp.avatar.ProactiveKind

/** 对话模式。第一版默认文字，用户可切到语音。 */
enum class CompanionMode { TEXT, VOICE }

enum class CompanionConnection { IDLE, CONNECTING, CONNECTED, ERROR }

enum class MessageRole { USER, ASSISTANT }

data class CompanionMessage(
    val role: MessageRole,
    val text: String,
    /** 正在流式接收：界面显示打字光标，不显示操作按钮。 */
    val streaming: Boolean = false,
    /**
     * 这条消息是不是她主动发起的。主动消息的呈现要与回复区分开——用户需要一眼
     * 看出「没人跟她说话，是她自己开口的」，否则会觉得系统在替他说话。
     */
    val proactive: ProactiveKind = ProactiveKind.REPLY,
    /** 待确认的动作 id。非空时界面显示确认/取消，而不是当作普通消息。 */
    val confirmationId: String? = null,
)

data class CompanionUiState(
    val mode: CompanionMode = CompanionMode.TEXT,
    val connection: CompanionConnection = CompanionConnection.IDLE,
    val messages: List<CompanionMessage> = emptyList(),
    val avatarState: String = "idle",
    val emotion: String? = null,
    val input: String = "",
    val error: String? = null,
    /** 有主动来电时非空：界面切到来电页，而不是插一条消息。 */
    val incomingCall: ProactiveKind? = null,
) {
    val canSend: Boolean get() = input.isNotBlank() && connection == CompanionConnection.CONNECTED

    /** 当前是否有待确认的动作。有的话输入区要让位给确认按钮。 */
    val pendingConfirmation: String?
        get() = messages.lastOrNull { it.confirmationId != null }?.confirmationId
}
