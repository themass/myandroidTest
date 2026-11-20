package com.openapi.imaging.cropassist

import com.openapi.imaging.quad.QuadNorm
import kotlin.math.exp

/**
 * 把模型的 `corner_heatmaps`（4×64×64）解码成归一化四边形。
 *
 * 从 provider 里抽出来单独成纯函数，是因为「四角点被压成轴对齐外接矩形」和
 * 「峰值不设置信度门槛、照单全收」这两个缺陷都发生在纯数值计算这一步（不碰 Bitmap、
 * 不碰 ONNX），抽出来才能用 JVM 单测把行为钉死。
 */
internal object CornerHeatmapDecoder {

    const val CHANNELS = 4

    /**
     * 角点置信度的下限。峰值必须先过 sigmoid 再比较：模型输出的是**原始 logits**
     * （makeacopy `DocQuadNet256` 文档：`corner_heatmaps`: logits (no sigmoid)），
     * 不过 sigmoid 就没法把「那个角没找到纸边」和「找到了但不准」区分开。
     * 0.5 是 sigmoid 的天然判决界；低于它说明模型自己都投了「这里不是角」。
     */
    const val MIN_CORNER_CONFIDENCE = 0.5f

    /** sigmoid；对很大的负数 exp 溢出成 Infinity，结果收敛到 0，正是想要的。 */
    fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

    /**
     * 解码四角热力图。
     *
     * @param heatmaps 展平的模型原始输出，长度 ≥ [CHANNELS]×heatmapSize²
     * @param canvasSize 推理画布边长（256），热力图铺满整个画布（含 letterbox 黑边）
     * @param offsetX/offsetY/scale 原图→画布的 letterbox 参数，用于反变换
     * @return 规范成 TL→TR→BR→BL 的四边形；任何一角置信度不足或几何退化时为 null
     */
    fun decode(
        heatmaps: FloatArray,
        heatmapSize: Int,
        canvasSize: Int,
        offsetX: Int,
        offsetY: Int,
        scale: Float,
        srcW: Int,
        srcH: Int,
        minConfidence: Float = MIN_CORNER_CONFIDENCE,
    ): QuadNorm? {
        val cells = heatmapSize * heatmapSize
        if (cells <= 0 || scale <= 0f || srcW <= 0 || srcH <= 0) return null
        if (heatmaps.size < CHANNELS * cells) return null

        val corners = ArrayList<QuadNorm.Point>(CHANNELS)
        for (channel in 0 until CHANNELS) {
            var bestIdx = 0
            var bestVal = Float.NEGATIVE_INFINITY
            val base = channel * cells
            for (i in 0 until cells) {
                val v = heatmaps[base + i]
                if (v > bestVal) {
                    bestVal = v
                    bestIdx = i
                }
            }
            // 置信度门槛：以前任何峰值都被当成角点，没有纸面的照片也能编出一个框。
            if (sigmoid(bestVal) < minConfidence) return null

            val hx = bestIdx % heatmapSize
            val hy = bestIdx / heatmapSize
            // 热力图格心 → 画布像素 → 原图像素 → 归一化。
            val canvasX = (hx + 0.5f) / heatmapSize * canvasSize
            val canvasY = (hy + 0.5f) / heatmapSize * canvasSize
            val origX = (canvasX - offsetX) / scale
            val origY = (canvasY - offsetY) / scale
            corners += QuadNorm.Point(
                (origX.coerceIn(0f, srcW.toFloat()) / srcW),
                (origY.coerceIn(0f, srcH.toFloat()) / srcH),
            )
        }

        // 通道→角的对应关系不确定，ordered() 负责规范成 TL→TR→BR→BL；
        // 几何退化（共线、面积过小、自交）都由 isValid 挡掉。
        val quad = QuadNorm(corners).ordered()
        return if (quad.isValid) quad else null
    }
}
