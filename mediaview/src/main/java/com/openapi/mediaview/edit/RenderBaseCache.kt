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
 * **硬要求：每个编辑会话持有自己的 [RenderBaseCache] 实例，绝不跨会话共享。**
 * 因为 identityHashCode 会被**复用**：[source] 被 GC 之后，新 decode 出来的位图完全可能拿到
 * 同一个 identityHashCode（哈希取自对象地址，地址会被回收再分配）。那时只要尺寸和预处理
 * 恰好也一样，[get] 就会把**上一张照片的底图**当成本张的底图返回——界面上看到的是别的地方的照片。
 * 这不是理论风险：同一台相机拍的两张照片尺寸必然相同，用户「编辑完退出 → 打开下一张 → 什么也没调」
 * 正好把键的四个分量全部撞齐，而宽/高在这里救不了。
 * 会话级实例让缓存在退出编辑时整体作废，从根上不给复用 identity 的机会
 * （缓存本身不持强引用去钉住 source，所以不能指望它把旧 source 撑住）。
 *
 * 逐出时不手动 `recycle()`：API 26+ 位图像素在 Java 堆上，逐出的帧交给 GC 就够了；
 * 手动回收反而可能让还在显示这一帧的人拿到 recycled 位图直接崩。
 *
 * 容量按**帧数**计（不是字节数）：[LruCache] 没有覆写 `sizeOf`，每条 entry 记 1，
 * 所以 capacity 就是「最多留几帧」，与帧的像素多少无关。预览同时需要留住的底图本来就只有一两张，
 * 默认 4 帧足够覆盖「撤销/重做来回切」，再多只是白占内存。
 */
class RenderBaseCache(capacity: Int = 4) {

    private val cache = LruCache<Key, Bitmap>(capacity)

    /** 命中返回**缓存中那个实例**（不要再改它）；未命中返回 null。 */
    fun get(source: Bitmap, edits: PhotoEdits, maxLongEdge: Int): Bitmap? =
        cache.get(Key(source, edits, maxLongEdge))

    /**
     * 存一帧；同一个键重复存就是替换。
     *
     * `bmp === source` 时**什么都不存**：预处理全是空操作（且没被降采样）时
     * [PhotoRenderer.renderBase] 会把 [source] 原样返回，把它存进缓存等于用缓存的强引用
     * 把调用方的源位图钉住——它本来在会话结束后就该跟着释放，被钉住之后反倒给
     * identityHashCode 复用留了更久的窗口，而且这一帧本来就等于「什么都不做」，存下来没有收益。
     */
    fun put(source: Bitmap, edits: PhotoEdits, maxLongEdge: Int, bmp: Bitmap) {
        if (bmp === source) return
        cache.put(Key(source, edits, maxLongEdge), bmp)
    }

    /**
     * 缓存键。
     *
     * - `sourceId`：源图的 identityHashCode。[PhotoEdits] 里不含源图信息，同一份 edits
     *   换一张图渲染结果完全不同，所以 identity 必须进键。
     *   注意它**不保证唯一、还会被复用**——所以有类注释里那条「会话级实例」的硬要求。
     * - `sourceWidth` / `sourceHeight`：只是多两道便宜的防线（同 identity 又同尺寸比单看
     *   hash 少见得多）。**别把它们当成兜底**：同一台相机的两张照片尺寸相同，那时它们完全无效。
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
