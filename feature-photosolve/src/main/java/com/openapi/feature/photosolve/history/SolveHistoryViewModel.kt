package com.openapi.feature.photosolve.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 解题历史列表的界面状态。 */
data class SolveHistoryUiState(
    val records: List<SolveRecord> = emptyList(),
    val loading: Boolean = true,
    /** 空列表且已经加载完：显示空状态，而不是一直转圈。 */
    val loaded: Boolean = false,
    /** 搜索关键字（题干/标题/答案）。空串表示不过滤。 */
    val query: String = "",
    /** 只看收藏。 */
    val favoritesOnly: Boolean = false,
) {
    /** 用户看到的列表：先按搜索/收藏过滤，收藏的排在前面（组内仍按最近更新排序）。 */
    val visible: List<SolveRecord>
        get() {
            val q = query.trim()
            val filtered = records.filter { r ->
                (q.isEmpty() || r.matches(q)) && (!favoritesOnly || r.favorite)
            }
            return filtered.sortedWith(compareByDescending<SolveRecord> { it.favorite }.thenByDescending { it.updatedAt })
        }

    val isEmpty: Boolean get() = loaded && visible.isEmpty() && query.isBlank() && !favoritesOnly
}

private fun SolveRecord.matches(query: String): Boolean {
    val q = query.lowercase()
    return title.lowercase().contains(q) ||
        question.lowercase().contains(q) ||
        answer.lowercase().contains(q)
}

/**
 * 解题历史：列表、搜索、收藏、重命名、删除、清空。
 *
 * 每次操作后重新读一次列表（数据量是几百条，读文件比维护增量状态更不容易出错）。
 */
class SolveHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SolveHistories.current(application)
    private val _uiState = MutableStateFlow(SolveHistoryUiState())
    val uiState: StateFlow<SolveHistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val list = runCatching { store.recent() }.getOrDefault(emptyList())
            _uiState.update { it.copy(records = list, loading = false, loaded = true) }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun toggleFavoritesOnly() {
        _uiState.update { it.copy(favoritesOnly = !it.favoritesOnly) }
    }

    fun rename(id: Long, title: String) {
        viewModelScope.launch {
            store.rename(id, title)
            refresh()
        }
    }

    fun setFavorite(id: Long, favorite: Boolean) {
        viewModelScope.launch {
            store.setFavorite(id, favorite)
            refresh()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            store.delete(id)
            refresh()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            store.clear()
            refresh()
        }
    }
}
