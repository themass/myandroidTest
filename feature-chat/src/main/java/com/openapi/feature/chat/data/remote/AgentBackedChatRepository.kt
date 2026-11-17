package com.openapi.feature.chat.data.remote

import android.content.Context
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.common.util.log.LogUtil
import com.openapi.core.config.Constants
import com.openapi.feature.chat.domain.model.ChatCharacterOption
import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatReply
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.ChatSession
import com.openapi.feature.chat.domain.model.ChatStreamEvent
import com.openapi.feature.chat.domain.model.HistoryPage
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.feature.chat.domain.model.OutgoingAttachment
import com.openapi.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.OffsetDateTime
import kotlin.coroutines.cancellation.CancellationException

/**
 * 「对话」页的仓库实现，底层换成 Python `companion-agent` 的 `/v1`。
 *
 * 为什么只换实现、不换 UI：`/v1` 的对话内核（服务端权威状态、断线续传、排队、多候选、跨会话记忆）
 * 比旧的 Java 链路强，但它没有客户端 UI；而 `chatkit`（3767 行）、附件、录音、283 角色选择器
 * 都在旧的 `feature-chat` 里且与后端无关。两者互补，所以迁移点落在 [ChatRepository] 这一层：
 * 上层一行不改，协议整体换掉。
 *
 * 三处刻意保留在旧 Java 链路（都在 `/v1` 上不存在，不是遗漏）：
 * - `synthesizeSpeech`：`/v1` 没有 TTS HTTP 接口，TTS 只存在于 LiveKit 语音管线里；
 * - `loadCharacters` / `createCharacter` 等：自定义人设是旧 Java 的 `setting_character` 表，
 *   与 `/v1` 的 `/v1/roles` 是两套数据模型，混改会把用户自己建的角色的内容截断；
 * - 语音转写：`/v1` 没有 ASR 路由，仍在 `:speech` + Java `/chat/ai/asr.json`。
 */
