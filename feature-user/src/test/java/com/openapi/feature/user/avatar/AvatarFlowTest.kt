package com.openapi.feature.user.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [AvatarFlow] 转移表钉死（纯 JVM）。
 *
 * 重点不是「路径能走通」，而是三条产品红线：
 * 1. 非法转移被忽略而不是崩溃（连点/竞态兜底）；
 * 2. 失败记住断点，Retry 不重复已完成的阶段（尤其不重复上传、不重新生成）；
 * 3. Retake 清掉全部服务端中间 id。
 */
class AvatarFlowTest {

    @Test
    fun happyPathWalksEveryStageInOrder() {
        val flow = AvatarFlow()
        assertEquals(AvatarUiState.Idle, flow.state)

        flow.on(AvatarEvent.ImageStaged("/tmp/a.jpg"))
        assertEquals(AvatarUiState.Staged("/tmp/a.jpg"), flow.state)

        flow.on(AvatarEvent.UploadStarted)
        assertEquals(AvatarUiState.Uploading("/tmp/a.jpg"), flow.state)

        flow.on(AvatarEvent.UploadDone("u_src"))
        assertEquals(AvatarUiState.Generating("u_src"), flow.state)

        flow.on(AvatarEvent.GenerateDone("u_gen"))
        assertEquals(AvatarUiState.Previewing("u_gen"), flow.state)

        assertTrue(flow.canApply())
        flow.on(AvatarEvent.ApplyStarted)
        assertEquals(AvatarUiState.Applying("u_gen"), flow.state)

        flow.on(AvatarEvent.ApplyDone)
        assertEquals(AvatarUiState.Saved, flow.state)
        assertFalse(flow.canApply())
    }

    @Test
    fun illegalTransitionsAreIgnoredNotThrown() {
        val flow = AvatarFlow()
        // 没图就上传/应用/保存
        flow.on(AvatarEvent.UploadStarted)
        assertEquals(AvatarUiState.Idle, flow.state)
        flow.on(AvatarEvent.ApplyStarted)
        assertEquals(AvatarUiState.Idle, flow.state)
        flow.on(AvatarEvent.ApplyDone)
        assertEquals(AvatarUiState.Idle, flow.state)

        // 重复点「生成」：第二次 UploadStarted 在 Uploading 态被忽略
        flow.on(AvatarEvent.ImageStaged("/tmp/b.jpg"))
        flow.on(AvatarEvent.UploadStarted)
        flow.on(AvatarEvent.UploadStarted)
        assertEquals(AvatarUiState.Uploading("/tmp/b.jpg"), flow.state)

        // 没预览就 ApplyDone
        val f2 = AvatarFlow()
        f2.on(AvatarEvent.ImageStaged("/tmp/c.jpg"))
        f2.on(AvatarEvent.UploadStarted)
        f2.on(AvatarEvent.ApplyDone)
        assertEquals(AvatarUiState.Uploading("/tmp/c.jpg"), f2.state)
    }

    @Test
    fun failureDuringGenerateKeepsSourceIdAndRetriesWithoutReupload() {
        val flow = AvatarFlow()
        flow.on(AvatarEvent.ImageStaged("/tmp/a.jpg"))
        flow.on(AvatarEvent.UploadStarted)
        flow.on(AvatarEvent.UploadDone("u_src"))
        // Generating 态失败：sourceUploadId 已在状态里，失败态必须带着它
        flow.on(AvatarEvent.Failure(AvatarFailureStage.GENERATE, AvatarUserReason.SERVICE_UNAVAILABLE))
        val failed = flow.state as AvatarUiState.Failed
        assertEquals(AvatarFailureStage.GENERATE, failed.stage)
        assertEquals("u_src", failed.sourceUploadId)
        assertEquals(null, failed.avatarUploadId)

        flow.on(AvatarEvent.Retry)
        // 从生成续跑，不回上传
        assertEquals(AvatarUiState.Generating("u_src"), flow.state)
    }

