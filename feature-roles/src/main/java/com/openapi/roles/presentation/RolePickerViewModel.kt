package com.openapi.roles.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openapi.roles.data.RolesDependencies
import com.openapi.roles.domain.RoleDivision
import com.openapi.roles.domain.RoleSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 列表筛选：精选、全部、收藏、最近，或某个分类。收藏和最近只读本地，不走网络。 */
sealed interface RoleFilter {
    data object Featured : RoleFilter
    data object All : RoleFilter
    data object Favorites : RoleFilter
    data object Recent : RoleFilter
    data class Division(val id: String) : RoleFilter
}

data class RolePickerState(
    val divisions: List<RoleDivision> = emptyList(),
    val filter: RoleFilter = RoleFilter.Featured,
    val query: String = "",
    val items: List<RoleSummary> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    /** 最近一次请求失败（网络或服务端），界面据此显示重试，而不是显示"没有结果"。 */
    val failed: Boolean = false,
    /** 已收藏的角色 id，列表里用来画星标。 */
    val favoriteIds: Set<String> = emptySet(),
) {
    val hasMore: Boolean get() = items.size < total
    val isLocalFilter: Boolean get() = filter == RoleFilter.Favorites || filter == RoleFilter.Recent
}

class RolePickerViewModel : ViewModel() {

    private val repository = RolesDependencies.require()

    private val _state = MutableStateFlow(
        RolePickerState(favoriteIds = repository.favorites().mapTo(HashSet()) { it.id }),
    )
    val state: StateFlow<RolePickerState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        reload()
        viewModelScope.launch {
            val divisions = repository.divisions()
            if (divisions != null) _state.update { it.copy(divisions = divisions) }
        }
    }

    fun onFilterChange(filter: RoleFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter) }
        reload()
    }

    /** 输入停顿 300ms 后再请求，避免每敲一个字都打一次服务端。 */
    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        reload(debounceMs = 300)
    }

    /** 用户选中了某个角色（即将开始对话），记入最近使用。 */
    fun onRolePicked(role: RoleSummary) {
        repository.recordUse(role)
    }

    fun retry() = reload()

    /** 切换收藏。在"收藏"列表里取消收藏会直接让该行消失。 */
    fun onToggleFavorite(role: RoleSummary) {
        repository.toggleFavorite(role)
        val ids = repository.favorites().mapTo(HashSet()) { it.id }
        _state.update { it.copy(favoriteIds = ids) }
        if (_state.value.filter == RoleFilter.Favorites) reload()
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLocalFilter || s.loading || s.loadingMore || !s.hasMore || s.failed) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            val page = fetch(s, offset = s.items.size)
            _state.update {
                if (page == null) {
                    it.copy(loadingMore = false, failed = true)
                } else {
                    // 以 id 去重：翻页期间服务端内容变动时，不让同一个角色出现两次
                    val known = it.items.mapTo(HashSet()) { r -> r.id }
                    it.copy(
                        items = it.items + page.items.filter { r -> r.id !in known },
                        total = page.total,
                        loadingMore = false,
                    )
                }
            }
        }
    }

    private fun reload(debounceMs: Long = 0) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val s = _state.value
            if (s.isLocalFilter) {
                val source = if (s.filter == RoleFilter.Favorites) repository.favorites() else repository.recents()
                val matched = source.filter { matches(it, s.query) }
                _state.update {
                    it.copy(loading = false, loadingMore = false, failed = false, items = matched, total = matched.size)
                }
                return@launch
            }
            _state.update { it.copy(loading = true, loadingMore = false, failed = false) }
            val page = fetch(_state.value, offset = 0)
            _state.update {
                if (page == null) {
                    it.copy(loading = false, failed = true, items = emptyList(), total = 0)
                } else {
                    it.copy(loading = false, items = page.items, total = page.total)
                }
            }
        }
    }

    private fun matches(role: RoleSummary, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        return role.name.contains(q, ignoreCase = true) || role.description.contains(q, ignoreCase = true)
    }

    private suspend fun fetch(s: RolePickerState, offset: Int) = repository.roles(
        division = (s.filter as? RoleFilter.Division)?.id,
        query = s.query,
        featuredOnly = s.filter == RoleFilter.Featured && s.query.isBlank(),
        limit = PAGE_SIZE,
        offset = offset,
    )

    private companion object {
        const val PAGE_SIZE = 30
    }
}
