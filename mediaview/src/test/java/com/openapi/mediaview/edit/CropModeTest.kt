package com.openapi.mediaview.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

/**
 * 裁剪模式的**几何**钉子：把手命中、比例预设选框、把手拖拽的最小框夹取、
 * 以及「已有裁剪时再框选」的窗口合成。
 *
 * 这些都是纯函数（见 [CropMode.kt] 的 `CropGeometry`），不碰 Compose 的布局与手势，
 * 所以是普通 JVM 单测。手势层（pointerInput）只是把触摸事件转成对这些函数的调用，本身不测。
 */
class CropModeTest {

    private val slop = 40f

    // ---- 把手命中 ----

    @Test
    fun hitHandleHitsEachCornerWithinSlop() {
        val rect = Rect(50f, 30f, 350f, 180f)
        // 正好压在角上
        assertEquals(CropHandle.TopLeft, hitHandle(rect, Offset(rect.left, rect.top), slop))
        assertEquals(CropHandle.TopRight, hitHandle(rect, Offset(rect.right, rect.top), slop))
        assertEquals(CropHandle.BottomLeft, hitHandle(rect, Offset(rect.left, rect.bottom), slop))
        assertEquals(CropHandle.BottomRight, hitHandle(rect, Offset(rect.right, rect.bottom), slop))

        // slop 之内（含框外一侧：手指常常压在框角外面一点）
        assertEquals(CropHandle.TopLeft, hitHandle(rect, Offset(rect.left - slop / 2f, rect.top - slop / 2f), slop))
        assertEquals(CropHandle.TopRight, hitHandle(rect, Offset(rect.right + slop / 3f, rect.top - slop / 3f), slop))
        assertEquals(CropHandle.BottomLeft, hitHandle(rect, Offset(rect.left - slop / 3f, rect.bottom + slop / 3f), slop))
        assertEquals(CropHandle.BottomRight, hitHandle(rect, Offset(rect.right - slop / 2f, rect.bottom + slop / 2f), slop))
    }

    @Test
    fun hitHandleReturnsNullInsideTheFrameAndBeyondSlop() {
        val rect = Rect(50f, 30f, 350f, 180f)
        // 框内中心、框边中点（那是「移动整框」区，本任务不做）都不是把手
        assertNull(hitHandle(rect, Offset(rect.center.x, rect.center.y), slop))
        assertNull(hitHandle(rect, Offset(rect.center.x, rect.top), slop))
        assertNull(hitHandle(rect, Offset(rect.left, rect.center.y), slop))
        // 越出 slop
        assertNull(hitHandle(rect, Offset(rect.left - slop * 2f, rect.top - slop * 2f), slop))
        assertNull(hitHandle(rect, Offset(rect.right + slop * 1.5f, rect.bottom + slop * 1.5f), slop))
        // 完全在框外的空白处
        assertNull(hitHandle(rect, Offset(0f, 0f), slop))
    }

    @Test
    fun hitHandlePicksTheNearestCornerWhenBothAreWithinSlop() {
        // 极小的框：两个角落在彼此的 slop 里，取更近的那个
        val rect = Rect(100f, 100f, 110f, 105f)
        assertEquals(CropHandle.TopLeft, hitHandle(rect, Offset(99f, 99f), slop))
        assertEquals(CropHandle.BottomRight, hitHandle(rect, Offset(111f, 106f), slop))
    }

    // ---- 比例预设 ----

    @Test
    fun squareAspectCentersInsideALandscapeFrame() {
        assertRect(Rect(100f, 0f, 300f, 200f), CropGeometry.fitAspectRect(CropAspect.Square, 400f, 200f), 1e-3f)
    }

    @Test
    fun sixteenNineClampsTheHeightToTheFrame() {
        val r = CropGeometry.fitAspectRect(CropAspect.SixteenNine, 400f, 200f)
        assertEquals(200f, r.height, 1e-3f) // 高度钳到画幅
        assertEquals(200f * 16f / 9f, r.width, 1e-2f)
        assertTrue("宽不能超出画幅：${r.width}", r.width <= 400f + 1e-3f)
        assertEquals(r.left, 400f - r.right, 1e-3f) // 居中
    }

