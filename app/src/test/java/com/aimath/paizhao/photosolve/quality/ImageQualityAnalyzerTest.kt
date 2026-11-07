package com.aimath.paizhao.photosolve.quality

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

    private fun report(gray: IntArray, shortSide: Int = 1000) =
        ImageQualityAnalyzer.analyze(gray, size, size, shortSide)

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
    fun shortSideBelowThresholdIsLowResolution() {
        val r = report(checkerboard(20, 230), shortSide = ImageQualityAnalyzer.MIN_SHORT_SIDE_PX - 1)
        assertTrue(QualityIssue.LOW_RESOLUTION in r.issues)
    }

    @Test
    fun sharpWellExposedLargeImageHasNoIssues() {
        val r = report(checkerboard(60, 200), shortSide = 1200)
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
        ImageQualityAnalyzer.analyze(IntArray(10), size, size, 100)
    }
}
