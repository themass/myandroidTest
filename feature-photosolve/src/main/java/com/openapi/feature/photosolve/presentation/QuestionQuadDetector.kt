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
 * 热力图（`corner_heatmaps` 1x4x64x64），检测器会把真实的四边形带在
 * [CropAssistResult.SuggestedBox.quad] 里；这里优先用它（斜拍题目不再退化成外接矩形）。
 * 只给出矩形框的旧式 provider 仍按矩形构造四边形。模型缺失时返回 null，
 * 由调用方决定是提示重拍还是让用户用 [defaultQuad] 手动拖角点。
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
        quadFrom(suggestion)
    }

    /**
     * provider 的建议 → 可用的四边形；不可用时 null。纯逻辑，[detect] 与单测共用。
     */
    internal fun quadFrom(suggestion: CropAssistResult.SuggestedBox): QuadNorm? {
        // provider 给出真实四边形就直接用；只有矩形时退化成直角四边形。接口不变。
        val quad = suggestion.quad ?: rectangleToQuad(suggestion.norm) ?: return null
        // 面积过大（几乎整张图）说明检测没找到纸面，而不是「纸面就是整张图」
        return if (quad.area() > QuadNorm.MAX_AREA) null else quad
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
