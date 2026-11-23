package com.openapi.feature.chat.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownStableTailTest {

    @Test
    fun singleParagraphIsAllTail() {
        val s = MarkdownStableTail.split("hello world")
        assertEquals("", s.stable)
        assertEquals("hello world", s.tail)
    }

    @Test
    fun twoParagraphsStableFirst() {
        val s = MarkdownStableTail.split("para1\n\npara2 growing")
        assertEquals("para1\n\n", s.stable)
        assertEquals("para2 growing", s.tail)
    }

    @Test
    fun streamingGrowthOnlyMovesTail() {
        val a = MarkdownStableTail.split("done\n\npart")
        val b = MarkdownStableTail.split("done\n\npartial longer")
        assertEquals(a.stable, b.stable)
        assertEquals("partial longer", b.tail)
    }
}
