package com.openapi.mediaview.edit

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PhotoEdits] / [EditCoords] / [EditHitTest] 都是纯数据与纯函数，不依赖 Android，是普通 JVM 单测。
 */
class PhotoEditsTest {

    private fun edits(w: Int = 1000, h: Int = 2000) = PhotoEdits.of(w, h)

    private fun stroke(x: Float = 0.1f, y: Float = 0.1f, width: Float = 0.01f) = EditOp.Stroke(
        points = listOf(Offset(x, y), Offset(x + 0.1f, y)),
        colorArgb = 0xFFFF0000,
        widthRatio = width,
    )

    private fun text(x: Float = 0.2f, y: Float = 0.2f, value: String = "你好") = EditOp.Text(
        value = value, x = x, y = y, colorArgb = 0xFF000000, sizeRatio = 0.05f,
    )

    // ---- 旋转 / 镜像 ----

    @Test
    fun rotateIsAccumulatedIntoOneEntry() {
        val e = edits().rotateClockwise().rotateClockwise()
        assertEquals(1, e.ops.count { it is EditOp.Rotate })
        assertEquals(2, e.quarterTurns)
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
        assertFalse(edits().flipHorizontal().flipHorizontal().isFlipped)
    }

    @Test
    fun flipOnceKeepsEntry() {
        assertTrue(edits().flipHorizontal().isFlipped)
    }

    // ---- 撤销 / 重做 ----

