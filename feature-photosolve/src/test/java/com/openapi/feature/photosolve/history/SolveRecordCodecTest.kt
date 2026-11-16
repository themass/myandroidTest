package com.openapi.feature.photosolve.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolveRecordCodecTest {

    @Test
    fun stepsRoundTripIncludingSeparatorLikeContent() {
        val steps = listOf("第一步 \$x=1\$", "第二步：\n换行\n也在里面", "第三步 \\frac{1}{2}")
        val encoded = SolveRecordCodec.encodeSteps(steps)
        assertEquals(steps, SolveRecordCodec.decodeSteps(encoded))
    }

    @Test
    fun emptyStepsRoundTrip() {
        assertEquals(emptyList<String>(), SolveRecordCodec.decodeSteps(SolveRecordCodec.encodeSteps(emptyList())))
        assertEquals(emptyList<String>(), SolveRecordCodec.decodeSteps(null))
        assertEquals(emptyList<String>(), SolveRecordCodec.decodeSteps(""))
    }

    @Test
    fun defaultTitleUsesPlainTextOfTheQuestion() {
        assertEquals("解方程 x²-5x+6=0", SolveRecordCodec.defaultTitle("解方程 \$x^2-5x+6=0\$"))
    }

    @Test
    fun longQuestionIsTruncatedWithEllipsis() {
        val long = "这是一道很长的题目".repeat(10)
        val title = SolveRecordCodec.defaultTitle(long)
        assertTrue(title.length <= SolveRecordCodec.TITLE_MAX + 1)
        assertTrue(title.endsWith("…"))
    }

    @Test
    fun blankQuestionFallsBackToUntitled() {
        assertEquals(SolveRecordCodec.UNTITLED, SolveRecordCodec.defaultTitle(""))
        assertEquals(SolveRecordCodec.UNTITLED, SolveRecordCodec.defaultTitle("   "))
        assertEquals(SolveRecordCodec.UNTITLED, SolveRecordCodec.defaultTitle(null))
        assertEquals(SolveRecordCodec.UNTITLED, SolveRecordCodec.defaultTitle("\$\\\\\$"))
    }

    @Test
    fun titleDoesNotContainLatexSource() {
        val title = SolveRecordCodec.defaultTitle("求 \$\\frac{1}{2}+\\sqrt{3}\$ 的值")
        assertFalse(title, title.contains("\\"))
        assertTrue(title, title.contains("1/2"))
        assertTrue(title, title.contains("√3"))
    }
}
