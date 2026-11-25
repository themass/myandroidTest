package com.openapi.mediaview.edit

import android.content.Context
import android.content.res.AssetManager
import android.util.Log

/**
 * 把 assets 里的 `.cube` 变成渲染器要的 `(id) -> Lut3D?`。
 *
 * **按 id 记忆化，但只记成功的结果**：一张 33³ 的表是 35937 行文本、约 1MB，解析要一两百毫秒。
 * 预览每拖一下强度滑杆都会问一次同一个 id，不记忆化的话滑杆就成了「反复读盘 + 反复 parse」，
 * 手感直接崩。
 *
 * **失败不进缓存**（旧实现把 null 也记死）：文件缺失/损坏确实「重试不会突然变好」，但读盘失败
 * 也可能是瞬时 IO（ContentProvider 抖动、内存压力下的读取失败），旧实现会让一次瞬时失败把
 * **整个进程**的滤镜全灭到下次冷启动——界面上就是六格全黑、点了没反应，而且没有任何恢复机会。
 * 失败不缓存的代价只是「下次再试一次」，而缺文件时 open 立刻返回、没有 parse 成本，可以接受。
 * 调用方（滤镜面板 / 渲染管线）会把「取不到」如实报出来，见 [FilterPanel] 与
 * [PhotoRenderer.render] 的 `onFilterUnavailable`。
 *
 * 记忆化是**进程级**的，这安全：[Lut3D] 是纯数据、被 [LutApply] 只读使用、不含 identity 语义，
 * 所以它不像 [RenderBaseCache] 缓存的位图那样需要「每会话一个实例」。
 * 常驻内存是每张 33³×3 个 float ≈ 430KB，六个滤镜合计约 2.6MB。
 *
 * 换滤镜文件（设计裁量 6：设计手调后直接覆盖 assets）不需要动这里——进程重启后自然重解析。
 */
object LutAssets {

    private const val TAG = "LutAssets"

    /** 只存**解析成功**的表；失败刻意不入表（见类注释）。 */
    private val memo = HashMap<String, Lut3D>()

    /**
     * 取表；解析失败 / 文件不存在返回 null（渲染器据此跳过滤镜并**上报**，不让一个坏文件把编辑器带崩）。
     *
     * 首次解析某个 id 会**同步**读盘并 parse，调用方别在主线程的滑动路径上第一次点它——
     * 滤镜面板的缩略图就是先在后台把用到的 id 焐热（见 [FilterPanel]）。
     */
    fun get(assets: AssetManager, id: String): Lut3D? = getImpl(id) {
        assets.open("${FilterPresets.ASSET_DIR}/$id.cube").bufferedReader().use { it.readText() }
    }

    /**
     * [get] 的记忆化内核（读盘动作注入进来，方便单测钉住「失败不被永久缓存」这条语义——
     * 真实 [AssetManager] 在 JVM 单测里没有可控的失败注入点）。
     */
    internal fun getImpl(id: String, read: (String) -> String?): Lut3D? = synchronized(memo) {
        memo[id]?.let { return@synchronized it }
        val lut = runCatching { read(id) }.getOrNull()?.let(CubeLut::parse)
        if (lut == null) {
            Log.w(TAG, "滤镜 $id 未能解析（资产缺失或文件不合预期）；不缓存失败，下次调用会重试")
        } else {
            memo[id] = lut
        }
        lut
    }

    /** 宿主用的形式：绑定到 Context 的 assets，签名正是渲染管线要的 `(String) -> Lut3D?`。 */
    fun provider(context: Context): (String) -> Lut3D? = provider(context.applicationContext.assets)

    /**
     * 只有 [AssetManager] 时的形式（导出路径拿不到 Context，只有 assets）。
     *
     * 与 [provider]`(Context)` 共用同一份记忆化——它们是同一批表的同一个进程级缓存，
     * 分开记等于让导出再付一次 parse 的钱。
     */
    fun provider(assets: AssetManager): (String) -> Lut3D? = { id -> get(assets, id) }
}