    @Test
    fun fourThreeFitsTheShorterSideOfAPortraitFrame() {
        val r = CropGeometry.fitAspectRect(CropAspect.FourThree, 300f, 600f)
        assertEquals(300f, r.width, 1e-3f)
        assertEquals(300f * 3f / 4f, r.height, 1e-2f)
        assertEquals(r.top, 600f - r.bottom, 1e-3f)
    }

    @Test
    fun freeAspectFillsTheWholeFrame() {
        assertRect(Rect(0f, 0f, 400f, 200f), CropGeometry.fitAspectRect(CropAspect.Free, 400f, 200f), 1e-3f)
    }

    @Test
    fun everyAspectStaysInsideTheFrame() {
        val frames = listOf(400f to 200f, 200f to 400f, 300f to 300f, 1080f to 720f)
        for ((w, h) in frames) for (a in CropAspect.values()) {
            val r = CropGeometry.fitAspectRect(a, w, h)
            assertTrue("$a @$w×$h 左越界 ${r.left}", r.left >= -1e-3f)
            assertTrue("$a @$w×$h 上越界 ${r.top}", r.top >= -1e-3f)
            assertTrue("$a @$w×$h 右越界 ${r.right}", r.right <= w + 1e-3f)
            assertTrue("$a @$w×$h 下越界 ${r.bottom}", r.bottom <= h + 1e-3f)
            assertTrue("$a @$w×$h 不能是空的", r.width > 0f && r.height > 0f)
            if (a.ratio != null) assertEquals(a.ratio!!, r.width / r.height, 1e-3f)
        }
    }

    @Test
    fun choosingAnAspectKeepsTheStateInSyncWithTheRect() {
        val state = CropEditorState(Rect(0f, 0f, 400f, 200f), CropAspect.Free)
        val next = state.withAspect(CropAspect.Square, Rect(0f, 0f, 400f, 200f))
        assertEquals(CropAspect.Square, next.aspect)
        assertRect(Rect(100f, 0f, 300f, 200f), next.rect, 1e-3f)
        // 原状态不变（不可变数据）
        assertEquals(CropAspect.Free, state.aspect)
        assertRect(Rect(0f, 0f, 400f, 200f), state.rect, 1e-3f)
    }

    // ---- 把手拖拽 ----

    @Test
    fun cornerDragNeverShrinksBelowOneEighthOfTheFrame() {
        val frame = Rect(0f, 0f, 400f, 200f)
        val minSide = CropGeometry.minCropSide(frame.width, frame.height)
        assertEquals(25f, minSide, 1e-4f) // 200/8
        val rect = Rect(50f, 30f, 350f, 180f)

        // 左上角一把拖过右下角：只能缩到最小框，且另一角不动
        val r = CropGeometry.resizeForHandle(rect, CropHandle.TopLeft, Offset(345f, 179f), frame, minSide, CropAspect.Free)
        assertEquals(25f, r.width, 1e-3f)
        assertEquals(25f, r.height, 1e-3f)
        assertEquals(350f, r.right, 1e-3f)
        assertEquals(180f, r.bottom, 1e-3f)

        // 右下角同理：左上角固定
        val r2 = CropGeometry.resizeForHandle(rect, CropHandle.BottomRight, Offset(-20f, -20f), frame, minSide, CropAspect.Free)
        assertEquals(50f, r2.left, 1e-3f)
        assertEquals(30f, r2.top, 1e-3f)
        assertEquals(25f, r2.width, 1e-3f)
        assertEquals(25f, r2.height, 1e-3f)
    }

    @Test
    fun cornerDragStaysInsideTheFrame() {
        val frame = Rect(0f, 0f, 400f, 200f)
        val minSide = CropGeometry.minCropSide(frame.width, frame.height)
        val rect = Rect(100f, 50f, 300f, 150f)
        val drags = listOf(
            CropHandle.TopLeft to Offset(-500f, -500f),
            CropHandle.TopRight to Offset(5000f, -500f),
            CropHandle.BottomLeft to Offset(-500f, 5000f),
            CropHandle.BottomRight to Offset(5000f, 5000f),
        )
        for ((handle, touch) in drags) {
            val r = CropGeometry.resizeForHandle(rect, handle, touch, frame, minSide, CropAspect.Free)
            assertTrue("$handle 越界 $r", r.left >= -1e-3f && r.top >= -1e-3f && r.right <= 400f + 1e-3f && r.bottom <= 200f + 1e-3f)
            assertTrue("$handle 最小框 $r", r.width >= minSide - 1e-3f && r.height >= minSide - 1e-3f)
        }
    }

