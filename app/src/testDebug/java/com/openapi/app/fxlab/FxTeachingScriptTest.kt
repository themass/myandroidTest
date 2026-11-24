package com.openapi.app.fxlab

import org.junit.Assert.assertEquals
import org.junit.Test

class FxTeachingScriptTest {

    @Test
    fun scriptHasFourStepsWithFixedCuesAndAnchors() {
        assertEquals(
            listOf("connected", "pat_head", "wave", "pat_head"),
            FxTeachingScript.steps.map { it.cue },
        )
        assertEquals(
            listOf("FULL", "HEAD", "HAND", "HEAD"),
            FxTeachingScript.steps.map { it.anchor },
        )
    }

    @Test
    fun eachStepCaptionNamesItsTeachingBeat() {
        assertEquals(
            listOf("讲解开始", "指给你看", "跟着做", "小结"),
            FxTeachingScript.steps.map { it.caption },
        )
    }

    @Test
    fun nextClampsAtLastStep() {
        assertEquals(3, FxTeachingScript.next(2))
        assertEquals(3, FxTeachingScript.next(3))
    }

    @Test
    fun prevClampsAtFirstStep() {
        assertEquals(0, FxTeachingScript.prev(1))
        assertEquals(0, FxTeachingScript.prev(0))
    }

    @Test
    fun nextAndPrevFromEveryIndexStayInRange() {
        for (i in 0..3) {
            assertTrueInRange(FxTeachingScript.next(i))
            assertTrueInRange(FxTeachingScript.prev(i))
        }
    }

    private fun assertTrueInRange(index: Int) {
        assertEquals(
            true,
            index in 0..FxTeachingScript.steps.lastIndex,
        )
    }
}
