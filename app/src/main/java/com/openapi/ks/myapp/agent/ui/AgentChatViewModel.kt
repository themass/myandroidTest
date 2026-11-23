package com.openapi.ks.myapp.agent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openapi.ks.myapp.agent.AgentApiException
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
import com.openapi.ks.myapp.agent.AgentReminder
import com.openapi.ks.myapp.agent.withEditedMessage
import com.openapi.ks.myapp.agent.withSelectedAlternative
import com.openapi.ks.myapp.agent.withoutUsageReminder
import com.openapi.ks.myapp.agent.withError
import com.openapi.ks.myapp.agent.withoutMessage
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

    /** 待发送的定时提醒（角色到点会主动联系用户）。由对话里的工具 + 确认创建，这里只查看与取消。 */
    private val _reminders = MutableStateFlow<List<AgentReminder>>(emptyList())
    val reminders: StateFlow<List<AgentReminder>> = _reminders.asStateFlow()

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
        viewModelScope.launch {
            runCatching { repository.regenerate(sessionId, messageId) }.onFailure { reportFailure(it) }
        }
    }

    /**
     * 回应待确认动作。结果以服务端为准：成功后 action.done 事件会把它从列表里移除；
     * 这里先本地移除避免连点，失败（含 404：已被处理或已过期）时也移除并提示。
     */
    fun resolveAction(confirmationId: String, accepted: Boolean) {
        _state.update { s -> s.copy(pendingConfirmations = s.pendingConfirmations.filterNot { it.confirmationId == confirmationId }) }
        viewModelScope.launch {
            runCatching { repository.confirmAction(confirmationId, accepted) }.onFailure { reportFailure(it) }
        }
    }

    /** 关掉连续使用提醒。 */
    fun dismissUsageReminder() {
        _state.update { it.withoutUsageReminder() }
    }

    /** 合规同意状态（训练用途默认关、主动联系总开关）。 */
    private val _consent = MutableStateFlow<com.openapi.ks.myapp.agent.AgentConsent?>(null)
    val consent: StateFlow<com.openapi.ks.myapp.agent.AgentConsent?> = _consent.asStateFlow()

    fun loadConsent() {
        viewModelScope.launch {
            runCatching { repository.consent() }.onSuccess { _consent.value = it }
        }
    }

    /** 改一项同意。失败了重新读一次，界面不会显示一个没保存成功的开关。 */
    fun setConsent(trainingConsent: Boolean? = null, proactiveEnabled: Boolean? = null) {
        viewModelScope.launch {
            runCatching { repository.updateConsent(trainingConsent, proactiveEnabled) }
                .onSuccess { _consent.value = it }
                .onFailure { reportFailure(it); loadConsent() }
        }
    }

    /** 导出自己的数据：回调给出 JSON 原文，由界面写到文件并交给系统分享。 */
    fun exportData(onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.exportData() }
                .onSuccess(onReady)
                .onFailure { reportFailure(it) }
        }
    }

    /** 恢复进入页面前就已经在等待的待确认动作。 */
    fun loadPendingActions() {
        viewModelScope.launch {
            runCatching { repository.pendingActions() }.onSuccess { pending ->
                _state.update { s ->
                    val known = s.pendingConfirmations.map { it.confirmationId }.toSet()
                    s.copy(pendingConfirmations = s.pendingConfirmations + pending.filter { it.confirmationId !in known })
                }
            }
        }
    }

    /** 左右滑动切换到另一条候选回复。回合进行中、或不是最新一轮时服务端返回 409，列表不变。 */
    fun selectAlternative(messageId: String) {
        viewModelScope.launch {
            runCatching { repository.selectAlternative(messageId) }
                .onSuccess { selected -> _state.update { it.withSelectedAlternative(selected) } }
                .onFailure { reportFailure(it) }
        }
    }

    /** 删除一条消息。回合进行中服务端返回 409：提示稍后再试，列表不变。 */
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteMessage(messageId) }
                .onSuccess { _state.update { it.withoutMessage(messageId) } }
                .onFailure { reportFailure(it) }
        }
    }

    /** 编辑自己的一条消息。 */
    fun editMessage(messageId: String, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.editMessage(messageId, content.trim()) }
                .onSuccess { updated -> _state.update { it.withEditedMessage(updated) } }
                .onFailure { reportFailure(it) }
        }
    }

    fun loadReminders() {
        viewModelScope.launch {
            runCatching { repository.reminders() }
                .onSuccess { _reminders.value = it.filter { r -> r.sessionId == sessionId } }
        }
    }

    fun cancelReminder(reminderId: String) {
        viewModelScope.launch {
            runCatching { repository.cancelReminder(reminderId) }
                .onSuccess { _reminders.update { list -> list.filterNot { it.id == reminderId } } }
                .onFailure { reportFailure(it) }
        }
    }

    fun dismissError() {
        _state.update { it.withError(null) }
    }

    private fun reportFailure(error: Throwable) {
        val text = when ((error as? AgentApiException)?.statusCode) {
            409 -> "正在回复中，请稍后再试"
            404 -> "这条内容已经不存在了"
            else -> "操作失败，请重试"
        }
        _state.update { it.withError(text) }
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
