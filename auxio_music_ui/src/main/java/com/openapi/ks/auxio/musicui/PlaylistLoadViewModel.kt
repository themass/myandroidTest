package com.openapi.ks.auxio.musicui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openapi.core.net.BaseService
import com.openapi.core.net.NetQueue
import com.openapi.core.net.request.InfoListVo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed class PlaylistLoadUiState {
    data object Idle : PlaylistLoadUiState()
    data object Loading : PlaylistLoadUiState()
    data class Error(val message: String) : PlaylistLoadUiState()
    data class Ready(
        val initialTracks: List<MusicTrack>,
        val hasMore: Boolean,
        /** 用于 Compose `viewModel(key)`：整页重新拉取时更换，分页 append 不变 */
        val sessionKey: Long,
    ) : PlaylistLoadUiState()
}

internal class PlaylistLoadViewModel(
    application: Application,
    private val apiRoot: String,
    private val channel: String,
    private val keyword: String,
    private val volleyTag: String,
) : AndroidViewModel(application) {

    private val baseService = BaseService().also { it.setup(application) }

    private val _ui = MutableStateFlow<PlaylistLoadUiState>(PlaylistLoadUiState.Idle)
    val uiState: StateFlow<PlaylistLoadUiState> = _ui.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private var nextStart = 0
    private var hasMoreRemote = false

    fun cancelRequests() {
        NetQueue.cancelRequest(volleyTag)
    }

    fun loadInitial() {
        if (_ui.value is PlaylistLoadUiState.Loading) {
            return
        }
        viewModelScope.launch {
            _ui.value = PlaylistLoadUiState.Loading
            nextStart = 0
            runCatching {
                fetchPageBlocking(nextStart)
            }.onSuccess { info ->
                val mapped = mapToTracks(info, idBase = 0L)
                if (mapped.isEmpty()) {
                    _ui.value = PlaylistLoadUiState.Error(
                        getApplication<Application>().getString(R.string.auxio_list_empty),
                    )
                    return@launch
                }
                advanceCursorAfterPage(info)
                _ui.value = PlaylistLoadUiState.Ready(
                    initialTracks = mapped,
                    hasMore = hasMoreRemote,
                    sessionKey = System.nanoTime(),
                )
            }.onFailure { e ->
                _ui.value = PlaylistLoadUiState.Error(
                    e.message ?: getApplication<Application>().getString(R.string.auxio_list_network_error),
                )
            }
        }
    }

    fun refreshAll(onTracks: (List<MusicTrack>) -> Unit) {
        viewModelScope.launch {
            if (_refreshing.value) {
                return@launch
            }
            _refreshing.value = true
            runCatching {
                nextStart = 0
                fetchPageBlocking(0)
            }.onSuccess { info ->
                val mapped = mapToTracks(info, idBase = 0L)
                if (mapped.isNotEmpty()) {
                    advanceCursorAfterPage(info)
                    onTracks(mapped)
                    val cur = _ui.value
                    val key = (cur as? PlaylistLoadUiState.Ready)?.sessionKey ?: System.nanoTime()
                    _ui.value = PlaylistLoadUiState.Ready(mapped, hasMoreRemote, key)
                }
            }
            _refreshing.value = false
        }
    }

    /**
     * 追加下一页；返回 null 表示无更多或失败。
     */
    fun loadMoreAppend(trackIdBase: Long, onAppend: (List<MusicTrack>) -> Unit) {
        if (!hasMoreRemote || _loadingMore.value) {
            return
        }
        viewModelScope.launch {
            _loadingMore.value = true
            runCatching {
                fetchPageBlocking(nextStart)
            }.onSuccess { info ->
                val mapped = mapToTracks(info, idBase = trackIdBase)
                if (mapped.isNotEmpty()) {
                    advanceCursorAfterPage(info)
                    onAppend(mapped)
                    val cur = _ui.value
                    if (cur is PlaylistLoadUiState.Ready) {
                        _ui.value = cur.copy(hasMore = hasMoreRemote, sessionKey = cur.sessionKey)
                    }
                }
            }
            _loadingMore.value = false
        }
    }

    private suspend fun fetchPageBlocking(start: Int): InfoListVo<RemoteSoundItemDto> =
        withContext(Dispatchers.IO) {
            val url = AuxioSoundListUrls.buildFullUrl(apiRoot, start, channel, keyword)
            baseService.getInfoListData(url, RemoteSoundItemDto::class.java, volleyTag)
        }

    private fun mapToTracks(info: InfoListVo<RemoteSoundItemDto>, idBase: Long): List<MusicTrack> {
        val list = info.voList ?: return emptyList()
        var id = idBase
        return list.mapNotNull { row ->
            val url = row.file?.trim().orEmpty().ifEmpty { return@mapNotNull null }
            MusicTrack(
                id = id++,
                title = row.name?.trim().orEmpty(),
                mediaUrl = url,
                artworkUrl = null,
            )
        }
    }

    private fun advanceCursorAfterPage(info: InfoListVo<RemoteSoundItemDto>) {
        val n = info.voList?.size ?: 0
        if (info.pageNum > 0) {
            nextStart = info.pageNum
        } else {
            nextStart += n
        }
        hasMoreRemote = info.hasMore && n > 0
    }

    override fun onCleared() {
        cancelRequests()
        super.onCleared()
    }

    class Factory(
        private val application: Application,
        private val apiRoot: String,
        private val channel: String,
        private val keyword: String,
        private val volleyTag: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlaylistLoadViewModel::class.java)) {
                return PlaylistLoadViewModel(application, apiRoot, channel, keyword, volleyTag) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
