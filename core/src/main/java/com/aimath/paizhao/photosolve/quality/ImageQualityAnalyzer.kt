package com.aimath.paizhao.photosolve.quality

/** 一项质量问题。界面据此给出具体建议，而不是只说"不清楚"。 */
enum class QualityIssue {
    /** 短边像素太少，题目里的小字会识别不出来。 */
    LOW_RESOLUTION,

    /** 拉普拉斯方差低，边缘不锐利，通常是对焦不准或手抖。 */
    BLURRY,

    /** 整体偏暗，或大面积接近全黑。 */
    TOO_DARK,

    /** 整体过曝，或大面积接近全白（反光、闪光灯）。 */
    TOO_BRIGHT,
}

/** 一张题目图的质量指标。阈值是经验值，只用来给出提示，不作为拒绝发送的硬性条件。 */
data class QualityReport(
    /** 拉普拉斯响应的方差，在统一缩放到 [ImageQualityAnalyzer.ANALYSIS_MAX_SIDE] 后计算。 */
    val sharpness: Double,
    /** 平均亮度，0..255。 */
    val meanLuma: Double,
    /** 亮度 <= [ImageQualityAnalyzer.DARK_LEVEL] 的像素占比。 */
    val darkFraction: Double,
    /** 亮度 >= [ImageQualityAnalyzer.BRIGHT_LEVEL] 的像素占比。 */
    val brightFraction: Double,
    /** 被分析图像（裁剪后）的短边像素数。 */
    val shortSidePx: Int,
) {
    val issues: List<QualityIssue>
        get() = buildList {
            if (shortSidePx < ImageQualityAnalyzer.MIN_SHORT_SIDE_PX) add(QualityIssue.LOW_RESOLUTION)
            if (sharpness < ImageQualityAnalyzer.BLUR_VARIANCE) add(QualityIssue.BLURRY)
            if (meanLuma < ImageQualityAnalyzer.DARK_MEAN || darkFraction > ImageQualityAnalyzer.DARK_FRACTION_LIMIT) {
                add(QualityIssue.TOO_DARK)
            }
            if (meanLuma > ImageQualityAnalyzer.BRIGHT_MEAN || brightFraction > ImageQualityAnalyzer.BRIGHT_FRACTION_LIMIT) {
                add(QualityIssue.TOO_BRIGHT)
            }
        }

    val isGood: Boolean get() = issues.isEmpty()
}

/**
 * 纯数学的清晰度与曝光判断，输入是已经缩放好的灰度数组（0..255），不依赖 Android。
 *
 * 在 Android 侧由 [QuestionImagePreparer] 把 Bitmap 缩放到 [ANALYSIS_MAX_SIDE] 再取灰度，
 * 这样不同分辨率的照片得到的方差可以比较。
 */
object ImageQualityAnalyzer {

    const val ANALYSIS_MAX_SIDE = 512

    /** 题目小字可读的最小短边（裁剪之后）。 */
    const val MIN_SHORT_SIDE_PX = 600

    /** 拉普拉斯方差低于此值判为模糊（在 512 分析尺度下）。 */
    const val BLUR_VARIANCE = 60.0

    const val DARK_LEVEL = 16
    const val BRIGHT_LEVEL = 240
    const val DARK_MEAN = 60.0
    const val BRIGHT_MEAN = 215.0
    const val DARK_FRACTION_LIMIT = 0.5
    const val BRIGHT_FRACTION_LIMIT = 0.5

    /**
     * @param gray 行优先的灰度数组，长度必须为 width*height
     * @param sourceShortSide 原图（裁剪后）在分辨率上的短边，用来判断清晰度之外的分辨率问题
     */
    fun analyze(gray: IntArray, width: Int, height: Int, sourceShortSide: Int): QualityReport {
        require(width > 0 && height > 0 && gray.size == width * height) { "gray buffer does not match size" }
        val n = gray.size.toDouble()
        var sum = 0.0
        var dark = 0
        var bright = 0
        for (v in gray) {
            sum += v
            if (v <= DARK_LEVEL) dark++
            if (v >= BRIGHT_LEVEL) bright++
        }
        return QualityReport(
            sharpness = laplacianVariance(gray, width, height),
            meanLuma = sum / n,
            darkFraction = dark / n,
            brightFraction = bright / n,
            shortSidePx = sourceShortSide,
        )
    }

    /** 3x3 拉普拉斯核 [0 1 0; 1 -4 1; 0 1 0] 响应的方差，只计算内部像素。 */
    fun laplacianVariance(gray: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3) return 0.0
        var count = 0
        var sum = 0.0
        var sumSq = 0.0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val i = row + x
                val lap = gray[i - width] + gray[i + width] + gray[i - 1] + gray[i + 1] - 4 * gray[i]
                sum += lap
                sumSq += lap.toDouble() * lap
                count++
            }
        }
        if (count == 0) return 0.0
        val mean = sum / count
        return sumSq / count - mean * mean
    }
}

/** 每种质量问题对应给用户的一句可执行建议。 */
fun QualityIssue.suggestion(): String = when (this) {
    QualityIssue.LOW_RESOLUTION -> "分辨率偏低，题目小字可能识别不准，建议靠近题目重拍"
    QualityIssue.BLURRY -> "照片有些模糊，建议稳住手机、对准题目重拍"
    QualityIssue.TOO_DARK -> "光线偏暗，建议开灯或换到明亮的地方"
    QualityIssue.TOO_BRIGHT -> "局部过曝（反光或闪光灯），建议避开光源"
}
