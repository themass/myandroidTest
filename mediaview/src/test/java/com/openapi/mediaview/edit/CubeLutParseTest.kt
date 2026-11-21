package com.openapi.mediaview.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CubeLut.parse] 是纯字符串处理，不需要 Android，所以这个类**不带** Robolectric 注解，
 * 跑在普通 JVM 上（同 PhotoEditsTest）。要真实位图的套表用例在 [Lut3DTest]。
 */
class CubeLutParseTest {

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

    @Test
    fun parsesTabSeparatedHeader() {
        // 有些导出工具用制表符对齐关键字和数值，表头不能只认空格
        val tabs = identityCube2.replace("LUT_3D_SIZE 2", "LUT_3D_SIZE\t2")
        val lut = CubeLut.parse(tabs)
        assertTrue("制表符分隔的表头也应当解析成功", lut != null)
        assertEquals(2, lut?.size)
    }

    @Test
    fun rejectsTooManyValues() {
        // 数值多出来同样不可信，而且要立刻停住、不把缓冲无限撑下去
        val extra = identityCube2 + "0.000000 0.000000 0.000000\n"
        assertNull("数字多于 size³ 组", CubeLut.parse(extra))
    }
}
