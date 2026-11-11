package com.openapi.feature.photosolve.data

import android.content.Context
import com.openapi.core.config.Constants
import com.openapi.feature.photosolve.presentation.PhotoSolveResult
import com.openapi.common.net.awaitUploadOrThrow
import com.openapi.common.net.http.BaseService
import com.openapi.common.net.http.NetError
import com.openapi.feature.photosolve.presentation.PhotoSolveError
import com.openapi.feature.photosolve.presentation.PhotoSolveException
import java.io.File

/**
 * 真实实现：走项目统一的 `:common` 网络层（[BaseService] + 挂起的 `awaitUpload`），
 * 对接 testserverV 的 `POST /api/photosolve/v1/solve.json`。
 *
 * 为什么用 `awaitUpload` 而不是自己 new OkHttpClient：
 * - 登录 token、设备 id、语言、Referer 等请求头都由 [com.openapi.common.net.request.BaseRequest] 统一加；
 * - 服务端返回的 `errno/data/error` 信封和 AES 加密的数据解码由 [com.openapi.common.net.request.DataBuilder] 处理，
 *   和聊天等接口完全一致；
 * - 32 位上传超时（[com.openapi.common.net.http.HttpClient] 的 uploadClient 是 180 秒）已经够模型推理。
 *
 * 错误码映射：服务端 errno（[NetError.BusinessError.getErrno]）原样透传成 [PhotoSolveException.code]，
 * 界面据此区分 40002 不是题目 / 42901 额度用完 / 50001 可重试；断网、超时归为 50001（可重试）。
 * 以前用的 `awaitUpload` 失败只返回 null，errno 全丢了，所有错误都显示成「服务不可用」。
 */
class NetworkPhotoSolveRepository @JvmOverloads constructor(
    context: Context,
    /** 相对路径，见 [com.openapi.core.config.Constants.PHOTOSOLVE_SOLVE_URL]。 */
    private val path: String = Constants.PHOTOSOLVE_SOLVE_URL,
) : PhotoSolveRepository {

    private val appContext = context.applicationContext
    private val service = BaseService().apply { setup(appContext) }

    override suspend fun solve(image: android.graphics.Bitmap, requestId: String): PhotoSolveResult {
        val compressed = com.openapi.common.image.ImageCompressor.compress(image)
        val file = File(appContext.cacheDir, "photosolve_${System.currentTimeMillis()}.jpg")
        try {
            file.writeBytes(compressed.bytes)
            val request = PhotoSolveRequest(
                clientRequestId = requestId,
                subject = "math",
            )
            val url = Constants.getUrl(path)
            val response = try {
                service.awaitUploadOrThrow(
                    url = url,
                    param = request,
                    files = listOf(file),
                    partName = "images",
                    clazz = PhotoSolveApiResponse::class.java,
                    tag = TAG,
                )
            } catch (e: NetError) {
                throw e.toPhotoSolveException()
            }

            return response.toResult()
        } finally {
            // 上传完就删：题目图不需要留在本地
            runCatching { file.delete() }
        }
    }

    private fun NetError.toPhotoSolveException(): PhotoSolveException = when (this) {
        is NetError.BusinessError -> {
            val code = if (errno == NetError.BusinessError.NO_CODE) PhotoSolveError.MODEL_UNAVAILABLE else errno
            PhotoSolveException(code, msg?.takeIf { it.isNotBlank() } ?: PhotoSolveError.userMessage(code))
        }
        is NetError.TimeoutError -> PhotoSolveException(PhotoSolveError.MODEL_UNAVAILABLE, "识别超时，请重试")
        else -> PhotoSolveException(PhotoSolveError.MODEL_UNAVAILABLE, "网络异常，请重试")
    }

    private fun PhotoSolveApiResponse.toResult(): PhotoSolveResult {
        if (!questionFound || questions.isEmpty()) {
            throw PhotoSolveException(PhotoSolveError.NOT_A_QUESTION, "没有识别到题目")
        }
        // 当前界面一次只展示一道题：取第一道，其余的在 steps 里合并说明（后续可做多题切换）
        val first = questions.first()
        return PhotoSolveResult(
            recognizedQuestion = first.text,
            steps = first.steps.mapNotNull { step ->
                step.text.ifBlank { null }?.let { text ->
                    if (step.latex.isBlank()) text else "$text  ${step.latex}"
                }
            },
            finalAnswer = first.finalAnswer,
            latex = first.finalAnswerLatex.takeIf { it.isNotBlank() },
            requestId = requestId,
            providerUsed = providerUsed,
            questionCount = questions.size,
        )
    }

    private companion object {
        const val TAG = "photosolve"
    }
}
