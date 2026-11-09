package com.openapi.ks.myapp.agent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openapi.ks.myapp.agent.AgentChatRepository
import com.openapi.ks.myapp.agent.AgentChatState
import com.openapi.ks.myapp.agent.cache.AgentMessageCache
import com.openapi.ks.myapp.agent.reduce
import com.openapi.ks.myapp.agent.withHistory
import com.openapi.ks.myapp.agent.withLocalSending
import com.openapi.ks.myapp.agent.withReceipt
import com.openapi.ks.myapp.agent.withRetrying
import com.openapi.ks.myapp.agent.withSendFailed
import com.openapi.ks.myapp.agent.withoutQueued
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 新对话页的 ViewModel（客户端设计 2.3）。
 *
 * 两条独立的协程：事件流（进入即开，离开即取消）与每次发送（互不阻塞）。
 * 发送永远可用，只受排队上限约束；「正在生成」不再禁止发送。
 */
class AgentChatViewModel(
    private val repository: AgentChatRepository,
    private val sessionId: String,
    private val cache: AgentMessageCache? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(AgentChatState())
    val state: StateFlow<AgentChatState> = _state.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private var eventsJob: Job? = null

    init {
        // 冷启动：先显示上次的窗口（离线可看），再用服务端历史覆盖。
        viewModelScope.launch {
            cache?.load(sessionId)?.let { cached ->
                _state.update { if (it.bubbles.isEmpty()) it.copy(bubbles = cached) else it }
            }
        }
        // 已同步的消息变化时写入窗口；debounce 避免流式输出时每个 token 都落盘。
        cache?.let { store ->
            viewModelScope.launch {
                _state.map { it.bubbles }.distinctUntilChanged().debounce(SAVE_DEBOUNCE_MS).collect {
                    store.save(sessionId, it)
                }
            }
        }
        loadHistory()
        startEvents()
    }

    fun onDraftChange(text: String) {
        _draft.value = text
    }

    fun send() {
        val text = _draft.value.trim()
        if (text.isEmpty() || _state.value.queued.size >= QUEUE_MAX) return
        _draft.value = ""
        val key = "local-" + UUID.randomUUID()
        _state.update { it.withLocalSending(key, text) }
        submit(key, text)
    }

    /** 重试失败的发送：同一个 client_message_id，服务端会去重。 */
    fun retry(clientMessageId: String) {
        val text = _state.value.bubbles.firstOrNull { it.key == clientMessageId }?.content ?: return
        _state.update { it.withRetrying(clientMessageId) }
        submit(clientMessageId, text)
    }

    fun cancel() {
        viewModelScope.launch { runCatching { repository.cancel(sessionId) } }
    }

    fun withdraw(messageId: String) {
        viewModelScope.launch {
            runCatching { repository.withdraw(sessionId, messageId) }
                .onSuccess { _state.update { it.withoutQueued(messageId) } }
        }
    }

    fun clearQueue() {
        viewModelScope.launch {
            runCatching { repository.clearQueue(sessionId) }
                .onSuccess { _state.update { it.copy(queued = emptyList()) } }
        }
    }

    fun retryFailedQueue() {
        viewModelScope.launch { runCatching { repository.retryFailedQueue(sessionId) } }
    }

    fun continueReply(messageId: String) {
        viewModelScope.launch { runCatching { repository.continueReply(sessionId, messageId) } }
    }

    fun regenerate(messageId: String) {
        viewModelScope.launch { runCatching { repository.regenerate(sessionId, messageId) } }
    }

    private fun submit(key: String, text: String) {
        viewModelScope.launch {
            runCatching { repository.submit(sessionId, text, key) }
                .onSuccess { receipt -> _state.update { it.withReceipt(key, receipt) } }
                .onFailure { _state.update { it.withSendFailed(key) } }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            runCatching { repository.history(sessionId, before = null) }
                .onSuccess { history -> _state.update { it.withHistory(history) } }
        }
    }

    private fun startEvents() {
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            repository.events(sessionId, _state.value.lastAppliedEventId).collect { update ->
                _state.update { reduce(it, update) }
                // 收到 gone：续传窗口已过期，用服务端历史整体覆盖。
                if (_state.value.needsHistoryReload) loadHistory()
            }
        }
    }

    override fun onCleared() {
        eventsJob?.cancel()
        super.onCleared()
    }

    companion object {
        /** 与服务端的排队上限一致（D §6.4）。超过时发送不可用，输入不清空。 */
        const val QUEUE_MAX = 10
        private const val SAVE_DEBOUNCE_MS = 800L
    }
}
