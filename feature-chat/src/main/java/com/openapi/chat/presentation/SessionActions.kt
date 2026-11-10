package com.openapi.chat.presentation

import com.openapi.chat.domain.repository.ChatRepository
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

    /** 切换会话：写入当前会话 id，然后重新加载这个会话的历史。 */
    fun select(sessionId: Long) {
        if (sessionId == state.value.sessionId) {
            state.update { it.copy(sessionsVisible = false) }
            return
        }
        repository.setCurrentSessionId(sessionId)
        state.update { it.copy(sessionsVisible = false) }
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