class AgentBackedChatRepository(
    private val context: Context,
    private val remote: AgentChatRemote,
    private val local: LocalChatStore,
    private val legacy: ChatRepository,
) : ChatRepository {

    private companion object {
        /**
         * `/v1` 的会话 id 与旧 Java 的会话 id 是两套独立编号，**绝不能共用一个 SP 键**：
         * 旧值 170 拿去请求 `/v1/sessions/170` 会打到别人的会话或 404。
         */
        const val PREF_SESSION = "agent_chat_current_session"
        /**
         * 没有选角色时的兜底角色。
         *
         * 必须是目录里真实存在的角色：`/v1` 按 role_id 加载角色卡，找不到时**不报错**，
         * 而是让回合产出空回复（我实测过 `__default__` 得到一条 content 为空的消息）。
         * 这个坑没有任何错误提示，所以宁可显式钉一个真实 id，也不留空。
         */
        const val FALLBACK_ROLE_ID = "specialized-chief-of-staff"
    }

    // ------------------------------------------------------------ 会话 id

    override fun currentSessionId(): Long =
        PreferenceUtils.getPrefLong(context, PREF_SESSION, 0L)

    override fun setCurrentSessionId(sessionId: Long) {
        PreferenceUtils.setSettingLong(context, PREF_SESSION, sessionId)
    }

    /**
     * 拿到一个可用的 `/v1` 会话 id。[sessionId] <= 0 时新建。
     *
     * `roleId` 为空时用 [FALLBACK_ROLE_ID]：会话的角色在服务端是**建会话时固定**的，
     * 之后换角色 = 换会话，所以这里必须一次定对。
     */
    private suspend fun resolveSessionId(
        sessionId: Long,
        roleId: String?,
        characterId: Int?,
        name: String = "新会话",
    ): Long = withContext(Dispatchers.IO) {
        if (sessionId > 0) return@withContext sessionId
        val role = roleId?.takeIf { it.isNotBlank() }
            ?: characterId?.takeIf { it > 0 }?.let { "u_$it" }
            ?: FALLBACK_ROLE_ID
        val created = remote.createSession(role, name)
        setCurrentSessionId(created.id)
        created.id
    }

    // ------------------------------------------------------------ 发送

    override fun sendMessageStream(
        sessionId: Long,
        userText: String,
        characterId: Int?,
        roleId: String?,
        persistUserMessage: Boolean,
        clientMessageId: String?,
        attachments: List<OutgoingAttachment>,
    ): Flow<ChatStreamEvent> = flow {
        val guard = MigrationGuard
        val sid = resolveSessionId(sessionId, roleId, characterId)
        val clientId = clientMessageId ?: "local-user-${System.currentTimeMillis()}"
        val assistantId = "local-assistant-${System.currentTimeMillis()}"

        if (!guard.begin()) {
            // 同一个会话同时只允许一个回合：/v1 会返回 409，这里提前拦住，错误信息更直白。
            emit(ChatStreamEvent.Error("上一轮回复还没结束"))
            return@flow
        }
        try {
            if (persistUserMessage) {
                withContext(Dispatchers.IO) {
                    local.persist(
                        sid,
                        ChatMessage(
                            id = clientId, role = ChatRole.USER, text = userText,
                            createdAtMillis = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            val receipt = if (attachments.isEmpty()) {
                remote.submit(sid, userText, clientId)
            } else {
                // 文字和附件一起提交：服务端把它们存成同一条消息、跑同一个回合，
                // 模型因此只回一次话。分成两轮会让同一句话得到两条回复。
                remote.submitWithAttachments(
                    sessionId = sid,
                    text = userText,
                    clientMessageId = clientId,
                    attachments = attachments.map {
                        JSONObject().put("upload_id", it.uploadId)
                            .put("kind", it.kind.name.lowercase())
                            .put("name", it.name)
                    },
                )
            }
            collectTurn(sid, receipt.turnId, assistantId, clientId, userText, persistUserMessage)
        } finally {
            guard.end()
        }
    }.flowOn(Dispatchers.IO)

    override fun regenerateStream(
        sessionId: Long,
        characterId: Int?,
        roleId: String?,
    ): Flow<ChatStreamEvent> = flow {
        val sid = resolveSessionId(sessionId, roleId, characterId)
        val assistantId = "local-assistant-${System.currentTimeMillis()}"
        if (!MigrationGuard.begin()) {
            emit(ChatStreamEvent.Error("上一轮回复还没结束"))
            return@flow
        }
        try {
            // /v1 的重新生成挂在「用户消息 id」上：取这一轮最后一条 user 消息。
            val lastUser = withContext(Dispatchers.IO) {
                remote.history(sid, null, 20).items.lastOrNull { it.role == "user" }
            } ?: run {
                emit(ChatStreamEvent.Error("没有可以重新回答的消息"))
                return@flow
            }
            remote.regenerate(sid, lastUser.id)
            collectTurn(sid, null, assistantId, null, null, false)
        } finally {
            MigrationGuard.end()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 订阅事件流，把 `/v1` 的事件折叠成 [ChatStreamEvent]。
     *
     * 关键点：先 submit 再订阅**是安全的**——服务端的事件是 Redis Stream，订阅时会先补发
     * `turn.snapshot`（带已生成的全部文字）。所以网络慢、或客户端重连时不会丢字，
     * 这也顺带把「断线续传」免费拿到了。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<ChatStreamEvent>.collectTurn(
        sessionId: Long,
        expectedTurnId: String?,
        assistantLocalId: String,
        clientMessageId: String?,
        userText: String?,
        persistUser: Boolean,
    ) {
        val acc = StringBuilder()
        var matchedTurn: String? = expectedTurnId
        var finished = false
        var errorEmitted = false

        // 直接 collect 并把事件 emit 给本函数的接收者。
        // 不能写成 `remote.events(..).transformWhile { emit(..) }`：那里的 emit 属于 transform 自己的下游，
        // 再套一层 `.collect { }` 就把所有 delta/done 全丢掉了——界面收不到任何增量，
        // 只能靠事后拉历史补出整段文字，表现为「回复出现了但同时又弹『没有收到回复』」。
        try {
            remote.events(sessionId, null).collect { frame ->
                val obj = runCatching { JSONObject(frame.data) }.getOrNull()
                if (obj != null) {
                    val turn = obj.optString("turn_id", "")
                    // 第一次拿到 turn_id 时把它固定下来，后续只认这一轮，避免并发回合串台。
                    if (turn.isNotEmpty() && matchedTurn == null) matchedTurn = turn
                    val ours = turn.isNotEmpty() && turn == matchedTurn
                    when (frame.event) {
                        "turn.snapshot" -> if (ours) {
                            // 补发的是「已生成的全部文字」，把本地没有的尾巴补上即可。
                            val text = obj.optString("text", "")
                            if (text.length > acc.length && text.startsWith(acc.toString())) {
                                val tail = text.substring(acc.length)
                                acc.append(tail)
                                emit(ChatStreamEvent.Delta(assistantLocalId, tail))
                            }
                        }
                        "message.delta" -> if (ours) {
                            val delta = obj.optString("delta", "")
                            if (delta.isNotEmpty()) {
                                acc.append(delta)
                                emit(ChatStreamEvent.Delta(assistantLocalId, delta))
                            }
                        }
                        "message.done" -> if (ours) {
                            val assistantMessageId = obj.optLong("assistant_message_id", 0L)
                            persistTurn(sessionId, clientMessageId, userText, persistUser, acc.toString())
                            emit(
                                ChatStreamEvent.Done(
                                    assistantId = assistantMessageId.takeIf { it > 0 }?.toString(),
                                    userId = null,
                                    fullText = acc.toString(),
                                ),
                            )
                            finished = true
                            throw TurnFinished
                        }
                        "turn.cancelled" -> if (ours) {
                            emit(
                                ChatStreamEvent.Done(
                                    assistantId = obj.optLong("assistant_message_id", 0L).takeIf { it > 0 }?.toString(),
                                    userId = null,
                                    fullText = acc.toString(),
                                ),
                            )
                            finished = true
                            throw TurnFinished
                        }
                        "error" -> if (ours || matchedTurn == null) {
                            val code = obj.optString("code", "unknown")
                            emit(ChatStreamEvent.Error(describeError(code), null))
                            errorEmitted = true
                            finished = true
                            throw TurnFinished
                        }
                    }
                }
            }
        } catch (_: TurnFinished) {
            // 本轮正常收尾（done / cancelled / error），不需要继续读事件流。
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!errorEmitted) emit(ChatStreamEvent.Error(e.message ?: "事件流出错"))
            return
        }

        if (!finished && !errorEmitted) {
            // 流断了但没收到 done：把已收到的内容交出去，让界面保留半截回复而不是整段消失。
            if (acc.isNotEmpty()) {
                persistTurn(sessionId, clientMessageId, userText, persistUser, acc.toString())
                emit(ChatStreamEvent.Done(assistantId = null, userId = null, fullText = acc.toString()))
            } else {
                emit(ChatStreamEvent.Error("没有收到回复，请重试"))
            }
        }
    }

    private suspend fun persistTurn(
        sessionId: Long,
        clientMessageId: String?,
        userText: String?,
        persistUser: Boolean,
        reply: String,
    ) {
        if (reply.isEmpty()) return
        withContext(Dispatchers.IO) {
            if (persistUser && clientMessageId != null && userText != null) {
                local.persist(
                    sessionId,
                    ChatMessage(
                        id = clientMessageId, role = ChatRole.USER, text = userText,
                        createdAtMillis = System.currentTimeMillis(),
                    ),
                )
            }
            local.persist(
                sessionId,
                ChatMessage(
                    id = "local-assistant-${System.currentTimeMillis()}",
                    role = ChatRole.ASSISTANT, text = reply,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** `/v1` 的错误码是本机看不懂的短串，翻成用户能据此行动的话。 */
    private fun describeError(code: String): String = when (code) {
        "content_policy" -> "这条消息不符合内容规范"
        "rate_limited" -> "太频繁了，稍等一下再发"
        "conflict" -> "上一轮回复还没结束"
        "invalid_json" -> "回复格式异常，请重试"
        else -> "回复失败（$code），请重试"
    }

    // ------------------------------------------------------------ 历史

    override suspend fun loadHistoryPage(sessionId: Long, beforeId: Long?, limit: Int): HistoryPage {
        if (sessionId <= 0) return HistoryPage(emptyList(), hasMore = false)
        val safeLimit = limit.coerceIn(1, 100)
        val page = runCatching { withContext(Dispatchers.IO) { remote.history(sessionId, beforeId, safeLimit) } }
            .getOrNull() ?: return HistoryPage(
            withContext(Dispatchers.IO) { local.loadCached(sessionId) },
            hasMore = false,
        )
        val messages = page.items.map { it.toChatMessage() }
        if (messages.isNotEmpty()) {
            withContext(Dispatchers.IO) { local.cacheRemote(sessionId, messages, replaceAll = beforeId == null) }
        }
        return HistoryPage(messages, page.hasMore)
    }

    override suspend fun editMessage(sessionId: Long, messageId: String, text: String): Boolean {
        val id = messageId.toLongOrNull() ?: return false
        return runCatching { withContext(Dispatchers.IO) { remote.editMessage(id, text) } }.isSuccess
    }

    override suspend fun deleteMessage(sessionId: Long, messageId: String): Boolean {
        val id = messageId.toLongOrNull() ?: return false
        val ok = runCatching { withContext(Dispatchers.IO) { remote.deleteMessage(id) } }.isSuccess
        if (ok) withContext(Dispatchers.IO) { local.delete(messageId) }
        return ok
    }

    override suspend fun clearLocalMessages(sessionId: Long) {
        if (sessionId <= 0) return
        withContext(Dispatchers.IO) { local.clear(sessionId) }
    }

    // ------------------------------------------------------------ 会话

    override fun sessions(): List<ChatSession> = local.cachedSessions()

    override suspend fun createSession(name: String): ChatSession? = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext null
        runCatching {
            remote.createSession(FALLBACK_ROLE_ID, trimmed).toChatSession().also { local.cacheSession(it) }
        }.getOrNull()
    }

    override suspend fun createSessionFor(name: String, roleId: String?, characterId: Int?): ChatSession? =
        withContext(Dispatchers.IO) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@withContext null
            val role = roleId?.takeIf { it.isNotBlank() }
                ?: characterId?.takeIf { it > 0 }?.let { "u_$it" }
                ?: FALLBACK_ROLE_ID
            runCatching {
                remote.createSession(role, trimmed).toChatSession().also { local.cacheSession(it) }
            }.getOrNull()
        }

    override suspend fun renameSession(sessionId: Long, name: String): ChatSession? = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || sessionId <= 0) return@withContext null
        runCatching {
            remote.renameSession(sessionId, trimmed).toChatSession().also { local.cacheSession(it) }
        }.getOrNull()
    }

    override suspend fun deleteSession(sessionId: Long): Boolean = withContext(Dispatchers.IO) {
        if (sessionId <= 0) return@withContext false
        val ok = runCatching { remote.deleteSession(sessionId) }.isSuccess
        if (!ok) return@withContext false
        local.forgetSession(sessionId)
        if (currentSessionId() == sessionId) {
            setCurrentSessionId(sessions().firstOrNull()?.id ?: 0L)
        }
        true
    }

    override suspend fun syncSessionsFromServer() = withContext(Dispatchers.IO) {
        val rows = runCatching { remote.sessions() }.getOrNull() ?: return@withContext
        val mapped = rows.map { it.toChatSession() }
        local.replaceSessions(mapped)
        if (currentSessionId() !in mapped.map { it.id }) {
            setCurrentSessionId(mapped.firstOrNull()?.id ?: 0L)
        }
    }

    // ------------------------------------------------------------ 附件 / 语音 / 人设

    /**
     * 图片附件走 `/v1`：先 `POST /v1/uploads` 拿到 `upload_id`，再随消息只带这个引用。
     *
     * 以前这里委托给旧的 multipart 链路（`/chat/ai/recognize.json`），当时有两个阻塞：
     * 服务端 TOS 的 secret 与 access key 不配对（`SignatureDoesNotMatch` 500），
     * 以及 `/v1/uploads` 只认图片。第一个是真 bug，已修（`.env` 的 `TOS_SECRET_KEY`）。
     * 第二个是能力边界：**文件**（非图片）在 `/v1` 仍然没有对应接口，继续走 legacy，
     * 这是功能缺口，不是绕路。
     *
     * 图片一旦上传成功，服务端回合层会把它读成 data URL 交给模型（见
     * `turn_runner._load_images` 与 `ImageAttachmentMiddleware`），所以这里必须带 `upload_id`
     * 而不是本地路径——本地路径只有这台手机认识。
     */
    /** 上传图片拿 upload_id，不产生回复。图片附件走这条，文字与它合成一个回合（见 ChatRepository.uploadImage）。 */
    override suspend fun uploadImage(localFile: File, fileName: String): String? =
        runCatching { withContext(Dispatchers.IO) { remote.upload(localFile, mimeOf(localFile)) } }.getOrNull()

    override suspend fun sendAttachment(
        sessionId: Long,
        kind: MessageKind,
        localFile: File,
        fileName: String,
        placeholderId: String,
    ): ChatReply? {
        if (kind != MessageKind.IMAGE) {
            // 文件没有 `/v1` 接口，仍走 legacy。这里不静默返回 null（那会吞掉功能）。
            return legacy.sendAttachment(sessionId, kind, localFile, fileName, placeholderId)
        }
        val session = resolveSessionId(sessionId, null, null)
        val uploadId = withContext(Dispatchers.IO) {
            remote.upload(localFile, mimeOf(localFile))
        }
        val attachment = JSONObject()
            .put("upload_id", uploadId)
            .put("kind", "image")
            .put("name", fileName)
        val receipt = remote.submitWithAttachments(
            sessionId = session,
            text = "",
            clientMessageId = placeholderId,
            attachments = listOf(attachment),
        )
        return ChatReply(id = placeholderId, text = awaitTurnText(session, receipt.turnId))
    }

    /** 按扩展名给 Content-Type；服务端只认 jpeg/png/webp/heic，其他一律按 jpeg 试。 */
    private fun mimeOf(file: File): String = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        else -> "image/jpeg"
    }

    /** 收一轮的完整文字（附件路径用：界面只关心最终那段回复，不需要逐字）。 */
    private suspend fun awaitTurnText(sessionId: Long, expectedTurnId: String?): String {
        val acc = StringBuilder()
        var matchedTurn = expectedTurnId
        try {
            remote.events(sessionId, null).collect { frame ->
                val obj = runCatching { JSONObject(frame.data) }.getOrNull()
                if (obj != null) {
                    val turn = obj.optString("turn_id", "")
                    if (turn.isNotEmpty() && matchedTurn == null) matchedTurn = turn
                    val ours = turn.isNotEmpty() && turn == matchedTurn
                    when (frame.event) {
                        "turn.snapshot" -> if (ours) {
                            val text = obj.optString("text", "")
                            if (text.length > acc.length && text.startsWith(acc.toString())) {
                                acc.setLength(0)
                                acc.append(text)
                            }
                        }
                        "message.delta" -> if (ours) acc.append(obj.optString("delta", ""))
                        "message.done", "turn.cancelled" -> if (ours) throw TurnFinished
                        "error" -> if (ours) throw TurnFinished
                    }
                }
            }
        } catch (_: TurnFinished) {
            // 正常收尾：这一轮已经结束，不需要继续读事件流。
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogUtil.w("AgentBackedChatRepository 附件回合读取失败: ${e.message}")
        }
        return acc.toString()
    }

    /**
     * TTS 仍在旧 Java 链路：`/v1` 没有任何 TTS HTTP 接口，
     * 语音合成只存在于 LiveKit 语音管线内部（`api/livekit/agent.py`）。
     * 要迁得先在 companion-agent 补 `/v1/ai/tts`。
     */
    override suspend fun synthesizeSpeech(messageId: String, text: String): String? =
        legacy.synthesizeSpeech(messageId, text)

    /** 自定义人设仍是旧 Java 的 `setting_character` 表，见类注释。 */
    override suspend fun loadCharacters(): List<ChatCharacterOption> = legacy.loadCharacters()
    override fun selectedCharacterId(): Int? = legacy.selectedCharacterId()
    override fun setSelectedCharacterId(characterId: Int?) = legacy.setSelectedCharacterId(characterId)
    override suspend fun createCharacter(title: String, content: String, avatarUrl: String?): ChatCharacterOption? =
        legacy.createCharacter(title, content, avatarUrl)
    override suspend fun updateCharacter(characterId: Int, title: String, content: String, avatarUrl: String?): Boolean =
        legacy.updateCharacter(characterId, title, content, avatarUrl)
    override suspend fun deleteCharacter(characterId: Int): Boolean = legacy.deleteCharacter(characterId)

    override suspend fun saveMessage(sessionId: Long, message: ChatMessage) {
        withContext(Dispatchers.IO) { local.persist(sessionId, message) }
    }

    override suspend fun localPendingMessages(sessionId: Long): List<ChatMessage> =
        withContext(Dispatchers.IO) { local.localPending(sessionId) }

    // ------------------------------------------------------------ 映射

    private fun AgentMessageVo.toChatMessage(): ChatMessage = ChatMessage(
        id = id.toString(),
        role = if (role == "user") ChatRole.USER else ChatRole.ASSISTANT,
        text = content,
        createdAtMillis = parseIso(createdAt),
        // /v1 只有一个 kind：chat。语音/图片是附件形态，客户端本地记录才是权威。
        kind = MessageKind.TEXT,
    )

    private fun AgentSessionVo.toChatSession(): ChatSession = ChatSession(
        id = id,
        name = name.ifBlank { "新会话" },
        isDefault = false,
        roleId = roleId,
        characterId = null,
    )

    private fun parseIso(value: String): Long = runCatching {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.getOrDefault(System.currentTimeMillis())
}

/**
 * 同一会话只允许一个回合。
 *
 * `/v1` 本身会拒绝并发提交（409 conflict），但等一个 HTTP 往返再报错，
 * 用户看到的是「发了但没反应几秒后才弹错」。这里在本地先拦一道。
 */
private object MigrationGuard {
    @Volatile private var busy = false

    fun begin(): Boolean {
        if (busy) return false
        busy = true
        return true
    }

    fun end() {
        busy = false
    }
}

/** 事件流里读到本轮结束的信号：用它退出 `collect`，比 `transformWhile` 的类型推断更可靠。 */
private object TurnFinished : Exception() {
    private fun readResolve(): Any = TurnFinished
    override fun fillInStackTrace(): Throwable = this
}

/** 本地缓存：/v1 会话 id 空间独立，历史以服务端为准，这里只做离线副本。 */
class LocalChatStore(private val context: Context) {

    @Volatile private var ready = false

    private fun db() = com.openapi.core.db.AppDatabase.getInstance()

    fun cachedSessions(): List<ChatSession> =
        db().chatSessionLogDao().loadAll().map {
            ChatSession(
                id = it.id ?: 0L,
                name = it.name.orEmpty(),
                isDefault = false,
                roleId = it.roleId,
                characterId = it.characterId,
            )
        }

    fun cacheSession(session: ChatSession) {
        val dao = db().chatSessionLogDao()
        dao.insertOrReplace(com.openapi.core.model.ChatSessionLog().also { log ->
            log.id = session.id
            log.name = session.name
            log.updatedAt = System.currentTimeMillis()
            log.roleId = session.roleId
            log.type = com.openapi.core.model.ChatSessionLog.TYPE.CUSTOMER.type
        })
    }

    fun replaceSessions(sessions: List<ChatSession>) {
        val dao = db().chatSessionLogDao()
        dao.deleteAll()
        sessions.forEach { cacheSession(it) }
    }

    fun forgetSession(sessionId: Long) {
        val db = db()
        db.chatLogDao().deleteBySession(sessionId)
        db.chatSessionLogDao().deleteById(sessionId)
    }

    fun loadCached(sessionId: Long): List<ChatMessage> =
        db().chatLogDao().bySession(sessionId).mapNotNull { log ->
            com.openapi.feature.chat.data.mapper.ChatLogMapper.toDomain(
                chatId = log.chatId,
                who = log.who,
                msgType = log.msgType,
                rawContent = log.content,
                createdAtMillis = log.createTime?.time ?: 0L,
            )
        }

    fun localPending(sessionId: Long): List<ChatMessage> =
        loadCached(sessionId).filter {
            it.role == ChatRole.USER && it.id.startsWith("local-")
        }

    fun persist(sessionId: Long, message: ChatMessage) {
        val log = com.openapi.core.model.ChatLog()
        log.content = com.openapi.feature.chat.data.mapper.ChatLogMapper.toRawContent(message)
        log.who = com.openapi.feature.chat.data.mapper.ChatLogMapper.toWho(message.role)
        log.msgType = com.openapi.feature.chat.data.mapper.ChatLogMapper.toMsgType(message.kind)
        log.chatId = message.id
        log.createTime = java.util.Date(message.createdAtMillis)
        log.sessionId = sessionId
        db().chatLogDao().save(log)
    }

    fun cacheRemote(sessionId: Long, messages: List<ChatMessage>, replaceAll: Boolean) {
        if (replaceAll) {
            db().chatLogDao().deleteBySession(sessionId)
        }
        messages.forEach { persist(sessionId, it) }
    }

    fun delete(messageId: String) {
        db().chatLogDao().deleteByChatId(messageId)
    }

    fun clear(sessionId: Long) {
        db().chatLogDao().deleteBySession(sessionId)
    }
}
