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
import org.json.JSONObject
import java.io.File

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

    /** 图片上传与附件回合（P3-17 拆出）：字节不进仓库类，这里只编排。 */
    private val attachments = AttachmentApi(remote)

    private companion object {
        const val TAG = "AgentChatRepo"

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
        location: com.openapi.feature.chat.domain.model.GeoLocation?,
    ): Flow<ChatStreamEvent> = flow {
        val guard = MigrationGuard
        val sid = resolveSessionId(sessionId, roleId, characterId)
        val clientId = clientMessageId ?: "local-user-${System.currentTimeMillis()}"
        val assistantId = "local-assistant-${System.currentTimeMillis()}"

        if (!guard.begin()) {
            // 同一个会话同时只允许一个回合：/v1 会返回 409，这里提前拦住，错误信息更直白。
            // 带机读 code：界面按 ChatStrings.previousTurnRunning() 出当前语言的文案。
            emit(ChatStreamEvent.Error(describeAgentError("conflict"), code = ChatStreamEvent.Error.CODE_PREVIOUS_TURN_RUNNING))
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
            val locationJson = location?.let { buildAgentLocationJson(it.latitude, it.longitude, it.accuracyMeters) }
            // 409「上一轮回复还没结束」的恢复：**先取消服务端那一轮，再重试一次**。
            // 以前点停止只杀本地 Job，服务端回合继续跑，用户再发就吃 409——
            // 「怎么发都失败」的那条链在这里断开。只重试一次：另一成因是别的设备正在跑这一轮，
            // 那种重试到底也是 409，再多只是把会话卡得更久；仍失败就原样交给界面（明确文案）。
            val receipt = try {
                withTurnConflictRecovery(
                    isConflict = { (it as? AgentChatRemote.Failure)?.status == 409 },
                    cancelTurn = { withContext(Dispatchers.IO) { remote.cancel(sid) } },
                    onConflictLogged = { LogUtil.w("$TAG cancel-before-retry failed session=$sid: ${it.message}") },
                ) {
                    withContext(Dispatchers.IO) {
                        if (attachments.isEmpty()) {
                            remote.submit(sid, userText, clientId, locationJson)
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
                                        // duration_s 只有视频带（服务端契约：可选 int 0..86400）；
                                        // 图片/文件不带这个 key，零时长也不写（没有可信值就别声称）
                                        .apply { it.durationSeconds?.let { d -> put("duration_s", d) } }
                                },
                                location = locationJson,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // 取消过后仍然 409（多半是别的设备占着这一轮）：不再往上抛裸异常——
                // 按机读 code 交给界面，文案走 ChatStrings.previousTurnRunning()。
                if ((e as? AgentChatRemote.Failure)?.status == 409) {
                    emit(ChatStreamEvent.Error(describeAgentError("conflict"), code = ChatStreamEvent.Error.CODE_PREVIOUS_TURN_RUNNING))
                    return@flow
                }
                throw e
            }
            collectTurn(
                sid,
                receipt.turnId,
                assistantId,
                clientId,
                userText,
                persistUserMessage,
                // 回执里的正式 id：带进 Done.userId，刚发出的气泡立刻可编辑/删除
                userServerId = receipt.messageId.takeIf { it > 0 }?.toString(),
            )
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
            emit(ChatStreamEvent.Error(describeAgentError("conflict"), code = ChatStreamEvent.Error.CODE_PREVIOUS_TURN_RUNNING))
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
            collectTurn(sid, null, assistantId, null, null, false, userServerId = null)
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
        userServerId: String?,
    ) {
        TurnEventCollector(
            open = { lastEventId -> remote.events(sessionId, lastEventId) },
            parse = ::parseAgentEvent,
        ).collect(
            emit = { emit(it) },
            expectedTurnId = expectedTurnId,
            assistantLocalId = assistantLocalId,
            userMessageId = userServerId,
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

    /**
     * A40「插话」：把一条排队文字立即提交（服务端按回合阶段裁决 merge/steer/queue/start）。
     *
     * 与 [sendMessageStream] 的关键差别：**这里不等回合**。回合进行中的提交在服务端不会
     * 409（Lua 判定直接给出裁决），所以没有 cancel-before-retry 那一段；merged/steered
     * 的回复会出现在**当前**事件流里（`turn.restarted` 已折叠），queued/started 由
     * [takeCaughtTurn] 在本轮收尾后接住。失败返回 ok=false——调用方把文字放回输入框。
     */
    override suspend fun steerQueuedMessage(
        sessionId: Long,
        userText: String,
        characterId: Int?,
        roleId: String?,
        clientMessageId: String,
    ): com.openapi.feature.chat.domain.model.SteerResult = withContext(Dispatchers.IO) {
        val sid = resolveSessionId(sessionId, roleId, characterId)
        val receipt = runCatching { remote.submit(sid, userText, clientMessageId, null) }.getOrNull()
            ?: return@withContext com.openapi.feature.chat.domain.model.SteerResult(false)
        // disposition 缺失 = 旧服务端或 duplicate 回执：宁可当失败（文字回输入框可重发），
        // 绝不静默丢一条用户输入。
        val disposition = receipt.disposition
            ?: return@withContext com.openapi.feature.chat.domain.model.SteerResult(false)
        val mapped = when (disposition) {
            "merged" -> com.openapi.feature.chat.domain.model.SteerDisposition.MERGED
            "steered" -> com.openapi.feature.chat.domain.model.SteerDisposition.STEERED
            "queued" -> com.openapi.feature.chat.domain.model.SteerDisposition.QUEUED
            "started" -> com.openapi.feature.chat.domain.model.SteerDisposition.STARTED
            else -> null
        } ?: return@withContext com.openapi.feature.chat.domain.model.SteerResult(false)
        // submit 已成功：本地缓存这条用户消息（离线重启后气泡与历史能对上）。
        local.persist(
            sid,
            ChatMessage(
                id = clientMessageId, role = ChatRole.USER, text = userText,
                createdAtMillis = System.currentTimeMillis(),
                delivery = com.openapi.feature.chat.domain.model.DeliveryStatus.SENT,
            ),
        )
        com.openapi.feature.chat.domain.model.SteerResult(
            ok = true,
            disposition = mapped,
            messageId = receipt.messageId,
            turnId = receipt.turnId,
        )
    }

    /**
     * A40：接住「插话」裁决为 queued/started 时服务端开的那条回合。
     *
     * 订阅时机在**上一轮事件流收尾之后**（ReplyStreamer 的 runTurn 末尾），因此不会与
     * 上一轮的连接串台。新订阅 = 只看未来事件 + 活跃回合的 snapshot：排空回合若已开始，
     * snapshot 补齐已生成的部分，一个字不丢。用户消息服务端已落库（[userServerId] 进 Done），
     * 本地只补助手行。
     */
    override fun takeCaughtTurn(
        sessionId: Long,
        skipTurnId: String?,
        userServerId: Long?,
        expectedTurnId: String?,
    ): Flow<ChatStreamEvent> = flow {
        if (sessionId <= 0) return@flow
        val assistantId = "local-assistant-${System.currentTimeMillis()}"
        TurnEventCollector(
            open = { lastEventId -> remote.events(sessionId, lastEventId) },
            parse = ::parseAgentEvent,
        ).collect(
            emit = { emit(it) },
            expectedTurnId = expectedTurnId,
            assistantLocalId = assistantId,
            userMessageId = userServerId?.takeIf { it > 0 }?.toString(),
            persist = { fullText -> persistTurn(sessionId, null, null, false, fullText) },
            skipTurnId = skipTurnId,
        )
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

    /**
     * 只删本地缓存行，不问服务端——乐观删除（MessageRemoval）在界面移除的同一刻调它。
     * 缓存行留着的话，「切会话先顶缓存」和兜底对齐会把刚删掉的消息再画出来。
     */
    override suspend fun deleteLocalMessage(messageId: String) {
        withContext(Dispatchers.IO) { local.delete(messageId) }
    }

    /**
     * 服务端取消（`POST /sessions/{id}/cancel`）。失败只记日志、不外抛：
     * 「停止」的本地收尾已经做完了，取消是补上服务端那一侧，它挂了不能让停止本身变成一次失败。
     */
    override suspend fun cancelTurn(sessionId: Long) {
        if (sessionId <= 0) return
        runCatching { withContext(Dispatchers.IO) { remote.cancel(sessionId) } }
            .onFailure { LogUtil.w("$TAG cancel turn failed session=$sessionId: ${it.message}") }
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

    // P3-21：cachedSessions 读 Room，这里切 IO 后再交给调用方
    override suspend fun sessions(): List<ChatSession> = withContext(Dispatchers.IO) { local.cachedSessions() }

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
     * 图片附件走 `/v1`：先拿到 `upload_id`，再随消息只带这个引用。
     * 直传优先、失败降级中转的完整逻辑在 [AttachmentApi.uploadImage]。
     *
     * 能力边界：文件（非图片）在 `/v1` 仍然没有对应接口，继续走 legacy，这是功能缺口，不是绕路。
     * 图片一旦上传成功，服务端回合层会把它读成 data URL 交给模型（见
     * `turn_runner._load_images` 与 `ImageAttachmentMiddleware`），所以这里必须带 `upload_id`
     * 而不是本地路径——本地路径只有这台手机认识。
     */
    override suspend fun uploadImage(localFile: File, fileName: String): String? =
        attachments.uploadImage(localFile)

    /** 视频只走直传（scene `chat-video`），没有中转降级；规则与失败语义见接口注释与 [AttachmentApi.uploadVideo]。 */
    override suspend fun uploadVideo(localFile: File, fileName: String): String? =
        attachments.uploadVideo(localFile)

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
        val uploadId = attachments.uploadRelayed(localFile)
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
        return ChatReply(id = placeholderId, text = attachments.awaitTurnText(session, receipt.turnId))
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

    override suspend fun localAttachmentPathsInUse(): Set<String> =
        withContext(Dispatchers.IO) { local.localAttachmentPathsInUse() }

    /**
     * 该会话的本地缓存整页消息，供切换会话时立刻显示。
     * 远程图片在这份缓存里是空路径（库里只存 upload_id，签名 URL 有 300 秒寿命），
     * 等服务端那页到了再换 URL——切会话的瞬间先看到文字历史，比白屏强。
     */
    override suspend fun cachedMessages(sessionId: Long): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            runCatching { local.loadCached(sessionId) }.getOrDefault(emptyList())
        }

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

