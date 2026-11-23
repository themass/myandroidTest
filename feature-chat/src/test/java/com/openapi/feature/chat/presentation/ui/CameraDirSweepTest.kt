package com.openapi.feature.chat.presentation.ui

import androidx.test.core.app.ApplicationProvider
import com.openapi.common.media.StagingGc
import com.openapi.feature.chat.presentation.AttachmentStager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * `camera/` 的清扫挂在哪条线程上（复审 minor）。
 *
 * 整改前基线：`newCameraUri` 只有 `mkdirs()`。我加清扫时把 `listFiles()` + 逐个 `delete()`
 * 放进了这个方法，而它是「+」面板点击 lambda 里直接调的（`ChatRoute.launchCamera`）——
 * 主线程上的目录遍历与删除，在慢存储上就是点一下拍摄卡一下。
 * 现在清扫挪到 [AttachmentStager.stagingDir]（选择器/拍照结果回来时已经在 IO 线程），
 * 这里钉两件事：
 * 1. `newCameraUri` **不扫**（它必须留在主线程上能安全调）；
 * 2. `camera/` 的超龄孤儿照样被清——被选图那条路顺手清掉（覆盖没丢，只是换了线程），
 *    同一份年龄规矩。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CameraDirSweepTest {

    private val stale = System.currentTimeMillis() - StagingGc.MAX_AGE_MILLIS - 60_000

    private fun staleFile(dir: File, name: String): File =
        File(dir, name).apply {
            writeText("bytes")
            assertTrue("设 mtime 失败", setLastModified(stale))
        }

    @Test
    fun `the helper the click path calls does not sweep`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File(context.filesDir, AttachmentStager.CAMERA_DIR).apply { mkdirs() }
        val stale = staleFile(dir, "photo_old.jpg")

        // `newCameraUri` 的 uri 本身不在这钉：FileProvider 的 meta-data 只在 :app 的清单里
        // （:app 的 FileProviderAuthorityTest 钉常量与清单一致），本模块的 JVM 测试造不出 uri。
        // 线程这件事要钉的是它调的那个目录助手——清扫只能出现在 IO 那条路上。
        val opened = AttachmentStager.cameraDir(context)

        assertTrue("拍照目录就是 camera/", opened.absolutePath.endsWith("camera"))
        assertTrue("点击线程上不做删除（清扫在 IO 那条路）", stale.exists())
        assertTrue("FileProvider 要求父目录存在", opened.isDirectory)
    }

    @Test
    fun `the album path sweeps the camera dir too`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val camera = File(context.filesDir, AttachmentStager.CAMERA_DIR).apply { mkdirs() }
        val stale = staleFile(camera, "photo_old.jpg")
        val fresh = File(camera, "photo_new.jpg").apply { writeText("bytes") }

        // 白名单必传（复审 nit 1）：这里两个文件都没有任何引用方，空要写出来
        AttachmentStager.stagingDir(context, keep = emptySet())

        assertFalse("取消拍摄留下的超龄字节必须被下一次选图回收", stale.exists())
        assertTrue("没到龄的不碰", fresh.exists())
    }
}
