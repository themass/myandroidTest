package com.openapi.chat.presentation

import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatSession
import com.openapi.roles.domain.SelectedRole

/** 对话界面状态。 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    /** 正在编辑的用户消息 id；发送时替换它并删掉它之后的所有消息。 */
    val editingMessageId: String? = null,
    val sending: Boolean = false,
    /** 助手回复正在流式输出（可以点停止）。 */
    val streaming: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val sessionId: Long = 0L,
    val characters: List<ChatCharacterOption> = emptyList(),
    val selectedCharacterId: Int? = null,
    /** 角色目录里选中的角色；和 [selectedCharacterId] 互斥，同一时间只有一个生效。 */
    val selectedRole: SelectedRole? = null,
    val rolePickerVisible: Boolean = false,
    val recording: Boolean = false,
    /** 录音中实时识别出的文字（仅语音样式 B 使用）。 */
    val partialText: String = "",
    /** 语音已录完、正在等 ASR 结果。 */
    val transcribing: Boolean = false,
    /** 微信式「转文字」松手后的编辑态：语音文件和识别文字，等用户选择发送原语音 / 发送文字 / 取消。 */
    val voiceDraft: VoiceDraft? = null,
    /** 正在播放朗读的那条助手消息 id。 */
    val speakingMessageId: String? = null,
    /** 点过赞 / 踩的助手消息 id。 */
    val likedIds: Set<String> = emptySet(),
    val dislikedIds: Set<String> = emptySet(),
    /** 待发送的附件草稿（图片或文件），显示在输入栏上方，发送时和文字一起发出。 */
    val drafts: List<DraftAttachment> = emptyList(),
    /** 会话列表，默认会话在前。 */
    val sessions: List<ChatSession> = emptyList(),
    val sessionsVisible: Boolean = false,
    /** 服务端还有更早的历史可以加载。 */
    val hasMoreHistory: Boolean = false,
    /** 正在加载更早的一页历史。 */
    val loadingOlder: Boolean = false,
) {
    val canSend: Boolean get() = (input.isNotBlank() || drafts.isNotEmpty()) && !sending

    val selectedCharacter: ChatCharacterOption?
        get() = characters.firstOrNull { it.id == selectedCharacterId }
}

/** 输入栏里待发送的一个附件。 */
data class DraftAttachment(
    val id: String,
    val kind: com.openapi.chat.domain.model.MessageKind,
    val staged: StagedAttachment,
)


/** 转文字编辑态里的一段语音：文件仍在本地，文字是识别结果（可编辑）。 */
data class VoiceDraft(val filePath: String, val durationSeconds: Int, val text: String)
