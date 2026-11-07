package com.openapi.home.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openapi.home.HomeDependencies
import com.openapi.home.domain.Channel
import com.openapi.home.domain.ChannelCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChannelsUiState(
    val categories: List<ChannelCategory> = emptyList(),
    val selectedId: String? = null,
    val items: List<Channel> = emptyList(),
    /** 首次加载或切换分类：整页转圈。 */
    val loading: Boolean = false,
    /** 下拉刷新：保留已有内容。 */
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    /** 只在没有任何内容可显示时为 true（加载更多失败不算）。 */
    val error: Boolean = false,
    val hasMore: Boolean = false,
    val nextStart: Int = 0,
)

class ChannelsViewModel : ViewModel() {

    private val entries get() = HomeDependencies.require()
    private val _state = MutableStateFlow(ChannelsUiState())
    val state: StateFlow<ChannelsUiState> = _state.asStateFlow()
    private var job: Job? = null

    /** 每次回到前台调用：重新读分类（设置里的开关可能变了），必要时加载。 */
    fun onResume(context: Context) {
        val categories = entries.categories(context)
        val before = _state.value
        val selected = before.selectedId?.takeIf { id -> categories.any { it.id == id } }
            ?: categories.firstOrNull()?.id
        _state.update { it.copy(categories = categories, selectedId = selected) }
        val needLoad = selected != null &&
            (selected != before.selectedId || (before.items.isEmpty() && !before.loading && !before.error))
        if (needLoad) load(selected!!, reset = true, keepItems = false)
    }

    fun select(id: String) {
        if (id == _state.value.selectedId) return
        _state.update { it.copy(selectedId = id) }
        load(id, reset = true, keepItems = false)
    }

    fun refresh() {
        _state.value.selectedId?.let { load(it, reset = true, keepItems = true) }
    }

    fun retry() {
        _state.value.selectedId?.let { load(it, reset = true, keepItems = false) }
    }

    fun loadMore() {
        val s = _state.value
        val id = s.selectedId ?: return
        if (!s.hasMore || s.loading || s.loadingMore || s.refreshing) return
        load(id, reset = false, keepItems = true)
    }

    private fun load(id: String, reset: Boolean, keepItems: Boolean) {
        job?.cancel()
        val start = if (reset) 0 else _state.value.nextStart
        _state.update {
            when {
                !reset -> it.copy(loadingMore = true, error = false)
                keepItems -> it.copy(refreshing = true, loading = false, loadingMore = false, error = false)
                else -> it.copy(
                    items = emptyList(), hasMore = false, nextStart = 0,
                    loading = true, refreshing = false, loadingMore = false, error = false,
                )
            }
        }
        job = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { entries.loadChannels(id, start) } }
            if (_state.value.selectedId != id) return@launch
            result.onSuccess { page ->
                _state.update {
                    it.copy(
                        items = if (reset) page.items else it.items + page.items,
                        hasMore = page.hasMore,
                        nextStart = page.nextStart,
                        loading = false, refreshing = false, loadingMore = false, error = false,
                    )
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _state.update {
                    it.copy(loading = false, refreshing = false, loadingMore = false, error = it.items.isEmpty())
                }
            }
        }
    }
}
