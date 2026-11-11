package com.openapi.chat.data.remote

import com.openapi.chat.data.mapper.ChatLogMapper
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatRole
import com.openapi.chat.domain.model.DeliveryStatus
import com.openapi.chat.domain.model.MessageKind
import com.openapi.common.util.log.LogUtil
import com.openapi.core.db.AppDatabase
import com.openapi.core.model.ChatLog
import com.openapi.chat.data.remote.ChatMessageVo
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 本地聊天记录（离线副本，Room 的 ChatLog 表）。服务端是权威，这里只保存一份可离线展示的副本，
 * 以及只存在本机的富内容（语音、图片、文件）和本地临时消息。
 * 方法都是阻塞的，调用方需要在后台线程调用。
 */
internal class MessageCache {

    private companion object {
        /** 本地临时消息的 id 前缀：这些消息只存在本地，服务端历史里没有。 */
        const val LOCAL_ID_PREFIX = "local-"
        /** 聊天记录只保留最近这么多条，沿用旧逻辑。 */
        const val CHAT_LOG_KEEP = 30L
        /** 单线程清理队列：所有实例共享，保证清理串行执行。 */
        val trimExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    }

    /** 最近一页之外的本地历史（服务端不可达时用）。 */
    fun loadCached(sessionId: Long): List<ChatMessage> =
        AppDatabase.getInstance().chatLogDao().bySession(sessionId).mapNotNull { log ->
            ChatLogMapper.toDomain(
                chatId = log.chatId,
                who = log.who,
                msgType = log.msgType,
                rawContent = log.content,
                createdAtMillis = log.createTime?.time ?: 0L,
            )
        }

    /** 本地先发出、还没有服务端 id 的用户消息（重启后仍要显示、要重发）。 */
    fun localPending(sessionId: Long): List<ChatMessage> {
        val dao = AppDatabase.getInstance().chatLogDao()
        fun pendingIn(id: Long) = dao.bySession(id)
            .filter { (it.chatId ?: "").startsWith(LOCAL_ID_PREFIX) && it.who == ChatLogMapper.toWho(ChatRole.USER) }
        // 没有会话时（id 为 0，比如刚删光会话）发出的消息存在 0 下面；重启后当前会话换成了服务端的会话，
        // 这些消息要归到当前会话，否则就「消失」了
        val orphans = if (sessionId > 0) pendingIn(0L) else emptyList()
        orphans.forEach { log ->
            log.sessionId = sessionId
            dao.save(log)
        }
        val rows = pendingIn(sessionId)
        LogUtil.i("$CHAT_TAG localPending session=$sessionId rows=${rows.size} adoptedFromZero=${orphans.size}")
        return rows.mapNotNull { log ->
            ChatLogMapper.toDomain(
                chatId = log.chatId,
                who = log.who,
                msgType = log.msgType,
                rawContent = log.content,
                createdAtMillis = log.createTime?.time ?: 0L,
            )
        }
    }

    fun clearLocal(sessionId: Long) {
        AppDatabase.getInstance().chatLogDao().deleteBySession(sessionId)
    }

    fun deleteLocal(messageId: String) {
        AppDatabase.getInstance().chatLogDao().deleteByChatId(messageId)
    }

    /**
     * 把一轮对话写进本地缓存（离线副本）。用服务端正式 id 作为 chatId；
     * 用户消息若是本地先写入的（临时 id），这里换成正式 id。空回复不缓存。
     */
    fun cacheTurn(
        sessionId: Long,
        clientUserId: String,
        userText: String?,
        serverUserId: Long,
        reply: String,
        serverAssistantId: Long,
        localAssistantId: String,
    ) {
        if (sessionId <= 0) return
        val now = System.currentTimeMillis()
        if (userText != null && serverUserId > 0) {
            val dao = AppDatabase.getInstance().chatLogDao()
            // 本地先存的用户消息如果带语音或附件，只换成正式 id，内容保留；直接按文字重写会把语音弄丢
            val local = dao.byChatId(clientUserId)?.toDomainOrNull()
            dao.deleteByChatId(clientUserId)
            val user = if (local != null && local.kind != MessageKind.TEXT) {
                local.copy(id = serverUserId.toString(), text = userText.ifBlank { local.text }, delivery = DeliveryStatus.SENT)
            } else {
                ChatMessage(id = serverUserId.toString(), role = ChatRole.USER, text = userText, createdAtMillis = now)
            }
            persist(sessionId, user)
        }
        if (reply.isNotEmpty()) {
            persist(
                sessionId,
                ChatMessage(
                    id = if (serverAssistantId > 0) serverAssistantId.toString() else localAssistantId,
                    role = ChatRole.ASSISTANT,
                    text = reply,
                    createdAtMillis = now,
                ),
            )
        }
    }

