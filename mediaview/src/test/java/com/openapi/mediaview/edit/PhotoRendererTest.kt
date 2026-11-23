package com.openapi.mediaview.edit

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max

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

    // ---- 底图 / 标注分层（renderBase + drawOps）----

    /**
     * 两条管线的输出逐像素比，返回**单通道最大差**（含 alpha）；尺寸不同返回 [Int.MAX_VALUE]。
     *
     * 分层拆分本该是纯搬移，所以这个值必须是 0；允许 ±1 是给中间那次「先画再缩放」与
     * 「先缩放再画」的唯一可能偏差留余量（见 [drawOpsOverRenderBaseMatchesFullRenderPixelForPixel]）。
     */
    private fun maxChannelDifference(a: Bitmap, b: Bitmap): Int {
        if (a.width != b.width || a.height != b.height) return Int.MAX_VALUE
        var worst = 0
        for (x in 0 until a.width) for (y in 0 until a.height) {
            val p = a.getPixel(x, y)
            val q = b.getPixel(x, y)
            worst = max(
                worst,
                max(
                    max(abs(Color.red(p) - Color.red(q)), abs(Color.green(p) - Color.green(q))),
                    max(abs(Color.blue(p) - Color.blue(q)), abs(Color.alpha(p) - Color.alpha(q))),
                ),
            )
        }
        return worst
    }

    private fun strokeAcross() = EditOp.Stroke(
        points = listOf(androidx.compose.ui.geometry.Offset(0.1f, 0.5f), androidx.compose.ui.geometry.Offset(0.9f, 0.5f)),
        colorArgb = 0xFFFF0000L,
        widthRatio = 0.1f,
    )

    /**
     * `renderBase` 只能跑到降采样为止：同一张图上带不带标注，底图必须逐像素一样。
     *
     * 底图刻意走「滤镜生效」这条路（而不是让它等于原图引用），否则「两边都返回 source」
     * 也能让断言成立，测不出「标注被画进了底图」。
     */
    @Test
    fun renderBaseLeavesAnnotationsOut() {
        val source = gradient(80, 40)
        val plain = PhotoRenderer.renderBase(
            source,
            passthroughEdits(source.width, source.height).push(EditOp.Filter("test", 100)),
        ) { liftBlackLut() }
        val annotated = PhotoRenderer.renderBase(
            source,
            passthroughEdits(source.width, source.height)
                .push(EditOp.Filter("test", 100))
                .push(strokeAcross())
                .push(EditOp.Text("你好", 0.2f, 0.2f, 0xFF00FF00L, 0.2f)),
        ) { liftBlackLut() }

        assertNotSame(source, plain)
        assertNotNull("底图必须真的套上了滤镜，否则下面的比较是空的", firstDifference(plain, source))
        assertEquals(plain.width, annotated.width)
        assertEquals(plain.height, annotated.height)
        assertEquals("renderBase 里不该有任何标注像素", 0, maxChannelDifference(plain, annotated))
    }

    /**
     * `drawOps(renderBase(…))` 必须和整链 `render(…)` 出同一张图（±1）。
     *
     * 这是「拆分没有改变对外行为」的正向证据：上面那些回归测试只证明整链没坏，
     * 这一条证明「底图 + 标注」两段合成回来还是那个结果。
     *
     * 顺带钉住一条缓存前提：`drawOps` 不能在传进来的底图上画（Task 6 会把底图缓存复用，
     * 就地画一次就把缓存里的那张污染了）。
     */
    @Test
    fun drawOpsOverRenderBaseMatchesFullRenderPixelForPixel() {
        val source = immutableSource()
        val e = edits(source)
            .push(strokeAcross())
            .push(EditOp.Text("你好", 0.15f, 0.1f, 0xFF0000FFL, 0.15f))
            .push(
                EditOp.Mosaic(
                    listOf(
                        androidx.compose.ui.geometry.Offset(0.1f, 0.85f),
                        androidx.compose.ui.geometry.Offset(0.9f, 0.85f),
                    ),
                    widthRatio = 0.12f,
                ),
            )

        val full = PhotoRenderer.render(source, e)
        val base = PhotoRenderer.renderBase(source, e)
        val pixelBefore = base.getPixel(100, 50)
        val composed = PhotoRenderer.drawOps(base, e)

        assertEquals(full.width, composed.width)
        assertEquals(full.height, composed.height)
        assertNotSame(source, composed)
        assertTrue(
            "合成结果与整链渲染最多差 1，实际最大差 ${maxChannelDifference(full, composed)}",
            maxChannelDifference(full, composed) <= 1,
        )
        // 反证：合成图确实画上了东西（否则上一条断言在比两张白图）
        assertNotEquals(Color.WHITE, composed.getPixel(100, 50))
        assertEquals("drawOps 必须在副本上画，不能改传入的底图", pixelBefore, base.getPixel(100, 50))

        // 缓存场景专门钉一次：Task 6 缓存的底图是**可变**帧，就地画一次就把这份底图污染了，
        // 之后每次命中都带着上一笔（上面那条断言在「底图恰好不可变」时抓不到这种事）。
        val mutableBase = base.copy(Bitmap.Config.ARGB_8888, /* isMutable = */ true)
        val mutablePxBefore = mutableBase.getPixel(100, 50)
        val drawn = PhotoRenderer.drawOps(mutableBase, e)
        assertEquals(Color.WHITE, mutablePxBefore)
        assertNotEquals(Color.WHITE, drawn.getPixel(100, 50))
        assertEquals("可变底图也不能被就地画", mutablePxBefore, mutableBase.getPixel(100, 50))
    }

    // ---- renderStrict / guardRender：失败语义（回退只剩 guardRender 一台机器）----

    /** 必然抛异常的滤镜取表：渲染管线里唯一一处「按 id 现取外部数据」，是最省事的注入点。 */
    private fun boomProvider(error: Throwable): (String) -> Lut3D? = { throw error }

    /**
     * 导出不能沿用 [PhotoRenderer.render] 的兜底：静默退回原图 = 用户以为裁过、加过滤镜，
     * 落盘的却是原图。所以 renderStrict 的失败信号是 null，而不是原图引用。
     */
    @Test
    fun renderStrictReportsFailureAsNullInsteadOfTheSourceReference() {
        val source = immutableSource()
        val e = edits(source).push(EditOp.Filter("boom", 100))
        val provider = boomProvider(IllegalStateException("表读不出来"))

        assertNull("渲染失败必须报 null，不能把原图当成品交出去", PhotoRenderer.renderStrict(source, e, lutProvider = provider))

        // 对照：预览路径同一个输入照旧退回原图（兜底只留在 render，预览必须有图）
        assertSame(source, PhotoRenderer.render(source, e, lutProvider = provider))
    }

    /** OOM 也算失败（报 null），不是往外抛——导出调用方据此提示用户，而不是让 App 挂掉。 */
    @Test
    fun renderStrictTurnsOutOfMemoryIntoNullToo() {
        val source = immutableSource()
        val e = edits(source).push(EditOp.Filter("boom", 100))
        val provider = boomProvider(OutOfMemoryError("fake：模拟整图分配失败"))

        assertNull(PhotoRenderer.renderStrict(source, e, lutProvider = provider))
        assertSame(source, PhotoRenderer.render(source, e, lutProvider = provider))
    }

    /** 成功路径必须和整链 [PhotoRenderer.render] 出同一张图：拆出 strict 版本不能改变链序。 */
    @Test
    fun renderStrictOnSuccessIsTheSamePictureAsRender() {
        val source = gradient(80, 40)
        val e = passthroughEdits(source.width, source.height)
            .push(EditOp.Filter("test", 100))
            .push(strokeAcross())

        val strict = PhotoRenderer.renderStrict(source, e) { liftBlackLut() }
        assertNotNull("这张输入本不该失败", strict)
        assertNotSame("成功时必须是新位图，不能把原图交出去", source, strict)
        assertNull(
            "renderStrict 与 render 的输出必须逐像素一致",
            firstDifference(strict!!, PhotoRenderer.render(source, e) { liftBlackLut() }),
        )
        // 反证：这条链真的改过像素（否则「逐像素一致」在两张原图之间也成立）
        assertNotNull(firstDifference(strict, source))
    }

    /** 没编辑过不是失败：identity edits 下 renderStrict 原样返回源图（导出据此照样落盘）。 */
    @Test
    fun renderStrictTreatsNoEditsAsSuccess() {
        val source = immutableSource()

        assertSame(source, PhotoRenderer.renderStrict(source, edits(source)))
    }

    // ---- guardRender：唯一的兜底机器（评审 Important 1）----

    /**
     * 兜底必须只有**一台**机器：预览的两条路径（[PhotoRenderer.render] 与 PhotoEditor 的
     * renderPreview）都从它过，所以它的语义要直接钉住——否则「两处逐字一致」只能靠人读 diff 守，
     * 而漂移恰好会发生在「坏回退 = 黑预览」的那个位置。
     *
     * 失败用真管线形状（[renderBase] + [boomProvider]），不是随手 throw：吞掉的必须真的是
     * 这条链会抛的那两类异常。
     */
    @Test
    fun guardRenderIsTheSingleFallbackMachineForBothFailureKinds() {
        val source = immutableSource()
        val e = edits(source).push(EditOp.Filter("boom", 100))
        val failing = { error: Throwable ->
            { PhotoRenderer.renderBase(source, e, lutProvider = boomProvider(error)) }
        }

        assertNull(
            "RuntimeException 必须被吞成 null",
            PhotoRenderer.guardRender("test", failing(IllegalStateException("表读不出来"))),
        )
        assertNull(
            "OOM 同样吞成 null（而不是往外抛，那会杀掉编辑器）",
            PhotoRenderer.guardRender("test", failing(OutOfMemoryError("fake"))),
        )

        // 成功路径：原样交出结果，不吞、不包装
        val ok = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        assertSame(ok, PhotoRenderer.guardRender("test") { ok })
    }

    /**
     * 上界钉子（#58）：吞的集合不许变宽。上一条只钉了下界（少了 RuntimeException/OOM 会红），
     * 把 catch 从 `RuntimeException` 扩到 `Throwable`/`Error` 它照样绿——而那就等于把
     * StackOverflowError、虚拟机内部 Error 这类「进程已经不可信」的信号也吞成一张源图，
     * 编辑器看起来正常、底下已经烂掉。所以这里直接钉：非 OOM 的 Error 必须原样抛出去。
     *
     * 用一个构造出来的 StackOverflowError，不真把 JVM 栈打爆。
     */
    @Test
    fun guardRenderDoesNotSwallowErrorsItWasNotGiven() {
        val boom = StackOverflowError("fake：只为钉 catch 集合的上界")
        val thrown = assertThrows(StackOverflowError::class.java) {
            PhotoRenderer.guardRender("test") { throw boom }
        }
        assertSame("必须原样抛出，不吞、不包装", boom, thrown)
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
