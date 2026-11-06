package com.openapi.chat.presentation

import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatSession
import com.openapi.roles.domain.SelectedRole

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
    /** 角色目录里选中的角色；和 [selectedCharacterId] 互斥，同一时间只有一个生效。 */
    val selectedRole: SelectedRole? = null,
    val rolePickerVisible: Boolean = false,
    val recording: Boolean = false,
    /** 语音已录完、正在等 ASR 结果。 */
    val transcribing: Boolean = false,
    /** 正在播放朗读的那条助手消息 id。 */
    val speakingMessageId: String? = null,
    /** 选中的图片已完成题目识别与质量判断，等待用户在审阅面板里确认。 */
    val pendingImage: ImageReview? = null,
    /** 会话列表，默认会话在前。 */
    val sessions: List<ChatSession> = emptyList(),
    val sessionsVisible: Boolean = false,
) {
    val canSend: Boolean get() = input.isNotBlank() && !sending

    val selectedCharacter: ChatCharacterOption?
        get() = characters.firstOrNull { it.id == selectedCharacterId }
}
