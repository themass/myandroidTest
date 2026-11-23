package com.openapi.feature.photosolve.history

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 历史详情的状态。 */
data class SolveDetailUiState(
    val record: SolveRecord? = null,
    val image: Bitmap? = null,
    val loading: Boolean = true,
    val notFound: Boolean = false,
    /** 图片文件丢失（记录还在）：详情照常显示文字，不显示图。 */
    val imageMissing: Boolean = false,
)

/**
 * 历史详情：读一条记录、收藏、重命名、删除。
 *
 * 图片按需解码（小图，不需要采样），失败时不阻塞文字内容的显示。
 */
class SolveDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SolveHistories.current(application)
    private val _uiState = MutableStateFlow(SolveDetailUiState())
    val uiState: StateFlow<SolveDetailUiState> = _uiState.asStateFlow()

    private var id: Long = -1

    fun load(recordId: Long) {
        id = recordId
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, notFound = false) }
            val record = runCatching { store.get(recordId) }.getOrNull()
            if (record == null) {
                _uiState.update { it.copy(loading = false, notFound = true, record = null, image = null) }
                return@launch
            }
            val image = withContext(Dispatchers.IO) { decode(record.imagePath) }
            _uiState.update {
                it.copy(
                    record = record,
                    image = image,
                    loading = false,
                    notFound = false,
                    imageMissing = record.imagePath == null || image == null,
                )
            }
        }
    }

    fun rename(title: String) {
        val current = id
        if (current <= 0) return
        viewModelScope.launch {
            store.rename(current, title)
            load(current)
        }
    }

    fun setFavorite(favorite: Boolean) {
        val current = id
        if (current <= 0) return
        viewModelScope.launch {
            store.setFavorite(current, favorite)
            // 只改本地状态，不重读图片（避免闪一下）
            _uiState.update { s -> s.copy(record = s.record?.copy(favorite = favorite)) }
        }
    }

    /** @param onDeleted 删除成功后回调（界面负责返回列表） */
    fun delete(onDeleted: () -> Unit) {
        val current = id
        if (current <= 0) return
        viewModelScope.launch {
            store.delete(current)
            onDeleted()
        }
    }

    private fun decode(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }
}
