package com.openapi.common.storage

import android.content.Context
import java.io.File

/**
 * App 自己写出来的目录，以及它们各自的用途、能否删除。
 *
 * 设计目标：**模块自己知道自己的目录，App 负责给用户看**。
 * 各模块只暴露目录名（常量），`:app` 在启动时把这些名字登记成用户能读懂的条目；
 * 设置页只依赖这张登记表，不认识任何具体模块。
 *
 * 为什么不自动扫描：`filesDir` / `cacheDir` 下有一堆系统与第三方库的目录
 * （glide 缓存、WebView、`okhttp` 临时文件……），自动扫描会把它们也列给用户，
 * 删掉可能弄坏别的功能。所以只列出**明确登记过**的目录，其余一律不碰。
 */
object StorageCatalog {

    /** 一个目录的说明。 */
    data class Entry(
        /** 目录名（`filesDir` 或 `cacheDir` 下的一级目录名）。 */
        val dirName: String,
        /** 属于哪一个存储根：内部 files 还是 cache。 */
        val root: Root,
        /** 用户看得懂的名字，例如「解题历史」。 */
        val title: String,
        /** 一句话说明里面是什么、删掉会怎样。 */
        val description: String,
        /**
         * 删除后是否不可恢复的用户数据。
         * true → 弹确认框并要求明确点「删除」；false → 缓存，直接清。
         */
        val userData: Boolean,
        /** 删除后是否需要通知相关模块刷新（由登记方提供回调）。 */
        val onCleared: (() -> Unit)? = null,
    ) {
        enum class Root { FILES, CACHE }
    }

    /** 一个目录的当前状态（体积等）。 */
    data class Usage(
        val entry: Entry,
        val path: String,
        val sizeBytes: Long,
        val fileCount: Int,
        /** 目录不存在（没产生过数据）。 */
        val missing: Boolean,
    )

    private val entries = LinkedHashMap<String, Entry>()

    /** 登记一个目录（同一个目录名重复登记时后一次覆盖）。 */
    @Synchronized
    fun register(entry: Entry) {
        entries[entry.dirName] = entry
    }

    @Synchronized
    fun registered(): List<Entry> = entries.values.toList()

    @Synchronized
    fun clearRegistryForTest() {
        entries.clear()
    }

    /**
     * 算出每个已登记目录的当前体积。扫描在调用线程做，调用方应放到 IO 线程。
     *
     * 只统计目录自身的文件，不跟随符号链接（不递归到别的目录之外）。
     */
    fun usage(context: Context): List<Usage> =
        registered().map { entry -> measure(context, entry) }

    /** 清空一个目录里的内容（保留目录本身）。调用方应放到 IO 线程。 */
    fun clear(context: Context, entry: Entry) {
        val dir = dirOf(context, entry)
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { child ->
            runCatching { child.deleteRecursively() }
        }
        entry.onCleared?.invoke()
    }

    /** 清空所有**缓存**类目录（不碰用户数据）。 */
    fun clearCaches(context: Context) {
        registered().filterNot { it.userData }.forEach { entry ->
            runCatching { clear(context, entry) }
        }
    }

    private fun dirOf(context: Context, entry: Entry): File = when (entry.root) {
        Entry.Root.FILES -> File(context.applicationContext.filesDir, entry.dirName)
        Entry.Root.CACHE -> File(context.applicationContext.cacheDir, entry.dirName)
    }

    private fun measure(context: Context, entry: Entry): Usage {
        val dir = dirOf(context, entry)
        if (!dir.isDirectory) {
            return Usage(entry, dir.absolutePath, 0L, 0, missing = true)
        }
        var bytes = 0L
        var count = 0
        dir.walkTopDown().forEach { f ->
            if (f.isFile) {
                bytes += f.length()
                count++
            }
        }
        return Usage(entry, dir.absolutePath, bytes, count, missing = false)
    }

    /** 人类可读的体积。0 显示「0 B」，小于 1KB 也显示字节数。 */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var i = 0
        while (value >= 1024 && i < units.lastIndex) {
            value /= 1024
            i++
        }
        return if (i == 0) "${bytes} ${units[0]}" else String.format(java.util.Locale.US, "%.1f %s", value, units[i])
    }
}
