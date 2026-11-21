package com.openapi.mediaview.edit

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 3D LUT 内核的回归测试。
 *
 * - [CubeLut.parse] 是纯字符串处理，输入都是测试里内嵌的 .cube 文本；
 * - [LutApply.apply] 要真实位图，所以整个类跑在 Robolectric 的 NATIVE 图形模式下（同 PhotoRendererTest）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class Lut3DTest {

    // ---- 测试用 .cube 文本 ----

    /** 2³ 单位表：r 变化最快，所以顺序是 (000)(100)(010)(110)(001)(101)(011)(111)。 */
    private val identityCube2 = """
        TITLE "identity"
        LUT_3D_SIZE 2
        DOMAIN_MIN 0.000000 0.000000 0.000000
        DOMAIN_MAX 1.000000 1.000000 1.000000

        0.000000 0.000000 0.000000
        1.000000 0.000000 0.000000
        0.000000 1.000000 0.000000
        1.000000 1.000000 0.000000
        0.000000 0.000000 1.000000
        1.000000 0.000000 1.000000
        0.000000 1.000000 1.000000
        1.000000 1.000000 1.000000
    """.trimIndent() + "\n"

    /** 把单通道曲线 [f] 铺成 size³ 的灰阶 .cube 文本（r=g=b，通道间不串扰）。 */
    private fun grayCube(size: Int, f: (Float) -> Float): String {
        val sb = StringBuilder("LUT_3D_SIZE ").append(size).append('\n')
        val last = (size - 1).toFloat()
        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val v = f(r / last) // 灰阶：三通道同曲线
                    sb.append(v).append(' ').append(f(g / last)).append(' ').append(f(b / last)).append('\n')
                }
            }
        }
        return sb.toString()
    }

    /**
     * 「抬黑」曲线：纯黑抬到 0.2，0.2 以上原样不动（在 x=0.2 处连续且斜率相接）。
     *
     * 断点选在 16³ 网格点 3/15 上，所以 0.2 以上的格点全是恒等值，插值出来的亮部**必须**
     * 一模一样。全局线性（out = a*x + b）做不到「黑场 +0.2 且亮部不动」——要 b=0.2 就得
     * a=0.8，中灰会被压暗，测试就能抓住。
     */
    private fun liftBlack(x: Float): Float =
        if (x < 0.2f) x + 0.2f * (1f - x / 0.2f) * (1f - x / 0.2f) else x

    /** 接近真实资产的调子：对比 1.15 + 黑场轻微抬升（连续性测试用）。 */
    private fun toneCurve(x: Float): Float =
        (0.5f + (x - 0.5f) * 1.15f).coerceIn(0f, 1f) + 0.03f * (1f - x)

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

    // ---- 解析 ----

    @Test
    fun parsesIdentityCube() {
        val lut = CubeLut.parse(identityCube2)
        assertTrue("单位表应当解析成功", lut != null)
        lut!!
        assertEquals(2, lut.size)
        assertEquals("size³*3 个分量", 8 * 3, lut.rgb.size)
        // 逐个采样点比对：r 最快变，所以前三组是 (0,0,0)(1,0,0)(0,1,0)，最后一组 (1,1,1)
        val expected = arrayOf(
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(1f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f),
            floatArrayOf(1f, 0f, 1f),
            floatArrayOf(0f, 1f, 1f),
            floatArrayOf(1f, 1f, 1f),
        )
        expected.forEachIndexed { i, e ->
            for (c in 0 until 3) {
                assertEquals("第 $i 组第 $c 通道", e[c], lut.rgb[i * 3 + c], 1e-6f)
            }
        }
    }

    @Test
    fun rejectsNonRgbLutOrder() {
        val bgr = identityCube2.replace("LUT_3D_SIZE 2", "LUT_ORDER BGR\nLUT_3D_SIZE 2")
        assertNull("非 RGB 顺序的表不能按 RGB 采样", CubeLut.parse(bgr))
    }

    @Test
    fun rejectsTooFewValues() {
        val truncated = identityCube2.lines().dropLast(2).joinToString("\n")
        assertNull("数字不够 size³ 组", CubeLut.parse(truncated))
    }

    @Test
    fun rejectsNonNumericValue() {
        val broken = identityCube2.replace("1.000000 0.000000 0.000000", "one 0.0 0.0")
        assertNull("非数字应当判坏", CubeLut.parse(broken))
    }

    @Test
    fun rejectsMissingSizeHeader() {
        val noHeader = identityCube2.lines().filterNot { it.startsWith("LUT_3D_SIZE") }.joinToString("\n")
        assertNull(CubeLut.parse(noHeader))
        assertNull("空文本", CubeLut.parse(""))
        assertNull("全是噪声", CubeLut.parse("not a lut at all\n0 0 0\n"))
    }

    @Test
    fun parses4SizeHeader() {
        val lut = CubeLut.parse(grayCube(4, ::liftBlack))
        assertTrue("4³ 表头应当解析成功", lut != null)
        lut!!
        assertEquals(4, lut.size)
        assertEquals(4 * 4 * 4 * 3, lut.rgb.size)
        assertEquals("第一个采样点是纯黑", 0.2f, lut.rgb[0], 1e-4f)
    }

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
        // 0.25 以上（≈64/255）完全不动：只有真的做了三线性插值才成立，全局线性会整体偏亮
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
