package com.openapi.imaging.quality

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
    /** 被分析图像（裁剪后）的长边像素数。 */
    val longSidePx: Int,
) {
    /** 总像素数。用 Long：长边 4096 × 短边 4096 已经逼近 Int 上限的一半，留足余量。 */
    val totalPixels: Long get() = shortSidePx.toLong() * longSidePx
    val issues: List<QualityIssue>
        get() = buildList {
            if (ImageQualityAnalyzer.isLowResolution(longSidePx, shortSidePx)) add(QualityIssue.LOW_RESOLUTION)
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

    /**
     * 裁剪之后，长边至少这么多像素，题目小字才读得出来。
     *
     * 为什么不再用「短边」：一道题裁出来常常是宽矮的长条（例如 1800×500，一两行字），
     * 短边只有 500 但字很清楚。旧规则 `短边 < 600` 会把这类图几乎每次都判成「分辨率偏低」，
     * 用户每次提交都被打断。字的大小取决于图的**长边**（整页宽度对应的像素）而不是短边。
     */
    const val MIN_LONG_SIDE_PX = 800

    /** 总像素下限：长边够但特别窄的一条（如 900×40）也没有足够信息。 */
    const val MIN_PIXELS = 250_000L

    /** 两项都只是经验值；任何一项不满足就提示，用户可以选择「仍然识别」。 */
    fun isLowResolution(longSidePx: Int, shortSidePx: Int): Boolean =
        longSidePx < MIN_LONG_SIDE_PX || longSidePx.toLong() * shortSidePx < MIN_PIXELS

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
     * @param sourceWidth 被评估图像（裁剪后、缩放分析之前）的真实宽，用来判断分辨率
     * @param sourceHeight 同上，高
     */
    fun analyze(gray: IntArray, width: Int, height: Int, sourceWidth: Int, sourceHeight: Int): QualityReport {
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
            shortSidePx = minOf(sourceWidth, sourceHeight),
            longSidePx = maxOf(sourceWidth, sourceHeight),
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
