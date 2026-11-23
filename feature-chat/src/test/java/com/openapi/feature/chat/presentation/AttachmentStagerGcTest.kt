package com.openapi.feature.chat.presentation

import androidx.test.core.app.ApplicationProvider
import com.openapi.common.media.StagingGc
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 对话页暂存目录的孤儿清扫（cleanup 项 5 的第二处调用点）。
 *
 * 清扫逻辑本身在 :common 的 [StagingGc]（那里有用例钉年龄规则），这里只钉**这条路真的会扫**：
 * `AttachmentStager` 打开 `filesDir/chat_attachments` 时顺手扫一次，超龄的孤儿走、没到龄的留。
 * 两个累积点（这个目录 + 拍照的 `camera/`）共用同一份年龄规矩，不各写一套。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AttachmentStagerGcTest {

    @Test
    fun openingTheStagingDirSweepsStaleOrphansAndKeepsFreshOnes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File(context.filesDir, "chat_attachments").apply { mkdirs() }
        val orphan = File(dir, "img_old.jpg").apply { writeText("orphan") }
        assertTrue("设 mtime 失败", orphan.setLastModified(System.currentTimeMillis() - StagingGc.MAX_AGE_MILLIS - 60_000))
        val fresh = File(dir, "img_fresh.jpg").apply { writeText("fresh") }

        // 这两个文件没有任何本地行/托盘引用（这个用例只钉「这条路会扫」），
        // 所以白名单是空的——空必须写出来，不给默认值（复审 nit 1）。
        val opened = AttachmentStager.stagingDir(context, keep = emptySet())

        assertTrue("打开的是那个目录本身（不是别的名字）", opened.absolutePath.endsWith("chat_attachments"))
        assertFalse("超龄孤儿必须走", orphan.exists())
        assertTrue("没到龄的文件不能碰（另一次会话可能正在用它）", fresh.exists())
    }
}
