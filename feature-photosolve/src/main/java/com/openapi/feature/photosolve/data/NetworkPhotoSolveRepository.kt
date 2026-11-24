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
}
