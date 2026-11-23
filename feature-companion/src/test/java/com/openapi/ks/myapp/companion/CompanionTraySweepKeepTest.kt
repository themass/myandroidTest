package com.openapi.ks.myapp.companion

import androidx.test.core.app.ApplicationProvider
import com.openapi.common.media.StagingGc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 待发条里那张图**不能**被这一次选图顺手扫掉（与对话页 `AttachmentSweepKeepTest` 同一类，
 * 复审 Important 的另一半）。
 *
 * `companion_attachments` 以前被认为「只有托盘引用、进程一退就没了」，所以清扫不带白名单。
 * 但托盘是进程内的：App 一直开着、图进了待发条却没发（先去回别的、或上传失败后没再按），
 * 一周后再选一张图 → 清扫按年龄把待发条里那张删了。用户看到的是：待发条上卡片还在，
 * 点发送得到「部分图片上传失败」，而且**怎么按都失败**——字节已经没了。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CompanionTraySweepKeepTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        CompanionDependencies.installStrings(FakeCompanionStrings())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a stale file sitting in the pending strip survives the sweep`() = runTest(dispatcher) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File(context.filesDir, CompanionViewModel.ATTACHMENT_DIR)
        dir.mkdirs()

        val inStrip = File(dir, "img_in_strip.jpg").apply {
            writeText("bytes")
            assertTrue("设 mtime 失败", setLastModified(System.currentTimeMillis() - StagingGc.MAX_AGE_MILLIS - 60_000))
        }
        val orphan = File(dir, "img_orphan.jpg").apply {
            writeText("bytes")
            assertTrue("设 mtime 失败", setLastModified(System.currentTimeMillis() - StagingGc.MAX_AGE_MILLIS - 60_000))
        }

        val vm = CompanionViewModel(
            api = null,
            transportFactory = { FakeCompanionTransport(reply = "") },
            inboxPollMillis = 60_000,
        )
        try {
            // 待发条里压着一张超龄的图（没发出去的那次留下的）
            vm.addPendingImage(inStrip)
            // 再选一次图：解码必然失败（没有 provider），但清扫照样跑过这个目录
            vm.onImagesPicked(
                context,
                listOf(android.net.Uri.parse("content://no.such.provider/1")),
                ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            )
            runCurrent()

            assertTrue("待发条引用中的字节必须留下——发送靠它", inStrip.exists())
            assertFalse("没人引用的超龄孤儿照旧走", orphan.exists())
            assertTrue("待发条里那张还在", vm.state.value.pendingImages.any { it.path == inStrip.absolutePath })
        } finally {
            vm.stopListening()
        }
    }
}
