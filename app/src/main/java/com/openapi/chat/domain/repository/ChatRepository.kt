package com.openapi.chat.domain.repository

import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatReply
import com.openapi.chat.domain.model.ChatSession
import com.openapi.chat.domain.model.ChatStreamEvent
import com.openapi.chat.domain.model.MessageKind
import kotlinx.coroutines.flow.Flow

/**
 * 对话数据入口。
 *
 * 实现放在 data 层，负责：
 * - 远端：`/chat/stream.json`（SSE 流式，见 docs/agent-and-media-roadmap.md 的对话契约；
 *   这是协议的第一个实现方，未来 Python agent-runtime 实现同一份契约时本接口不用改）
 * - 本地：Room 的 ChatLog 表（经 AppDatabase；旧 greenDAO 库不再读取，升级后聊天记录从空开始）
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
     * @param roleId 角色目录（/api/roles/v1）里的角色 id；非空时服务端优先用它，
     *   查不到再回退 [characterId]。老服务端不认识这个字段，会直接忽略。
     */
    fun sendMessageStream(
        sessionId: Long,
        userText: String,
        characterId: Int?,
        roleId: String? = null,
    ): Flow<ChatStreamEvent>

    /** 当前会话 id，来自 SP（Constants.CHAT_SESSION）。 */
    fun currentSessionId(): Long

    /** 可选人设列表（`/user/setting/character.json`）。 */
    suspend fun loadCharacters(): List<ChatCharacterOption>

    /** 当前选中的人设 id，持久化在本地（替代老版本直接存 content 字符串的做法）。 */
    fun selectedCharacterId(): Int?

    fun setSelectedCharacterId(characterId: Int?)

    /** 持久化一条本地消息（语音、图片、文件等）。 */
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

    /** 会话列表：默认会话在前，其余按创建顺序。 */
    fun sessions(): List<ChatSession>

    /** 切换当前会话（写入 SP，之后的历史和发送都以它为准）。 */
    fun setCurrentSessionId(sessionId: Long)

    /** 新建会话。名称为空或已存在返回 null，不静默覆盖同名会话。 */
    suspend fun createSession(name: String): ChatSession?

    /**
     * 删除会话。至少保留一个会话，只剩一个时返回 false；删的是当前会话时，切到剩下的第一个。
     */
    suspend fun deleteSession(sessionId: Long): Boolean

    /** 从服务端（`/chat/sessions.json`）同步会话列表到本地；失败静默，保留本地列表。 */
    suspend fun syncSessionsFromServer()
}

