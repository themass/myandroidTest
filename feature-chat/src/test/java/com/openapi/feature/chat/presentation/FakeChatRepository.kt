package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ChatCharacterOption
import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatReply
import com.openapi.feature.chat.domain.model.ChatSession
import com.openapi.feature.chat.domain.model.ChatStreamEvent
import com.openapi.feature.chat.domain.model.HistoryPage
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.domain.model.OutgoingAttachment
import com.openapi.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** 测试用的假仓库：只记录会话相关的调用，其它方法返回空结果。 */
internal class FakeChatRepository : ChatRepository {
    var current = 1L
    var nextCreated: ChatSession? = null
    var renameResult: ChatSession? = null
    var deleteOk = true
    var sessionList = listOf(ChatSession(1L, "默认", isDefault = true))
    val setCurrentCalls = mutableListOf<Long>()
    val deleted = mutableListOf<Long>()
    val renamed = mutableListOf<Pair<Long, String>>()
    var syncCalls = 0
    var selectedCharacter: Int? = null
    val selectedCharacterCalls = mutableListOf<Int?>()
    var createdCharacter: ChatCharacterOption? = null
    var updateCharacterOk = true
    var deleteCharacterOk = true
    val deletedCharacters = mutableListOf<Int>()
    var deleteMessageOk = true
    val deletedMessages = mutableListOf<String>()
    var history: List<ChatMessage> = emptyList()
    var clearedLocal = 0
    var olderMessages: List<ChatMessage> = emptyList()
    var olderHasMore = false
    var historyFails = false
    var pending: List<ChatMessage> = emptyList()
    val saved = mutableListOf<ChatMessage>()

    /** >0 时模拟慢服务端：验证「本地缓存先顶上」和「迟到的缓存不盖服务端」两条时序规则。 */
    var historyDelayMs: Long = 0
    override suspend fun loadHistoryPage(sessionId: Long, beforeId: Long?, limit: Int): HistoryPage {
        if (historyDelayMs > 0) kotlinx.coroutines.delay(historyDelayMs)
        if (historyFails) throw java.io.IOException("offline")
        return if (beforeId == null) HistoryPage(history, hasMore = false) else HistoryPage(olderMessages, olderHasMore)
    }
    /** 非 null 时 sendMessageStream 返回它：流式回复的表征测试在这里注入 Delta/Done 序列。 */
    var streamFlow: Flow<ChatStreamEvent>? = null
    override fun sendMessageStream(
        sessionId: Long, userText: String, characterId: Int?, roleId: String?,
        persistUserMessage: Boolean, clientMessageId: String?,
        attachments: List<OutgoingAttachment>,
    ): Flow<ChatStreamEvent> = streamFlow ?: emptyFlow()
    /** regenerateStream 被调了几次：验证「带图消息的重跑走服务端，而不是整条重发」。 */
    var regenerateStreamCalls = 0
    override fun regenerateStream(sessionId: Long, characterId: Int?, roleId: String?): Flow<ChatStreamEvent> {
        regenerateStreamCalls++
        return emptyFlow()
    }
    var editOk = true
    override suspend fun editMessage(sessionId: Long, messageId: String, text: String) = editOk
    /** 一键清空是否可用；[clearHistoryCalls] 记录调用次数（整改清单 P1-9）。 */
    var clearHistoryOk = false
    var clearHistoryCalls = 0
    override suspend fun clearHistory(sessionId: Long): Boolean {
        clearHistoryCalls++
        return clearHistoryOk
    }
    override suspend fun clearLocalMessages(sessionId: Long) {
        clearedLocal++
    }
    override suspend fun localPendingMessages(sessionId: Long): List<ChatMessage> = pending
    /** 每个会话的「本地缓存」；[cachedMessagesDelay] 模拟慢 IO，用来验证迟到的旧会话缓存不会盖掉新会话。 */
    val cachedMessages = mutableMapOf<Long, List<ChatMessage>>()
    val cachedMessagesDelay = mutableMapOf<Long, Long>()
    override suspend fun cachedMessages(sessionId: Long): List<ChatMessage> {
        cachedMessagesDelay[sessionId]?.let { kotlinx.coroutines.delay(it) }
        return cachedMessages[sessionId] ?: emptyList()
    }
    override suspend fun deleteMessage(sessionId: Long, messageId: String): Boolean {
        deletedMessages += messageId
        return deleteMessageOk
    }
    override fun currentSessionId(): Long = current
    override suspend fun loadCharacters(): List<ChatCharacterOption> = emptyList()
    override fun selectedCharacterId(): Int? = selectedCharacter
    override fun setSelectedCharacterId(characterId: Int?) {
        selectedCharacter = characterId
        selectedCharacterCalls += characterId
    }
    override suspend fun saveMessage(sessionId: Long, message: ChatMessage) {
        saved += message
    }
    /** 非 null 时 uploadImage 直接返回它；同时记录每次调用的文件，验证「该不该重新上传」。 */
    var uploadImageResult: String? = null
    val uploadImageCalls = mutableListOf<java.io.File>()
    /** true 时 uploadImage 永不返回（真挂起点，只能被取消打断），供停止用例打断。 */
    var uploadImageHangs = false
    override suspend fun uploadImage(localFile: java.io.File, fileName: String): String? {
        uploadImageCalls += localFile
        if (uploadImageHangs) kotlinx.coroutines.suspendCancellableCoroutine<Nothing> { }
        return uploadImageResult
    }
    /** 重签通道（整改清单 P1-10）：非 null 时 refreshImageUrl 返回它；记录每个 id 的调用次数。 */
    var refreshImageUrlResult: String? = null
    val refreshImageUrlCalls = mutableListOf<String>()
    override suspend fun refreshImageUrl(uploadId: String): String? {
        refreshImageUrlCalls += uploadId
        return refreshImageUrlResult
    }
    override suspend fun sendAttachment(
        sessionId: Long, kind: MessageKind, localFile: java.io.File, fileName: String, placeholderId: String,
    ): ChatReply? = null
    override suspend fun synthesizeSpeech(messageId: String, text: String): String? = null
    override suspend fun createCharacter(title: String, content: String, avatarUrl: String?): ChatCharacterOption? = createdCharacter
    override suspend fun updateCharacter(characterId: Int, title: String, content: String, avatarUrl: String?) = updateCharacterOk
    override suspend fun deleteCharacter(characterId: Int): Boolean {
        deletedCharacters += characterId
        return deleteCharacterOk
    }
    override fun sessions(): List<ChatSession> = sessionList
    override fun setCurrentSessionId(sessionId: Long) {
        setCurrentCalls += sessionId
        current = sessionId
    }
    override suspend fun createSession(name: String): ChatSession? = nextCreated

    /** 记录“为角色建会话”的调用；成功时像真实仓库一样把新会话加进列表。 */
    val createdFor = mutableListOf<Triple<String, String?, Int?>>()
    var createForFails = false
    private var nextForId = 100L
    override suspend fun createSessionFor(name: String, roleId: String?, characterId: Int?): ChatSession? {
        createdFor += Triple(name, roleId, characterId)
        if (createForFails) return null
        val s = ChatSession(nextForId++, name, isDefault = false, roleId = roleId, characterId = characterId)
        sessionList = listOf(s) + sessionList
        return s
    }
    override suspend fun renameSession(sessionId: Long, name: String): ChatSession? {
        renamed += sessionId to name
        return renameResult
    }
    override suspend fun deleteSession(sessionId: Long): Boolean {
        deleted += sessionId
        return deleteOk
    }
    override suspend fun syncSessionsFromServer() {
        syncCalls++
    }
}
