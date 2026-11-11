package com.openapi.chat.data.remote

import android.content.Context
import com.openapi.chat.domain.model.ChatSession
import com.openapi.network.http.BaseService
import com.openapi.network.awaitInfoList
import com.openapi.common.util.android.PreferenceUtils
import com.openapi.core.config.Constants
import com.openapi.core.db.AppDatabase
import com.openapi.core.model.ChatSessionLog
import com.openapi.chat.data.remote.ChatSessionVo
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 会话：当前会话 id、会话列表（本地缓存 + 服务端同步）、新建、重命名、删除。 */
internal class SessionStore(
    private val context: Context,
    private val remote: ChatRemote,
    private val baseService: BaseService,
) {

    fun currentSessionId(): Long = PreferenceUtils.getPrefLong(context, Constants.CHAT_SESSION, 0)

    fun setCurrentSessionId(sessionId: Long) {
        PreferenceUtils.setSettingLong(context, Constants.CHAT_SESSION, sessionId)
    }

    fun sessions(): List<ChatSession> {
        return AppDatabase.getInstance().chatSessionLogDao().loadAll().map { log ->
            ChatSession(
                id = log.id ?: 0L,
                name = log.name.orEmpty(),
                // 服务端没有“默认会话”的概念，列表里每个会话都可以删除
                isDefault = false,
            )
        }
    }

    suspend fun createSession(name: String): ChatSession? = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext null
        val dao = AppDatabase.getInstance().chatSessionLogDao()
        if (dao.loadAll().any { it.name == trimmed }) return@withContext null
        val vo = remote.postForResult(
            Constants.getUrl(Constants.CHAT_SESSION_CREATE_URL),
            SessionCreateParam(name = trimmed),
            ChatSessionVo::class.java,
        ) ?: return@withContext null
        dao.insertOrReplace(vo.toLog())
        vo.toSession()
    }

    suspend fun renameSession(sessionId: Long, name: String): ChatSession? = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || sessionId <= 0) return@withContext null
        val vo = remote.postForResult(
            Constants.getUrl(Constants.CHAT_SESSION_RENAME_URL),
            SessionRenameParam(id = sessionId, name = trimmed),
            ChatSessionVo::class.java,
        ) ?: return@withContext null
        AppDatabase.getInstance().chatSessionLogDao().insertOrReplace(vo.toLog())
        vo.toSession()
    }

    suspend fun deleteSession(sessionId: Long): Boolean = withContext(Dispatchers.IO) {
        val ok = remote.postForOk(
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

    suspend fun syncSessionsFromServer() {
        withContext(Dispatchers.IO) {
            val list = runCatching {
                baseService.awaitInfoList(
                    Constants.getUrl(Constants.CHAT_SESSION_URL),
                    ChatSessionVo::class.java,
                    CHAT_TAG,
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
}