    @Test
    fun failureDuringApplyKeepsAvatarIdAndRetriesWithoutRegenerating() {
        val flow = AvatarFlow()
        flow.on(AvatarEvent.ImageStaged("/tmp/a.jpg"))
        flow.on(AvatarEvent.UploadStarted)
        flow.on(AvatarEvent.UploadDone("u_src"))
        flow.on(AvatarEvent.GenerateDone("u_gen"))
        flow.on(AvatarEvent.ApplyStarted)
        flow.on(AvatarEvent.Failure(AvatarFailureStage.APPLY, AvatarUserReason.NETWORK))
        val failed = flow.state as AvatarUiState.Failed
        assertEquals("u_gen", failed.avatarUploadId)
        assertEquals("u_src", failed.sourceUploadId) // 断点信息逐层继承

        flow.on(AvatarEvent.Retry)
        assertEquals(AvatarUiState.Applying("u_gen"), flow.state)
    }

    @Test
    fun failureDuringUploadRetriesFromStagedFileWhenItStillExists() {
        val file = File.createTempFile("avatar_stage", ".jpg").apply { deleteOnExit() }
        val flow = AvatarFlow()
        flow.on(AvatarEvent.ImageStaged(file.absolutePath))
        flow.on(AvatarEvent.UploadStarted)
        flow.on(AvatarEvent.Failure(AvatarFailureStage.UPLOAD, AvatarUserReason.NETWORK))
        val failed = flow.state as AvatarUiState.Failed
        assertEquals(file.absolutePath, failed.stagedPath)

        flow.on(AvatarEvent.Retry)
        assertEquals(AvatarUiState.Uploading(file.absolutePath), flow.state)
    }

    @Test
    fun retryFallsBackToIdleWhenStagedFileVanished() {
        val flow = AvatarFlow()
        flow.on(AvatarEvent.ImageStaged("/definitely/not/here.jpg"))
        flow.on(AvatarEvent.UploadStarted)
        flow.on(AvatarEvent.Failure(AvatarFailureStage.UPLOAD, AvatarUserReason.NETWORK))
        flow.on(AvatarEvent.Retry)
        // 暂存文件丢了（缓存被清）→ 只能回选图，不假装可续
        assertEquals(AvatarUiState.Idle, flow.state)
    }

    @Test
    fun retryOnNonFailedStateIsNoOp() {
        val flow = AvatarFlow()
        flow.on(AvatarEvent.ImageStaged("/tmp/a.jpg"))
        flow.on(AvatarEvent.Retry)
        assertEquals(AvatarUiState.Staged("/tmp/a.jpg"), flow.state)
    }

    @Test
    fun retakeClearsAllServerSideIds() {
        val flow = AvatarFlow()
        flow.on(AvatarEvent.ImageStaged("/tmp/a.jpg"))
        flow.on(AvatarEvent.UploadStarted)
        flow.on(AvatarEvent.UploadDone("u_src"))
        flow.on(AvatarEvent.GenerateDone("u_gen"))
        flow.on(AvatarEvent.Failure(AvatarFailureStage.APPLY, AvatarUserReason.NETWORK))
        flow.on(AvatarEvent.Retake)
        assertEquals(AvatarUiState.Idle, flow.state)
        // Idle 之后 Retry 是 no-op，不能把旧 u_gen 复活
        flow.on(AvatarEvent.Retry)
        assertEquals(AvatarUiState.Idle, flow.state)
    }

    @Test
    fun failureFromIdleCarriesNothing() {
        val flow = AvatarFlow()
        flow.on(AvatarEvent.Failure(AvatarFailureStage.STAGE, AvatarUserReason.BAD_IMAGE))
        val failed = flow.state as AvatarUiState.Failed
        assertEquals(null, failed.stagedPath)
        assertEquals(null, failed.sourceUploadId)
        assertEquals(null, failed.avatarUploadId)
    }
}
