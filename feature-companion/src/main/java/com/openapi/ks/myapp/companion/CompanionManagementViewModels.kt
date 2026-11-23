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
    /** 她对你的了解（只含当前有效的）。 */
    val profile: List<ProfileEntry> = emptyList(),
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
                // 画像读不到（服务端还没上这一块、网络抖动）不该让整个记忆页失败：这一块空着就好
                val profile = runCatching { api.profile(roleId) }.getOrDefault(emptyList())
                    .filter { it.current }
                _state.update {
                    it.copy(
                        facts = facts.items,
                        factsHasMore = facts.hasMore,
                        events = events,
                        profile = profile,
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

    /** 「别再提」：先从界面拿掉，服务端失败会整体刷新回来。 */
    fun suppressProfile(id: String) {
        _state.update { st -> st.copy(profile = st.profile.filterNot { it.id == id }) }
        mutate { api.suppressProfileEntry(roleId, id) }
    }

    fun deleteProfile(id: String) {
        _state.update { st -> st.copy(profile = st.profile.filterNot { it.id == id }) }
        mutate { api.deleteProfileEntry(roleId, id) }
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

// ---- 用户档案（Markdown 长文）----

data class ProfileDocState(
    /** 可查档案的角色列表（档案按角色隔离，先选角色再看档案）。 */
    val characters: List<CharacterSummary> = emptyList(),
    val charactersLoading: Boolean = false,
    /** 当前选中的角色 id；null 表示还没选过（默认选中列表第一个 / 会话角色）。 */
    val roleId: String? = null,
    val doc: ProfileDoc? = null,
    val docLoading: Boolean = false,
    /** 编辑框里的当前文本；null 表示尚未载入（界面显示加载中，不显示空编辑器）。 */
    val draft: String? = null,
    val saving: Boolean = false,
    /** 刚保存成功的提示（短暂显示后由界面清掉）。 */
    val savedHint: Boolean = false,
    val error: String? = null,
)

/**
 * 档案页：每 (用户, 角色) 一份 Markdown。角色切换时换 [roleId] 重新拉取；
 * 编辑在 [draft] 上进行，「保存」整篇覆盖，失败回滚到服务端版本。
 */
class ProfileDocViewModel(
    private val api: CompanionApi,
    /** 会话里正在聊的角色：优先选中它（档案入口就在它的聊天页菜单里）。 */
    private val currentCharacterId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileDocState())
    val state: StateFlow<ProfileDocState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(charactersLoading = true) }
        viewModelScope.launch {
            try {
                val page = api.characters(limit = PAGE_SIZE, offset = 0)
                // 优先选中会话角色；列表里没有（老服务端/被删）就退回列表第一个
                val roleId = page.items.firstOrNull { it.id == currentCharacterId }?.id
                    ?: page.items.firstOrNull()?.id
                _state.update {
                    it.copy(
                        characters = page.items,
                        charactersLoading = false,
                        roleId = roleId,
                    )
                }
                if (roleId != null) loadDoc(roleId) else _state.update { it.copy(docLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(charactersLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun selectRole(roleId: String) {
        if (roleId == _state.value.roleId) return
        _state.update { it.copy(roleId = roleId, doc = null, draft = null, error = null) }
        loadDoc(roleId)
    }

    private fun loadDoc(roleId: String) {
        _state.update { it.copy(docLoading = true, error = null) }
        viewModelScope.launch {
            try {
                // 拉文档同时把草稿初始化为服务端内容：编辑器一载入就有可编辑的文本
                val doc = api.profileDoc(roleId)
                _state.update { it.copy(doc = doc, draft = doc.markdown, docLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(docLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun onDraftChange(text: String) {
        _state.update { it.copy(draft = text, savedHint = false) }
    }

    /** 保存：整篇覆盖。成功后记下提示；失败回滚草稿到服务端版本并给错误。 */
    fun save() {
        val st = _state.value
        val roleId = st.roleId ?: return
        val draft = st.draft ?: return
        if (st.saving) return
        _state.update { it.copy(saving = true, error = null, savedHint = false) }
        viewModelScope.launch {
            try {
                api.updateProfileDoc(roleId, draft)
                _state.update {
                    it.copy(
                        saving = false,
                        savedHint = true,
                        // 版本与来源以服务端为准：重新拉一次，拿到新的 version/source
                        doc = runCatching { api.profileDoc(roleId) }.getOrNull(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 回滚草稿到服务端版本：别让用户以为改成功了
                _state.update {
                    it.copy(saving = false, error = e.message ?: "保存失败", draft = st.doc?.markdown)
                }
            }
        }
    }

    fun dismissSavedHint() {
        _state.update { it.copy(savedHint = false) }
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
