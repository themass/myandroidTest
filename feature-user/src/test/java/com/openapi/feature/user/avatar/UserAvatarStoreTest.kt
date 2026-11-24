package com.openapi.feature.user.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * [UserAvatarStore.installInto] 的「失败不覆盖旧头像」原子性（纯 JVM + temp 目录）。
 * pref 读写是薄壳（要 Context），不在这测——核心是**换文件**这一步。
 */
class UserAvatarStoreTest {

    private fun tempDir(): File =
        java.nio.file.Files.createTempDirectory("avatar_store").toFile().apply { deleteOnExit() }

    @Test
    fun installWritesCurrentJpg() {
        val dir = tempDir()
        val target = UserAvatarStore.installInto(dir, byteArrayOf(1, 2, 3))
        assertNotNull(target)
        assertEquals("current.jpg", target!!.name)
        assertEquals(3, target.readBytes().size)
        // 不留 tmp 残骸
        assertFalse(File(dir, "current.jpg.tmp").exists())
    }

    @Test
    fun reinstallReplacesAtomicallyAndLeavesNoTmp() {
        val dir = tempDir()
        UserAvatarStore.installInto(dir, byteArrayOf(1))
        val second = UserAvatarStore.installInto(dir, byteArrayOf(9, 9))
        assertNotNull(second)
        assertEquals(2, second!!.readBytes().size)
        assertEquals(false, File(dir, "current.jpg.tmp").exists())
        // 目录里只有 current.jpg
        assertEquals(listOf("current.jpg"), dir.list()!!.sorted())
    }

    @Test
    fun emptyBytesStillInstallWithoutClobberingSemantics() {
        // 空字节是合法入参（调用方保证不会发生：download 失败返回 null 而不是空数组）。
        val dir = tempDir()
        assertNotNull(UserAvatarStore.installInto(dir, ByteArray(0)))
    }
}
