package com.openapi.ks.myapp.agent.cache

import android.content.Context
import com.openapi.ks.myapp.agent.AgentBubble
import com.openapi.ks.myapp.agent.BubbleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 每个会话最近 [WINDOW] 条已同步消息的本地窗口（客户端设计 9）。
 *
 * 只保存服务端已确认的消息（有 message_id）；发送中、排队中、失败的状态是短期的，不入库。
 * 保存是整窗替换，所以读出来的顺序就是保存时的顺序。
 */
class AgentMessageCache(context: Context) {

    private val dao = AgentCacheDatabase.get(context).messageDao()

    suspend fun load(sessionId: String): List<AgentBubble> = withContext(Dispatchers.IO) {
        dao.latest(sessionId, WINDOW).asReversed().map { row ->
            AgentBubble(
                key = row.id,
                messageId = row.id,
                role = row.role,
                content = row.content,
                state = BubbleState.SYNCED,
            )
        }
    }

    suspend fun save(sessionId: String, bubbles: List<AgentBubble>) = withContext(Dispatchers.IO) {
        val synced = bubbles
            .filter { it.messageId != null && it.state == BubbleState.SYNCED }
            .takeLast(WINDOW)
        val rows = synced.mapIndexed { index, bubble ->
            AgentMessageEntity().apply {
                id = bubble.messageId ?: ""
                this.sessionId = sessionId
                role = bubble.role
                content = bubble.content
                createdSeq = index.toLong()
            }
        }
        dao.deleteSession(sessionId)
        if (rows.isNotEmpty()) dao.upsert(rows)
    }

    companion object {
        const val WINDOW = 100
    }
}
