package com.openapi.common.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StorageCatalog] 的纯逻辑部分：登记、去重、体积格式化。
 * 真正的目录扫描需要 Android Context，留给 instrumented 测试。
 */
class StorageCatalogTest {

    private fun entry(
        name: String,
        root: StorageCatalog.Root = StorageCatalog.Root.FILES,
        userData: Boolean = false,
    ) = StorageCatalog.Entry(name, root, "title-$name", "desc", userData)

    @Test
    fun registerAndListPreservesOrder() {
        StorageCatalog.clearRegistryForTest()
        StorageCatalog.register(entry("a"))
        StorageCatalog.register(entry("b"))
        StorageCatalog.register(entry("c"))
        assertEquals(listOf("a", "b", "c"), StorageCatalog.registered().map { it.name })
    }

    @Test
    fun registeringSameDirTwiceOverwrites() {
        StorageCatalog.clearRegistryForTest()
        StorageCatalog.register(entry("a"))
        StorageCatalog.register(entry("a", userData = true))
        assertEquals(1, StorageCatalog.registered().size)
        assertTrue(StorageCatalog.registered().single().userData)
    }

    @Test
    fun formatSizeUsesReadableUnits() {
        assertEquals("0 B", StorageCatalog.formatSize(0))
        assertEquals("0 B", StorageCatalog.formatSize(-5))
        assertEquals("512 B", StorageCatalog.formatSize(512))
        assertEquals("1.0 KB", StorageCatalog.formatSize(1024))
        assertEquals("1.5 KB", StorageCatalog.formatSize(1536))
        assertEquals("2.0 MB", StorageCatalog.formatSize(2L * 1024 * 1024))
        assertEquals("1.0 GB", StorageCatalog.formatSize(1024L * 1024 * 1024))
    }

    @Test
    fun formatSizeDoesNotExceedUnitList() {
        // 2TB 仍然是 GB，不会输出 "2.0 TB"（units 里没有 TB）
        val out = StorageCatalog.formatSize(2L * 1024 * 1024 * 1024 * 1024)
        assertTrue(out, out.endsWith("GB"))
    }

    @Test
    fun userDataFlagSeparatesClearableFromProtected() {
        StorageCatalog.clearRegistryForTest()
        StorageCatalog.register(entry("cache", userData = false))
        StorageCatalog.register(entry("history", userData = true))
        val registered = StorageCatalog.registered()
        assertEquals(listOf("cache"), registered.filterNot { it.userData }.map { it.name })
        assertEquals(listOf("history"), registered.filter { it.userData }.map { it.name })
    }
    // ---- 新增：文件模式 / 自定义清理 ----

    @Test
    fun filesKindSplitsNamesOnPipe() {
        val e = StorageCatalog.Entry(
            name = "profile.jpg|banner.jpg",
            root = StorageCatalog.Root.FILES,
            title = "头像与背景",
            description = "",
            userData = true,
            kind = StorageCatalog.Kind.FILES,
        )
        // 名字按 | 拆分；根目录是 FILES
        assertEquals(StorageCatalog.Kind.FILES, e.kind)
        assertEquals(listOf("profile.jpg", "banner.jpg"), e.name.split('|'))
    }

    @Test
    fun externalDownloadsIsItsOwnRoot() {
        // Glide 缓存在外部存储，不能和内部 files/cache 混在一起统计
        assertEquals(3, StorageCatalog.Root.values().size)
        assertTrue(StorageCatalog.Root.values().contains(StorageCatalog.Root.EXTERNAL_DOWNLOADS))
    }

    @Test
    fun entryCanCarryCustomClearAction() {
        var cleared = false
        val e = StorageCatalog.Entry(
            name = "glide",
            root = StorageCatalog.Root.EXTERNAL_DOWNLOADS,
            title = "图片缓存",
            description = "",
            userData = false,
            onClear = { cleared = true },
        )
        // 只验证回调挂在条目上；真正调用需要 Context，属于 instrumented 测试。
        // 回调是 (Context) -> Unit，单测里不 invoke，只确认它被保存下来了。
        assertNotNull(e.onClear)
        assertFalse(cleared)
    }
}
