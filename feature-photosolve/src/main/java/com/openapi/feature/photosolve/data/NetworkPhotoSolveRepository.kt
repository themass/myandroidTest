package com.openapi.feature.photosolve.data

import android.content.Context
import com.openapi.core.config.Constants
import com.openapi.feature.photosolve.presentation.PhotoSolveResult
import com.openapi.common.net.awaitUploadOrThrow
import com.openapi.common.net.http.NetError
import com.openapi.common.net.http.BaseService
import com.openapi.feature.photosolve.presentation.PhotoSolveError
import com.openapi.feature.photosolve.presentation.PhotoSolveException
import java.io.File
import java.util.UUID

/**
 * 真实实现：走项目统一的 `:network` 层（[BaseService] + 挂起的 `awaitUpload`），
 * 对接 testserverV 的 `POST /api/photosolve/v1/solve.json`。
 *
 * 为什么用 `awaitUpload` 而不是自己 new OkHttpClient：
 * - 登录 token、设备 id、语言、Referer 等请求头都由 [com.openapi.common.net.request.BaseRequest] 统一加；
 * - 服务端返回的 `errno/data/error` 信封和 AES 加密的数据解码由 [com.openapi.common.net.request.DataBuilder] 处理，
 *   和聊天等接口完全一致；
 * - 32 位上传超时（[com.openapi.common.net.http.HttpClient] 的 uploadClient 是 180 秒）已经够模型推理。
 *
 * 错误码映射：服务端 errno 直接透传成 [PhotoSolveError]；网络错误（连接失败/超时/解析失败）
 * 统一按 [PhotoSolveError.MODEL_UNAVAILABLE]（可重试）处理。
 */
class NetworkPhotoSolveRepository @JvmOverloads constructor(
    context: Context,
    /** 相对路径，见 [com.openapi.core.config.Constants.PHOTOSOLVE_SOLVE_URL]。 */
    private val path: String = Constants.PHOTOSOLVE_SOLVE_URL,
) : PhotoSolveRepository {

    private val appContext = context.applicationContext
    private val service = BaseService().also { it.setup(appContext) }

    override suspend fun solve(image: android.graphics.Bitmap): PhotoSolveResult {
        val compressed = com.openapi.common.image.ImageCompressor.compress(image)
        val file = File(appContext.cacheDir, "photosolve_${System.currentTimeMillis()}.jpg")
        try {
            file.writeBytes(compressed.bytes)
            // 不猜学科：服务端会把 subject 当成学生提示写进提示词，猜错反而误导模型
            val request = PhotoSolveRequest(
                clientRequestId = UUID.randomUUID().toString(),
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
                // 服务端的 errno 是界面契约（40002「不是题目」要回取景页），不能压成一句通用提示
                throw PhotoSolveException(errnoOf(e), messageOf(e))
            } ?: throw PhotoSolveException(PhotoSolveError.MODEL_UNAVAILABLE, "服务返回为空，请重试")

            return response.toResult()
        } finally {
            // 上传完就删：题目图不需要留在本地
            runCatching { file.delete() }
        }
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

        /**
         * 服务端 errno → 客户端错误码。
         *
         * 网络层把 errno 原样带在 [NetError.BusinessError] 上（`getErrno()`），所以这里是纯映射；
         * 0 表示老版本网络层没填，按可重试处理。未知 errno 同样按可重试处理，
         * 并用服务端文案覆盖默认提示（服务端最清楚这次为什么失败）。
         */
        fun errnoOf(error: NetError): Int {
            val code = (error as? NetError.BusinessError)?.errno ?: 0
            return if (code in KNOWN) code else PhotoSolveError.MODEL_UNAVAILABLE
        }

        fun messageOf(error: NetError): String =
            (error as? NetError.BusinessError)?.msg?.takeIf { it.isNotBlank() }
                ?: if (error is NetError.TimeoutError) "识别超时，请重试" else "网络异常，请重试"

        /** 客户端认识的 errno，与 [PhotoSolveError] 一一对应。 */
        val KNOWN = setOf(
            PhotoSolveError.BAD_REQUEST,
            PhotoSolveError.BLURRY,
            PhotoSolveError.NOT_A_QUESTION,
            PhotoSolveError.BAD_IMAGE,
            PhotoSolveError.QUOTA_EXCEEDED,
            PhotoSolveError.MODEL_UNAVAILABLE,
            PhotoSolveError.PROVIDER_NOT_CONFIGURED,
        )
    }
}
