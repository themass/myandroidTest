package com.aimath.paizhao.photosolve

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aimath.paizhao.photosolve.ImageUtils.resizeSmart
import com.aimath.paizhao.photosolve.cropassist.CropAssistRegistry
import com.aimath.paizhao.photosolve.cropassist.CropAssistSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoSolveViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PhotoSolveRepository = MockPhotoSolveRepository()
    private val cropAssistSettings = CropAssistSettingsRepository(application)

    val cropAssistRegistry = CropAssistRegistry(application)

    private val _uiState = MutableStateFlow(PhotoSolveUiState())
    val uiState: StateFlow<PhotoSolveUiState> = _uiState.asStateFlow()

    private val _activeCropAssistId = MutableStateFlow(cropAssistSettings.getActiveProviderId())
    val activeCropAssistId: StateFlow<String> = _activeCropAssistId.asStateFlow()

    fun setActiveCropAssistProvider(id: String) {
        cropAssistSettings.setActiveProviderId(id)
        _activeCropAssistId.value = id
    }

    fun onRequestCapture() {
        _uiState.update { it.copy(step = PhotoSolveStep.Capture, errorMessage = null) }
    }

    fun onPhotoCaptured(bitmap: Bitmap) {
        _uiState.update { it.copy(step = PhotoSolveStep.Crop, capturedBitmap = bitmap) }
    }

    fun onCaptureCancelled() {
        _uiState.update { it.copy(step = PhotoSolveStep.Home) }
    }

    fun onCropCancelled() {
        _uiState.update { it.copy(step = PhotoSolveStep.Capture, capturedBitmap = null) }
    }

    /** The user finished cropping (via easycrop, or ML Kit's own scan flow) — kick off the mock solve. */
    fun onCropConfirmed(croppedByUser: Bitmap) {
        viewModelScope.launch {
            val cropped = withContext(Dispatchers.Default) { croppedByUser.resizeSmart() }

            _uiState.update {
                it.copy(step = PhotoSolveStep.Recognizing, croppedBitmap = cropped, recognizingStatusIndex = 0)
            }

            val statusCycler = launch {
                while (isActive) {
                    delay(900)
                    _uiState.update { s -> s.copy(recognizingStatusIndex = (s.recognizingStatusIndex + 1).coerceAtMost(2)) }
                }
            }

            val result = runCatching { repository.solve(cropped) }
            statusCycler.cancel()

            result.onSuccess { solved ->
                _uiState.update { it.copy(step = PhotoSolveStep.Result, result = solved) }
            }.onFailure {
                _uiState.update { it.copy(step = PhotoSolveStep.Crop, errorMessage = "识别失败,请重试") }
            }
        }
    }

    fun onRetake() {
        _uiState.value = PhotoSolveUiState()
    }
}
