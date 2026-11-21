package com.openapi.mediaview.edit

import android.graphics.Bitmap
import android.util.LruCache

/**
 * 「预处理底图」的缓存：把 [PhotoRenderer.renderBase] 的结果按
 * 「原图 identity + 预处理指纹 + 输出边长」存起来复用。
 *
 * 为什么可以缓存：底图只取决于原图像素、Rotate/Flip/Crop/Adjust/Filter 这几项预处理和输出边长，
 * 和涂鸦/文字/马赛克无关。编辑时标注每变一笔都要重画，预处理却没有理由重跑
 * （滤镜是整图逐像素三线性插值，是链路里最贵的一段），所以把它存下来。
 *
 * 用对缓存依赖两条约定：
 * - **source 必须当成不可变的**：键里只有它的 identity，原图像素被就地改了缓存不会失效。
 * - 取出来的位图**只读**：要往上画标注就走 [PhotoRenderer.drawOps]（它在副本上画）。
 *   自己 `Canvas(base)` 直接画会污染这份底图，之后每次命中都带上那一笔。
 *
 * 逐出时不手动 `recycle()`：API 26+ 位图像素在 Java 堆上，逐出的帧交给 GC 就够了；
 * 手动回收反而可能让还在显示这一帧的人拿到 recycled 位图直接崩。
 *
 * 容量按**帧数**计（不是字节数）：预览同时需要留住的底图本来就只有一两张，
 * 默认 4 帧足够覆盖「撤销/重做来回切」，再多只是白占内存。
 */
class RenderBaseCache(capacity: Int = 4) {

    private val cache = LruCache<Key, Bitmap>(capacity)

    /** 命中返回**缓存中那个实例**（不要再改它）；未命中返回 null。 */
    fun get(source: Bitmap, edits: PhotoEdits, maxLongEdge: Int): Bitmap? =
        cache.get(Key(source, edits, maxLongEdge))

    /** 存一帧；同一个键重复存就是替换。 */
    fun put(source: Bitmap, edits: PhotoEdits, maxLongEdge: Int, bmp: Bitmap) {
        cache.put(Key(source, edits, maxLongEdge), bmp)
    }

    /**
     * 缓存键。
     *
     * - `sourceId`：源图的 identityHashCode。[PhotoEdits] 里不含源图信息，同一份 edits
     *   换一张图渲染结果完全不同，所以 identity 必须进键。
     * - `sourceWidth` / `sourceHeight`：identityHashCode 不保证唯一，同 hash 又同尺寸的概率
     *   比单看 hash 低得多——两个廉价整数，换掉一类极难复现的「显示成另一张图」。
     * - [preprocessingFingerprint]：预处理段读到的全部输入。
     * - `maxLongEdge`：预览传屏幕尺寸、导出传 [PhotoRenderer.MAX_OUTPUT_LONG_EDGE]，
     *   同一份 edits 的两种尺寸必须各存一帧，否则一边会拿到另一边的糊图或浪费一次整图渲染。
     */
    private data class Key(
        val sourceId: Int,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val fingerprint: String,
        val maxLongEdge: Int,
    ) {
        constructor(source: Bitmap, edits: PhotoEdits, maxLongEdge: Int) : this(
            System.identityHashCode(source),
            source.width,
            sourceHeight = source.height,
            fingerprint = preprocessingFingerprint(edits),
            maxLongEdge = maxLongEdge,
        )
    }
}

/**
 * 预处理指纹：[PhotoRenderer.renderBase] 在标注之外会读到的每一项都在这里有一格。
 *
 * **字段顺序固定** `q=,flip=,crop=,adj=,flt=`：键就是个字符串，往中间插一个字段等于把
 * 用户手上所有历史缓存一次性作废，而这种作废查不出痕迹。追加只加在末尾。
 *
 * 标注（[EditOp.Stroke] / [EditOp.Text] / [EditOp.Mosaic]）刻意**不在**指纹里——它们不改底图。
 * 反过来少写一个字段就是「滑杆拖了画面不动」，所以预处理段每多一种输入，这里必须同步加一项。
 */
internal fun preprocessingFingerprint(edits: PhotoEdits): String = buildString {
    append("q=").append(edits.quarterTurns)
    append(",flip=").append(edits.isFlipped)
    append(",crop=")
    val crop = edits.crop
    if (crop == null) append('-')
    else append(crop.left).append('/').append(crop.top).append('/').append(crop.right).append('/').append(crop.bottom)
    val adjust = edits.adjust
    append(",adj=").append(adjust.brightness).append('/').append(adjust.contrast).append('/').append(adjust.saturation)
    append(",flt=")
    val filter = edits.filter
    if (filter == null) append('-') else append(filter.id).append(':').append(filter.intensity)
}