    @Test
    fun lockedAspectKeepsTheRatioWhileDraggingACorner() {
        val frame = Rect(0f, 0f, 400f, 200f)
        val minSide = CropGeometry.minCropSide(frame.width, frame.height)
        val rect = Rect(20f, 10f, 380f, 190f)
        for (aspect in listOf(CropAspect.Square, CropAspect.FourThree, CropAspect.SixteenNine)) {
            for (handle in CropHandle.values()) {
                val r = CropGeometry.resizeForHandle(rect, handle, Offset(123f, 77f), frame, minSide, aspect)
                assertEquals("$aspect/$handle 比例", aspect.ratio!!, r.width / r.height, 1e-3f)
                assertTrue("$aspect/$handle 最小框 $r", min(r.width, r.height) >= minSide - 1e-2f)
                assertTrue("$aspect/$handle 越界 $r", r.left >= -1e-3f && r.top >= -1e-3f && r.right <= 400f + 1e-3f && r.bottom <= 200f + 1e-3f)
            }
        }
    }

    @Test
    fun lockedAspectStillKeepsTheMinimumFrameWhenTheAnchorIsNearAnEdge() {
        val frame = Rect(0f, 0f, 1000f, 300f)
        val minSide = CropGeometry.minCropSide(frame.width, frame.height) // 37.5
        // 锚点离上边只有 10px：可用的竖直空间比最小框还窄
        val rect = Rect(400f, 10f, 600f, 200f)
        val r = CropGeometry.resizeForHandle(rect, CropHandle.BottomRight, Offset(410f, 11f), frame, minSide, CropAspect.Square)
        assertTrue("短边必须守住最小框：$r", min(r.width, r.height) >= minSide - 1e-3f)
        assertEquals(CropAspect.Square.ratio!!, r.width / r.height, 1e-3f)
        assertTrue("竖直空间不够就横向让位越界：$r", r.top >= -1e-3f && r.bottom <= 300f + 1e-3f)
    }

    // ---- 选框 → Crop，以及与既有裁剪窗口的合成 ----

    @Test
    fun selectionInPixelsBecomesANormalizedCropOfTheViewport() {
        val norm = CropGeometry.normalizeSelection(Rect(100f, 0f, 300f, 200f), Rect(0f, 0f, 400f, 200f))
        assertEquals(0.25f, norm.left, 1e-4f)
        assertEquals(0f, norm.top, 1e-4f)
        assertEquals(0.75f, norm.right, 1e-4f)
        assertEquals(1f, norm.bottom, 1e-4f)
    }

    @Test
    fun composingWithAnExistingWindowMultipliesTheTwoWindows() {
        val window = Rect(0.2f, 0.1f, 0.8f, 0.9f) // 已有裁剪：全幅空间里的窗口
        val sel = Rect(0.1f, 0.2f, 0.6f, 0.7f)    // 视口内（= 旧裁剪帧内）的新框
        val composed = CropGeometry.composedCrop(window, sel)

        assertEquals(0.2f + 0.1f * 0.6f, composed.left, 1e-5f)
        assertEquals(0.1f + 0.2f * 0.8f, composed.top, 1e-5f)
        assertEquals(0.2f + 0.6f * 0.6f, composed.right, 1e-5f)
        assertEquals(0.1f + 0.7f * 0.8f, composed.bottom, 1e-5f)
    }

    @Test
    fun withoutAnExistingWindowTheSelectionIsTheCropItself() {
        val sel = Rect(0.1f, 0.2f, 0.6f, 0.7f)
        val composed = CropGeometry.composedCrop(Rect(0f, 0f, 1f, 1f), sel)
        assertEquals(0.1f, composed.left, 1e-5f)
        assertEquals(0.2f, composed.top, 1e-5f)
        assertEquals(0.6f, composed.right, 1e-5f)
        assertEquals(0.7f, composed.bottom, 1e-5f)
    }

