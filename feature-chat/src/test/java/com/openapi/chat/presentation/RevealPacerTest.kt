package com.openapi.chat.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class RevealPacerTest {

    @Test
    fun smallBacklogRevealsOneCharacterPerTick() {
        assertEquals(1, RevealPacer.revealCount(0, ended = false))
        assertEquals(1, RevealPacer.revealCount(7, ended = false))
        assertEquals(1, RevealPacer.revealCount(8, ended = false))
    }

    @Test
    fun biggerBacklogRevealsFasterAndIsCapped() {
        assertEquals(10, RevealPacer.revealCount(80, ended = false))
        assertEquals(48, RevealPacer.revealCount(10_000, ended = false))
    }

    @Test
    fun endedStreamDrainsAtLeastAThirdOfTheBacklog() {
        // 积压 300：不结束时 37 字一拍；结束后至少 100 字一拍
        assertEquals(37, RevealPacer.revealCount(300, ended = false))
        assertEquals(100, RevealPacer.revealCount(300, ended = true))
        // 积压很少时结束与否一样
        assertEquals(1, RevealPacer.revealCount(2, ended = true))
    }

    @Test
    fun cutLeavesShortTextAlone() {
        assertEquals(3, RevealPacer.safeCut("abcdef", 3))
        assertEquals(6, RevealPacer.safeCut("abcdef", 99))
        assertEquals(0, RevealPacer.safeCut("", 5))
    }

    @Test
    fun cutNeverSplitsASurrogatePair() {
        val text = "a😀b" // a, 😀 (two UTF-16 units), b
        // 切在 emoji 中间（位置 2）时，向后多取一个单元，保留完整的 emoji
        assertEquals(3, RevealPacer.safeCut(text, 2))
        assertEquals(1, RevealPacer.safeCut(text, 1))
    }
}
