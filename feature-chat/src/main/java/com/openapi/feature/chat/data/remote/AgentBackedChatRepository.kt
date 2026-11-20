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
import com.openapi.feature.chat.domain.model.ImageAttachment
import com.openapi.feature.chat.domain.model.ImageSource
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
                            // submit 还没成功，这条此刻不是「已发送」：崩溃重启后
                            // mergePending 才能把它认出来标成失败供重试，而不是被当成已落库的正式行。
                            delivery = com.openapi.feature.chat.domain.model.DeliveryStatus.SENDING,
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
     * 订阅事件流，把 `/v1` 的事件折叠成 [ChatStreamEvent]（折叠规则与断线续传在 [TurnEventCollector]）。
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
        TurnEventCollector(
            open = { lastEventId -> remote.events(sessionId, lastEventId) },
            parse = ::parseAgentEvent,
        ).collect(
            emit = { emit(it) },
            expectedTurnId = expectedTurnId,
            assistantLocalId = assistantLocalId,
            persist = { fullText -> persistTurn(sessionId, clientMessageId, userText, persistUser, fullText) },
        )
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
                        // 走到这里 submit 已成功、回合已开始：发送中的临时状态到此为止。
                        delivery = com.openapi.feature.chat.domain.model.DeliveryStatus.SENT,
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
            // 先缓存「未换 URL」的形态：库里只存 upload_id。签名 URL 只有 300 秒寿命，
            // 写进缓存等于第二天打开会话时拿到一张坏图。
            withContext(Dispatchers.IO) {
                local.cacheRemote(sessionId, messages, replaceAll = beforeId == null)
            }
        }
        return HistoryPage(hydrateImages(messages), page.hasMore)
    }

    /**
     * 把一页历史里的远程图片换成可渲染的签名 URL（每张一次 `GET /uploads/{id}`，
     * 服务端给 300 秒）。失败的那张保持空路径——下次刷新会再试，
     * 但不能让一张图拉不动就整页报错。
     */
    private suspend fun hydrateImages(messages: List<ChatMessage>): List<ChatMessage> =
        // IO 线程池：取签名 URL 是阻塞 HTTP，不能占主调度器。见 hydrateRemoteImages 的并发说明。
        withContext(Dispatchers.IO) {
            hydrateRemoteImages(messages) { id -> runCatching { remote.attachmentUrl(id) }.getOrNull() }
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

    /** 服务端一键清空（companion-agent 的 `DELETE /sessions/{id}/messages`）。 */
    override suspend fun clearHistory(sessionId: Long): Boolean {
        if (sessionId <= 0) return false
        return runCatching { withContext(Dispatchers.IO) { remote.clearHistory(sessionId) } }.isSuccess
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
    /**
     * 上传图片拿 upload_id，不产生回复。图片附件走这条，文字与它合成一个回合（见 ChatRepository.uploadImage）。
     *
     * 优先**直传**（ADR-011 同款）：sign 要一个单 key 只写的短期凭证，字节直接 PUT 进桶，
     * 不再穿过应用服务器；发消息时服务端按 upload_id 绑定校验（归属/时效/HEAD 复核）。
     * 签发或 PUT 任何一步失败（配额、CORS、网络）都**降级回中转** `/v1/uploads`——
     * 旧通道同时是降级通道，直传挂了不能把整条发送带走。heic 不在直传白名单里，直接走中转。
     */
    override suspend fun uploadImage(localFile: File, fileName: String): String? =
        runCatching {
            withContext(Dispatchers.IO) {
                val mime = mimeOf(localFile)
                if (mime != "image/heic") {
                    runCatching {
                        val (uploadId, putUrl) = remote.signUpload("chat-image", mime, localFile.length())
                        remote.putToPresigned(putUrl, localFile, mime)
                        uploadId
                    }.onFailure {
                        // 只记异常类型与消息：预签名 URL 是 bearer secret，绝不进日志
                        LogUtil.w("直传失败，已降级中转上传: ${it.message}")
                    }.getOrNull()?.let { return@withContext it }
                }
                remote.upload(localFile, mime)
            }
        }.getOrNull()

    /**
     * 重新换取一个签名 URL（整改清单 P1-10）。和历史整页换 URL 走同一条
     * `GET /uploads/{id}` 通道（`AgentChatRemote.attachmentUrl`），所以服务端不需要新接口。
     */
    override suspend fun refreshImageUrl(uploadId: String): String? =
        runCatching { withContext(Dispatchers.IO) { remote.attachmentUrl(uploadId) } }.getOrNull()

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

    /**
     * 该会话的本地缓存整页消息，供切换会话时立刻显示。
     * 远程图片在这份缓存里是空路径（库里只存 upload_id，签名 URL 有 300 秒寿命），
     * 等服务端那页到了再换 URL——切会话的瞬间先看到文字历史，比白屏强。
     */
    override suspend fun cachedMessages(sessionId: Long): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            runCatching { local.loadCached(sessionId) }.getOrDefault(emptyList())
        }

    // ------------------------------------------------------------ 映射

    private fun AgentMessageVo.toChatMessage(): ChatMessage {
        // 历史里的图片只带 upload_id（字节在对象存储）。先挂成 REMOTE 形态，
        // loadHistoryPage 再逐个换签名 URL；有图就把 kind 判成 IMAGE，
        // 本地缓存才有富类型可保（TEXT 行会被刷新清掉）。
        val images = attachments.filter { it.kind == "image" }.map {
            ImageAttachment(path = "", source = ImageSource.REMOTE, uploadId = it.uploadId)
        }
        return ChatMessage(
            id = id.toString(),
            role = if (role == "user") ChatRole.USER else ChatRole.ASSISTANT,
            text = content,
            createdAtMillis = parseIso(createdAt),
            kind = if (images.isNotEmpty()) MessageKind.IMAGE else MessageKind.TEXT,
            images = images,
            clientMessageId = clientMessageId,
        )
    }

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
class LocalChatStore @JvmOverloads constructor(
    private val context: Context,
    /** 单测注入内存库；生产传 null，走全局 AppDatabase。 */
    private val injectedDb: com.openapi.core.db.AppDatabase? = null,
) {

    @Volatile private var ready = false

    private fun db() = injectedDb ?: com.openapi.core.db.AppDatabase.getInstance()

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

    /**
     * 本地行里「还没送达」的那些：SENDING（上次没发完）或 FAILED（可重试）。
     * 已 SENT 的 local- 行不算——服务端历史里一定有它的正式版（对不上 id 也对得上内容），
     * 再合进列表就是同一句话显示两遍。整页刷新保留 local- 行以后，这里必须按状态过滤。
     */
    fun localPending(sessionId: Long): List<ChatMessage> =
        loadCached(sessionId).filter {
            it.role == ChatRole.USER && it.id.startsWith("local-") &&
                it.delivery != com.openapi.feature.chat.domain.model.DeliveryStatus.SENT
        }

    private fun toLog(sessionId: Long, message: ChatMessage): com.openapi.core.model.ChatLog {
        val log = com.openapi.core.model.ChatLog()
        log.content = com.openapi.feature.chat.data.mapper.ChatLogMapper.toRawContent(message)
        log.who = com.openapi.feature.chat.data.mapper.ChatLogMapper.toWho(message.role)
        log.msgType = com.openapi.feature.chat.data.mapper.ChatLogMapper.toMsgType(message.kind)
        log.chatId = message.id
        log.createTime = java.util.Date(message.createdAtMillis)
        log.sessionId = sessionId
        return log
    }

    fun persist(sessionId: Long, message: ChatMessage) {
        db().chatLogDao().save(toLog(sessionId, message))
    }

    /**
     * 把服务端一页消息写进本地缓存。
     *
     * 服务端历史只有文字和 upload_id；语音时长、图片的本机文件路径只存在本地行里，
     * 所以整页刷新只能清「服务端能原样重建」的纯文字行——以前的 deleteBySession
     * 连富内容行一起删，图片/语音消息退出重进就再也回不来。
     *
     * 每条服务端消息先找本地同一次发送的行（chatId == 服务端带回的 client_message_id，
     * 或已经并过的服务端 id）：文字用服务端的，附件优先本机文件（文件还在就直接显示，
     * 不依赖 5 分钟的签名 URL），upload_id 两边合到一起，然后整体改挂到服务端正式 id 下、
     * 删掉旧的临时 id 行。合并后缓存与历史一一对应，不会「同一句话存两行」。
     *
     * 性能（整改清单 P0-6.3）：合并只在内存里算，写库集中在最后一个事务——
     * 一次 saveAll（Room 内部对整列表复用预编译语句）+ 一次 deleteByChatIds，
     * 代替以前「每条消息一个隐式事务」的逐条 save。30 条一页从 30+ 个事务降到 1 个。
     */
    fun cacheRemote(sessionId: Long, messages: List<ChatMessage>, replaceAll: Boolean) {
        val db = db()
        val dao = db.chatLogDao()
        if (replaceAll) dao.deleteSessionTextOnly(sessionId)
        val local = dao.bySession(sessionId).mapNotNull { log ->
            com.openapi.feature.chat.data.mapper.ChatLogMapper.toDomain(
                chatId = log.chatId, who = log.who, msgType = log.msgType,
                rawContent = log.content, createdAtMillis = log.createTime?.time ?: 0L,
            )?.let { (log.chatId ?: "") to it }
        }.toMap()
        val toSave = ArrayList<com.openapi.core.model.ChatLog>(messages.size)
        val staleIds = ArrayList<String>()
        messages.forEach { message ->
            val client = message.clientMessageId
            val mine = local[message.id] ?: client?.let { local[it] }
            if (mine == null) {
                toSave.add(toLog(sessionId, message))
                return@forEach
            }
            // 本机文件还在就继续用本机文件；否则退回服务端对象（渲染时换签名 URL）
            val keepLocalImages = mine.images.isNotEmpty() && mine.images.size == message.images.size &&
                mine.images.all { it.source == com.openapi.feature.chat.domain.model.ImageSource.FILE && java.io.File(it.path).exists() }
            val images = if (keepLocalImages) {
                mine.images.mapIndexed { i, img -> img.copy(uploadId = message.images[i].uploadId ?: img.uploadId) }
            } else {
                message.images
            }
            toSave.add(
                toLog(
                    sessionId,
                    mine.copy(
                        id = message.id,
                        text = message.text.ifBlank { mine.text },
                        createdAtMillis = message.createdAtMillis,
                        kind = when {
                            mine.kind != MessageKind.TEXT -> mine.kind
                            images.isNotEmpty() -> MessageKind.IMAGE
                            else -> MessageKind.TEXT
                        },
                        images = images,
                        delivery = com.openapi.feature.chat.domain.model.DeliveryStatus.SENT,
                        clientMessageId = null,
                    ),
                ),
            )
            if (mine.id != message.id) staleIds.add(mine.id)
        }
        db.runInTransaction {
            if (toSave.isNotEmpty()) dao.saveAll(toSave)
            // saveAll 按列表顺序逐条 REPLACE：若先删的行 id 恰好也是本页某条的目标 id，
            // 删在写后仍会误删新行，所以「行没被本页重新写入」才删。当前调用方传的都是
            // 本地临时 id（local- 前缀，服务端 id 不会撞上），这里保险起见按目标 id 过滤。
            val rewritten = toSave.mapNotNull { it.chatId }.toSet()
            val toDelete = staleIds.filterNot { it in rewritten }
            if (toDelete.isNotEmpty()) dao.deleteByChatIds(toDelete)
        }
    }

    fun delete(messageId: String) {
        db().chatLogDao().deleteByChatId(messageId)
    }

    fun clear(sessionId: Long) {
        db().chatLogDao().deleteBySession(sessionId)
    }
}
