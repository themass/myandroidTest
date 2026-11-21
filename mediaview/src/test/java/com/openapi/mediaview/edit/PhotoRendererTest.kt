package com.openapi.mediaview.edit

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

        // 马赛克现在是折线笔迹（3acd69d4）：一条横贯中线、粗细 = 短边的笔迹覆盖整幅图。
        // widthRatio=1 → 笔宽 = 1×短边100 = 100 像素，正好从顶到底。
        val out = PhotoRenderer.render(
            source,
            edits(source).push(
                EditOp.Mosaic(
                    listOf(
                        androidx.compose.ui.geometry.Offset(0f, 0.5f),
                        androidx.compose.ui.geometry.Offset(1f, 0.5f),
                    ),
                    widthRatio = 1f,
                ),
            ),
        )

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
        // 蓝块以 0.15×200=30 为中心、关于归一化坐标对称（27..35）：
        // 26..34 会整体偏半像素，宽边再折到 100 宽的显示上就放大成 ~7px 量化误差，测不出真 bug。
        val marked = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (x in 27..35) for (y in 16..24) setPixel(x, y, Color.BLUE)
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

    // ---- 滤镜 ----

    /** 渲染器测试共用的「抬黑」表（和 [Lut3DTest] 同一批夹具）。 */
    private fun liftBlackLut(): Lut3D = CubeLut.parse(grayCube(16, ::liftBlack))!!

    /**
     * 只有 [EditOp.Rotate]` (0)` 这一条空操作：让 [PhotoRenderer.render] 走进完整管线
     * （否则 ops 为空会直接返回原图引用，比较就没有意义），同时保证输出 = 输入像素。
     */
    private fun passthroughEdits(w: Int, h: Int) = PhotoEdits(w, h).push(EditOp.Rotate(0))

    private fun gradient(w: Int = 40, h: Int = 20): Bitmap {
        val mutable = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (x in 0 until w) for (y in 0 until h) {
            val v = x * 255 / (w - 1)
            mutable.setPixel(x, y, Color.rgb(v, (v / 2).coerceAtMost(255), 255 - v))
        }
        return mutable.copy(Bitmap.Config.ARGB_8888, false)
    }

    /** 两张位图尺寸与逐像素是否完全一致；不一致时返回第一处差异的描述。 */
    private fun firstDifference(a: Bitmap, b: Bitmap): String? {
        if (a.width != b.width || a.height != b.height) return "尺寸 ${a.width}x${a.height} vs ${b.width}x${b.height}"
        for (x in 0 until a.width) for (y in 0 until a.height) {
            if (a.getPixel(x, y) != b.getPixel(x, y)) return "($x,$y): ${a.getPixel(x, y)} vs ${b.getPixel(x, y)}"
        }
        return null
    }

    @Test
    fun filterBrightensBlackPixelsInsteadOfFallingBackToSource() {
        val source = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.BLACK) }
            .copy(Bitmap.Config.ARGB_8888, false)
        val edits = PhotoEdits(source.width, source.height).push(EditOp.Filter("test", 100))

        val out = PhotoRenderer.render(source, edits) { liftBlackLut() }

        // 渲染失败时 render 会退回原图引用；这里必须是渲染出来的新位图
        assertNotSame(source, out)
        val px = out.getPixel(5, 5)
        // 抬黑表把 0 抬到 0.2：0.2 × 255 = 51
        assertTrue("纯黑应被抬到 51 附近，实际 $px", Color.red(px) in 49..53)
        assertEquals("灰阶表不该偏色", Color.red(px), Color.green(px))
        assertEquals(Color.green(px), Color.blue(px))
        assertEquals("alpha 必须原样保留", 255, Color.alpha(px))
    }

    @Test
    fun zeroIntensityIsPixelIdenticalToNoFilter() {
        val source = gradient()
        val withFilter = PhotoRenderer.render(source, passthroughEdits(source.width, source.height).push(EditOp.Filter("test", 0))) { liftBlackLut() }
        val withoutFilter = PhotoRenderer.render(source, passthroughEdits(source.width, source.height)) { liftBlackLut() }

        assertNotSame(withoutFilter, withFilter)
        assertNull("强度 0 必须和没套滤镜逐像素一致", firstDifference(withFilter, withoutFilter))
    }

    @Test
    fun providerReturningNullSkipsFilterAndKeepsPostAdjustState() {
        val source = gradient()
        val missing = PhotoRenderer.render(source, passthroughEdits(source.width, source.height).push(EditOp.Filter("missing", 100))) { null }
        val withoutFilter = PhotoRenderer.render(source, passthroughEdits(source.width, source.height)) { liftBlackLut() }
        val applied = PhotoRenderer.render(source, passthroughEdits(source.width, source.height).push(EditOp.Filter("test", 100))) { liftBlackLut() }

        assertNull("缺滤镜资产就当作没滤镜", firstDifference(missing, withoutFilter))
        // 反证：同一张表在强度 100 下确实会改变这张图（否则上一条断言是空的）
        assertNotNull("渐变图上套抬黑表本应有像素变化", firstDifference(applied, withoutFilter))
    }

    /**
     * Adjust 与 Filter 同时存在时必须是「先 Adjust 再 Filter」。
     *
     * 手算（灰阶源值 74）：
     * - Adjust(brightness=-50) → 平移量 -50×0.8 = -40 → 74 − 40 = 34（正好落在 16³ 表的格点 2 上，
     *   因为格点间距是 255/15 = 17，所以插值就是格点值本身）
     * - 抬黑表格点 2：liftBlack(2/15) = 2/15 + 0.2×(1 − 2/3)² = 7/45 → 7/45×255 = 39.67 → 40
     *
     * 顺序反了会得到不同的值：先套表 74 落在恒等段 → 74，再 Adjust → 34。
     */
    @Test
    fun adjustAndFilterComposeAdjustFirst() {
        val w = 20; val h = 10
        val source = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.rgb(74, 74, 74)) }
            .copy(Bitmap.Config.ARGB_8888, false)
        val adjust = EditOp.Adjust(brightness = -50, contrast = 0, saturation = 0)

        val out = PhotoRenderer.render(
            source,
            PhotoEdits(w, h).push(adjust).push(EditOp.Filter("test", 100)),
        ) { liftBlackLut() }
        // 反证用：只 Adjust 不套表（=34），说明滤镜确实参与了、且不是在 Adjust 之前跑的
        val adjustOnly = PhotoRenderer.render(source, PhotoEdits(w, h).push(adjust)) { liftBlackLut() }

        val px = out.getPixel(5, 5)
        assertTrue("Adjust→Filter 的合成结果应为 40（±2），实际 $px", Color.red(px) in 38..42)
        assertEquals("不该偏色", Color.red(px), Color.green(px))
        assertEquals(Color.green(px), Color.blue(px))
        assertTrue("只 Adjust 时应停在 34（±2），实际 ${Color.red(adjustOnly.getPixel(5, 5))}", Color.red(adjustOnly.getPixel(5, 5)) in 32..36)
    }

    // ---- 裁剪 ----

    /** 400×200 渐变图裁中心 50%：输出尺寸由窗口精确决定，四角必须等于源上对应的那四个点。 */
    @Test
    fun cropOfHalfTheFrameOutputsTheWindowWithSourcePixelsAtItsCorners() {
        val source = gradient(400, 200)
        val out = PhotoRenderer.render(source, PhotoEdits(400, 200).setCrop(EditOp.Crop(0.25f, 0.25f, 0.75f, 0.75f)))

        assertNotSame(source, out)
        assertEquals(200, out.width)
        assertEquals(100, out.height)
        // 窗口 [0.25,0.75] → 源上 x 100..299、y 50..149
        listOf(0 to 100, 199 to 299).forEach { (outX, srcX) ->
            listOf(0 to 50, 99 to 149).forEach { (outY, srcY) ->
                val got = out.getPixel(outX, outY)
                val want = source.getPixel(srcX, srcY)
                assertEquals("($outX,$outY) vs 源 ($srcX,$srcY) R", Color.red(want).toDouble(), Color.red(got).toDouble(), 2.0)
                assertEquals("($outX,$outY) vs 源 ($srcX,$srcY) G", Color.green(want).toDouble(), Color.green(got).toDouble(), 2.0)
                assertEquals("($outX,$outY) vs 源 ($srcX,$srcY) B", Color.blue(want).toDouble(), Color.blue(got).toDouble(), 2.0)
            }
        }
    }

    /**
     * 出界标注靠渲染 clip，而不是改数据：x=0.5 处一条竖白线，裁左半（窗口右边界正好压在线中心），
     * 笔迹的左半边被截掉 → 输出的左半区一个白像素都没有，右半区有。
     */
    @Test
    fun annotationCrossingTheCropEdgeIsClippedInsteadOfRewritten() {
        val source = gradient(400, 200)
        val line = EditOp.Stroke(
            points = listOf(androidx.compose.ui.geometry.Offset(0.5f, 0.1f), androidx.compose.ui.geometry.Offset(0.5f, 0.9f)),
            colorArgb = 0xFFFFFFFFL,
            widthRatio = 0.1f,
        )
        val cropped = PhotoRenderer.render(source, PhotoEdits(400, 200).push(line).setCrop(EditOp.Crop(0f, 0f, 0.5f, 1f)))

        assertNotSame(source, cropped)
        assertEquals(200, cropped.width)
        assertEquals(200, cropped.height)
        assertEquals("窗口外的半条笔迹不该被画进来", 0, countWhite(cropped, 0, cropped.width / 2))
        assertTrue("窗口内的半条笔迹必须还在", countWhite(cropped, cropped.width / 2, cropped.width) > 0)
    }

    /** 撤销裁剪：标注数据一直都在（迁移过一次就回不来了），所以左半区的白线必须原样复现。 */
    @Test
    fun undoingTheCropBringsTheClippedAnnotationBack() {
        val source = gradient(400, 200)
        val line = EditOp.Stroke(
            points = listOf(androidx.compose.ui.geometry.Offset(0.5f, 0.1f), androidx.compose.ui.geometry.Offset(0.5f, 0.9f)),
            colorArgb = 0xFFFFFFFFL,
            widthRatio = 0.1f,
        )
        val withCrop = PhotoEdits(400, 200).push(line).setCrop(EditOp.Crop(0f, 0f, 0.5f, 1f))
        val withoutCrop = withCrop.undo()
        assertNull(withoutCrop.crop)
        assertEquals(1, withoutCrop.ops.size) // 笔迹还在

        val out = PhotoRenderer.render(source, withoutCrop)
        assertEquals(400, out.width)
        assertTrue("撤销裁剪后线的全宽都回来了", countWhite(out, 0, out.width / 2) > 0)
    }

    /** 输出位图上 [fromX, toX) 这段里「接近纯白」的像素数。 */
    private fun countWhite(b: Bitmap, fromX: Int, toX: Int): Int {
        var n = 0
        for (x in fromX until toX) for (y in 0 until b.height) {
            val p = b.getPixel(x, y)
            if (Color.red(p) > 200 && Color.green(p) > 200 && Color.blue(p) > 200) n++
        }
        return n
    }
}
