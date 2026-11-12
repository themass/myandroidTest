package com.openapi.imaging.quality


import com.openapi.imaging.quality.ImageQualityAnalyzer
import com.openapi.imaging.quality.QualityIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageQualityAnalyzerTest {

    private val size = 64

    private fun flat(value: Int) = IntArray(size * size) { value }

    /** 1 像素宽的黑白棋盘格：边缘最锐利。 */
    private fun checkerboard(low: Int, high: Int) = IntArray(size * size) { i ->
        val x = i % size
        val y = i / size
        if ((x + y) % 2 == 0) low else high
    }

    /** 缓慢变化的渐变：几乎没有高频边缘。 */
    private fun gradient() = IntArray(size * size) { i -> (i % size) * 255 / (size - 1) }

    /** 默认来源尺寸 2000×1500：分辨率充足，让测试只关注自己要验证的那一项。 */
    private fun report(gray: IntArray, sourceW: Int = 2000, sourceH: Int = 1500) =
        ImageQualityAnalyzer.analyze(gray, size, size, sourceW, sourceH)

    @Test
    fun flatImageIsBlurryWithZeroSharpness() {
        val r = report(flat(128))
        assertEquals(0.0, r.sharpness, 1e-9)
        assertTrue(QualityIssue.BLURRY in r.issues)
    }

    @Test
    fun highContrastEdgesAreSharp() {
        val r = report(checkerboard(20, 230))
        assertTrue(r.sharpness > ImageQualityAnalyzer.BLUR_VARIANCE)
        assertFalse(QualityIssue.BLURRY in r.issues)
    }

    @Test
    fun smoothGradientHasLowSharpness() {
        val r = report(gradient())
        assertTrue(r.sharpness < ImageQualityAnalyzer.BLUR_VARIANCE)
    }

    @Test
    fun blackImageIsTooDark() {
        val r = report(flat(0))
        assertTrue(QualityIssue.TOO_DARK in r.issues)
        assertFalse(QualityIssue.TOO_BRIGHT in r.issues)
    }

    @Test
    fun whiteImageIsTooBright() {
        val r = report(flat(255))
        assertTrue(QualityIssue.TOO_BRIGHT in r.issues)
        assertFalse(QualityIssue.TOO_DARK in r.issues)
    }

    @Test
    fun smallImageIsLowResolution() {
        // 640×480：长边不足 800
        val r = report(checkerboard(20, 230), sourceW = 640, sourceH = 480)
        assertTrue(QualityIssue.LOW_RESOLUTION in r.issues)
    }

    @Test
    fun wideShortStripOfAQuestionIsNotLowResolution() {
        // 回归：1800×500 的单题长条（短边 < 旧阈值 600）清晰度没问题，不该被打断
        val r = report(checkerboard(20, 230), sourceW = 1800, sourceH = 500)
        assertFalse(r.issues.toString(), QualityIssue.LOW_RESOLUTION in r.issues)
    }

    @Test
    fun tallNarrowColumnIsNotLowResolutionEither() {
        val r = report(checkerboard(20, 230), sourceW = 500, sourceH = 1800)
        assertFalse(QualityIssue.LOW_RESOLUTION in r.issues)
    }

    @Test
    fun longButExtremelyThinStripIsLowResolution() {
        // 900×40：长边够，但总共只有 36000 像素，没有足够信息
        val r = report(checkerboard(20, 230), sourceW = 900, sourceH = 40)
        assertTrue(QualityIssue.LOW_RESOLUTION in r.issues)
    }

    @Test
    fun orientationDoesNotMatter() {
        val landscape = report(checkerboard(20, 230), sourceW = 700, sourceH = 400)
        val portrait = report(checkerboard(20, 230), sourceW = 400, sourceH = 700)
        assertEquals(landscape.issues, portrait.issues)
    }

    @Test
    fun sharpWellExposedLargeImageHasNoIssues() {
        val r = report(checkerboard(60, 200), sourceW = 1600, sourceH = 1200)
        assertTrue(r.issues.toString(), r.isGood)
    }

    @Test
    fun meanAndFractionsAreComputed() {
        val half = IntArray(size * size) { i -> if (i < size * size / 2) 0 else 255 }
        val r = report(half)
        assertEquals(127.5, r.meanLuma, 1e-9)
        assertEquals(0.5, r.darkFraction, 1e-9)
        assertEquals(0.5, r.brightFraction, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedBufferIsRejected() {
        ImageQualityAnalyzer.analyze(IntArray(10), size, size, 100, 100)
    }
}
