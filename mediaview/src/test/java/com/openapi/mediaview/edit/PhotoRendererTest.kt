package com.openapi.mediaview.edit

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 真机回归：只有标注（涂鸦/文字/马赛克）、没有旋转/调整时，渲染器直接在解码出的
 * **不可变**位图上 `Canvas(...)`，抛 IllegalStateException 后被吞掉、退回原图——
 * 用户编辑完点「完成」，发出去的还是原图，界面上没有任何提示。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PhotoRendererTest {

    /** 和真机一样：解码器给出的位图是不可变的。 */
    private fun immutableSource(color: Int = Color.WHITE): Bitmap {
        val mutable = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        mutable.eraseColor(color)
        return mutable.copy(Bitmap.Config.ARGB_8888, /* isMutable = */ false)
    }

    private fun edits(source: Bitmap) = PhotoEdits(source.width, source.height)

    @Test
    fun strokeOnImmutableSourceIsActuallyDrawn() {
        val source = immutableSource()
        val stroke = EditOp.Stroke(
            points = listOf(androidx.compose.ui.geometry.Offset(0.1f, 0.5f), androidx.compose.ui.geometry.Offset(0.9f, 0.5f)),
            colorArgb = 0xFFFF0000L,
            widthRatio = 0.1f,
        )
        val out = PhotoRenderer.render(source, edits(source).push(stroke))

        assertNotSame("渲染失败时会退回原图引用", source, out)
        assertNotEquals("线条中点应当被画成红色", Color.WHITE, out.getPixel(100, 50))
    }

    @Test
    fun mosaicOnImmutableSourceChangesPixels() {
        // 水平渐变：每一列颜色都不同。像素化后一个色块内的列必须变成同一种颜色，
        // 所以一定有像素改变（纯色块拼成的图像素化后可能「看起来没变」，测不出东西）
        val mutable = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        for (x in 0 until 200) for (y in 0 until 100) {
            val v = x * 255 / 199
            mutable.setPixel(x, y, Color.rgb(v, v, v))
        }
        val source = mutable.copy(Bitmap.Config.ARGB_8888, false)

        val out = PhotoRenderer.render(source, edits(source).push(EditOp.Mosaic(0f, 0f, 1f, 1f)))

        assertNotSame(source, out)
        var changed = 0
        for (x in 0 until 200) for (y in 0 until 100) if (out.getPixel(x, y) != source.getPixel(x, y)) changed++
        assertNotEquals("整图马赛克后至少有像素改变", 0, changed)
        // 同一个色块内的相邻两列颜色相同（块宽 = 0.02 × 短边 100 = 2 像素）
        assertEquals(out.getPixel(10, 50), out.getPixel(11, 50))
    }

    @Test
    fun renderNeverMutatesCallerBitmap() {
        // 调用方（编辑器预览）还在显示原图：渲染不能在它身上画
        val mutable = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val stroke = EditOp.Stroke(
            points = listOf(androidx.compose.ui.geometry.Offset(0.1f, 0.5f), androidx.compose.ui.geometry.Offset(0.9f, 0.5f)),
            colorArgb = 0xFFFF0000L,
            widthRatio = 0.1f,
        )
        PhotoRenderer.render(mutable, edits(mutable).push(stroke))

        assertEquals(Color.WHITE, mutable.getPixel(100, 50))
    }

    /** 找出位图里所有满足条件的像素的平均坐标。 */
    private fun centroid(b: Bitmap, match: (Int) -> Boolean): Pair<Float, Float>? {
        var sx = 0f; var sy = 0f; var n = 0
        for (x in 0 until b.width) for (y in 0 until b.height) if (match(b.getPixel(x, y))) { sx += x; sy += y; n++ }
        return if (n == 0) null else (sx / n) to (sy / n)
    }

    /**
     * 旋转 + 镜像同时存在时，标注必须仍然落在照片上同一个物体上。
     *
     * 做法：在原图 (0.15, 0.2) 处放一块蓝色，只做旋转+镜像渲染一次，看蓝块跑到哪；
     * 再在白图上同一个归一化坐标画一个红点，做同样的旋转+镜像，红点必须落在同一位置。
     */
    @Test
    fun annotationsStayOnTheSameSpotUnderRotateAndFlip() {
        val w = 200; val h = 100
        val marked = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (x in 26..34) for (y in 16..24) setPixel(x, y, Color.BLUE)
        }.copy(Bitmap.Config.ARGB_8888, false)
        val blank = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            .copy(Bitmap.Config.ARGB_8888, false)

        for (turns in 1..3) {
            var e = PhotoEdits(w, h)
            repeat(turns) { e = e.rotateClockwise() }
            e = e.flipHorizontal()

            val blueAt = centroid(PhotoRenderer.render(marked, e)) { Color.blue(it) > 200 && Color.red(it) < 80 }!!
            val dot = EditOp.Stroke(
                points = listOf(androidx.compose.ui.geometry.Offset(0.15f, 0.2f), androidx.compose.ui.geometry.Offset(0.151f, 0.2f)),
                colorArgb = 0xFFFF0000L,
                widthRatio = 0.06f,
            )
            val redAt = centroid(PhotoRenderer.render(blank, e.push(dot))) { Color.red(it) > 200 && Color.green(it) < 80 }!!

            assertEquals("turns=$turns 标注 x 偏离照片", blueAt.first, redAt.first, 4f)
            assertEquals("turns=$turns 标注 y 偏离照片", blueAt.second, redAt.second, 4f)
        }
    }

    /**
     * 「镜像」按钮必须让**用户看到的画面**左右翻转，不管之前转了几次。
     *
     * 旋转 90° 后如果只在原图坐标里镜像，屏幕上看到的是上下翻转（真机上点出来的问题）。
     */
    @Test
    fun flipMirrorsWhatTheUserSeesAfterRotation() {
        val w = 40; val h = 20
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until w) for (y in 0 until h) setPixel(x, y, Color.rgb(x * 6, y * 12, 0))
        }.copy(Bitmap.Config.ARGB_8888, false)

        for (turns in 0..3) {
            var e = PhotoEdits(w, h)
            repeat(turns) { e = e.rotateClockwise() }
            // 渲染需要至少一个操作；turns=0 时单独渲染一次「只镜像」与原图比
            val shown = if (turns == 0) src else PhotoRenderer.render(src, e)
            val flipped = PhotoRenderer.render(src, e.flipHorizontalOnScreen())

            assertEquals("turns=$turns 宽度", shown.width, flipped.width)
            for (x in 0 until shown.width step 3) for (y in 0 until shown.height step 3) {
                assertEquals(
                    "turns=$turns 像素 ($x,$y) 应等于翻转前的 (${shown.width - 1 - x},$y)",
                    shown.getPixel(shown.width - 1 - x, y),
                    flipped.getPixel(x, y),
                )
            }
        }
    }
}
