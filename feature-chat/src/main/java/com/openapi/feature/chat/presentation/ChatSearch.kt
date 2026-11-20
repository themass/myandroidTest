package com.openapi.feature.chat.presentation

import com.openapi.core.db.AppDatabase
import com.openapi.core.model.ChatMessageHit
import com.openapi.feature.chat.domain.model.ChatSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 整改清单 P2-15：会话与消息的本地搜索。
 * 服务端没有搜索接口，本地缓存就是唯一可搜的数据源。
 */

/**
 * 会话按名称过滤。会话列表整表已在内存里（ChatUiState.sessions），不再查库；
 * 大小写不敏感的子串匹配（SQLite LIKE 对 ASCII 的行为，与消息搜索一致）。
 */
fun filterSessions(sessions: List<ChatSession>, query: String): List<ChatSession> {
    val needle = query.trim()
    if (needle.isEmpty()) return sessions
    return sessions.filter { it.name.contains(needle, ignoreCase = true) }
}

/** 一次搜索最多返回这么多条命中，够一屏翻页，也避免全表匹配把列表撑爆。 */
const val MESSAGE_SEARCH_LIMIT = 50

/** 跨会话搜消息正文，阻塞 DAO 调用切 IO；空白词不查。 */
suspend fun searchMessages(query: String, limit: Int = MESSAGE_SEARCH_LIMIT): List<ChatMessageHit> {
    if (query.isBlank()) return emptyList()
    return withContext(Dispatchers.IO) {
        AppDatabase.getInstance().chatLogDao().searchContent(query, limit)
    }
}
