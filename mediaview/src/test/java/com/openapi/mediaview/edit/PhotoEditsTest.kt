package com.openapi.mediaview.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PhotoEdits] 是纯数据，不依赖 Android，所以这些测试是普通 JVM 单测
 * （:mediaview 的 android-library.gradle 会为它配 JUnit；这里只用到了 java 侧类型）。
 */
class PhotoEditsTest {

    private fun edits(w: Int = 1000, h: Int = 2000) = PhotoEdits.of(w, h)

    @Test
    fun rotateIsAccumulatedIntoOneEntry() {
        val e = edits().rotateClockwise().rotateClockwise()
        assertEquals(1, e.ops.count { it is EditOp.Rotate })
        assertEquals(2, e.quarterTurns)
        // 旋转 180° 时宽高不互换
        assertEquals(1000, e.displayWidth)
        assertEquals(2000, e.displayHeight)
    }

    @Test
    fun oddQuarterTurnSwapsDisplaySize() {
        val e = edits().rotateClockwise()
        assertEquals(2000, e.displayWidth)
        assertEquals(1000, e.displayHeight)
    }

    @Test
    fun fourRotationsReturnToZero() {
        val e = edits().rotateClockwise().rotateClockwise().rotateClockwise().rotateClockwise()
        assertEquals(0, e.quarterTurns)
        assertEquals(1000, e.displayWidth)
    }

    @Test
    fun flipTwiceIsNoFlip() {
        val e = edits().flipHorizontal().flipHorizontal()
        assertFalse(e.ops.contains(EditOp.FlipHorizontal))
    }

    @Test
    fun flipOnceKeepsEntry() {
        val e = edits().flipHorizontal()
        assertTrue(e.ops.contains(EditOp.FlipHorizontal))
    }

    @Test
    fun undoAndRedoRoundTrip() {
        val e = edits().push(stroke())
        assertTrue(e.canUndo)
        val undone = e.undo()
        assertFalse(undone.canUndo)
        assertTrue(undone.canRedo)
        val redone = undone.redo()
        assertEquals(1, redone.ops.size)
        assertFalse(redone.canRedo)
    }

    @Test
    fun newEditClearsRedo() {
        val e = edits().push(stroke()).undo().push(stroke())
        assertFalse(e.canRedo)
        assertEquals(1, e.ops.size)
    }

    /**
     * 重做和撤销一样是**逐条**的：reset 把原来的两条操作放进重做栈（保持原顺序），
     * 因此要重做两次才完全恢复。这样「撤销一步 / 重做一步」在任何状态下行为一致。
     */
    @Test
    fun resetCanBeRedoneStepByStep() {
        val e = edits().push(stroke()).push(stroke()).reset()
        assertEquals(0, e.ops.size)
        assertEquals(2, e.redo.size)
        val once = e.redo()
        assertEquals(1, once.ops.size)
        assertEquals(1, once.redo.size)
        assertEquals(2, once.redo().ops.size)
    }

    @Test
    fun adjustDefaultsToIdentity() {
        val adj = edits().adjust
        assertTrue(adj.isIdentity)
        assertEquals(EditOp.Adjust(0, 0, 0), adj)
    }

    private fun stroke() = EditOp.Stroke(
        points = listOf(androidx.compose.ui.geometry.Offset(0.1f, 0.1f), androidx.compose.ui.geometry.Offset(0.2f, 0.2f)),
        colorArgb = 0xFFFF0000,
        widthRatio = 0.01f,
    )
}
