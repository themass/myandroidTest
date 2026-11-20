package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 会话列表的增删改切换。从 ChatViewModel 拆出来：状态和刷新通过参数传入，便于用假仓库测试。
 */
internal class SessionActions(
    private val repository: ChatRepository,
    private val state: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    private val refresh: () -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** 切换会话：写入当前会话 id，立刻显示该会话的本地缓存，再向服务端重拉历史。 */
    fun select(sessionId: Long) {
        if (sessionId == state.value.sessionId) {
            state.update { it.copy(sessionsVisible = false) }
            return
        }
        repository.setCurrentSessionId(sessionId)
        val previousSessionId = state.value.sessionId
        state.update { it.copy(sessionsVisible = false) }
        // 先把目标会话的本地缓存顶上方：服务端一页要 0.4~2 秒，期间不读缓存的话，
        // 上一个会话的消息会在界面上多停留几秒——用户读作「切换慢」甚至「串会话」。
        scope.launch {
            val cached = repository.cachedMessages(sessionId)
            // 缓存为空就不顶：留着旧会话的内容也比白屏/闪空强，服务端页到了自然替换。
            if (cached.isEmpty()) return@launch
            // Room 读盘回来时可能已经发生两件事，任何一件都不许再画这份缓存：
            // 1. 用户又切到了别的会话（仓库里的当前会话已变）——迟到的缓存会盖成新界面的内容；
            // 2. 服务端那一页已经到了并画完（refresh 完成时才把 state.sessionId 推进）——
            //    拿更旧、还没有签名 URL 的缓存盖掉新鲜内容，是倒退。
            if (repository.currentSessionId() != sessionId || state.value.sessionId != previousSessionId) return@launch
            state.update { it.copy(messages = cached) }
        }
        refresh()
    }

    /** 新建会话并立即切过去。 */
    fun create(name: String) {
        scope.launch {
            val created = withContext(io) { repository.createSession(name) }
            if (created == null) {
                state.update { it.copy(error = "创建失败：名称不能为空、不能与已有会话重名，或网络异常") }
                return@launch
            }
            repository.setCurrentSessionId(created.id)
            state.update { it.copy(sessionsVisible = false) }
            refresh()
        }
    }

    /** 删除会话（允许删空，服务端会在下次拉列表时自动新建一个）。删的是当前会话时，refresh 会切换。 */
    fun delete(sessionId: Long) {
        scope.launch {
            val ok = withContext(io) { repository.deleteSession(sessionId) }
            if (!ok) {
                state.update { it.copy(error = "删除失败，请稍后重试") }
                return@launch
            }
            refresh()
        }
    }

    /** 重命名会话（服务端）；成功后刷新会话列表。 */
    fun rename(sessionId: Long, name: String) {
        scope.launch {
            val renamed = repository.renameSession(sessionId, name)
            if (renamed == null) {
                state.update { it.copy(error = "重命名失败：名称不能为空，或网络异常") }
                return@launch
            }
            reloadSessions()
        }
    }

    /** 只刷新会话列表，不动消息。 */
    suspend fun reloadSessions() {
        val sessions = withContext(io) { repository.sessions() }
        state.update { it.copy(sessions = sessions) }
    }
}
