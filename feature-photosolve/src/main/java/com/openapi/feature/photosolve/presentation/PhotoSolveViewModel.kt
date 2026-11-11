package com.openapi.feature.photosolve.presentation

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openapi.common.image.ImageUtils.resizeSmart
import com.openapi.feature.photosolve.data.PhotoSolveRepositories
import com.openapi.imaging.cropassist.CropAssistRegistry
import com.openapi.imaging.cropassist.CropAssistResult
import com.openapi.imaging.cropassist.CropAssistSettingsRepository
import com.openapi.imaging.image.ImagePipeline.enhanceForDocument
import com.openapi.imaging.image.RealEsrganUpscaler
import com.openapi.imaging.quality.QuestionImagePreparer
import com.openapi.imaging.quad.QuadNorm
import com.openapi.imaging.quad.QuadWarp
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

/**
 * 拍照解题的状态机：
 *
 * ```
 * Home → Capture → （检测到四边形）QuadAdjust → 透视矫正 → 质量检查 → Recognizing → Result
 *                └（没检测到 / 检测器不可用）→ 提示 → 仍可手动 Crop → 质量检查 → Recognizing → Result
 * ```
 *
 * 和旧流程的区别：拍照后先判断「画面里有没有题目区域」，有就让用户微调四个角再透视矫正；
 * 没有就明确提示（而不是静默裁一个框把背景发给模型）。真正的「这是不是题目」最终由服务端
 * 模型判断（40002），端上只做几何层面的预筛。
 */
class PhotoSolveViewModel(application: Application) : AndroidViewModel(application) {

    private val cropAssistSettings = CropAssistSettingsRepository(application)
    /** 模型未随包提供时 [RealEsrganUpscaler.upscaleSmall] 直接返回 null，流程不变。 */
    private val upscaler = RealEsrganUpscaler(application)

    val cropAssistRegistry = CropAssistRegistry(application)
    private val qualityPreparer = QuestionImagePreparer(cropAssistRegistry)

    /**
     * 题目四边形检测：与裁剪页共用 DocQuadNet，但这里要的是「有没有检测到」，
     * 所以直接问 provider 而不是走 [QuestionImagePreparer] 的裁剪逻辑。
     */
    private val quadDetector = QuestionQuadDetector(cropAssistRegistry.docQuadNet)

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

    /**
     * 拍完/选完图：先在端上找题目区域。
     *
     * 检测在 IO/Default 线程做（模型推理几百毫秒），期间界面显示「检测中」；
     * 检测到就进四角微调页，检测不到就提示用户重新对准（仍允许手动裁剪）。
     */
    fun onPhotoCaptured(bitmap: Bitmap) {
        _uiState.update {
            it.copy(
                step = PhotoSolveStep.Capture,
                capturedBitmap = bitmap,
                detecting = true,
                errorMessage = null,
                frameCheck = null,
            )
        }
        viewModelScope.launch {
            val available = quadDetector.isAvailable()
            if (!available) {
                _uiState.update {
                    it.copy(
                        detecting = false,
                        frameCheck = QuestionFrameCheck.DetectorUnavailable("检测模型未安装"),
                        // 没有检测器时退化为手动框选：直接进裁剪页，不让用户卡在这一步
                        step = PhotoSolveStep.Crop,
                        quad = quadDetector.defaultQuad(),
                    )
                }
                return@launch
            }
            val quad = quadDetector.detect(bitmap)
            if (quad == null) {
                _uiState.update {
                    it.copy(
                        detecting = false,
                        frameCheck = QuestionFrameCheck.NotFound("未检测到题目边缘"),
                        // 留在取景页，用户可以直接重拍；也可以选择手动框选
                        step = PhotoSolveStep.Capture,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        step = PhotoSolveStep.QuadAdjust,
                        detecting = false,
                        frameCheck = QuestionFrameCheck.Found(quad, quadDetector.displayName, 0),
                        quad = quad,
                    )
                }
            }
        }
    }

    /** 四角微调页拖动角点。 */
    fun onQuadChanged(quad: QuadNorm) {
        _uiState.update { it.copy(quad = quad) }
    }

    /** 四角微调页点「重新检测」。 */
    fun onRedetect() {
        val source = _uiState.value.capturedBitmap ?: return
        _uiState.update { it.copy(detecting = true) }
        viewModelScope.launch {
            val quad = quadDetector.detect(source)
            _uiState.update {
                it.copy(
                    detecting = false,
                    quad = quad ?: quadDetector.defaultQuad(),
                    frameCheck = if (quad == null) QuestionFrameCheck.NotFound("未检测到题目边缘") else it.frameCheck,
                )
            }
        }
    }

