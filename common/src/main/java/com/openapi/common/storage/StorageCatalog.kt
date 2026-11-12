package com.openapi.common.storage

import android.content.Context
import java.io.File

/**
 * App 自己写出来的目录与文件，以及它们各自的用途、能否删除。
 *
 * 设计目标：**模块自己知道自己的数据在哪，App 负责给用户看**。
 * 各模块只暴露名字（常量），`:app` 在启动时把这些名字登记成用户能读懂的条目；
 * 设置页只依赖这张登记表，不认识任何具体模块。
 *
 * 为什么不自动扫描：`filesDir` / `cacheDir` 下有一堆系统与第三方库的目录
 * （glide 缓存、WebView、`okhttp` 临时文件……），自动扫描会把它们也列给用户，
 * 删掉可能弄坏别的功能。所以只列出**明确登记过**的条目，其余一律不碰。
 */
object StorageCatalog {

    /** 条目在哪个根目录下。 */
    enum class Root {
        /** `context.filesDir`：随 App 卸载删除，属于持久数据。 */
        FILES,

        /** `context.cacheDir`：系统可在低存储时自动清理。 */
        CACHE,

        /**
         * `getExternalFilesDir(DIRECTORY_DOWNLOADS)`：外部存储的 App 私有目录。
         * 卸载删除，但用户用文件管理器能看到。Glide 的图片缓存在这里。
         */
        EXTERNAL_DOWNLOADS,
    }

    /** 条目是目录还是一组文件。 */
    enum class Kind {
        /** [Entry.name] 是一级目录名，统计时递归。 */
        DIRECTORY,

        /** [Entry.name] 是 `filesDir` 下的文件名，**多个用 `|` 分隔**（头像 + 背景）。 */
        FILES,
    }

    /**
     * 清理方式。
     *
     * 大部分条目直接删内容即可；但有的东西**不能让外部随便删**——
     * Glide 的磁盘缓存是一个持有打开的日志文件的 DiskLruCache，直接删目录会让它的
     * 状态与磁盘不一致(后续读写报错)，必须走 [android.content.Context] 那边的 `clearDiskCache()`。
     * 这类条目在登记时提供 [Entry.onClear]。
     */
    data class Entry(
        /** 目录名，或文件模式下用 `|` 分隔的文件名列表。 */
        val name: String,
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
        val kind: Kind = Kind.DIRECTORY,
        /**
         * 自定义清理逻辑（可选）。给了就用它，不再直接删文件——
         * 用于"删文件会弄坏别的组件状态"的情况，例如 Glide。
         * 体积统计仍然按文件系统算。
         */
        val onClear: ((Context) -> Unit)? = null,
    )

    /** 一个条目的当前状态（体积等）。 */
    data class Usage(
        val entry: Entry,
        /** 用户可见的路径，多个文件时用换行分隔。 */
        val path: String,
        val sizeBytes: Long,
        val fileCount: Int,
        /** 条目不存在（没产生过数据）。 */
        val missing: Boolean,
    )

    private val entries = LinkedHashMap<String, Entry>()

    /** 登记一个条目（同一个 [Entry.name] 重复登记时后一次覆盖）。 */
    @Synchronized
    fun register(entry: Entry) {
        entries[entry.name] = entry
    }

    @Synchronized
    fun registered(): List<Entry> = entries.values.toList()

    @Synchronized
    fun clearRegistryForTest() {
        entries.clear()
    }

    /**
     * 算出每个已登记条目的当前体积。扫描在调用线程做，调用方应放到 IO 线程。
     */
    fun usage(context: Context): List<Usage> = registered().map { measure(context, it) }

    /**
     * 清理一个条目。调用方应放到 IO 线程。
     *
     * 有 [Entry.onClear] 的条目交给它；其余直接删内容（目录保留，文件直接删）。
     */
    fun clear(context: Context, entry: Entry) {
        val custom = entry.onClear
        if (custom != null) {
            runCatching { custom(context.applicationContext) }
            return
        }
        when (entry.kind) {
            Kind.DIRECTORY -> {
                val dir = dirOf(context, entry)
                if (!dir.isDirectory) return
                dir.listFiles()?.forEach { child -> runCatching { child.deleteRecursively() } }
            }
            Kind.FILES -> filesOf(context, entry).forEach { runCatching { it.delete() } }
        }
    }

    /** 清空所有**缓存**类条目（不碰用户数据）。 */
    fun clearCaches(context: Context) {
        registered().filterNot { it.userData }.forEach { entry ->
            runCatching { clear(context, entry) }
        }
    }

    // ------------------------------------------------------------------

    private fun dirOf(context: Context, entry: Entry): File = File(baseDir(context, entry.root), entry.name)

    private fun filesOf(context: Context, entry: Entry): List<File> =
        entry.name.split('|').filter { it.isNotBlank() }.map { File(baseDir(context, entry.root), it.trim()) }

    private fun baseDir(context: Context, root: Root): File {
        val app = context.applicationContext
        return when (root) {
            Root.FILES -> app.filesDir
            Root.CACHE -> app.cacheDir
            // 外部目录可能为 null（未挂载 / 被移除的存储卡）：退回到 cacheDir，
            // 宁可统计不到也不让设置页崩
            Root.EXTERNAL_DOWNLOADS -> app.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: app.cacheDir
        }
    }

    private fun measure(context: Context, entry: Entry): Usage = when (entry.kind) {
        Kind.DIRECTORY -> {
            val dir = dirOf(context, entry)
            if (!dir.isDirectory) {
                Usage(entry, dir.absolutePath, 0L, 0, missing = true)
            } else {
                var bytes = 0L
                var count = 0
                dir.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        bytes += f.length()
                        count++
                    }
                }
                Usage(entry, dir.absolutePath, bytes, count, missing = false)
            }
        }
        Kind.FILES -> {
            val files = filesOf(context, entry)
            val existing = files.filter { it.isFile }
            if (existing.isEmpty()) {
                Usage(entry, files.joinToString("\n") { it.absolutePath }, 0L, 0, missing = true)
            } else {
                Usage(
                    entry = entry,
                    path = files.joinToString("\n") { it.absolutePath },
                    sizeBytes = existing.sumOf { it.length() },
                    fileCount = existing.size,
                    missing = false,
                )
            }
        }
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
