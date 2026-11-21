package com.openapi.mediaview.edit

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [LutApply.apply] 与内置滤镜资产的回归测试。
 *
 * 套表要真实位图，所以这个类跑在 Robolectric 的 NATIVE 图形模式下（同 PhotoRendererTest）。
 * 纯字符串的解析用例在 [CubeLutParseTest]，那边不带 Robolectric。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class Lut3DTest {

    private fun mutableBitmap(colors: List<Int>): Bitmap {
        val bmp = Bitmap.createBitmap(colors.size, 1, Bitmap.Config.ARGB_8888)
        colors.forEachIndexed { i, c -> bmp.setPixel(i, 0, c) }
        return bmp
    }

    /** 单位表下每个通道的绝对误差（0..255 的像素值之差）。 */
    private fun delta(before: Int, after: Int): Int =
        maxOf(
            Math.abs(Color.red(before) - Color.red(after)),
            Math.abs(Color.green(before) - Color.green(after)),
            Math.abs(Color.blue(before) - Color.blue(after)),
        )

    // ---- 应用 ----

    private val sampleColors = listOf(
        Color.rgb(0, 0, 0),
        Color.rgb(16, 16, 16),
        Color.rgb(64, 64, 64),
        Color.rgb(128, 60, 200),
        Color.rgb(200, 200, 200),
        Color.rgb(255, 255, 255),
    )

    @Test
    fun identityLutKeepsPixelsWithinOne() {
        val lut = CubeLut.parse(identityCube2)!!
        val before = sampleColors
        val out = LutApply.apply(mutableBitmap(before), lut, 100)
        for (i in sampleColors.indices) {
            assertTrue(
                "单位表下 ${before[i]} 变成了 ${out.getPixel(i, 0)}",
                delta(before[i], out.getPixel(i, 0)) <= 1,
            )
        }
    }

    @Test
    fun applyMutatesTargetInPlaceAndReturnsIt() {
        val lut = CubeLut.parse(grayCube(16, ::liftBlack))!!
        val target = mutableBitmap(listOf(Color.rgb(0, 0, 0)))
        val out = LutApply.apply(target, lut, 100)
        assertSame("就地修改并返回同一张位图", target, out)
        assertNotEquals("纯黑应当被抬亮", Color.BLACK, out.getPixel(0, 0))
    }

    @Test
    fun liftBlackCubeOnlyTouchesShadows() {
        val lut = CubeLut.parse(grayCube(16, ::liftBlack))!!
        val target = mutableBitmap(sampleColors)
        val out = LutApply.apply(target, lut, 100)

        val black = out.getPixel(0, 0)
        assertTrue(
            "纯黑应当被抬到 0.2 附近，实际 ${Color.red(black)}",
            Color.red(black) in 45..57 && Color.red(black) == Color.green(black) && Color.green(black) == Color.blue(black),
        )
        // 0.2 以上完全不动：只有真的做了三线性插值才成立，全局线性会整体偏亮
        for (i in 2 until sampleColors.size) {
            assertTrue(
                "亮部 ${sampleColors[i]} 不该被改动，实际 ${out.getPixel(i, 0)}",
                delta(sampleColors[i], out.getPixel(i, 0)) <= 1,
            )
        }
    }

    @Test
    fun zeroIntensityIsNoOp() {
        val lut = CubeLut.parse(grayCube(16, ::liftBlack))!!
        val target = mutableBitmap(sampleColors)
        val out = LutApply.apply(target, lut, 0)
        for (i in sampleColors.indices) {
            assertEquals("强度 0 必须逐像素原样", sampleColors[i], out.getPixel(i, 0))
        }
    }

    @Test
    fun halfIntensityIsMidpointOfLerp() {
        val lut = CubeLut.parse(grayCube(16, ::liftBlack))!!
        val out = LutApply.apply(mutableBitmap(listOf(Color.rgb(0, 0, 0))), lut, 50)
        val v = Color.red(out.getPixel(0, 0))
        // out = orig + (lut(orig) - orig) * 50 / 100 = 0 + 51 * 0.5
        assertTrue("黑像素在强度 50 下应当落在 (0+51)/2 附近，实际 $v", v in 24..28)
        assertEquals("灰阶表不该引入偏色", v, Color.green(out.getPixel(0, 0)))
        assertEquals(v, Color.blue(out.getPixel(0, 0)))
    }

    @Test
    fun trilinearOutputHasNoSteps() {
        val lut = CubeLut.parse(grayCube(33, ::toneCurve))!!
        val samples = 64
        val values = (0 until samples).map { it * 255 / (samples - 1) }
        val target = mutableBitmap(values.map { Color.rgb(it, it, it) })
        val out = LutApply.apply(target, lut, 100)

        var maxStep = 0
        var previous = Color.red(out.getPixel(0, 0))
        for (i in 1 until samples) {
            val now = Color.red(out.getPixel(i, 0))
            maxStep = maxOf(maxStep, Math.abs(now - previous))
            previous = now
        }
        assertTrue("沿对角线相邻输出的最大跳变 $maxStep 应当 ≤ 8", maxStep <= 8)
    }

    // ---- 真实资产 ----

    @Test
    fun bundledFilterAssetsParse() {
        assertEquals(
            "滤镜 id 顺序",
            listOf("creamy", "coolwhite", "retro", "moody", "airy", "mono"),
            FilterPresets.ids,
        )
        assertEquals("filters", FilterPresets.ASSET_DIR)
        for (id in FilterPresets.ids) {
            val file = assetFile(id)
            assertTrue("缺少滤镜资产 ${file.path}", file.isFile)
            val lut = CubeLut.parse(file.readText())
            assertTrue("$id 应当能解析", lut != null)
            lut!!
            assertEquals("$id 的表尺寸", 33, lut.size)
            assertEquals(33 * 33 * 33 * 3, lut.rgb.size)
        }
    }

    /**
     * 单元测试的工作目录在不同 Gradle 版本下可能是模块目录或仓库根目录，两个候选路径都试。
     */
    private fun assetFile(id: String): File {
        val rel = listOf(FilterPresets.ASSET_DIR, "$id.cube")
        val candidates = listOf(
            File("src/main/assets", rel.joinToString(File.separator)),
            File("mediaview/src/main/assets", rel.joinToString(File.separator)),
        )
        return candidates.firstOrNull { it.isFile } ?: candidates.first()
    }
}
