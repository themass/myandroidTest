package com.openapi.feature.photosolve.data

import com.openapi.feature.photosolve.presentation.PhotoSolveError
import com.openapi.feature.photosolve.presentation.PhotoSolveException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SolveResultMapperTest {

    private fun question(
        text: String = "",
        latex: String = "",
        thinking: String = "",
        steps: List<PhotoSolveApiResponse.Step> = emptyList(),
        answer: String = "",
        answerLatex: String = "",
    ) = PhotoSolveApiResponse.Question(
        index = 1, text = text, latex = latex, thinking = thinking,
        steps = steps, finalAnswer = answer, finalAnswerLatex = answerLatex,
    )

    @Test
    fun mapsBasicFields() {
        val api = PhotoSolveApiResponse(
            requestId = "r1", providerUsed = "ep-x", subject = "math", questionFound = true,
            questions = listOf(question(text = "解 \$x^2=4\$", thinking = "直接开方", answer = "\$x=\\pm 2\$")),
        )
        val r = SolveResultMapper.map(api)
        assertEquals("解 \$x^2=4\$", r.recognizedQuestion)
        assertEquals("直接开方", r.thinking)
        assertEquals("math", r.subject)
        assertEquals("r1", r.requestId)
        assertEquals("\$x=\\pm 2\$", r.finalAnswer)
    }

    @Test
    fun notAQuestionThrows40002() {
        val api = PhotoSolveApiResponse(questionFound = false, questions = emptyList())
        try {
            SolveResultMapper.map(api)
            fail("expected PhotoSolveException")
        } catch (e: PhotoSolveException) {
            assertEquals(PhotoSolveError.NOT_A_QUESTION, e.code)
        }
    }

    @Test
    fun emptyQuestionsAlsoThrows40002() {
        try {
            SolveResultMapper.map(PhotoSolveApiResponse(questionFound = true, questions = emptyList()))
            fail("expected PhotoSolveException")
        } catch (e: PhotoSolveException) {
            assertEquals(PhotoSolveError.NOT_A_QUESTION, e.code)
        }
    }

    // ---- 旧字段 latex 并回文本 ----

    @Test
    fun latexFieldIsAppendedWhenTextDoesNotContainIt() {
        val api = PhotoSolveApiResponse(questions = listOf(question(text = "求值", latex = "\\frac{1}{2}")))
        val r = SolveResultMapper.map(api)
        assertTrue(r.recognizedQuestion, r.recognizedQuestion.contains("求值"))
        assertTrue(r.recognizedQuestion, r.recognizedQuestion.contains("\\frac{1}{2}"))
        assertTrue(r.recognizedQuestion, r.recognizedQuestion.contains("\$\$"))
    }

    @Test
    fun latexFieldIsNotDuplicatedWhenTextAlreadyHasIt() {
        val api = PhotoSolveApiResponse(questions = listOf(question(text = "求 \$\$\\frac{1}{2}\$\$ 的值", latex = "\\frac{1}{2}")))
        val r = SolveResultMapper.map(api)
        assertEquals(1, Regex("\\\\frac\\{1\\}\\{2\\}").findAll(r.recognizedQuestion).count())
    }

    @Test
    fun whitespaceDifferencesDoNotCountAsDifferent() {
        val api = PhotoSolveApiResponse(questions = listOf(question(text = "求 \$ \\frac{1}{2} \$ 的值", latex = "\\frac{1}{2}")))
        val r = SolveResultMapper.map(api)
        assertEquals(1, Regex("frac").findAll(r.recognizedQuestion).count())
    }

    @Test
    fun emptyLatexLeavesTextAlone() {
        assertEquals("求值", SolveResultMapper.merge("求值", ""))
        assertEquals("求值", SolveResultMapper.merge("求值", "   "))
    }

    @Test
    fun emptyTextBecomesTheLatexBlock() {
        assertEquals("\$\$\n\\frac{1}{2}\n\$\$", SolveResultMapper.merge("", "\\frac{1}{2}"))
        assertEquals("\$x\$", SolveResultMapper.merge("", "\$x\$"))
    }

    @Test
    fun stepsKeepOrderAndDropBlanks() {
        val api = PhotoSolveApiResponse(
            questions = listOf(
                question(
                    text = "q",
                    steps = listOf(
                        PhotoSolveApiResponse.Step(1, "第一步", ""),
                        PhotoSolveApiResponse.Step(2, "  ", ""),
                        PhotoSolveApiResponse.Step(3, "第二步", "x=1"),
                    ),
                ),
            ),
        )
        val r = SolveResultMapper.map(api)
        assertEquals(2, r.steps.size)
        assertEquals("第一步", r.steps[0])
        assertTrue(r.steps[1].contains("第二步"))
        assertTrue(r.steps[1].contains("x=1"))
    }

    @Test
    fun questionCountReflectsAllQuestions() {
        val api = PhotoSolveApiResponse(
            questions = listOf(question(text = "a"), question(text = "b"), question(text = "c")),
        )
        val r = SolveResultMapper.map(api)
        assertEquals(3, r.questionCount)
        assertEquals("a", r.recognizedQuestion)
    }

    @Test
    fun missingThinkingBecomesEmpty() {
        val r = SolveResultMapper.map(PhotoSolveApiResponse(questions = listOf(question(text = "q"))))
        assertEquals("", r.thinking)
        assertNull(r.latex)
    }
}
