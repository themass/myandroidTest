package com.openapi.ks.myapp.data.chat

import com.openapi.core.net.NetRequest
import com.openapi.chat.data.mapper.ChatLogMapper
import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatReply
import com.openapi.chat.domain.model.MessageKind
import com.openapi.ks.myapp.bean.vo.Choice
import com.openapi.chat.domain.model.ChatRole
import com.openapi.chat.domain.model.ChatSession
import com.openapi.chat.domain.model.ChatStreamEvent
import com.openapi.chat.domain.repository.ChatRepository
import com.openapi.core.util.GsonUtils
import com.openapi.core.net.request.CommonResponse
import com.openapi.core.util.LogUtil
import com.openapi.core.util.PreferenceUtils
import com.openapi.core.net.request.BaseRequest
import com.openapi.ks.myapp.base.MyApplication
import com.openapi.ks.myapp.bean.form.ChatLog
import com.openapi.ks.myapp.bean.form.ChatSessionLog
import com.openapi.ks.myapp.bean.vo.CharacterVo
import com.openapi.ks.myapp.bean.vo.TtsResponseVo
import com.openapi.ks.myapp.constant.Constants
import com.openapi.core.net.BaseService
import com.openapi.ks.myapp.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
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
import kotlin.coroutines.resume

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

