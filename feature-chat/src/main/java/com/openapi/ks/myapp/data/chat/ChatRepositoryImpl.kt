package com.openapi.ks.myapp.data.chat

import com.openapi.core.net.ApiRequest
import com.openapi.chat.data.mapper.ChatLogMapper
import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatReply
import com.openapi.chat.domain.model.HistoryPage
import com.openapi.chat.domain.model.DeliveryStatus
import com.openapi.chat.domain.model.MessageKind
import com.openapi.ks.myapp.bean.vo.ChatMessagePageVo
import com.openapi.ks.myapp.bean.vo.ChatMessageVo
import com.openapi.ks.myapp.bean.vo.ChatSessionVo
import com.openapi.ks.myapp.bean.vo.Choice
import com.openapi.chat.domain.model.ChatRole
import com.openapi.chat.domain.model.ChatSession
import com.openapi.chat.domain.model.ChatStreamEvent
import com.openapi.chat.domain.repository.ChatRepository
import com.openapi.core.net.request.CommonResponse
import com.openapi.core.util.LogUtil
import com.openapi.core.util.PreferenceUtils
import com.openapi.core.net.request.BaseRequest
import com.openapi.ks.myapp.bean.form.ChatLog
import com.openapi.ks.myapp.bean.form.ChatSessionLog
import com.openapi.ks.myapp.bean.vo.CharacterVo
import com.openapi.ks.myapp.bean.vo.TtsResponseVo
import com.openapi.ks.myapp.constant.Constants
import com.openapi.core.net.BaseService
import com.openapi.core.net.awaitPost
import com.openapi.core.net.awaitPostOk
import com.openapi.core.net.awaitUpload
import com.openapi.ks.myapp.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * [ChatRepository] 的 app 侧实现。
 *
 * 放在 app 而不是 features:chat，是因为它必须用到本应用的后端协议细节：
 * - 鉴权（UA 签名/devid/Token，见 [BaseRequest] 构造函数）——这里复用它只是为了拿一份
 *   跟其它接口一致的 header，没有真的通过 Volley 发请求
 * - greenDAO 的 ChatLog 表
 *
 * 这些都属于「本应用协议」，不属于「通用聊天能力」，所以不上移到 core。
 * 依赖方向依然是 features:chat ← app（由 ChatDependencies.install 注入）。
 *
 * 对话契约见 docs/agent-and-media-roadmap.md 第一节：现在打的是 `/chat/stream.json`，
 * 这是协议的第一个（临时）实现方，未来换成 Python agent-runtime 时这个类要改，
 * 但 features:chat 那边的 [ChatRepository] 接口和上层代码不用动。
 */
/**
 * [BaseService.postData] 走 [BeanUtil.transBean2Map] 的字段反射序列化，所有字段都必须
 * 非空且和服务端 `CharacterForm` 字段名一致（title/content/url/id），否则要么序列化出
 * 字面量 "null" 字符串，要么服务端收不到对应参数。
 */
// BeanUtil.transBean2Map 直接用 field.get(obj) 读字段，不调用 setAccessible(true)，
// 对 Lombok/Kotlin data class 生成的 private 字段会静默拿不到值（异常被吞掉，字段从表单里
// 消失）。沿用仓库里其它 Form 类的写法：裸的 public 字段，用 @JvmField 避免 Kotlin
// 自动生成 private 字段 + getter。
private class CharacterCreateParam(
    @JvmField val title: String,
    @JvmField val content: String,
    @JvmField val url: String,
)

private class CharacterUpdateParam(
    @JvmField val id: Int,
    @JvmField val title: String,
    @JvmField val content: String,
    @JvmField val url: String,
)

private class CharacterDeleteParam(
    @JvmField val id: Int,
)

private class SpeechParam(
    @JvmField val id: String,
    @JvmField val content: String,
)

private class AttachmentParam(
    @JvmField val id: String,
    @JvmField val sessionId: Long,
)

/** 最新一页消息：不带 beforeId（[MessagesOlderPageParam] 才带），避免 null 被序列化成字面量 "null"。 */
private class MessagesFirstPageParam(
    @JvmField val sessionId: Long,
    @JvmField val limit: Int,
)

private class MessagesOlderPageParam(
    @JvmField val sessionId: Long,
    @JvmField val beforeId: Long,
    @JvmField val limit: Int,
)

private class SessionCreateParam(
    @JvmField val name: String,
)