    @Test
    fun undoAndRedoRoundTrip() {
        val e = edits().push(stroke())
        assertTrue(e.canUndo)
        val undone = e.undo()
        assertFalse(undone.canUndo)
        assertTrue(undone.canRedo)
        assertEquals(0, undone.ops.size)
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

    @Test
    fun resetCanBeUndoneBackToEverything() {
        val e = edits().push(stroke()).push(text()).reset()
        assertEquals(0, e.ops.size)
        assertEquals(2, e.undo().ops.size) // 一步撤销回到还原之前
    }

    @Test
    fun adjustDefaultsToIdentity() {
        val adj = edits().adjust
        assertTrue(adj.isIdentity)
        assertEquals(EditOp.Adjust(0, 0, 0), adj)
    }

    // ---- 滤镜 ----

    @Test
    fun filterIsNullWhenNoFilterOp() {
        assertNull(edits().filter)
        assertNull(edits().push(stroke()).push(text()).filter)
        assertNull(edits().push(EditOp.Adjust(10, 0, 0)).rotateClockwise().filter)
    }

    @Test
    fun lastFilterWins() {
        val e = edits().push(EditOp.Filter("creamy", 40)).push(stroke()).push(EditOp.Filter("retro", 80))
        assertEquals(EditOp.Filter("retro", 80), e.filter)
    }

    @Test
    fun removingTheVisibleFilterFallsBackToTheEarlierOne() {
        val e = edits().push(EditOp.Filter("creamy", 40)).push(EditOp.Filter("retro", 80))
        assertEquals(EditOp.Filter("creamy", 40), e.removeAt(1).filter)
        assertNull(e.removeAt(1).removeAt(0).filter)
    }

    @Test
    fun filterDoesNotDisturbTheAdjustAccessor() {
        var e = edits().push(EditOp.Adjust(10, -20, 30)).push(EditOp.Filter("creamy", 60))
        assertEquals(EditOp.Adjust(10, -20, 30), e.adjust)
        e = e.push(EditOp.Adjust(0, 0, 0))
        assertTrue("最后一条 Adjust 生效", e.adjust.isIdentity)
        assertEquals(EditOp.Filter("creamy", 60), e.filter)
    }

    // ---- 滤镜面板的滑杆语义（Task 6）----

    /**
     * 面板上拖强度滑杆：中间过程只换预览（[PhotoEdits.withOpsUnrecorded]），
     * 撤销栈必须一动不动——否则拖一次滑杆留下几十步「撤销」，用户按十次才回到拖动之前。
     */
    @Test
    fun draggingFilterIntensityAddsNoUndoStep() {
        val base = edits().setFilter("creamy", 60)
        var live = base
        listOf(20, 45, 80, 100).forEach { v ->
            val i = live.ops.indexOfLast { it is EditOp.Filter }
            live = base.withOpsUnrecorded(live.ops.toMutableList().also { it[i] = EditOp.Filter("creamy", v) })
        }
        assertEquals("拖动途中不产生历史", base.undoStack.size, live.undoStack.size)
        assertEquals(listOf<EditOp>(EditOp.Filter("creamy", 60)), base.ops)
        assertEquals(EditOp.Filter("creamy", 100), live.filter)
    }

    /** 松手那一下才是「一步」：撤销回到拖动之前的强度，而不是把滤镜整条删掉。 */
    @Test
    fun committingFilterIntensityIsExactlyOneUndoStep() {
        val base = edits().push(stroke()).setFilter("creamy", 60)
        // 拖动过程中的一串预览（无历史）
        val live = base.withOpsUnrecorded(
            base.ops.toMutableList().also { it[1] = EditOp.Filter("creamy", 85) },
        )
        // 松手：以拖动前的 base 为底，replaceAt 一次
        val committed = base.setFilter("creamy", 85)
        assertEquals("只记一步", base.undoStack.size + 1, committed.undoStack.size)
        assertEquals(EditOp.Filter("creamy", 85), committed.filter)
        assertEquals(EditOp.Filter("creamy", 60), committed.undo().filter)
        // 与 withOps(live.ops) 等价（同一份结果，都是恰一步）
        assertEquals(committed.ops, base.withOps(live.ops).ops)
        assertEquals(base.withOps(live.ops).undoStack.size, committed.undoStack.size)
    }

    /** 面板是互斥的：换滤镜＝替换，全列表至多一条 Filter（渲染取最后一条，但面板不该攒）。 */
    @Test
    fun setFilterNeverStacksMoreThanOneFilterOp() {
        val s = stroke()
        val t = text()
        val e = edits()
            .setFilter("creamy", 100)
            .push(s)
            .setFilter("retro", 40)
            .push(t)
            .setFilter("mono", 70)
        assertEquals("全列表至多一条 Filter", 1, e.ops.count { it is EditOp.Filter })
        assertEquals(EditOp.Filter("mono", 70), e.filter)
        // 原位替换：Filter 停在它第一次落下的位置（index 0），标注的相对顺序不受扰动。
        // 位置本身无实现含义（渲染按类型挑最后一条），这里钉的是「不会攒、也不会把标注挤走」。
        assertEquals(listOf<EditOp>(EditOp.Filter("mono", 70), s, t), e.ops)
    }

    /** 再点已选中的那一格 = 回到无滤镜，且可以撤销回来。 */
    @Test
    fun setFilterNullRemovesTheFilterOpAndIsUndoable() {
        val f = EditOp.Filter("moody", 55)
        val s = stroke()
        val e = edits().push(s).setFilter("moody", 55)
        assertEquals(2, e.ops.size)
        val cleared = e.setFilter(null)
        assertNull(cleared.filter)
        assertEquals(listOf<EditOp>(s), cleared.ops)
        assertEquals(f, cleared.undo().filter)
        // 本来就没有滤镜时，移除是空操作：不产生一步历史
        val noFilter = edits().push(s)
        assertEquals(noFilter, noFilter.setFilter(null))
    }

    /** 重复点同一格同一强度不该堆历史（[PhotoEdits.commit] 的内容相同即空操作）。 */
    @Test
    fun setFilterWithIdenticalIdAndIntensityDoesNotCreateHistory() {
        val e = edits().setFilter("airy", 100)
        assertEquals(e.undoStack.size, e.setFilter("airy", 100).undoStack.size)
        assertEquals(1, e.ops.count { it is EditOp.Filter })
    }

    /** 换滤镜不该动到旋转（Rotate 始终在头部、只有一条）。 */
    @Test
    fun setFilterLeavesRotationAndFlipAloneAtTheFront() {
        val e = edits().rotateClockwise().flipHorizontal().setFilter("coolwhite", 30).setFilter("retro", 90)
        assertEquals(1, e.ops.count { it is EditOp.Rotate })
        assertEquals(1, e.quarterTurns)
        assertTrue(e.isFlipped)
        assertEquals(1, e.ops.count { it is EditOp.Filter })
        assertEquals(EditOp.Filter("retro", 90), e.filter)
    }

    // ---- 选中框用的「无标注会话」 ----

    /**
     * 选中框只关心画幅（旋转/镜像/裁剪），标注要丢掉；
     * 但 **Crop 必须留着**——滤掉它，裁剪后的选中框就会按整幅画面算，框跑偏（Task 3 挂账）。
     */
    @Test
    fun editsWithoutOpsDropsAnnotationsButKeepsCropRotationAndFlip() {
        val crop = EditOp.Crop(0.1f, 0.1f, 0.9f, 0.9f)
        val e = edits()
            .rotateClockwise()
            .flipHorizontal()
            .push(stroke())
            .setCrop(crop)
            .push(text())
            .push(EditOp.Mosaic(listOf(Offset(0.3f, 0.3f), Offset(0.6f, 0.4f)), widthRatio = 0.06f))
            .setFilter("creamy", 60)
            .push(EditOp.Adjust(10, 0, 0))

        val stripped = editsWithoutOps(e)
        assertEquals(crop, stripped.crop)
        assertEquals(1, stripped.quarterTurns)
        assertTrue(stripped.isFlipped)
        assertNull("滤镜不参与选中框几何", stripped.filter)
        assertTrue("调整不参与选中框几何", stripped.adjust.isIdentity)
        // 保留项按原有相对顺序：rotateClockwise/flipHorizontal 会把自己的记录提到头部，
        // 所以这里先 Flip 再 Rotate，Crop 落在它当初的位置上。顺序对选中框无影响
        // （几何只读 quarterTurns/isFlipped/crop），断言它只是把「保留了哪三条」钉死。
        assertEquals(
            "只留 Rotate/Flip/Crop",
            listOf<EditOp>(EditOp.FlipHorizontal, EditOp.Rotate(1), crop),
            stripped.ops,
        )
        // 画幅口径不变：裁剪后的显示尺寸跟着走
        assertEquals(e.displayWidth, stripped.displayWidth)
        assertEquals(e.displayHeight, stripped.displayHeight)
    }

    /** 没有裁剪时行为与扩之前逐点相同（回归保护）。 */
    @Test
    fun editsWithoutOpsWithoutCropKeepsOnlyRotateAndFlip() {
        val e = edits().rotateClockwise().push(stroke()).push(text())
        assertEquals(
            listOf<EditOp>(EditOp.Rotate(1)),
            editsWithoutOps(e).ops,
        )
    }

    // ---- 选中后删除 / 修改 / 拖动 ----

    @Test
    fun removeAtDeletesThatAnnotationOnly() {
        val s1 = stroke(0.1f)
        val t = text()
        val s2 = stroke(0.5f)
        val e = edits().push(s1).push(t).push(s2).removeAt(1)
        assertEquals(listOf<EditOp>(s1, s2), e.ops)
    }

    @Test
    fun removeAtCanBeUndone() {
        val t = text()
        val e = edits().push(stroke()).push(t).removeAt(1)
        assertEquals(1, e.ops.size)
        assertEquals(t, e.undo().ops[1])
    }

    @Test
    fun removeAtOutOfRangeIsNoop() {
        val e = edits().push(stroke())
        assertEquals(e, e.removeAt(5))
        assertEquals(e, e.removeAt(-1))
    }

    @Test
    fun dragTextMovesItAndUndoRestoresOriginalPosition() {
        val t = text(x = 0.2f, y = 0.3f)
        val e = edits().push(t)
        val moved = e.replaceAt(0, t.translated(0.1f, -0.05f))
        val m = moved.ops[0] as EditOp.Text
        assertEquals(0.3f, m.x, 1e-6f)
        assertEquals(0.25f, m.y, 1e-6f)
        // 撤销是回到拖动之前，而不是把整段文字删掉
        assertEquals(t, moved.undo().ops[0])
        assertEquals(1, moved.undo().ops.size)
    }

    @Test
    fun translateMovesEveryPointOfAStroke() {
        val moved = stroke(0.1f, 0.1f).translated(0.2f, 0.3f) as EditOp.Stroke
        assertEquals(0.3f, moved.points[0].x, 1e-6f)
        assertEquals(0.4f, moved.points[0].y, 1e-6f)
        assertEquals(0.4f, moved.points[1].x, 1e-6f)
    }

    @Test
    fun replaceAtIdenticalContentDoesNotCreateHistory() {
        val t = text()
        val e = edits().push(t)
        val same = e.replaceAt(0, t)
        assertEquals(e.undoStack.size, same.undoStack.size)
    }

    @Test
    fun unrecordedUpdatesKeepOneUndoStepWhenCommitted() {
        val base = edits().push(stroke())
        // 模拟滑杆拖动：多次实时预览，不记历史
        var live = base
        listOf(0.012f, 0.02f, 0.03f).forEach { w ->
            live = base.withOpsUnrecorded(listOf((base.ops[0] as EditOp.Stroke).copy(widthRatio = w)))
        }
        assertEquals(base.undoStack.size, live.undoStack.size)
        // 松手：一次提交，撤销一次回到拖动之前
        val committed = base.withOps(live.ops)
        assertEquals(0.03f, (committed.ops[0] as EditOp.Stroke).widthRatio, 1e-6f)
        assertEquals(0.01f, (committed.undo().ops[0] as EditOp.Stroke).widthRatio, 1e-6f)
    }

    // ---- 裁剪 ----

    @Test
    fun cropIsNullWhenNoCropOp() {
        assertNull(edits().crop)
        assertNull(edits().push(stroke()).rotateClockwise().crop)
    }

    @Test
    fun setCropNeverStacksMoreThanOneCropOp() {
        val e = edits()
            .setCrop(EditOp.Crop(0.1f, 0.1f, 0.9f, 0.9f))
            .push(stroke())
            .setCrop(EditOp.Crop(0f, 0.2f, 0.5f, 0.8f))
            .push(text())
            .setCrop(EditOp.Crop(0.3f, 0f, 1f, 1f))
        assertEquals("全列表至多一条 Crop", 1, e.ops.count { it is EditOp.Crop })
        assertEquals(EditOp.Crop(0.3f, 0f, 1f, 1f), e.crop)
        assertEquals(3, e.ops.size) // 3 次 setCrop 只占 1 条裁剪记录 + 2 条标注
    }

    @Test
    fun replacingCropIsOneUndoStepBackToThePreviousWindow() {
        val first = EditOp.Crop(0.1f, 0.1f, 0.9f, 0.9f)
        val second = EditOp.Crop(0.25f, 0.25f, 0.75f, 0.75f)
        val e = edits().setCrop(first).setCrop(second)
        assertEquals(second, e.crop)
        assertEquals(first, e.undo().crop)
        assertEquals(second, e.undo().redo().crop)
    }

    @Test
    fun setCropNullRemovesTheCropOpAndIsUndoable() {
        val crop = EditOp.Crop(0.1f, 0.1f, 0.9f, 0.9f)
        val s = stroke()
        val e = edits().push(s).setCrop(crop)
        assertEquals(2, e.ops.size)
        val cleared = e.setCrop(null)
        assertNull(cleared.crop)
        assertEquals(listOf<EditOp>(s), cleared.ops)
        assertEquals(crop, cleared.undo().crop)
        // 没有 Crop 时移除是空操作：不产生一步历史
        val noCrop = edits().push(s)
        assertEquals(noCrop, noCrop.setCrop(null))
    }

    @Test
    fun setCropWithIdenticalWindowDoesNotCreateHistory() {
        val crop = EditOp.Crop(0.1f, 0.1f, 0.9f, 0.9f)
        val e = edits().setCrop(crop)
        assertEquals(e.undoStack.size, e.setCrop(crop).undoStack.size)
    }

    @Test
    fun displaySizeCountsCropWindow() {
        val crop = EditOp.Crop(0.1f, 0.1f, 0.9f, 0.9f)
        // 1000×2000，未旋转，窗口宽高 0.8 → 800×1600
        assertEquals(800, edits().setCrop(crop).displayWidth)
        assertEquals(1600, edits().setCrop(crop).displayHeight)
        // 顺时针 90°：先宽高互换（2000×1000）再乘窗口 → 1600×800
        val quarter = edits().rotateClockwise().setCrop(crop)
        assertEquals(1600, quarter.displayWidth)
        assertEquals(800, quarter.displayHeight)
        // 满幅窗口与不裁剪一致
        assertEquals(1000, edits().setCrop(EditOp.Crop(0f, 0f, 1f, 1f)).displayWidth)
        assertEquals(1000, edits().displayWidth)
    }

    @Test
    fun cropRejectsAnEmptyOrOutOfBoundsWindow() {
        assertThrows(IllegalArgumentException::class.java) { EditOp.Crop(0.5f, 0.1f, 0.5f, 0.9f) }
        assertThrows(IllegalArgumentException::class.java) { EditOp.Crop(0.1f, 0.1f, 1.2f, 0.9f) }
        assertThrows(IllegalArgumentException::class.java) { EditOp.Crop(-0.1f, 0f, 0.5f, 1f) }
    }

    // ---- 坐标映射 ----

    @Test
    fun displayAndSourceCoordinatesAreInverseForEveryRotationAndFlip() {
        val points = listOf(Offset(0.1f, 0.2f), Offset(0.9f, 0.3f), Offset(0.5f, 0.5f), Offset(0.25f, 0.8f))
        for (turns in 0..3) {
            for (flip in listOf(false, true)) {
                var e = edits()
                repeat(turns) { e = e.rotateClockwise() }
                if (flip) e = e.flipHorizontal()
                points.forEach { p ->
                    val back = EditCoords.toSourceNorm(EditCoords.toDisplayNorm(p, e), e)
                    assertEquals("turns=$turns flip=$flip x", p.x, back.x, 1e-5f)
                    assertEquals("turns=$turns flip=$flip y", p.y, back.y, 1e-5f)
                }
            }
        }
    }

    @Test
    fun rotationMovesTopLeftToTopRightOnClockwiseQuarterTurn() {
        val e = edits().rotateClockwise()
        val d = EditCoords.toDisplayNorm(0f, 0f, e)
        assertEquals(1f, d.x, 1e-6f) // 原图左上角 → 顺时针 90° 后在右上角
        assertEquals(0f, d.y, 1e-6f)
    }

    @Test
    fun flipMirrorsHorizontally() {
        val d = EditCoords.toDisplayNorm(0.2f, 0.3f, edits().flipHorizontal())
        assertEquals(0.8f, d.x, 1e-6f)
        assertEquals(0.3f, d.y, 1e-6f)
    }

    // ---- 点选 ----

    @Test
    fun tapOnStrokeSelectsIt() {
        val e = edits().push(stroke(0.1f, 0.2f)) // 水平线 (0.1,0.2)→(0.2,0.2)
        assertEquals(0, EditHitTest.findOpAt(e, Offset(0.15f, 0.2f)))
    }

    @Test
    fun tapFarFromAnythingSelectsNothing() {
        val e = edits().push(stroke(0.1f, 0.2f))
        assertNull(EditHitTest.findOpAt(e, Offset(0.8f, 0.8f)))
    }

    @Test
    fun tapJustOutsideWithinFingerToleranceStillSelects() {
        val e = edits().push(stroke(0.1f, 0.2f, width = 0.004f))
        // 线宽很细，但手指点在旁边一小段也要能选中
        assertNotNull(EditHitTest.findOpAt(e, Offset(0.15f, 0.2f + 0.01f)))
    }

    @Test
    fun overlappingAnnotationsSelectTheTopmost() {
        val e = edits().push(stroke(0.1f, 0.2f)).push(text(0.12f, 0.19f, "A"))
        assertEquals(1, EditHitTest.findOpAt(e, Offset(0.13f, 0.2f)))
    }

    @Test
    fun tapInsideTextBlockSelectsText() {
        val t = text(0.2f, 0.3f, "你好世界")
        val e = edits().push(t)
        val box = EditHitTest.selectionBounds(t, e)!!
        assertEquals(0, EditHitTest.findOpAt(e, Offset((box.left + box.right) / 2f, (box.top + box.bottom) / 2f)))
    }

    @Test
    fun mosaicStrokeCanBeSelectedAlongItsPath() {
        val m = EditOp.Mosaic(listOf(Offset(0.2f, 0.4f), Offset(0.6f, 0.4f)), widthRatio = 0.06f)
        val e = edits().push(m)
        assertEquals(0, EditHitTest.findOpAt(e, Offset(0.4f, 0.4f)))
        assertNull(EditHitTest.findOpAt(e, Offset(0.4f, 0.9f)))
    }

    @Test
    fun hitTestFollowsRotation() {
        // 原图上 y=0.2 的一条横线，顺时针转 90° 后在显示坐标里变成 x=0.8 的竖线
        val e = edits().push(stroke(0.1f, 0.2f)).rotateClockwise()
        assertNotNull(EditHitTest.findOpAt(e, EditCoords.toDisplayNorm(0.15f, 0.2f, e)))
        assertNull(EditHitTest.findOpAt(e, Offset(0.15f, 0.2f)))
    }

    @Test
    fun selectionBoundsEncloseAStroke() {
        val s = stroke(0.1f, 0.2f)
        val box = EditHitTest.selectionBounds(s, edits().push(s))!!
        assertTrue(box.left <= 0.1f && box.right >= 0.2f)
        assertTrue(box.top <= 0.2f && box.bottom >= 0.2f)
    }
}
