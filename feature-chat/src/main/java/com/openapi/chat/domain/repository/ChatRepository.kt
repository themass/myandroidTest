package com.openapi.chat.domain.repository

import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatReply
import com.openapi.chat.domain.model.ChatSession
import com.openapi.chat.domain.model.ChatStreamEvent
import com.openapi.chat.domain.model.HistoryPage
import com.openapi.chat.domain.model.MessageKind
import kotlinx.coroutines.flow.Flow

/**
 * 对话数据入口。
 *
 * 会话、消息的权威数据都在服务端（`/api/chat/...`，设计见服务端 docs/chat-history-server.md）：
 * - 会话 id、消息 id 都是服务端分配的；本地不再生成会话 id；
 * - 历史从服务端按页拉取（[loadHistoryPage]），本地 Room 只是离线缓存，服务端不可达时才读它；
 * - 发送时不上传历史，只带 sessionId + 当轮 text。
 *
 * 实现放在 data 层。domain 层只认 [ChatMessage]/[ChatStreamEvent]，看不到协议细节。
 */
interface ChatRepository {

    /**
     * 拉取一页历史（按时间正序）。
     *
     * @param beforeId 为 null 时取最新一页；否则取 id 小于它的更早一页
     * @return 服务端不可达时回退到本地缓存（只对最新一页有意义，此时 hasMore 为 false）
     */
    suspend fun loadHistoryPage(sessionId: Long, beforeId: Long? = null, limit: Int = 30): HistoryPage

    /**
     * 发送一条用户消息，流式接收助理回复。
     *
     * 请求只带 sessionId（0 表示新建会话）、text、客户端消息 id，以及人设（characterId/roleId，有则带）。
     * 流里每一帧都带 sessionId：会话是新建的话，这里会把新 id 写进本地“当前会话”。
     * Flow 被取消时实现必须释放底层连接。
     *
     * @param characterId 人设 id（setting_character 表），0 或 null 表示不指定
     * @param roleId 角色目录里的角色 id；非空时服务端优先用它
     * @param persistUserMessage 为 false 时不在本地缓存用户消息（已经缓存过的重发场景）
     * @param clientMessageId 客户端消息 id。重发（失败后重试）时传原来的 id，服务端据此去重；
     *   为 null 时由实现生成新的 id。
     */
    fun sendMessageStream(
        sessionId: Long,
        userText: String,
        characterId: Int?,
        roleId: String? = null,
        persistUserMessage: Boolean = true,
        clientMessageId: String? = null,
    ): Flow<ChatStreamEvent>

    /**
     * 重新生成：服务端删掉最后一条用户消息之后的助手回复，再重新回答。
     * 请求不带 text，只带 sessionId、regenerate=true 和人设。
     */
    fun regenerateStream(
        sessionId: Long,
        characterId: Int?,
        roleId: String?,
    ): Flow<ChatStreamEvent>

    /**
     * 编辑一条用户消息。服务端会删掉这条之后的所有消息。
     * [messageId] 必须是服务端消息 id（数字）；调用成功后，调用方应再调用 [regenerateStream] 重新回答。
     *
     * @return 成功返回 true；失败（网络、非用户消息、消息不存在等）返回 false
     */
    suspend fun editMessage(sessionId: Long, messageId: String, text: String): Boolean

    /**
     * 删除一条消息。成功后再删除本地缓存。
     * [messageId] 必须是服务端消息 id；失败返回 false，本地保留该消息。
     */
    /** 只清本地缓存里该会话的消息（服务端已清空后调用）。 */
    suspend fun clearLocalMessages(sessionId: Long)

    /** 本地还没有被服务端历史覆盖的用户消息（附件、正在发送或失败的消息），用于冷启动时恢复。 */
    suspend fun localPendingMessages(sessionId: Long): List<ChatMessage>

    suspend fun deleteMessage(sessionId: Long, messageId: String): Boolean

    /** 当前会话 id，来自 SP（Constants.CHAT_SESSION）。0 表示还没有会话。 */
    fun currentSessionId(): Long

    /** 可选人设列表（`/user/setting/character.json`）。 */
    suspend fun loadCharacters(): List<ChatCharacterOption>

    /** 当前选中的人设 id，持久化在本地。 */
    fun selectedCharacterId(): Int?

    fun setSelectedCharacterId(characterId: Int?)

    /** 持久化一条本地消息到离线缓存（语音、图片、文件等，以及助手回复）。 */
    suspend fun saveMessage(
        sessionId: Long,
        message: ChatMessage,
    )

    /**
     * 上传一个图片或文件附件，等待大模型的回复。
     *
     * 上传走旧版已有的接口（图片 `/chat/ai/recognize.json`，文件 `/chat/ai/local/file.json`），
     * [localFile] 必须已经是要上传的最终文件（图片已压缩）。
     *
     * @param placeholderId 本地占位气泡的 id，服务端用它关联这次请求
     * @return 回复；失败（网络、未登录等）返回 null
     */
    suspend fun sendAttachment(
        sessionId: Long,
        kind: MessageKind,
        localFile: java.io.File,
        fileName: String,
        placeholderId: String,
    ): ChatReply?

    /**
     * 把一段助手回复合成语音（`/chat/ai/tts.json`），返回可播放的本地 `file://` 地址；失败返回 null。
     */
    suspend fun synthesizeSpeech(messageId: String, text: String): String?

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

    /** 本地缓存的会话列表，按服务端 updatedAt 倒序。 */
    fun sessions(): List<ChatSession>

    /** 切换当前会话（写入 SP，之后的历史和发送都以它为准）。 */
    fun setCurrentSessionId(sessionId: Long)

    /**
     * 在服务端新建会话并写入本地缓存。名称为空或与已有会话重名返回 null（不调用服务端）。
     * 服务端失败也返回 null。
     */
    suspend fun createSession(name: String): ChatSession?

    /** 服务端重命名会话；成功返回更新后的会话，失败返回 null。 */
    suspend fun renameSession(sessionId: Long, name: String): ChatSession?

    /**
     * 删除会话（服务端 + 本地缓存）。允许删空；服务端 `/sessions.json` 会在没有会话时自动新建一个。
     * 删的是当前会话时，切到剩下的第一个（没有则为 0）。
     */
    suspend fun deleteSession(sessionId: Long): Boolean

    /**
     * 从服务端（`/chat/sessions.json`）同步会话列表到本地缓存，整表替换。
     * 当前会话不在列表里（比如已被删除或来自旧版本）时，切到列表第一个。
     * 服务端不可达时静默保留本地缓存。
     */
    suspend fun syncSessionsFromServer()
}
