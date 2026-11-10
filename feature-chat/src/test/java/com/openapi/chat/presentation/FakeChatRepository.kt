package com.openapi.chat.presentation

import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatReply
import com.openapi.chat.domain.model.ChatSession
import com.openapi.chat.domain.model.ChatStreamEvent
import com.openapi.chat.domain.model.HistoryPage
import com.openapi.chat.domain.model.MessageKind
import com.openapi.chat.domain.repository.ChatRepository
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

    override suspend fun loadHistoryPage(sessionId: Long, beforeId: Long?, limit: Int) =
        HistoryPage(if (beforeId == null) history else emptyList(), hasMore = false)
    override fun sendMessageStream(
        sessionId: Long, userText: String, characterId: Int?, roleId: String?,
        persistUserMessage: Boolean, clientMessageId: String?,
    ): Flow<ChatStreamEvent> = emptyFlow()
    override fun regenerateStream(sessionId: Long, characterId: Int?, roleId: String?): Flow<ChatStreamEvent> = emptyFlow()
    override suspend fun editMessage(sessionId: Long, messageId: String, text: String) = true
    override suspend fun clearLocalMessages(sessionId: Long) {
        clearedLocal++
    }
    override suspend fun localPendingMessages(sessionId: Long): List<ChatMessage> = emptyList()
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
    override suspend fun saveMessage(sessionId: Long, message: ChatMessage) {}
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
