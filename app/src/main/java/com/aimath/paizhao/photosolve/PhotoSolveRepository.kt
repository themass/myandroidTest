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

    override suspend fun solve(image: Bitmap): PhotoSolveResult {
        delay(1500)
        return sampleResults.random()
    }
}