    /**
     * 把服务端一页消息写进本地缓存，并返回合并后的消息列表。
     *
     * 服务端历史里只有文字。语音、图片、文件的本机信息（路径、时长、文件名）只在本地记录里，
     * 所以同一个 chatId 已经有本地富内容记录时，用服务端的文字和时间更新它，类型和附件保留。
     * [replaceAll] 为 true 时（拉最新一页）清掉这个会话的纯文字缓存，避免已删除或重新生成的消息残留；
     * 富内容记录和本地临时消息（local- 开头）不清，它们在服务端找不回来。
     */
    fun cacheRemotePage(sessionId: Long, rows: List<ChatMessageVo>, replaceAll: Boolean): List<ChatMessage> {
        val dao = AppDatabase.getInstance().chatLogDao()
        val existing = dao.bySession(sessionId)
        val rich = existing
            .filter { it.msgType != ChatLogMapper.toMsgType(MessageKind.TEXT) }
            .mapNotNull { log -> log.toDomainOrNull()?.let { (log.chatId ?: "") to it } }
            .toMap()
        if (replaceAll) {
            existing
                .filter { it.msgType == ChatLogMapper.toMsgType(MessageKind.TEXT) && !(it.chatId ?: "").startsWith(LOCAL_ID_PREFIX) }
                .forEach { dao.deleteByChatId(it.chatId) }
        }
        return rows.map { row ->
            val server = row.toDomainMessage()
            val local = rich[server.id]
            val message = if (local != null) {
                local.copy(text = server.text.ifBlank { local.text }, createdAtMillis = server.createdAtMillis, delivery = DeliveryStatus.SENT)
            } else {
                server
            }
            val log = ChatLog()
            log.content = ChatLogMapper.toRawContent(message)
            log.who = ChatLogMapper.toWho(message.role)
            log.msgType = ChatLogMapper.toMsgType(message.kind)
            log.chatId = message.id
            log.createTime = Date(message.createdAtMillis)
            log.sessionId = sessionId
            dao.save(log)
            message
        }
    }

    /** 写入一条消息，并在后台清理多余的记录。 */
    fun persist(sessionId: Long, message: ChatMessage) {
        val log = ChatLog()
        log.content = ChatLogMapper.toRawContent(message)
        log.who = ChatLogMapper.toWho(message.role)
        log.msgType = ChatLogMapper.toMsgType(message.kind)
        log.chatId = message.id
        log.createTime = Date(message.createdAtMillis)
        log.sessionId = sessionId
        AppDatabase.getInstance().chatLogDao().save(log)
        // 清理放到后台：不拖慢发消息
        trimExecutor.execute {
            try {
                AppDatabase.getInstance().chatLogDao().trimToLatest(CHAT_LOG_KEEP)
            } catch (e: Throwable) {
                LogUtil.e(e)
            }
        }
    }

    private fun ChatLog.toDomainOrNull(): ChatMessage? = ChatLogMapper.toDomain(
        chatId = chatId,
        who = who,
        msgType = msgType,
        rawContent = content,
        createdAtMillis = createTime?.time ?: 0L,
    )

    private fun ChatMessageVo.toDomainMessage() = ChatMessage(
        // 服务端消息 id 直接用作 chatId / 列表 id，之后编辑、删除都用它
        id = id.toString(),
        role = if (role == "user") ChatRole.USER else ChatRole.ASSISTANT,
        text = content.orEmpty(),
        createdAtMillis = createdAt,
    )
}
