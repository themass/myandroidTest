package com.openapi.common.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StorageCatalog] 的纯逻辑部分：登记、去重、体积格式化。
 * 真正的目录扫描需要 Android Context，留给 instrumented 测试。
 */
class StorageCatalogTest {

    private fun entry(
        name: String,
        root: StorageCatalog.Entry.Root = StorageCatalog.Entry.Root.FILES,
        userData: Boolean = false,
        onCleared: (() -> Unit)? = null,
    ) = StorageCatalog.Entry(name, root, "title-$name", "desc", userData, onCleared)

    @Test
    fun registerAndListPreservesOrder() {
        StorageCatalog.clearRegistryForTest()
        StorageCatalog.register(entry("a"))
        StorageCatalog.register(entry("b"))
        StorageCatalog.register(entry("c"))
        assertEquals(listOf("a", "b", "c"), StorageCatalog.registered().map { it.dirName })
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
        assertEquals(listOf("cache"), registered.filterNot { it.userData }.map { it.dirName })
        assertEquals(listOf("history"), registered.filter { it.userData }.map { it.dirName })
    }
}
