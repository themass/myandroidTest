package com.openapi.feature.chat.data.remote

import android.content.Context
import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.domain.model.ChatSession
import com.openapi.feature.chat.domain.model.MessageKind

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

    /**
     * 本地行还引用着的附件路径，**跨会话**、只算没到 SENT 的那些（接口注释见
     * [com.openapi.feature.chat.domain.repository.ChatRepository.localAttachmentPathsInUse]）。
     *
     * 跨会话是有意的：暂存目录不按会话分（`filesDir/chat_attachments` 一个目录装所有会话的字节），
     * 而「哪个字节还有本地行引用」只有查库知道。已到 SENT 的行不进这里：服务端历史里一定有它，
     * 图没了也能走签名 URL，那些字节是真的孤儿。
     *
     * 投递状态存在 content 的 JSON 里（不是列），SQL 判不了，所以按 `local-` 前缀取行、
     * 映射完再筛。**判据与 [localPending] 不完全相同**（复审 nit 3，这里写实话）：
     * 那条还要 `role == USER`，这里不筛 role —— `local-assistant-*` / `local-pending-*` /
     * `local-voice-*` 也在捞出来的批里。为什么不补谓词：助手占位行没有附件，voice 行的字节在
     * `cacheDir/voice_messages`（见 `speech` 的 VoiceRecorder.kt:65）而不是暂存目录，
     * 筛了结果不变；最坏后果是**过留**（多活一轮 GC），不会误删。
     * 逐行汇 paths 的循环与 [MessageCache] 共用 [keptAttachmentPaths]（两份会漂）。
     */
    fun localAttachmentPathsInUse(): Set<String> =
        keptAttachmentPaths(runCatching { db().chatLogDao().localRows() }.getOrDefault(emptyList()))

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
            // 视频与图片同一套规矩：文件还在就播本地文件（首帧缩略图也只有本地行有），
            // 文件被系统清了才退回服务端对象（播放 URL 点开时现取）
            val keepLocalVideos = mine.videos.isNotEmpty() && mine.videos.size == message.videos.size &&
                mine.videos.all { java.io.File(it.path).exists() }
            val videos = if (keepLocalVideos) {
                mine.videos.mapIndexed { i, v ->
                    v.copy(uploadId = message.videos[i].uploadId ?: v.uploadId)
                }
            } else {
                message.videos
            }
            // 位置：服务端历史带回的就是权威坐标，但本地行才有「这条到底带没带精度」的信息；
            // 两边都有时以服务端为准，服务端没带回（旧服务端）就留着本行的
            val location = message.location ?: mine.location
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
                            videos.isNotEmpty() -> MessageKind.VIDEO
                            location != null -> MessageKind.LOCATION
                            else -> MessageKind.TEXT
                        },
                        images = images,
                        videos = videos,
                        location = location,
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