    /**
     * 用户确认四个角：透视矫正 → 质量检查 → 解题。
     *
     * 透视矫正在 OpenCV 里做（几百毫秒），期间同样显示检测中；矫正失败（OpenCV 不可用）
     * 时 [QuadWarp] 内部会退化成外接矩形裁剪，不会让流程中断。
     */
    fun onQuadConfirmed() {
        val state = _uiState.value
        val source = state.capturedBitmap ?: return
        val quad = state.quad ?: quadDetector.defaultQuad()
        viewModelScope.launch {
            _uiState.update { it.copy(detecting = true) }
            val cropped = withContext(Dispatchers.Default) { QuadWarp.warp(source, quad) }
            _uiState.update { it.copy(detecting = false, croppedBitmap = cropped) }
            checkQualityAndSolve(cropped)
        }
    }

    fun onCaptureCancelled() {
        _uiState.update { it.copy(step = PhotoSolveStep.Home) }
    }

    fun onCropCancelled() {
        _uiState.update { it.copy(step = PhotoSolveStep.Capture, capturedBitmap = null) }
    }

    /** 旧的手动裁剪页确认（没有检测器或用户主动改框时使用）。 */
    fun onCropConfirmed(croppedByUser: Bitmap) {
        _uiState.update { it.copy(croppedBitmap = croppedByUser) }
        viewModelScope.launch { checkQualityAndSolve(croppedByUser) }
    }

    /** 从取景页选择「手动框选」：跳过四边形检测，直接进裁剪页。 */
    fun onManualCropRequested() {
        val source = _uiState.value.capturedBitmap ?: return
        _uiState.update { it.copy(step = PhotoSolveStep.Crop, quad = null, capturedBitmap = source) }
    }

    private suspend fun checkQualityAndSolve(bitmap: Bitmap) {
        // 清晰度/曝光不合格时先停下来让用户决定：重拍，或者仍然识别（识别结果可能不准）
        val issues = qualityPreparer.qualityOf(bitmap).issues
        if (issues.isEmpty()) {
            solve(bitmap)
        } else {
            _uiState.update { it.copy(qualityWarning = QualityWarning(bitmap, issues)) }
        }
    }

    /** 用户在清晰度提示里选择「仍然识别」。 */
    fun onSolveAnyway() {
        val warning = _uiState.value.qualityWarning ?: return
        _uiState.update { it.copy(qualityWarning = null) }
        viewModelScope.launch { solve(warning.bitmap) }
    }

    fun onDismissQualityWarning() {
        _uiState.update { it.copy(qualityWarning = null) }
    }

    /** 清晰度提示里选择「重新拍照」：回到拍照页，清掉当前图。 */
    fun onRetakeAfterQualityWarning() {
        _uiState.update {
            it.copy(
                step = PhotoSolveStep.Capture,
                capturedBitmap = null,
                croppedBitmap = null,
                quad = null,
                qualityWarning = null,
                errorMessage = null,
            )
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
            _uiState.update { it.copy(step = PhotoSolveStep.Result, result = solved, errorCode = null) }
        }.onFailure { e ->
            val code = (e as? PhotoSolveException)?.code
            _uiState.update { state ->
                when (code) {
                    // 图片不清晰：复用模糊提示弹窗，用户可以选择仍然提交
                    PhotoSolveError.BLURRY -> state.copy(
                        step = PhotoSolveStep.QuadAdjust,
                        qualityWarning = QualityWarning(
                            cropped,
                            listOf(com.openapi.imaging.quality.QualityIssue.BLURRY),
                        ),
                        errorCode = code,
                    )
                    // 不是题目：回到取景页，明确提示重新对准（而不是只弹一个 toast）
                    PhotoSolveError.NOT_A_QUESTION -> state.copy(
                        step = PhotoSolveStep.Capture,
                        errorCode = code,
                        errorMessage = PhotoSolveError.userMessage(code),
                    )
                    else -> state.copy(
                        step = PhotoSolveStep.QuadAdjust,
                        errorCode = code,
                        errorMessage = PhotoSolveError.userMessage(code ?: PhotoSolveError.MODEL_UNAVAILABLE),
                    )
                }
            }
        }
    }

    /** 结果页/错误弹窗上的「重试」：用同一张裁剪图再解一次。 */
    fun onRetry() {
        val bitmap = _uiState.value.croppedBitmap ?: return
        _uiState.update { it.copy(errorMessage = null, errorCode = null) }
        viewModelScope.launch { solve(bitmap) }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onRetake() {
        _uiState.value = PhotoSolveUiState()
    }
}
