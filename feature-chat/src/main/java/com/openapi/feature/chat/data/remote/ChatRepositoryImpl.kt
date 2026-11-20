package com.openapi.feature.chat.data.remote

import android.content.Context
import com.openapi.feature.chat.domain.model.ChatCharacterOption
import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatReply
import com.openapi.feature.chat.domain.model.ChatSession
import com.openapi.feature.chat.domain.model.ChatStreamEvent
import com.openapi.feature.chat.domain.model.HistoryPage
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.domain.model.OutgoingAttachment
import com.openapi.feature.chat.domain.repository.ChatRepository
import com.openapi.common.net.http.BaseService
import com.openapi.core.config.Constants
import com.openapi.feature.chat.data.remote.ChatMessagePageVo
import com.openapi.feature.chat.data.remote.ChatMessageVo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * [ChatRepository] 的 app 侧实现：只做组合，具体工作交给各协作类。
 *
 * - [ChatRemote]：远程调用的统一包装；
 * - [MessageCache]：本地聊天记录（离线副本、富内容、本地临时消息）；
 * - [SessionStore]：会话；[CharacterRemote]：角色；
 * - [AttachmentUploader]：附件；[SpeechRemote]：语音合成。
 *
 * 放在 app 而不是 features:chat，是因为它必须用到本应用的后端协议细节：
 * - 鉴权（UA 签名/devid/Token，见 BaseRequest 构造函数）——只用来拿一份跟其它接口一致的 header；
 * - 本地的 ChatLog 表。
 * 依赖方向依然是 features:chat ← app（由 ChatDependencies.install 注入）。
 *
 * 流式回复不再在这里实现：运行时一律走 AgentBackedChatRepository 的 /v1 事件流
 * （见其类注释），打旧端点 `/chat/stream.json` 的第二套 SSE 客户端已删除（P3-24）。
 * 本类只剩 TTS、自定义人设和非图片附件这三处 /v1 还没有接口的委托。
 */
class ChatRepositoryImpl(private val context: Context) : ChatRepository {

    internal companion object {
        /** 报错桩的文案：测试按它钉桩，别随手改字。 */
        const val LEGACY_STREAM_DISABLED = "legacy 流式链路已停用，对话走 /v1"
    }

    // 不能写 `BaseService().apply { setup(context) }`：apply 里 `context` 会解析成 BaseService 自己的
    // 公开字段（此时还是 null），等于 setup(null)，所有上传在构造请求时 NPE。用 also + it 明确指定。
    private val baseService = BaseService().also { it.setup(context) }
    private val remote = ChatRemote(baseService)
    private val cache = MessageCache()
    private val sessionStore = SessionStore(context, remote, baseService)
    private val characterStore = CharacterRemote(context, baseService, remote)
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

    /**
     * 流式的两个方法只剩报错桩：运行时不会走到（对话页用的是 ChatDependencies 里的
     * AgentBackedChatRepository），保留实现只是为了接口完整。返回 Error 而不是抛异常——
     * 调用方对 Error 已有完整的 UI 兜底，抛出去会崩。见 LegacyStreamingStubTest。
     */
    override fun sendMessageStream(
        sessionId: Long,
        userText: String,
        characterId: Int?,
        roleId: String?,
        persistUserMessage: Boolean,
        clientMessageId: String?,
        attachments: List<OutgoingAttachment>,
    ): Flow<ChatStreamEvent> = legacyStreamingDisabled()

    override fun regenerateStream(
        sessionId: Long,
        characterId: Int?,
        roleId: String?,
    ): Flow<ChatStreamEvent> = legacyStreamingDisabled()

    private fun legacyStreamingDisabled(): Flow<ChatStreamEvent> =
        flowOf(ChatStreamEvent.Error(LEGACY_STREAM_DISABLED))

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

    /** 旧 Java 服务端没有「清空会话消息」接口，清空走 MessageRemoval 的逐条删除回退路径。 */
    override suspend fun clearHistory(sessionId: Long): Boolean = false

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
        // P3-21：cache.persist 是 Room 写，此前漏了切线程，主线程调用会崩
        withContext(Dispatchers.IO) { cache.persist(sessionId, message) }
    }

    override suspend fun localPendingMessages(sessionId: Long): List<ChatMessage> =
        withContext(Dispatchers.IO) { cache.localPending(sessionId) }

    /** 切换会话时立刻显示的本地缓存（见接口注释）；远程图片等服务端页到达后再换 URL。 */
    override suspend fun cachedMessages(sessionId: Long): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            runCatching { cache.loadCached(sessionId) }.getOrDefault(emptyList())
        }

    /**
     * 旧链路没有「只上传不回复」的接口（`recognize.json` 本身就是一次识别回合），
     * 这里返回 null；图文消息只在 `/v1` 上成立。legacy 实现不再被用作运行时实现。
     */
    override suspend fun uploadImage(localFile: File, fileName: String): String? = null

    override suspend fun sendAttachment(        sessionId: Long,
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

    override suspend fun sessions(): List<ChatSession> = sessionStore.sessions()

    override fun setCurrentSessionId(sessionId: Long) = sessionStore.setCurrentSessionId(sessionId)

    override suspend fun createSession(name: String): ChatSession? = sessionStore.createSession(name)

    override suspend fun createSessionFor(name: String, roleId: String?, characterId: Int?): ChatSession? =
        sessionStore.createSessionFor(name, roleId, characterId)

    override suspend fun renameSession(sessionId: Long, name: String): ChatSession? =
        sessionStore.renameSession(sessionId, name)

    override suspend fun deleteSession(sessionId: Long): Boolean = sessionStore.deleteSession(sessionId)

    override suspend fun syncSessionsFromServer() = sessionStore.syncSessionsFromServer()
}
