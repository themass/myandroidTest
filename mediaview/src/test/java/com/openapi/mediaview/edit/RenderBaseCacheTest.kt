package com.openapi.mediaview.edit

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.geometry.Offset

/**
 * [RenderBaseCache]：「预处理底图」的 LruCache（source 按 identity + 预处理指纹 + 边长做键）。
 *
 * 整类走 Robolectric + NATIVE 图形：键和值都是真 [Bitmap]，纯 JVM 造不出来。
 *
 * 这里钉住的是**缓存键的语义**，不是渲染正确性（渲染归 [PhotoRendererTest]）：
 * 命中/未命中必须只由「底图会不会变」决定——标注变不动键，预处理任何一个参数变键就变。
 * 键少算一个字段 = 用户滑了亮度还看到旧底图；多算一个字段 = 缓存永远不命中、白白重算。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderBaseCacheTest {

    /**
     * 渲染器测试共用的「抬黑」表（和 [Lut3DTest] / [PhotoRendererTest] 同一批夹具）。
     * 这里只用来让渲染**真的走过滤镜那一段**：命中缓存的判据必须是「provider 零调用」，
     * 而不是「图没变」（恒等表/不套表时图也不会变，测不出东西）。
     */
    private fun liftBlackLut(): Lut3D = CubeLut.parse(grayCube(16, ::liftBlack))!!

    /** 16×16 渐变源图：16² 的 LruCache size = 256，容量 4 意味着恰好 4 帧。 */
    private fun source(): Bitmap {
        val mutable = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (x in 0 until 16) for (y in 0 until 16) {
            mutable.setPixel(x, y, Color.rgb(x * 16, y * 16, 128))
        }
        return mutable
    }

    /** 底图会真的改变像素的最小预处理组合：抬黑滤镜 + 非恒等 Adjust。 */
    private fun filterEdits(w: Int = 16, h: Int = 16): PhotoEdits =
        PhotoEdits(w, h).push(EditOp.Filter("test", 100))

    private fun stroke() = EditOp.Stroke(
        points = listOf(Offset(0.1f, 0.5f), Offset(0.9f, 0.5f)),
        colorArgb = 0xFFFF0000L,
        widthRatio = 0.1f,
    )

    @Test
    fun sameKeyHits() {
        val source = source()
        val cache = RenderBaseCache()
        val edits = filterEdits()

        assertNull("第一查必 miss", cache.get(source, edits, 640))
        val base = PhotoRenderer.renderBase(source, edits) { liftBlackLut() }
        cache.put(source, edits, 640, base)

        assertSame("同 (source, 预处理, 边) 必须命中同一实例", base, cache.get(source, edits, 640))
    }

    @Test
    fun differentMaxLongEdgeDoesNotShareEntries() {
        val source = source()
        val cache = RenderBaseCache()
        val edits = filterEdits()

        val big = PhotoRenderer.renderBase(source, edits, 1280) { liftBlackLut() }
        cache.put(source, edits, 1280, big)
        val small = PhotoRenderer.renderBase(source, edits, 640) { liftBlackLut() }
        cache.put(source, edits, 640, small)

        assertSame("1280 帧", big, cache.get(source, edits, 1280))
        assertSame("640 帧", small, cache.get(source, edits, 640))
        assertNotSame("不同边长各存各的", big, small)
    }

    @Test
    fun changingOnlyAnnotationsStillHits() {
        val source = source()
        val cache = RenderBaseCache()
        val baseEdits = filterEdits()
        val base = PhotoRenderer.renderBase(source, baseEdits) { liftBlackLut() }
        cache.put(source, baseEdits, 640, base)

        // 只加标注：底图一个像素都不会变，必须命中
        assertSame(
            base,
            cache.get(source, baseEdits.push(stroke()).push(EditOp.Text("你好", 0.2f, 0.2f, 0xFF00FF00L, 0.2f)), 640),
        )
        assertSame(base, cache.get(source, baseEdits.push(EditOp.Mosaic(listOf(Offset(0.5f, 0.5f)), 0.1f)), 640))
        assertSame("把标注删掉也要命中", base, cache.get(source, baseEdits, 640))
    }

    @Test
    fun changingAdjustParametersMisses() {
        val source = source()
        val cache = RenderBaseCache()
        val e1 = filterEdits().push(EditOp.Adjust(brightness = 20, contrast = 0, saturation = 0))
        val base1 = PhotoRenderer.renderBase(source, e1) { liftBlackLut() }
        cache.put(source, e1, 640, base1)

        val e2 = filterEdits().push(EditOp.Adjust(brightness = 21, contrast = 0, saturation = 0))
        assertNull("亮度 20 → 21：底图变了，旧帧不能命中", cache.get(source, e2, 640))
        val e3 = filterEdits().push(EditOp.Adjust(brightness = 20, contrast = 10, saturation = 0))
        assertNull("换另一个参数轴也要 miss", cache.get(source, e3, 640))
        assertNotNull("原键仍在（不是被新查询冲掉）", cache.get(source, e1, 640))
    }

    @Test
    fun changingSourceIdentityMisses() {
        val a = source()
        val b = source()
        assertNotSame("前置条件：两张不同的位图", a, b)
        val cache = RenderBaseCache()
        val edits = filterEdits()

        val baseA = PhotoRenderer.renderBase(a, edits) { liftBlackLut() }
        cache.put(a, edits, 640, baseA)

        assertNull("identity 变了就是另一张源图", cache.get(b, edits, 640))
        assertSame("原源图仍命中", baseA, cache.get(a, edits, 640))
    }

    @Test
    fun capacityEvictsOldestFrames() {
        val source = source()
        val cache = RenderBaseCache(capacity = 4)
        // 6 个互不相同的键：亮度 0..5 各自产生不同的底图
        repeat(6) { i ->
            val e = PhotoEdits(16, 16).push(EditOp.Adjust(brightness = i, contrast = 0, saturation = 0))
            cache.put(source, e, 640, PhotoRenderer.renderBase(source, e))
        }

        // LruCache 逐出最久未用的：前两帧（亮度 0、1）必须在容量 4 之外
        val e0 = PhotoEdits(16, 16).push(EditOp.Adjust(brightness = 0, contrast = 0, saturation = 0))
        val e1 = PhotoEdits(16, 16).push(EditOp.Adjust(brightness = 1, contrast = 0, saturation = 0))
        assertNull("put 6 帧、容量 4：最早的帧已被逐出", cache.get(source, e0, 640))
        assertNull(cache.get(source, e1, 640))
    }

    @Test
    fun secondGetForSameKeyDoesNotCallProviderAgain() {
        val source = source()
        val cache = RenderBaseCache()
        val edits = filterEdits()
        var calls = 0
        val counting = { _: String ->
            calls++
            liftBlackLut()
        }

        // 第一遍：miss → 真的渲染（provider 被调）→ 入缓存
        assertNull(cache.get(source, edits, 640))
        val base = cache.get(source, edits, 640) ?: PhotoRenderer.renderBase(source, edits, 640, counting).also {
            cache.put(source, edits, 640, it)
        }
        assertEquals("miss 时必须渲染一次", 1, calls)

        // 第二遍：走同一个「查缓存，没有才渲染」的调用点形状 → 预处理一段一个像素都不重算
        val second = cache.get(source, edits, 640) ?: PhotoRenderer.renderBase(source, edits, 640, counting).also {
            cache.put(source, edits, 640, it)
        }
        assertSame(base, second)
        assertEquals("同键二次查询期间 lutProvider 必须零调用", 1, calls)
    }
}
