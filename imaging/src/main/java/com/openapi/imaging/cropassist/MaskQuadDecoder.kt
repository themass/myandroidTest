package com.openapi.imaging.cropassist

import com.openapi.imaging.quad.QuadNorm

/**
 * 把模型的 `mask_logits`（1×64×64 纸面轮廓掩码）解码成归一化四边形——
 * [CornerHeatmapDecoder] 四角路径判负后的**兜底**。
 *
 * 为什么需要（真纸样本离线取证，`scripts/probe/photosolve_offline_chain.py`）：
 * 「四角全过 0.5」对随手拍纸面过严——两张真纸实拍只有 2/4、3/4 个角过线，
 * 3/4 那张崩溃的角把几何也带崩（非凸、面积 0.005），所以「放宽成 3/4 接受」
 * 出不了好框。但同一批样本上模型第二输出 `mask_logits` 一直是健康的
 * （真纸框面积 0.30/0.34，肉眼核对贴合；屏幕翻拍的小 mask 0.054 会被
 * MIN_AREA 正确挡掉）。
 *
 * 做法：sigmoid≥0.5 视为前景，取四个对角方向（min x+y、max x−y、max x+y、
 * min x−y）的极值前景格作为 TL/TR/BR/BL。退化（空 mask、碎块、非凸、
 * 面积越界）一律返回 null——兜底也必须保守，判负仍走默认框 + 黄说明。
 */
internal object MaskQuadDecoder {

    /** 前景判定界（sigmoid(0)=0.5；模型输出是原始 logits）。 */
    private const val FOREGROUND_LOGIT = 0f

    /**
     * @param maskLogits 展平的 `mask_logits`（heatmapSize²）
     * @param heatmapSize/canvasSize/offsetX/offsetY/scale/srcW/srcH 与
     *   [CornerHeatmapDecoder.decode] 同一组 letterbox 参数
     * @return 规范成 TL→TR→BR→BL 的四边形；掩码为空或几何退化时为 null
     */
    fun decode(
        maskLogits: FloatArray,
        heatmapSize: Int,
        canvasSize: Int,
        offsetX: Int,
        offsetY: Int,
        scale: Float,
        srcW: Int,
        srcH: Int,
    ): QuadNorm? {
        val cells = heatmapSize * heatmapSize
        if (cells <= 0 || scale <= 0f || srcW <= 0 || srcH <= 0) return null
        if (maskLogits.size < cells) return null

        var tl = -1
        var tr = -1
        var br = -1
        var bl = -1
        var tlKey = Int.MAX_VALUE
        var trKey = Int.MIN_VALUE
        var brKey = Int.MIN_VALUE
        var blKey = Int.MAX_VALUE
        for (hy in 0 until heatmapSize) {
            val row = hy * heatmapSize
            for (hx in 0 until heatmapSize) {
                if (maskLogits[row + hx] < FOREGROUND_LOGIT) continue
                val sum = hx + hy
                val diff = hx - hy
                if (sum < tlKey) { tlKey = sum; tl = row + hx }
                if (diff > trKey) { trKey = diff; tr = row + hx }
                if (sum > brKey) { brKey = sum; br = row + hx }
                if (diff < blKey) { blKey = diff; bl = row + hx }
            }
        }
        if (tl < 0) return null // 空掩码

        fun point(idx: Int): QuadNorm.Point {
            val hx = idx % heatmapSize
            val hy = idx / heatmapSize
            // 与 CornerHeatmapDecoder 完全同一套「格心 → 画布 → 原图 → 归一化」反变换。
            val canvasX = (hx + 0.5f) / heatmapSize * canvasSize
            val canvasY = (hy + 0.5f) / heatmapSize * canvasSize
            val origX = (canvasX - offsetX) / scale
            val origY = (canvasY - offsetY) / scale
            return QuadNorm.Point(
                (origX.coerceIn(0f, srcW.toFloat()) / srcW),
                (origY.coerceIn(0f, srcH.toFloat()) / srcH),
            )
        }

        val quad = QuadNorm(listOf(point(tl), point(tr), point(br), point(bl))).ordered()
        // isValid 挡共线/自交/过小；面积上限挡「mask 糊满整屏」——那种情况
        // 自动贴合没有意义（QuestionQuadDetector 的 MAX_AREA 检查同口径）。
        return if (quad.isValid && quad.area() <= QuadNorm.MAX_AREA) quad else null
    }
}
