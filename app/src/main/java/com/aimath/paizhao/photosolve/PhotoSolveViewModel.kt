package com.aimath.paizhao.photosolve

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aimath.paizhao.photosolve.ImageUtils.resizeSmart
import com.aimath.paizhao.photosolve.cropassist.CropAssistRegistry
import com.aimath.paizhao.photosolve.cropassist.CropAssistSettingsRepository
import com.aimath.paizhao.photosolve.image.ImagePipeline.enhanceForDocument
import com.aimath.paizhao.photosolve.image.RealEsrganUpscaler
import com.aimath.paizhao.photosolve.quality.QuestionImagePreparer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoSolveViewModel(application: Application) : AndroidViewModel(application) {

    private val cropAssistSettings = CropAssistSettingsRepository(application)
    /** 模型未随包提供时 [RealEsrganUpscaler.upscaleSmall] 直接返回 null，流程不变。 */
    private val upscaler = RealEsrganUpscaler(application)

    val cropAssistRegistry = CropAssistRegistry(application)
    private val qualityPreparer = QuestionImagePreparer(cropAssistRegistry)

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
            // 清晰度/曝光不合格时先停下来让用户决定：重拍，或者仍然识别（识别结果可能不准）
            val issues = qualityPreparer.qualityOf(croppedByUser).issues
            if (issues.isEmpty()) {
                solve(croppedByUser)
            } else {
                _uiState.update { it.copy(qualityWarning = QualityWarning(croppedByUser, issues)) }
            }
        }
    }

    /** 用户在清晰度提示里选择"仍然识别"。 */
    fun onSolveAnyway() {
        val warning = _uiState.value.qualityWarning ?: return
        _uiState.update { it.copy(qualityWarning = null) }
        viewModelScope.launch { solve(warning.bitmap) }
    }

    fun onDismissQualityWarning() {
        _uiState.update { it.copy(qualityWarning = null) }
    }

    /** 清晰度提示里选择"重新拍照"：回到拍照页，清掉当前图。 */
    fun onRetakeAfterQualityWarning() {
        _uiState.update {
            it.copy(step = PhotoSolveStep.Capture, capturedBitmap = null, qualityWarning = null, errorMessage = null)
        }
    }

    private suspend fun solve(croppedByUser: Bitmap) = coroutineScope {
        val cropped = withContext(Dispatchers.Default) {
            val resized = croppedByUser.resizeSmart()
            (upscaler.upscaleSmall(resized) ?: resized).enhanceForDocument()
        }

        _uiState.update {
            it.copy(step = PhotoSolveStep.Recognizing, croppedBitmap = cropped, recognizingStatusIndex = 0)
        }

        val statusCycler = launch {
            while (isActive) {
                delay(900)
                _uiState.update { s -> s.copy(recognizingStatusIndex = (s.recognizingStatusIndex + 1).coerceAtMost(2)) }
            }
        }

        val result = runCatching { PhotoSolveRepositories.current().solve(cropped) }
        statusCycler.cancel()

        result.onSuccess { solved ->
            _uiState.update { it.copy(step = PhotoSolveStep.Result, result = solved) }
        }.onFailure {
            _uiState.update { it.copy(step = PhotoSolveStep.Crop, errorMessage = "识别失败,请重试") }
        }
    }

    fun onRetake() {
        _uiState.value = PhotoSolveUiState()
    }
}
