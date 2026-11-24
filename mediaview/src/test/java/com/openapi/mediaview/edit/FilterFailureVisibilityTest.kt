package com.openapi.mediaview.edit

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「滤镜全是黑框、点了没反应」这条链上**可测的两段**的钉子：
 *
 * 1. [buildFilterThumbs]：provider 取不到表时不能只是静默跳过——必须报出**缺了几张**（面板据此画
 *    占位格 + 顶部提示行）。旧实现返回纯 Map，缺哪几格在调用方那儿根本看不出来。
 * 2. [PhotoRenderer.renderBase] 的 [PhotoRenderer.render] 侧 `onFilterUnavailable`：有 Filter
 *    但表取不到时，画面降级（不崩、停在 Adjust 之后）**并且**回调带着那个 id 走一次——
 *    旧实现是唯一一处静默出口（`?: return sourceBitmap`）。
 * 3. 16 色色板 + 「自定义」产出的任意色能落到 [PhotoEdits]（argb 逐位正确）。
 *
 * 面板本身（Compose UI）不在这里测：JVM 单测里没有 compose-ui-test 依赖，钉的是它下面的函数。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FilterFailureVisibilityTest {

    private fun source(): Bitmap =
        Bitmap.createBitmap(24, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(120, 90, 200)) }

    // ---- 缩略图生成：缺表必须可数 ----

    @Test
    fun allProvidersMissingReportsEveryPresetAsMissingInsteadOfAnEmptyTable() {
        val thumbs = runBlocking { buildFilterThumbs(source()) { null } }

        assertTrue("取不到表就不该产出任何缩略图", thumbs.map.isEmpty())
        assertEquals(
            "六格全部取不到：缺表计数必须是 6（面板据此提示，而不是留六格黑框）",
            FilterPresets.ids.size,
            thumbs.missing.size,
        )
        assertEquals(FilterPresets.ids.toList(), thumbs.missing)
    }

    @Test
    fun partialFailureCountsOnlyTheBrokenPresets() {
        val broken = setOf("retro", "mono")
        val thumbs = runBlocking {
            buildFilterThumbs(source()) { id -> if (id in broken) null else CubeLut.parse(identityCube2) }
        }

        assertEquals(listOf("retro", "mono"), thumbs.missing)
        assertEquals(
            "能取到的四格照常出图",
            FilterPresets.ids.filterNot { it in broken }.toSet(),
            thumbs.map.keys,
        )
        for ((id, bmp) in thumbs.map) {
            assertTrue("$id 的缩略图不该是空位图", bmp.width > 0 && bmp.height > 0)
        }
    }

    @Test
    fun allAvailableIsNoMissing() {
        val thumbs = runBlocking { buildFilterThumbs(source()) { CubeLut.parse(identityCube2) } }

        assertEquals(emptyList<String>(), thumbs.missing)
        assertEquals(FilterPresets.ids.size, thumbs.map.size)
    }

    // ---- 渲染侧：降级 + 可观测信号 ----

    @Test
    fun renderReportsTheFilterIdItCouldNotApply() {
        val src = source()
        val edits = PhotoEdits(src.width, src.height).push(EditOp.Filter("moody", 100))
        val reported = mutableListOf<String>()

        val out = PhotoRenderer.render(src, edits, onFilterUnavailable = { reported += it }) { null }

        assertEquals("取不到的表必须上报 id", listOf("moody"), reported)
        // 画面降级但不崩：没有滤镜的效果，停在（这里没有的）Adjust 之后的状态 = 源图像素
        assertEquals("跳过滤镜后像素不该变", src.getPixel(5, 5), out.getPixel(5, 5))
    }

    @Test
    fun renderStaysSilentWhenTheFilterSucceeds() {
        val src = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.BLACK) }
            .copy(Bitmap.Config.ARGB_8888, false)
        val edits = PhotoEdits(src.width, src.height).push(EditOp.Filter("lift", 100))
        val reported = mutableListOf<String>()

        val out = PhotoRenderer.render(src, edits, onFilterUnavailable = { reported += it }) {
            CubeLut.parse(grayCube(16, ::liftBlack))
        }

        assertEquals("成功路径不该打扰调用方", emptyList<String>(), reported)
        // 反证：这张表确实动了像素（否则「没上报」是空的）
        assertTrue("抬黑表应把纯黑抬亮", Color.red(out.getPixel(5, 5)) > 30)
    }

    @Test
    fun renderBaseSignalFiresOncePerRenderAndExportPathCanSeeItToo() {
        val src = source()
        val edits = PhotoEdits(src.width, src.height).push(EditOp.Filter("creamy", 80))
        var calls = 0
        var id: String? = null

        val base = PhotoRenderer.renderBase(
            src, edits,
            onFilterUnavailable = { calls++; id = it },
            lutProvider = { null },
        )

        assertEquals(1, calls)
        assertEquals("creamy", id)
        // 严格路径（导出）同样接这个信号：落盘没有滤镜时宿主必须能提示
        var exportCalls = 0
        val strict = PhotoRenderer.renderStrict(
            src, edits,
            onFilterUnavailable = { exportCalls++ },
            lutProvider = { null },
        )
        assertNotNull("表取不到不是渲染失败，strict 照样出图", strict)
        assertEquals(1, exportCalls)
        assertEquals(src.getPixel(3, 3), base.getPixel(3, 3))
    }

    // ---- 色板：16 档 + 自定义色落到数据模型 ----

    @Test
    fun paletteHasSixteenColorsInRowsOfEight() {
        assertEquals("设计稿的 16 色色板", 16, EditSwatches.All.size)
        assertEquals("色板不许有重复档", 16, EditSwatches.All.map { it.toArgb() }.distinct().size)
        // 每行 8 个由 chunked(SWATCHES_PER_ROW=8) 保证：这里钉住 16 = 2×8
        assertEquals(2, EditSwatches.All.chunked(8).size)
    }

    /** 「自定义」产出的任意 HSL 颜色走回 onPick(Color) → toArgb().toLong() → 落进 PhotoEdits。 */
    @Test
    fun customColorLandsInPhotoEditsWithTheExactArgb() {
        // 设计稿对话框默认的 H/S/L
        val c = hslToColor(210f, 70f, 50f)
        val argb = c.toArgb().toLong()

        val e = PhotoEdits.of(100, 100).push(
            EditOp.Stroke(
                points = listOf(androidx.compose.ui.geometry.Offset(0.1f, 0.1f), androidx.compose.ui.geometry.Offset(0.9f, 0.9f)),
                colorArgb = argb,
                widthRatio = 0.01f,
            ),
        )
        val stored = (e.ops.single() as EditOp.Stroke).colorArgb
        assertEquals("argb 必须逐位落进数据模型", argb, stored)
        assertEquals("alpha 恒不透明", 0xFF000000L, stored and 0xFF000000L)
    }

    /** HSL 往返：色板上的墨色取色后回推，色相/饱和/明度落在同一档附近（滑杆重开接着调）。 */
    @Test
    fun rgbToHslRoundTripsBackToTheSameColor() {
        for (c in listOf(EditSwatches.Red, EditSwatches.Blue, EditSwatches.Yellow, EditSwatches.Gray)) {
            val hsl = rgbToHsl(c)
            val back = hslToColor(hsl.hue, hsl.sat, hsl.light)
            assertEquals("${c} 色相回推", c.red, back.red, 0.01f)
            assertEquals("${c} 饱和度回推", c.green, back.green, 0.01f)
            assertEquals("${c} 明度回推", c.blue, back.blue, 0.01f)
        }
    }

    /** 纯函数边界：滑杆端点必须给出可预期的极值（全黑 / 全白 / 纯色）。 */
    @Test
    fun hslEndpointsAreTheExpectedExtremes() {
        assertEquals(0x000000L, hslToColor(0f, 0f, 0f).toArgb().toLong() and 0xFFFFFFL)
        assertEquals(0xFFFFFFL, hslToColor(0f, 0f, 100f).toArgb().toLong() and 0xFFFFFFL)
        assertEquals(0xFF0000L, hslToColor(0f, 100f, 50f).toArgb().toLong() and 0xFFFFFFL)
        // 色相绕圈：370° 和 10° 是同一个颜色
        assertEquals(hslToColor(10f, 80f, 60f).toArgb(), hslToColor(370f, 80f, 60f).toArgb())
    }
}
