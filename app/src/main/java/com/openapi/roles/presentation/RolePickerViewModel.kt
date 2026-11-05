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

/** 列表筛选：精选、全部，或某个分类。 */
sealed interface RoleFilter {
    data object Featured : RoleFilter
    data object All : RoleFilter
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
) {
    val hasMore: Boolean get() = items.size < total
}

class RolePickerViewModel : ViewModel() {

    private val repository = RolesDependencies.require()

    private val _state = MutableStateFlow(RolePickerState())
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

    fun retry() = reload()

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMore || s.failed) return
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
