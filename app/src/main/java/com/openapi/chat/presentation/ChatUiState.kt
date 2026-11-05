package com.openapi.chat.presentation

import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.chat.domain.model.ChatMessage

/** 对话界面状态。 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val sessionId: Long = 0L,
    val characters: List<ChatCharacterOption> = emptyList(),
    val selectedCharacterId: Int? = null,
    val recording: Boolean = false,
    /** 语音已录完、正在等 ASR 结果。 */
    val transcribing: Boolean = false,
    /** 正在合成朗读的那条助手消息 id。 */
    val speakingMessageId: String? = null,
) {
    val canSend: Boolean get() = input.isNotBlank() && !sending

    val selectedCharacter: ChatCharacterOption?
        get() = characters.firstOrNull { it.id == selectedCharacterId }
}
