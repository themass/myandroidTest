package com.openapi.ks.myapp.companion

import androidx.test.core.app.ApplicationProvider
import com.openapi.common.media.StagingGc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 选图落盘那条路的可测性（cleanup 项 6）：`onImagesPicked` 与它的暂存目录以前**零测试引用**
 * ——整段结构（哪个目录、文件名怎么来、超龄孤儿谁清）只能靠读代码确认。
 *
 * 这里钉两件事：
 * 1. 暂存目录就是 `filesDir/companion_attachments`（`ATTACHMENT_DIR` 那个常量与真实路径同源）；
 * 2. 走到这个目录时会顺手扫掉超龄孤儿（:common 的 [StagingGc]，与对话页共用一份）。
 *
 * 图片解码本身不在这里钉（`MediaStager` 在 :common 有自己的用例）：JVM 上读不到真图，
 * 所以喂一个解析不了的 uri，让 `stageImage` 回 null 走 `continue`——**清扫发生在解码之前**，
 * 这条断言与解码成不成功无关，正是它该有的耦合方向。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CompanionStagingGcTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        CompanionDependencies.installStrings(FakeCompanionStrings())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `picking images sweeps stale orphans out of the staging dir`() = runTest(dispatcher) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File(context.filesDir, CompanionViewModel.ATTACHMENT_DIR)
        dir.mkdirs()

        val orphan = File(dir, "img_old.jpg").apply { writeText("orphan") }
        assertTrue("设 mtime 失败", orphan.setLastModified(System.currentTimeMillis() - StagingGc.MAX_AGE_MILLIS - 60_000))
        val fresh = File(dir, "img_fresh.jpg").apply { writeText("fresh") }

        val vm = CompanionViewModel(
            api = null,
            transportFactory = { FakeCompanionTransport(reply = "") },
            inboxPollMillis = 60_000,
        )
        // 一个没有 provider 的 uri：解码必然失败（stageImage 回 null → continue），
        // 但暂存目录已经被打开并清扫过。
        vm.onImagesPicked(
            context,
            listOf(android.net.Uri.parse("content://no.such.provider/1")),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        runCurrent()

        assertFalse("超龄孤儿必须被这次选图顺手清掉", orphan.exists())
        assertTrue("没到龄的文件不能碰", fresh.exists())
        assertEquals("这一轮没有图片进待发条（解码失败那条路）", 0, vm.state.value.pendingImages.size)
        vm.stopListening()
    }
}
