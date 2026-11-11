package com.openapi.chat.data.remote

import android.content.Context
import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatReply
import com.openapi.chat.domain.model.ChatSession
import com.openapi.chat.domain.model.ChatStreamEvent
import com.openapi.chat.domain.model.HistoryPage
import com.openapi.chat.domain.model.MessageKind
import com.openapi.chat.domain.repository.ChatRepository
import com.openapi.common.net.http.BaseService
import com.openapi.core.config.Constants
import com.openapi.chat.data.remote.ChatMessagePageVo
import com.openapi.chat.data.remote.ChatMessageVo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * [ChatRepository] 的 app 侧实现：只做组合，具体工作交给各协作类。
 *
 * - [ChatRemote]：远程调用的统一包装；
 * - [MessageCache]：本地聊天记录（离线副本、富内容、本地临时消息）；
 * - [SessionStore]：会话；[CharacterRemote]：角色；
 * - [ChatStreamClient]：流式回复；[AttachmentUploader]：附件；[SpeechRemote]：语音合成。
 *
 * 放在 app 而不是 features:chat，是因为它必须用到本应用的后端协议细节：
 * - 鉴权（UA 签名/devid/Token，见 BaseRequest 构造函数）——只用来拿一份跟其它接口一致的 header；
 * - 本地的 ChatLog 表。
 * 依赖方向依然是 features:chat ← app（由 ChatDependencies.install 注入）。
 *
 * 对话契约见 docs/agent-and-media-roadmap.md 第一节：现在打的是 `/chat/stream.json`，
 * 未来换成 Python agent-runtime 时改动的是协作类，这个类的接口不变。
 */
class ChatRepositoryImpl(private val context: Context) : ChatRepository {

    private val baseService = BaseService().apply { setup(context) }
    private val remote = ChatRemote(baseService)
    private val cache = MessageCache()
    private val sessionStore = SessionStore(context, remote, baseService)
    private val characterStore = CharacterRemote(context, baseService, remote)
    private val streamClient = ChatStreamClient(context, cache, sessionStore)
    private val attachments = AttachmentUploader(baseService)
    private val speech = SpeechRemote(context, remote)

    override fun currentSessionId(): Long = sessionStore.currentSessionId()

    override fun selectedCharacterId(): Int? = characterStore.selectedCharacterId()

    override fun setSelectedCharacterId(characterId: Int?) = characterStore.setSelectedCharacterId(characterId)

    override suspend fun loadCharacters(): List<ChatCharacterOption> = characterStore.loadCharacters()

    override suspend fun loadHistoryPage(sessionId: Long, beforeId: Long?, limit: Int): HistoryPage {
        if (sessionId <= 0) return HistoryPage(emptyList(), hasMore = false)
        val safeLimit = limit.coerceIn(1, 200)
        val url = Constants.getUrl(Constants.CHAT_MESSAGES_URL)
        val page: ChatMessagePageVo? = if (beforeId == null) {
            remote.postForResult(url, MessagesFirstPageParam(sessionId, safeLimit), ChatMessagePageVo::class.java)
        } else {
            remote.postForResult(url, MessagesOlderPageParam(sessionId, beforeId, safeLimit), ChatMessagePageVo::class.java)
        }
        if (page != null) {
            // 返回合并过本机附件信息的消息：服务端只有文字，语音、图片、文件要从本地记录里补回来
            val merged = withContext(Dispatchers.IO) {
                cache.cacheRemotePage(sessionId, page.voList, replaceAll = beforeId == null)
            }
            return HistoryPage(merged, page.hasMore)
        }
        // 服务端不可达：只能给出本地缓存的最新一页，更早的历史离线时拿不到
        if (beforeId != null) return HistoryPage(emptyList(), hasMore = false)
        val cached = withContext(Dispatchers.IO) { cache.loadCached(sessionId) }
        return HistoryPage(cached, hasMore = false)
    }

    override fun sendMessageStream(
        sessionId: Long,
        userText: String,
        characterId: Int?,
        roleId: String?,
        persistUserMessage: Boolean,
        clientMessageId: String?,
    ): Flow<ChatStreamEvent> = streamClient.sendMessageStream(
        sessionId = sessionId,
        userText = userText,
        characterId = characterId,
        roleId = roleId,
        persistUserMessage = persistUserMessage,
        clientMessageId = clientMessageId,
    )

    override fun regenerateStream(
        sessionId: Long,
        characterId: Int?,
        roleId: String?,
    ): Flow<ChatStreamEvent> = streamClient.regenerateStream(sessionId, characterId, roleId)

    override suspend fun editMessage(sessionId: Long, messageId: String, text: String): Boolean {
        val id = messageId.toLongOrNull() ?: return false
        if (sessionId <= 0) return false
        return remote.postForResult(
            Constants.getUrl(Constants.CHAT_MESSAGE_EDIT_URL),
            MessageEditParam(sessionId, id, text),
            ChatMessageVo::class.java,
        ) != null
    }

    override suspend fun clearLocalMessages(sessionId: Long) {
        if (sessionId <= 0) return
        withContext(Dispatchers.IO) { cache.clearLocal(sessionId) }
    }

    override suspend fun deleteMessage(sessionId: Long, messageId: String): Boolean {
        val id = messageId.toLongOrNull() ?: return false
        if (sessionId <= 0) return false
        val ok = remote.postForOk(
            Constants.getUrl(Constants.CHAT_MESSAGE_DELETE_URL),
            MessageDeleteParam(sessionId, id),
        )
        if (ok) {
            withContext(Dispatchers.IO) { cache.deleteLocal(messageId) }
        }
        return ok
    }

    override suspend fun synthesizeSpeech(messageId: String, text: String): String? =
        speech.synthesize(messageId, text)

    override suspend fun saveMessage(sessionId: Long, message: ChatMessage) {
        cache.persist(sessionId, message)
    }

    override suspend fun localPendingMessages(sessionId: Long): List<ChatMessage> =
        withContext(Dispatchers.IO) { cache.localPending(sessionId) }

    override suspend fun sendAttachment(
        sessionId: Long,
        kind: MessageKind,
        localFile: File,
        fileName: String,
        placeholderId: String,
    ): ChatReply? = attachments.send(sessionId, kind, localFile, fileName, placeholderId)

    override suspend fun createCharacter(title: String, content: String, avatarUrl: String?): ChatCharacterOption? =
        characterStore.createCharacter(title, content, avatarUrl)

    override suspend fun updateCharacter(characterId: Int, title: String, content: String, avatarUrl: String?): Boolean =
        characterStore.updateCharacter(characterId, title, content, avatarUrl)

    override suspend fun deleteCharacter(characterId: Int): Boolean = characterStore.deleteCharacter(characterId)

    override fun sessions(): List<ChatSession> = sessionStore.sessions()

    override fun setCurrentSessionId(sessionId: Long) = sessionStore.setCurrentSessionId(sessionId)

    override suspend fun createSession(name: String): ChatSession? = sessionStore.createSession(name)

    override suspend fun renameSession(sessionId: Long, name: String): ChatSession? =
        sessionStore.renameSession(sessionId, name)

    override suspend fun deleteSession(sessionId: Long): Boolean = sessionStore.deleteSession(sessionId)

    override suspend fun syncSessionsFromServer() = sessionStore.syncSessionsFromServer()
}
