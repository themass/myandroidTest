package com.openapi.feature.photosolve.data

import android.graphics.Bitmap
import com.openapi.common.image.ImageCompressor
import com.openapi.feature.photosolve.presentation.PhotoSolveResult

/** 拍照、相册与截图入口共用 agent 的独立视觉接口，不生成聊天会话或保存题目图到缓存。 */
class NetworkPhotoSolveRepository(
    baseUrl: String,
    tokenProvider: () -> String?,
) : PhotoSolveRepository {
    private val remote = AgentPhotoSolveRemote(baseUrl, tokenProvider)

    override suspend fun solve(image: Bitmap, requestId: String): PhotoSolveResult {
        val compressed = ImageCompressor.compress(image)
        return SolveResultMapper.map(remote.solve(compressed.bytes, requestId))
    }

    override suspend fun solveWithProgress(
        image: Bitmap,
        requestId: String,
        onProgress: (PhotoSolveResult) -> Unit,
    ): PhotoSolveResult {
        val bytes = ImageCompressor.compress(image).bytes
        val steps = mutableListOf<String>()
        var question = ""
        var thinking = ""
        var answer = ""
        var subject = "math"
        var provider: String? = null
        fun publish() = onProgress(
            PhotoSolveResult(
                recognizedQuestion = question,
                steps = steps.toList(),
                finalAnswer = answer,
                requestId = requestId,
                providerUsed = provider,
                thinking = thinking,
                subject = subject,
            ),
        )
        return try {
            val response = remote.solveStreaming(
                image = bytes,
                requestId = requestId,
                onFirstFrame = ::publish,
                onThinking = { thinking += it },
                onQuestion = { question += it },
                onStep = { step -> steps += step; publish() },
                onAnswer = { answer = it; publish() },
            )
            SolveResultMapper.map(response).also { final ->
                subject = final.subject
                provider = final.providerUsed
                onProgress(final)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (apiFailure: com.openapi.feature.photosolve.presentation.PhotoSolveException) {
            throw apiFailure
        } catch (_: Exception) {
            // SSE connection/protocol failure: retry through the established full-JSON endpoint.
            SolveResultMapper.map(remote.solve(bytes, requestId)).also(onProgress)
        }
    }
}
