package com.openapi.ks.myapp.companion

/** 对话模式。第一版默认文字，用户可切到语音。 */
enum class CompanionMode { TEXT, VOICE }

enum class CompanionConnection { IDLE, CONNECTING, CONNECTED, ERROR }

enum class MessageRole { USER, ASSISTANT }

data class CompanionMessage(
    val role: MessageRole,
    val text: String,
    /** 正在流式接收：界面显示打字光标，不显示操作按钮。 */
    val streaming: Boolean = false,
)

data class CompanionUiState(
    val mode: CompanionMode = CompanionMode.TEXT,
    val connection: CompanionConnection = CompanionConnection.IDLE,
    val messages: List<CompanionMessage> = emptyList(),
    val avatarState: String = "idle",
    val emotion: String? = null,
    val input: String = "",
    val error: String? = null,
) {
    val canSend: Boolean get() = input.isNotBlank() && connection == CompanionConnection.CONNECTED
}
