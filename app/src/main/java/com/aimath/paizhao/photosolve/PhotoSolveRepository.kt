package com.aimath.paizhao.photosolve

import android.graphics.Bitmap
import kotlinx.coroutines.delay

/**
 * Submits a cropped question image and returns a worked solution.
 *
 * This is the extension point for a real backend: swap [MockPhotoSolveRepository] for an
 * implementation backed by Retrofit/OkHttp (mirroring aixue's `aiclasstoc/photoExplanation`
 * endpoints) without touching [PhotoSolveViewModel] or any UI.
 */
interface PhotoSolveRepository {
    suspend fun solve(image: Bitmap): PhotoSolveResult
}

/** 服务端错误码（见 docs photo-solve-api）：40001 不清晰、40002 不是题目、40003 图片过大或格式不支持、42901 额度用完、50001 模型服务失败。 */
class PhotoSolveException(val code: Int, message: String) : Exception(message)

/** Mocked implementation: simulates upload/recognition latency and returns a canned worked problem. */
class MockPhotoSolveRepository : PhotoSolveRepository {

    private val sampleResults = listOf(
        PhotoSolveResult(
            recognizedQuestion = "解方程：x² - 5x + 6 = 0",
            steps = listOf(
                "观察方程,尝试因式分解。",
                "x² - 5x + 6 = (x - 2)(x - 3)",
                "令 (x - 2)(x - 3) = 0,得 x - 2 = 0 或 x - 3 = 0",
                "解得 x = 2 或 x = 3",
            ),
            finalAnswer = "x = 2 或 x = 3",
        ),
        PhotoSolveResult(
            recognizedQuestion = "计算：(3x + 2)(x - 5)",
            steps = listOf(
                "使用分配律展开两个括号。",
                "(3x + 2)(x - 5) = 3x·x + 3x·(-5) + 2·x + 2·(-5)",
                "= 3x² - 15x + 2x - 10",
                "合并同类项：3x² - 13x - 10",
            ),
            finalAnswer = "3x² - 13x - 10",
        ),
    )

    /**
     * 按顺序轮流返回：成功、40001、40002、40003、42901、50001。
     * 这样点几次「拍照解题」就能把每条错误分支都走一遍（A29）。
     */
    private val script: List<Int?> = listOf(null, 40001, 40002, 40003, 42901, 50001)
    private var step = 0

    override suspend fun solve(image: Bitmap): PhotoSolveResult {
        delay(1500)
        val code = script[step % script.size]
        step++
        if (code != null) throw PhotoSolveException(code, "mock error $code")
        return sampleResults.random()
    }
}
