package com.openapi.ks.myapp.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---- 角色列表 ----

data class CharacterListState(
    val items: List<CharacterSummary> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
)

class CharacterListViewModel(private val api: CompanionApi) : ViewModel() {

    private val _state = MutableStateFlow(CharacterListState())
    val state: StateFlow<CharacterListState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val page = api.characters(limit = PAGE_SIZE, offset = 0)
                _state.update { CharacterListState(items = page.items, total = page.total) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}

// ---- 记忆与事件 ----

data class MemoryState(
    val facts: List<MemoryFact> = emptyList(),
    val factsHasMore: Boolean = false,
    val events: List<TimelineEventItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * ``roleId``：记忆与事件都按角色隔离，不带角色读到的是一个空空间
 * （服务端把「不属于任何角色」的记忆放在用户级主体下，会话里写的都在角色主体下）。
 */
class MemoryViewModel(private val api: CompanionApi, private val roleId: String) : ViewModel() {

    private val _state = MutableStateFlow(MemoryState())
    val state: StateFlow<MemoryState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val facts = api.memory(roleId, limit = PAGE_SIZE, offset = 0)
                val events = api.events(roleId, includePast = true)
                _state.update {
                    it.copy(
                        facts = facts.items,
                        factsHasMore = facts.hasMore,
                        events = events,
                        loading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    /** 用户删掉她记住的一条：先从界面拿掉，服务端失败再整体刷新回来。 */
    fun deleteFact(id: String) {
        _state.update { st -> st.copy(facts = st.facts.filterNot { it.id == id }) }
        mutate { api.deleteMemory(roleId, id) }
    }

    fun deleteEvent(id: String) {
        _state.update { st -> st.copy(events = st.events.filterNot { it.id == id }) }
        mutate { api.deleteEvent(id) }
    }

    fun markEventDone(id: String) {
        mutate { api.setEventStatus(id, "done") }
    }

    fun addEvent(title: String, atIso: String, kind: String) {
        if (title.isBlank()) return
        mutate { api.addEvent(roleId, title.trim(), atIso, kind) }
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "操作失败") }
                reload()
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}

// ---- 偏好 ----

data class SettingsState(
    val prefs: CompanionPreferences? = null,
    val saving: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

class CompanionSettingsViewModel(private val api: CompanionApi) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val prefs = api.preferences()
                _state.update { it.copy(prefs = prefs, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    /** 每个开关改完立即保存：偏好是安全开关，不该让用户再找一个「保存」按钮。 */
    fun update(patch: Map<String, Any?>) {
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val saved = api.updatePreferences(patch)
                _state.update { it.copy(prefs = saved, saving = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message ?: "保存失败") }
                load()
            }
        }
    }

    /**
     * 暂停主动到某个时刻为止；传空串表示恢复。服务端把 null 视为「不修改」，
     * 所以恢复必须用显式的空串，不能用 null。
     */
    fun pauseUntil(iso: String) = update(mapOf("paused_until" to iso))
}