class ChatRepositoryImpl(
    private val context: MyApplication,
) : ChatRepository {

    private val baseService = BaseService().apply { setup(context) }

    /** SSE 连接必须不设读超时——回复多久吐完完全看大模型，不是一个固定时间能框住的。 */
    private val streamHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private companion object {
        const val TAG = "chat"
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

    override suspend fun loadHistory(sessionId: Long): List<ChatMessage> {
        val logs = AppDatabase.getInstance().chatLogDao().bySession(sessionId)
        return logs.mapNotNull { log ->
            ChatLogMapper.toDomain(
                chatId = log.chatId,
                who = log.who,
                msgType = log.msgType,
                rawContent = log.content,
                createdAtMillis = log.createTime?.time ?: 0L,
            )
        }
    }

    override fun sendMessageStream(
        sessionId: Long,
        userText: String,
        characterId: Int?,
        roleId: String?,
    ): Flow<ChatStreamEvent> = callbackFlow {
        val userMessage = ChatMessage(
            id = "local-${System.currentTimeMillis()}",
            role = ChatRole.USER,
            text = userText,
            createdAtMillis = System.currentTimeMillis(),
        )
        persist(sessionId, userMessage)

        // 历史以 {role, content} 数组提交，字段名与后端约定一致（role: user/assistant）
        val history = loadHistory(sessionId)
            .filterNot { it.pending }
            .map { mapOf("role" to (if (it.role == ChatRole.USER) "user" else "assistant"), "content" to it.text) }
            .plus(mapOf("role" to "user", "content" to userText))

        val msgId = userMessage.id
        val bodyBuilder = FormBody.Builder()
            .add("id", msgId)
            .add("content", GsonUtils.getInstance().toJson(history))
            .add("sessionId", sessionId.toString())
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
                            if (!line.startsWith("data:")) continue
                            val payload = line.removePrefix("data:").trim()
                            if (payload.isEmpty()) continue
                            val chunk = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                            val delta = chunk.optString("delta", "")
                            val done = chunk.optBoolean("done", false)
                            val error = chunk.optString("error", null)
                            if (error != null) {
                                trySend(ChatStreamEvent.Error(error))
                                close()
                                return
                            }
                            if (delta.isNotEmpty()) {
                                fullText.append(delta)
                                trySend(ChatStreamEvent.Delta(msgId, delta))
                            }
                            if (done) {
                                val reply = ChatMessage(
                                    id = msgId,
                                    role = ChatRole.ASSISTANT,
                                    text = fullText.toString(),
                                    createdAtMillis = System.currentTimeMillis(),
                                )
                                persist(sessionId, reply)
                                trySend(ChatStreamEvent.Done(msgId, fullText.toString()))
                                close()
                                return
                            }
                        }
                        // 流结束但没收到 done=true 帧（比如连接被提前挂断），按已收到的内容收尾，
                        // 而不是整段丢弃——用户至少能看到已经生成的部分。
                        if (!call.isCanceled()) {
                            val reply = ChatMessage(
                                id = msgId,
                                role = ChatRole.ASSISTANT,
                                text = fullText.toString(),
                                createdAtMillis = System.currentTimeMillis(),
                            )
                            if (fullText.isNotEmpty()) persist(sessionId, reply)
                            trySend(ChatStreamEvent.Done(msgId, fullText.toString()))
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

    override suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        AppDatabase.getInstance().chatLogDao().deleteByChatId(messageId)
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
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                baseService.postData(
                    url,
                    param,
                    listOf(localFile),
                    object : CommonResponse.ResponseOkListener<Choice>() {
                        override fun onResponse(response: Choice) {
                            val reply = ChatReply(
                                id = response.id ?: placeholderId,
                                text = response.message?.content.orEmpty(),
                            )
                            if (cont.isActive) cont.resume(reply)
                        }
                    },
                    object : CommonResponse.ResponseErrorListener() {
                        override fun onError() {
                            if (cont.isActive) cont.resume(null)
                        }
                    },
                    partName,
                    TAG,
                    Choice::class.java,
                )
            }
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
        return AppDatabase.getInstance().chatSessionLogDao().loadAll()
            .sortedWith(
                compareBy<ChatSessionLog>(
                    { it.type != ChatSessionLog.TYPE.DEF.type },
                    { it.id ?: 0L },
                ),
            )
            .map { log ->
                ChatSession(
                    id = log.id ?: 0L,
                    name = log.name.orEmpty(),
                    isDefault = log.type == ChatSessionLog.TYPE.DEF.type,
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
        val log = ChatSessionLog()
        log.name = trimmed
        log.createTime = Date()
        log.setting = ""
        log.type = ChatSessionLog.TYPE.CUSTOMER.type
        // 插入会返回新行 id（Room 不回填实体）
        val id = dao.insertOrReplace(log)
        ChatSession(id = id, name = trimmed, isDefault = false)
    }

    override suspend fun deleteSession(sessionId: Long): Boolean = withContext(Dispatchers.IO) {
        if (sessions().size < 2) return@withContext false
        AppDatabase.getInstance().chatSessionLogDao().deleteById(sessionId)
        if (currentSessionId() == sessionId) {
            sessions().firstOrNull()?.let { setCurrentSessionId(it.id) }
        }
        true
    }

    override suspend fun syncSessionsFromServer() {
        withContext(Dispatchers.IO) {
            val list = runCatching {
                baseService.getInfoListData(
                    Constants.getUrl(Constants.CHAT_SESSION_URL),
                    ChatSessionLog::class.java,
                    TAG,
                )
            }.getOrNull()?.voList
            if (!list.isNullOrEmpty()) {
                AppDatabase.getInstance().chatSessionLogDao().insertOrReplaceAll(list)
            }
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
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                baseService.postData(
                    url,
                    param,
                    object : CommonResponse.ResponseOkListener<T>() {
                        override fun onResponse(response: T) {
                            if (cont.isActive) cont.resume(response)
                        }
                    },
                    object : CommonResponse.ResponseErrorListener() {
                        override fun onError() {
                            if (cont.isActive) cont.resume(null)
                        }
                    },
                    TAG,
                    clazz,
                )
            }
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
        val probe = BaseRequest<Any>(context, NetRequest.Method.POST, Constants.getUrl(Constants.CHAT_STREAM_URL), null, null, null)
        val builder = okhttp3.Headers.Builder()
        probe.headers.forEach { (k, v) -> builder.add(k, v) }
        return builder.build()
    }
}