private class SessionRenameParam(
    @JvmField val id: Long,
    @JvmField val name: String,
)

private class SessionIdParam(
    @JvmField val id: Long,
)

private class MessageDeleteParam(
    @JvmField val sessionId: Long,
    @JvmField val id: Long,
)

private class MessageEditParam(
    @JvmField val sessionId: Long,
    @JvmField val id: Long,
    @JvmField val text: String,
)

class ChatRepositoryImpl(
    private val context: android.content.Context,
) : ChatRepository {

    private val baseService = BaseService().apply { setup(context) }

    /** SSE 连接必须不设读超时——回复多久吐完完全看大模型，不是一个固定时间能框住的。 */
    private val streamHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private companion object {
        const val TAG = "chat"
        /** 本地临时消息的 id 前缀：这些消息只存在本地，服务端历史里没有。 */
        const val LOCAL_ID_PREFIX = "local-"
        const val PREF_SELECTED_CHARACTER_ID = "chat_selected_character_id"
        /** 聊天记录只保留最近这么多条，沿用旧逻辑。 */
        const val CHAT_LOG_KEEP = 30L
        /** 单线程清理队列：所有实例共享，保证清理串行执行。 */
        val trimExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    }

    override fun currentSessionId(): Long =
        PreferenceUtils.getPrefLong(context, Constants.CHAT_SESSION, 0)

    override fun selectedCharacterId(): Int? {
        val id = PreferenceUtils.getPrefInt(context, PREF_SELECTED_CHARACTER_ID, -1)
        return id.takeIf { it > 0 }
    }

    override fun setSelectedCharacterId(characterId: Int?) {
        PreferenceUtils.setPrefInt(context, PREF_SELECTED_CHARACTER_ID, characterId ?: -1)
    }

    override suspend fun loadCharacters(): List<ChatCharacterOption> = withContext(Dispatchers.IO) {
        runCatching {
            baseService.getInfoListData(
                Constants.getUrl(Constants.API_CHARACTER_URL),
                CharacterVo::class.java,
                TAG,
            )
        }.getOrNull()?.voList.orEmpty().map { it.toOption() }
    }

    override suspend fun loadHistoryPage(sessionId: Long, beforeId: Long?, limit: Int): HistoryPage {
        if (sessionId <= 0) return HistoryPage(emptyList(), hasMore = false)
        val safeLimit = limit.coerceIn(1, 200)
        val url = Constants.getUrl(Constants.CHAT_MESSAGES_URL)
        val remote = if (beforeId == null) {
            postForResult(url, MessagesFirstPageParam(sessionId, safeLimit), ChatMessagePageVo::class.java)
        } else {
            postForResult(url, MessagesOlderPageParam(sessionId, beforeId, safeLimit), ChatMessagePageVo::class.java)
        }
        if (remote != null) {
            // 返回合并过本机附件信息的消息：服务端只有文字，语音、图片、文件要从本地记录里补回来
            val merged = withContext(Dispatchers.IO) { cacheRemotePage(sessionId, remote.voList, replaceAll = beforeId == null) }
            return HistoryPage(merged, remote.hasMore)
        }
        // 服务端不可达：只能给出本地缓存的最新一页，更早的历史离线时拿不到
        if (beforeId != null) return HistoryPage(emptyList(), hasMore = false)
        val cached = withContext(Dispatchers.IO) {
            AppDatabase.getInstance().chatLogDao().bySession(sessionId).mapNotNull { log ->
                ChatLogMapper.toDomain(
                    chatId = log.chatId,
                    who = log.who,
                    msgType = log.msgType,
                    rawContent = log.content,
                    createdAtMillis = log.createTime?.time ?: 0L,
                )
            }
        }
        return HistoryPage(cached, hasMore = false)
    }

    override fun sendMessageStream(
        sessionId: Long,
        userText: String,
        characterId: Int?,
        roleId: String?,
        persistUserMessage: Boolean,
        clientMessageId: String?,
    ): Flow<ChatStreamEvent> = streamChat(
        requestSessionId = sessionId,
        text = userText,
        regenerate = false,
        characterId = characterId,
        roleId = roleId,
        persistUserMessage = persistUserMessage,
        clientMessageId = clientMessageId,
    )

    override fun regenerateStream(
        sessionId: Long,
        characterId: Int?,
        roleId: String?,
    ): Flow<ChatStreamEvent> = streamChat(
        requestSessionId = sessionId,
        text = null,
        regenerate = true,
        characterId = characterId,
        roleId = roleId,
        persistUserMessage = false,
        clientMessageId = null,
    )

    /**
     * 对话流式请求的公共实现。
     *
     * 请求只带 sessionId、客户端消息 id、当轮 text（或 regenerate=true 时不带 text），
     * 历史由服务端按 sessionId 读取，这里不再上传历史。
     *
     * 会话 id：请求里 sessionId<=0 表示新建会话，新 id 通过流里每一帧的 sessionId 返回，
     * 收到后写进 SP 作为当前会话。
     */
    private fun streamChat(
        requestSessionId: Long,
        text: String?,
        regenerate: Boolean,
        characterId: Int?,
        roleId: String?,
        persistUserMessage: Boolean,
        clientMessageId: String?,
    ): Flow<ChatStreamEvent> = callbackFlow {
        val now = System.currentTimeMillis()
        val clientMsgId = clientMessageId ?: "local-user-$now"
        val assistantId = "local-assistant-$now"
        // 服务端会把它回写到每一帧里；请求本身是新会话（<=0）时才采用这个 id
        var activeSessionId = requestSessionId

        if (!regenerate && persistUserMessage && requestSessionId > 0 && text != null) {
            persist(
                requestSessionId,
                ChatMessage(
                    id = clientMsgId,
                    role = ChatRole.USER,
                    text = text,
                    createdAtMillis = now,
                ),
            )
        }

        val bodyBuilder = FormBody.Builder()
            .add("sessionId", requestSessionId.toString())
        if (regenerate) {
            bodyBuilder.add("regenerate", "true")
        } else {
            bodyBuilder.add("id", clientMsgId)
            bodyBuilder.add("text", text.orEmpty())
        }
        if (characterId != null && characterId > 0) {
            bodyBuilder.add("characterId", characterId.toString())
        }
        if (!roleId.isNullOrBlank()) {
            bodyBuilder.add("roleId", roleId)
        }

        val httpRequest = Request.Builder()
            .url(Constants.getUrl(Constants.CHAT_STREAM_URL))
            .headers(authHeaders())
            .post(bodyBuilder.build())
            .build()

        val call = streamHttpClient.newCall(httpRequest)
        val fullText = StringBuilder()

        fun adoptSessionId(id: Long) {
            if (requestSessionId > 0 || id <= 0 || id == activeSessionId) return
            activeSessionId = id
            setCurrentSessionId(id)
        }

        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                LogUtil.e(e)
                trySend(ChatStreamEvent.Error(e.message ?: "网络请求失败"))
                close()
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        trySend(ChatStreamEvent.Error("HTTP ${resp.code}"))
                        close()
                        return
                    }
                    val source = resp.body?.source()
                    if (source == null) {
                        trySend(ChatStreamEvent.Error("空响应"))
                        close()
                        return
                    }
                    try {
                        while (!call.isCanceled()) {
                            val line = source.readUtf8Line() ?: break
                            // 服务端出错时返回的是普通 JSON（{"errno":10000,"error":"..."}），不是 data: 帧；
                            // 不能当成空流收尾，要把错误原文交给界面
                            if (line.startsWith("{")) {
                                val body = runCatching { JSONObject(line) }.getOrNull()
                                if (body != null && body.optInt("errno", 0) != 0) {
                                    trySend(ChatStreamEvent.Error(body.optString("error", "").ifBlank { "服务器错误 ${body.optInt("errno")}" }))
                                    close()
                                    return
                                }
                                // 错误帧：{"error":"...","done":true,"userMessageId":N,"assistantMessageId":0}
                                val frameError = body?.optString("error", "").orEmpty()
                                if (frameError.isNotBlank()) {
                                    val errUserId = body!!.optLong("userMessageId", 0L).takeIf { it > 0 }?.toString()
                                    trySend(ChatStreamEvent.Error(frameError, errUserId))
                                    close()
                                    return
                                }
                            }
                            if (!line.startsWith("data:")) continue
                            val payload = line.removePrefix("data:").trim()
                            if (payload.isEmpty()) continue
                            val chunk = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                            adoptSessionId(chunk.optLong("sessionId", 0L))
                            val delta = chunk.optString("delta", "")
                            val done = chunk.optBoolean("done", false)
                            val error = chunk.optString("error", null)
                            if (error != null) {
                                val errUserId = chunk.optLong("userMessageId", 0L).takeIf { it > 0 }?.toString()
                                trySend(ChatStreamEvent.Error(error, errUserId))
                                close()
                                return
                            }
                            if (delta.isNotEmpty()) {
                                fullText.append(delta)
                                trySend(ChatStreamEvent.Delta(assistantId, delta))
                            }
                            if (done) {
                                val serverUserId = chunk.optLong("userMessageId", 0L)
                                val serverAssistantId = chunk.optLong("assistantMessageId", 0L)
                                cacheTurn(
                                    sessionId = activeSessionId,
                                    clientUserId = clientMsgId,
                                    userText = if (!regenerate && (persistUserMessage || clientMessageId != null)) text else null,
                                    serverUserId = serverUserId,
                                    reply = fullText.toString(),
                                    serverAssistantId = serverAssistantId,
                                    localAssistantId = assistantId,
                                )
                                trySend(
                                    ChatStreamEvent.Done(
                                        assistantId = serverAssistantId.takeIf { it > 0 }?.toString(),
                                        userId = serverUserId.takeIf { it > 0 }?.toString(),
                                        fullText = fullText.toString(),
                                    ),
                                )
                                close()
                                return
                            }
                        }
                        // 流结束但没收到 done=true 帧（比如连接被提前挂断），按已收到的内容收尾，
                        // 而不是整段丢弃——用户至少能看到已经生成的部分。
                        if (!call.isCanceled() && fullText.isEmpty()) {
                            // 一个字都没收到就结束了：这是失败，不是「空回复」
                            trySend(ChatStreamEvent.Error("服务器没有返回内容，请稍后重试"))
                        } else if (!call.isCanceled()) {
                            // 没有 done 帧，服务端 id 未知：本地按临时 id 缓存，ViewModel 会兜底拉取历史
                            cacheTurn(
                                sessionId = activeSessionId,
                                clientUserId = clientMsgId,
                                userText = null,
                                serverUserId = 0L,
                                reply = fullText.toString(),
                                serverAssistantId = 0L,
                                localAssistantId = assistantId,
                            )
                            trySend(ChatStreamEvent.Done(assistantId = null, userId = null, fullText = fullText.toString()))
                        }
                    } catch (e: Exception) {
                        LogUtil.e(e)
                        trySend(ChatStreamEvent.Error(e.message ?: "流式读取失败"))
                    } finally {
                        close()
                    }
                }
            }
        })

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    /**
     * 把一轮对话写进本地缓存（离线副本）。用服务端正式 id 作为 chatId；
     * 用户消息若是本地先写入的（临时 id），这里换成正式 id。空回复不缓存。
     */
    private fun cacheTurn(
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

    override suspend fun editMessage(sessionId: Long, messageId: String, text: String): Boolean {
        val id = messageId.toLongOrNull() ?: return false
        if (sessionId <= 0) return false
        return postForResult(
            Constants.getUrl(Constants.CHAT_MESSAGE_EDIT_URL),
            MessageEditParam(sessionId, id, text),
            ChatMessageVo::class.java,
        ) != null
    }

    override suspend fun clearLocalMessages(sessionId: Long) {
        if (sessionId <= 0) return
        withContext(Dispatchers.IO) { AppDatabase.getInstance().chatLogDao().deleteBySession(sessionId) }
    }

    override suspend fun deleteMessage(sessionId: Long, messageId: String): Boolean {
        val id = messageId.toLongOrNull() ?: return false
        if (sessionId <= 0) return false
        val ok = postForOk(
            Constants.getUrl(Constants.CHAT_MESSAGE_DELETE_URL),
            MessageDeleteParam(sessionId, id),
        )
        if (ok) {
            withContext(Dispatchers.IO) { AppDatabase.getInstance().chatLogDao().deleteByChatId(messageId) }
        }
        return ok
    }

    override suspend fun synthesizeSpeech(messageId: String, text: String): String? {
        val vo = postForResult(
            Constants.getUrl(Constants.CHAT_TTS_URL),
            SpeechParam(id = messageId, content = text),
            TtsResponseVo::class.java,
        ) ?: return null
        val data = vo.data?.takeIf { it.isNotEmpty() } ?: return null
        val dir = File(context.filesDir, "tts").apply { mkdirs() }
        val file = File(dir, vo.fileName?.takeIf { it.isNotBlank() } ?: "tts_$messageId.mp3")
        return withContext(Dispatchers.IO) {
            runCatching { file.writeBytes(android.util.Base64.decode(data, android.util.Base64.DEFAULT)) }
                .getOrNull()
                ?.let { "file://" + file.absolutePath }
        }
    }

    override suspend fun saveMessage(sessionId: Long, message: ChatMessage) {
        persist(sessionId, message)
    }

    override suspend fun localPendingMessages(sessionId: Long): List<ChatMessage> = withContext(Dispatchers.IO) {
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
        LogUtil.i("$TAG localPending session=$sessionId rows=${rows.size} adoptedFromZero=${orphans.size}")
        rows
            .mapNotNull { log ->
                ChatLogMapper.toDomain(
                    chatId = log.chatId,
                    who = log.who,
                    msgType = log.msgType,
                    rawContent = log.content,
                    createdAtMillis = log.createTime?.time ?: 0L,
                )
            }
    }

    override suspend fun sendAttachment(
        sessionId: Long,
        kind: MessageKind,
        localFile: File,
        fileName: String,
        placeholderId: String,
    ): ChatReply? {
        val isImage = kind == MessageKind.IMAGE
        val url = Constants.getUrl(if (isImage) Constants.CHAT_IMAGE_URL else Constants.CHAT_FILE_URL)
        // 服务端用这两个表单名区分图片与文件（recognize.json 读 image，file.json 读 file）
        val partName = if (isImage) "image" else "file"
        val param = AttachmentParam(id = placeholderId, sessionId = sessionId)
        return baseService.awaitUpload(url, param, listOf(localFile), partName, Choice::class.java, TAG)
            ?.let { response ->
                ChatReply(
                    id = response.id ?: placeholderId,
                    text = response.message?.content.orEmpty(),
                )
            }
    }

    override suspend fun createCharacter(title: String, content: String, avatarUrl: String?): ChatCharacterOption? {
        // BeanUtil.transBean2Map 用 String.valueOf() 序列化每个字段，null 会变成字面量字符串
        // "null" 发给服务端——用空串兜底，避免这个坑
        val param = CharacterCreateParam(title = title, content = content, url = avatarUrl.orEmpty())
        val vo = postForResult(Constants.API_CHARACTER_CREATE_URL, param, CharacterVo::class.java) ?: return null
        return vo.toOption()
    }

    override suspend fun updateCharacter(characterId: Int, title: String, content: String, avatarUrl: String?): Boolean {
        val param = CharacterUpdateParam(id = characterId, title = title, content = content, url = avatarUrl.orEmpty())
        return postForResult(Constants.API_CHARACTER_UPDATE_URL, param, CharacterVo::class.java) != null
    }

    override suspend fun deleteCharacter(characterId: Int): Boolean {
        val param = CharacterDeleteParam(id = characterId)
        return postForResult(Constants.API_CHARACTER_DELETE_URL, param, Any::class.java) != null
    }

    override fun sessions(): List<ChatSession> {
        return AppDatabase.getInstance().chatSessionLogDao().loadAll().map { log ->
            ChatSession(
                id = log.id ?: 0L,
                name = log.name.orEmpty(),
                // 服务端没有“默认会话”的概念，列表里每个会话都可以删除
                isDefault = false,
            )
        }
    }

    override fun setCurrentSessionId(sessionId: Long) {
        PreferenceUtils.setSettingLong(context, Constants.CHAT_SESSION, sessionId)
    }

    override suspend fun createSession(name: String): ChatSession? = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext null
        val dao = AppDatabase.getInstance().chatSessionLogDao()
        if (dao.loadAll().any { it.name == trimmed }) return@withContext null
        val vo = postForResult(
            Constants.getUrl(Constants.CHAT_SESSION_CREATE_URL),
            SessionCreateParam(name = trimmed),
            ChatSessionVo::class.java,
        ) ?: return@withContext null
        dao.insertOrReplace(vo.toLog())
        vo.toSession()
    }

    override suspend fun renameSession(sessionId: Long, name: String): ChatSession? = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || sessionId <= 0) return@withContext null
        val vo = postForResult(
            Constants.getUrl(Constants.CHAT_SESSION_RENAME_URL),
            SessionRenameParam(id = sessionId, name = trimmed),
            ChatSessionVo::class.java,
        ) ?: return@withContext null
        AppDatabase.getInstance().chatSessionLogDao().insertOrReplace(vo.toLog())
        vo.toSession()
    }

    override suspend fun deleteSession(sessionId: Long): Boolean = withContext(Dispatchers.IO) {
        val ok = postForOk(
            Constants.getUrl(Constants.CHAT_SESSION_DELETE_URL),
            SessionIdParam(id = sessionId),
        )
        if (!ok) return@withContext false
        val db = AppDatabase.getInstance()
        db.chatLogDao().deleteBySession(sessionId)
        db.chatSessionLogDao().deleteById(sessionId)
        if (currentSessionId() == sessionId) {
            // 没有剩下的会话时回到 0：下次发送由服务端新建。若仍指向已删除的 id，之后每次发送都会报「会话不存在」
            setCurrentSessionId(sessions().firstOrNull()?.id ?: 0L)
        }
        true
    }

    override suspend fun syncSessionsFromServer() {
        withContext(Dispatchers.IO) {
            val list = runCatching {
                baseService.getInfoListData(
                    Constants.getUrl(Constants.CHAT_SESSION_URL),
                    ChatSessionVo::class.java,
                    TAG,
                )
            }.getOrNull()?.voList ?: return@withContext
            val dao = AppDatabase.getInstance().chatSessionLogDao()
            // 服务端是权威：整表替换，本地残留的旧会话（比如升级前的本地 id）一并清掉
            dao.deleteAll()
            if (list.isNotEmpty()) dao.insertOrReplaceAll(list.map { it.toLog() })
            val ids = list.map { it.id }
            if (currentSessionId() !in ids) setCurrentSessionId(ids.firstOrNull() ?: 0L)
        }
    }

    private fun CharacterVo.toOption() = ChatCharacterOption(
        id = (id ?: 0L).toInt(),
        title = title.orEmpty(),
        avatarUrl = url,
        preview = content.orEmpty().take(40),
        mine = isMine,
        content = content.orEmpty(),
    )

    /**
     * [BaseService.postData] 的挂起包装。
     *
     * 失败（网络错误/服务端拒绝，比如编辑了不是自己的角色）统一返回 null，不抛异常——
     * 调用方只关心"成功还是不成功"，错误详情已经在 [CommonResponse.ResponseErrorListener] 里打了日志。
     */
    private suspend fun <T> postForResult(url: String, param: Any, clazz: Class<T>): T? =
        baseService.awaitPost(url, param, clazz, TAG)

    /**
     * 与 [postForResult] 相同，但只关心“是否成功”：服务端接口只返回 errno，没有业务数据时用它。
     */
    private suspend fun postForOk(url: String, param: Any): Boolean =
        baseService.awaitPostOk(url, param, TAG)

    /**
     * 把服务端一页消息写进本地缓存，并返回合并后的消息列表。
     *
     * 服务端历史里只有文字。语音、图片、文件的本机信息（路径、时长、文件名）只在本地记录里，
     * 所以同一个 chatId 已经有本地富内容记录时，用服务端的文字和时间更新它，类型和附件保留。
     * [replaceAll] 为 true 时（拉最新一页）清掉这个会话的纯文字缓存，避免已删除或重新生成的消息残留；
     * 富内容记录和本地临时消息（local- 开头）不清，它们在服务端找不回来。
     */
    private fun cacheRemotePage(sessionId: Long, rows: List<ChatMessageVo>, replaceAll: Boolean): List<ChatMessage> {
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

    private fun ChatSessionVo.toSession() = ChatSession(
        id = id,
        name = name.orEmpty(),
        isDefault = false,
    )

    private fun ChatSessionVo.toLog() = ChatSessionLog().also { log ->
        log.id = id
        log.name = name.orEmpty()
        log.createTime = Date(createdAt)
        log.updatedAt = updatedAt
        log.type = ChatSessionLog.TYPE.CUSTOMER.type
    }

    private fun persist(sessionId: Long, message: ChatMessage) {
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

    /**
     * 复用 [BaseRequest] 构造函数里的 UA 签名/devid/Token 组装逻辑，拿一份跟其它接口
     * 一致的 header——这里只是借它算 header，从不把这个 Request 交给 Volley 执行。
     */
    private fun authHeaders(): okhttp3.Headers {
        val probe = BaseRequest<Any>(context, ApiRequest.Method.POST, Constants.getUrl(Constants.CHAT_STREAM_URL), null, null, null)
        val builder = okhttp3.Headers.Builder()
        probe.headers.forEach { (k, v) -> builder.add(k, v) }
        return builder.build()
    }
}
