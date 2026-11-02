package com.openapi.chat.domain.repository

import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatStreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * 对话数据入口。
 *
 * 实现放在 data 层，负责：
 * - 远端：`/chat/stream.json`（SSE 流式，见 docs/agent-and-media-roadmap.md 的对话契约；
 *   这是协议的第一个实现方，未来 Python agent-runtime 实现同一份契约时本接口不用改）
 * - 本地：greenDAO 的 ChatLog 表（复用 DBManager，保证升级后老会话可读）
 *
 * domain 层只认 [ChatMessage]/[ChatStreamEvent]，看不到协议细节。
 */
interface ChatRepository {

    /** 读取当前会话的历史消息，按时间正序。 */
    suspend fun loadHistory(sessionId: Long): List<ChatMessage>

    /**
     * 发送一条用户消息，流式接收助理回复。
     *
     * 实现需要：持久化用户消息 → 组装历史 → 发起流式请求 → 逐段 emit [ChatStreamEvent.Delta]
     * → 收到 [ChatStreamEvent.Done] 时把完整回复持久化。调用方（ViewModel）通过
     * [kotlinx.coroutines.flow.Flow] 的取消机制控制生命周期：Flow 被取消时实现必须
     * 释放底层连接，不能让 SSE 连接悬挂。
     *
     * @param characterId 人设 id（setting_character 表），0 或 null 表示不指定，用后端默认人设
     */
    fun sendMessageStream(
        sessionId: Long,
        userText: String,
        characterId: Int?,
    ): Flow<ChatStreamEvent>

    /** 当前会话 id，来自 SP（Constants.CHAT_SESSION）。 */
    fun currentSessionId(): Long

    /** 可选人设列表（`/user/setting/character.json`）。 */
    suspend fun loadCharacters(): List<ChatCharacterOption>

    /** 当前选中的人设 id，持久化在本地（替代老版本直接存 content 字符串的做法）。 */
    fun selectedCharacterId(): Int?

    fun setSelectedCharacterId(characterId: Int?)

    /** 持久化一条语音消息（ASR 转写结果或 TTS 产物）。 */
    suspend fun saveVoiceMessage(
        sessionId: Long,
        message: ChatMessage,
    )

    /**
     * 创建一个自定义角色，归属当前用户。
     *
     * @return 创建成功的角色；失败（比如未登录）返回 null，调用方据此提示错误，不静默吞掉
     */
    suspend fun createCharacter(title: String, content: String, avatarUrl: String?): ChatCharacterOption?

    /** 更新自己的自定义角色；失败（不是自己的角色/未登录）返回 false。 */
    suspend fun updateCharacter(characterId: Int, title: String, content: String, avatarUrl: String?): Boolean

    /** 删除自己的自定义角色；失败返回 false。 */
    suspend fun deleteCharacter(characterId: Int): Boolean
}

