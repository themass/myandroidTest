package com.openapi.mediaview.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [EditCoords] 的裁剪窗口：唯一的「原图坐标 ⇄ 裁剪后显示帧」换算处。
 *
 * 这里的两个「全幅」参考实现（[legacyFullDisplayNorm] / [legacyFullSourceNorm]）是**故意**从
 * 加 crop 之前的 [EditCoords] 里抄出来的：本文件用它钉住两件事——
 * 1. 没有 Crop 时新入口必须和旧语义逐点相同（零回归）；
 * 2. 有 Crop 时新入口 = 「全幅映射」再叠窗口，而不是另发明一套映射。
 */
class EditCoordsCropTest {

    private fun edits(w: Int = 1000, h: Int = 2000) = PhotoEdits.of(w, h)

    private fun editsWith(turns: Int, flipped: Boolean, crop: EditOp.Crop? = null): PhotoEdits {
        var e = edits()
        repeat(turns) { e = e.rotateClockwise() }
        if (flipped) e = e.flipHorizontal()
        return if (crop == null) e else e.setCrop(crop)
    }

    // ---- 加 crop 之前的语义（参考实现，不参与生产代码）----

    private fun legacyFullDisplayNorm(x: Float, y: Float, turns: Int, flipped: Boolean): Offset {
        val d = when (turns % 4) {
            1 -> Offset(1f - y, x)
            2 -> Offset(1f - x, 1f - y)
            3 -> Offset(y, 1f - x)
            else -> Offset(x, y)
        }
        return if (flipped) Offset(1f - d.x, d.y) else d
    }

    private fun legacyFullSourceNorm(x: Float, y: Float, turns: Int, flipped: Boolean): Offset {
        val sx = if (flipped) 1f - x else x
        return when (turns % 4) {
            1 -> Offset(y, 1f - sx)
            2 -> Offset(1f - sx, 1f - y)
            3 -> Offset(1f - y, sx)
            else -> Offset(sx, y)
        }
    }

    // ---- 没有 Crop 时语义恒等（回归钉子）----

    @Test
    fun cropWindowIsTheWholeFrameWithoutCropOp() {
        assertEquals(Rect(0f, 0f, 1f, 1f), EditCoords.cropWindow(edits()))
        assertEquals(Rect(0f, 0f, 1f, 1f), EditCoords.cropWindow(editsWith(turns = 3, flipped = true)))
    }

    @Test
    fun withoutCropTheWindowMappingIsIdenticalToTheOldFullFrameMapping() {
        val points = listOf(Offset(0f, 0f), Offset(0.1f, 0.2f), Offset(0.9f, 0.3f), Offset(0.5f, 0.5f))
        for (turns in 0..3) for (flipped in listOf(false, true)) {
            val e = editsWith(turns, flipped)
            points.forEach { p ->
                val expected = legacyFullDisplayNorm(p.x, p.y, turns, flipped)
                val got = EditCoords.toDisplayNorm(p, e)
                assertEquals("turns=$turns flip=$flipped x", expected.x, got.x, 1e-6f)
                assertEquals("turns=$turns flip=$flipped y", expected.y, got.y, 1e-6f)
                val back = EditCoords.toSourceNorm(expected, e)
                assertEquals("turns=$turns flip=$flipped 逆变换 x", p.x, back.x, 1e-6f)
                assertEquals("turns=$turns flip=$flipped 逆变换 y", p.y, back.y, 1e-6f)
            }
        }
    }

    // ---- 往返互逆：4 旋转 × 2 镜像 × 3 窗口 ----

    private val windows = listOf(
        EditOp.Crop(0.1f, 0.2f, 0.9f, 0.95f),
        EditOp.Crop(0f, 0f, 1f, 1f),
        EditOp.Crop(0.4f, 0.35f, 0.6f, 0.4f),
    )

    @Test
    fun displayAndSourceStayInverseThroughACropWindow() {
        val points = listOf(
            Offset(0.05f, 0.05f), Offset(0.5f, 0.5f), Offset(0.93f, 0.12f), Offset(0.45f, 0.8f),
        )
        for (turns in 0..3) for (flipped in listOf(false, true)) for (crop in windows) {
            val e = editsWith(turns, flipped, crop)
            // 只有落在窗口里的源点才在裁剪帧内；往返仍必须回到原点
            points.forEach { p ->
                val back = EditCoords.toSourceNorm(EditCoords.toDisplayNorm(p, e), e)
                assertEquals("turns=$turns flip=$flipped $crop x", p.x, back.x, 1e-4f)
                assertEquals("turns=$turns flip=$flipped $crop y", p.y, back.y, 1e-4f)
            }
        }
    }

    // ---- 窗口边角 ----

    @Test
    fun windowCornersMapToTheCroppedFrameEdges() {
        val corners = listOf(
            Triple(0f, 0f, Offset(0f, 0f)),   // 窗口左上 → 裁剪帧左上
            Triple(1f, 1f, Offset(1f, 1f)),   // 窗口右下 → 裁剪帧右下
            Triple(0.5f, 0.5f, Offset(0.5f, 0.5f)), // 窗口中心 → 裁剪帧中心
        )
        for (turns in 0..3) for (flipped in listOf(false, true)) for (crop in windows) {
            val e = editsWith(turns, flipped, crop)
            corners.forEach { (fu, fv, expected) ->
                // 先在全幅显示空间里挑一个点（窗口自己的边角/中心），再求它对应的源点
                val src = legacyFullSourceNorm(crop.left + fu * (crop.right - crop.left), crop.top + fv * (crop.bottom - crop.top), turns, flipped)
                val got = EditCoords.toDisplayNorm(src, e)
                assertEquals("turns=$turns flip=$flipped $crop 左上 x", expected.x, got.x, 1e-4f)
                assertEquals("turns=$turns flip=$flipped $crop 左上 y", expected.y, got.y, 1e-4f)
            }
        }
    }

    @Test
    fun windowOutsideTheCropMapsOutsideZeroOne() {
        // 窗口 [0.25,0.75]：全幅 x=0.1 的点在窗口左边 → 裁剪帧里必须是负数（渲染时被 clip）
        val e = editsWith(turns = 0, flipped = false, crop = EditOp.Crop(0.25f, 0f, 0.75f, 1f))
        val src = legacyFullSourceNorm(0.1f, 0.5f, 0, false)
        val d = EditCoords.toDisplayNorm(src, e)
        assertEquals(-0.3f, d.x, 1e-4f)
        assertEquals(0.5f, d.y, 1e-4f)
    }
}