    /**
     * 核心钉子：**两步框选 = 一个 Crop**。
     * 同一个像素，先按「旧裁剪 + 视口坐标」算源点，和按「合成后的裁剪 + 新框坐标」算源点，
     * 必须落在同一个原图位置——否则裁剪模式下看到的画面和确认之后的画面就会错位。
     */
    @Test
    fun twoSuccessiveCropsMapTheSamePixelToTheSameSourcePoint() {
        val windows = listOf(
            EditOp.Crop(0.2f, 0.1f, 0.8f, 0.9f),
            EditOp.Crop(0f, 0f, 0.5f, 0.4f),
            EditOp.Crop(0.55f, 0.3f, 1f, 1f),
        )
        val selections = listOf(
            Rect(0.15f, 0.25f, 0.85f, 0.75f),
            Rect(0f, 0f, 1f, 1f),
            Rect(0.4f, 0.05f, 0.6f, 0.35f),
        )
        val samples = listOf(
            Offset(0.05f, 0.05f), Offset(0.5f, 0.5f), Offset(0.93f, 0.12f), Offset(0.45f, 0.8f),
        )
        for (turns in 0..3) for (flipped in listOf(false, true)) for (w1 in windows) for (sel in selections) {
            val base = build(w1, turns, flipped)
            val composed = CropGeometry.composedCrop(EditCoords.cropWindow(base), sel)
            val after = base.setCrop(composed)

            // 全列表至多一条 Crop（一步 undo 覆盖旧裁剪的前提）
            assertEquals(1, after.ops.count { it is EditOp.Crop })
            assertEquals(composed, after.crop)

            for (n in samples) {
                // 新裁剪帧里的 n → 源点
                val viaNew = EditCoords.toSourceNorm(n, after)
                // 同一个像素在旧裁剪帧（= 裁剪模式视口）里的位置 → 源点
                val inViewport = Offset(sel.left + n.x * sel.width, sel.top + n.y * sel.height)
                val viaOld = EditCoords.toSourceNorm(inViewport, base)
                assertEquals("turns=$turns flip=$flipped $w1 sel=$sel n=$n x", viaOld.x, viaNew.x, 1e-4f)
                assertEquals("turns=$turns flip=$flipped $w1 sel=$sel n=$n y", viaOld.y, viaNew.y, 1e-4f)
            }

            // 一步撤销覆盖掉旧裁剪：撤销后回到第一个窗口，而不是「少一层窗口」的中间态。
            // 整屏框选合成出来就是原窗口本身，setCrop 按设计不产生历史（见 PhotoEdits.commit），
            // 所以那种情况跳过。
            if (composed != w1) {
                assertEquals("$w1 sel=$sel 撤销应回到旧窗口", w1, after.undo().crop)
            }
        }
    }

    @Test
    fun composedCropAlwaysStaysAValidWindow() {
        // 视口边缘的极细选框（浮点误差可能压出 left == right），必须仍能被 EditOp.Crop 接受
        val tiny = CropGeometry.composedCrop(Rect(0.5f, 0.5f, 1f, 1f), Rect(0.999999f, 0.999999f, 1f, 1f))
        assertNotNull(tiny)
        assertTrue(tiny.left < tiny.right && tiny.top < tiny.bottom)
        for (v in listOf(tiny.left, tiny.top, tiny.right, tiny.bottom)) assertTrue("越界 $v", v in 0f..1f)

        val full = CropGeometry.composedCrop(Rect(0.1f, 0.2f, 0.3f, 0.9f), Rect(0f, 0f, 1f, 1f))
        assertEquals(0.1f, full.left, 1e-6f)
        assertEquals(0.3f, full.right, 1e-6f)
        assertTrue(full.left < full.right && full.top < full.bottom)
    }

    // ---- 辅助 ----

    private fun build(crop: EditOp.Crop, turns: Int, flipped: Boolean): PhotoEdits {
        var e = PhotoEdits.of(1000, 2000)
        repeat(turns) { e = e.rotateClockwise() }
        if (flipped) e = e.flipHorizontal()
        return e.setCrop(crop)
    }

    private fun assertRect(expected: Rect, actual: Rect, tolerance: Float) {
        assertEquals(expected.left, actual.left, tolerance)
        assertEquals(expected.top, actual.top, tolerance)
        assertEquals(expected.right, actual.right, tolerance)
        assertEquals(expected.bottom, actual.bottom, tolerance)
    }
}
