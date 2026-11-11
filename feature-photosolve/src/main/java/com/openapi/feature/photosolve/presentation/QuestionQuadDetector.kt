package com.openapi.feature.photosolve.presentation

import android.graphics.Bitmap
import com.openapi.imaging.cropassist.CropAssistProvider
import com.openapi.imaging.cropassist.CropAssistResult
import com.openapi.imaging.quad.QuadNorm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从一张照片里检测题目纸面的四个角。
 *
 * 现有 [com.openapi.imaging.cropassist.DocQuadNetCropAssistProvider] 的模型输出本来就是四个角的
 * 热力图（`corner_heatmaps` 1x4x64x64），但当前接口只把四个点压成了外接矩形（`CropNorm`），
 * 斜拍的题目因此会带进大量背景。这里复用同一个 [CropAssistProvider] 的能力，把「四点」这一层
 * 暴露出来；模型缺失时退化为「整张图」，让用户手动拖角点。
 *
 * 之所以不直接改 [CropAssistProvider] 的返回类型：裁剪页（A27 的手动框选）仍然按矩形工作，
 * 改它会牵动那一整条已经稳定的流程；这里作为独立的、仅供拍照解题使用的能力。
 */
class QuestionQuadDetector(
    private val provider: CropAssistProvider,
) {

    /** 显示名，界面上说明用的是哪个检测器。 */
    val displayName: String get() = provider.displayName

    suspend fun isAvailable(): Boolean = provider.isAvailable()

    /**
     * 检测题目区域。
     *
     * @return 检测到的四边形；检测不到时返回 null（调用方决定是提示重拍还是让用户手动框）
     */
    suspend fun detect(bitmap: Bitmap): QuadNorm? = withContext(Dispatchers.Default) {
        val suggestion = provider.suggest(bitmap) as? CropAssistResult.SuggestedBox ?: return@withContext null
        // provider 目前给的是外接矩形；先按矩形构造四边形，四个角都是直角。
        // 换成输出四点的 provider 时，这里改成读它的 corners 即可，接口不变。
        rectangleToQuad(suggestion.norm)
    }

    /** 外接矩形 → 四边形（顺序 TL、TR、BR、BL）。 */
    fun rectangleToQuad(rect: com.openapi.imaging.CropNorm): QuadNorm? {
        val quad = QuadNorm(
            listOf(
                QuadNorm.Point(rect.left, rect.top),
                QuadNorm.Point(rect.right, rect.top),
                QuadNorm.Point(rect.right, rect.bottom),
                QuadNorm.Point(rect.left, rect.bottom),
            ),
        )
        if (!quad.isValid) return null
        // 面积过大（几乎整张图）说明检测没找到纸面，而不是「纸面就是整张图」
        return if (quad.area() > QuadNorm.MAX_AREA) null else quad
    }

    /** 检测不到时的默认四边形：中间 80% 的区域，用户可以直接拖四个角到题目边缘。 */
    fun defaultQuad(): QuadNorm = QuadNorm(
        listOf(
            QuadNorm.Point(0.10f, 0.15f),
            QuadNorm.Point(0.90f, 0.15f),
            QuadNorm.Point(0.90f, 0.85f),
            QuadNorm.Point(0.10f, 0.85f),
        ),
    )
}
