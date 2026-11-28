package com.openapi.ks.myapp.companion

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 催话的 wire 形状（C7 空闲分支）钉在 `control.input{kind:"nudge", nudge_id}`——
 * 与 touch / attachments 同一入口、同一 `kind` 判别（attachmentsArgs 的同款教训：
 * 内联字面量改一个字母编译照过，只是功能永远静默失效）。
 *
 * 契约定在 `docs/design/2026-11-28-heard-prefix-feedback-contract.md` §3。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NudgeArgsTest {

    @Test
    fun `nudge args carry kind and idempotency key`() {
        val args = nudgeArgs("nudge-7")
        assertEquals("nudge", args.getString("kind"))
        assertEquals("nudge-7", args.getString("nudge_id"))
    }

    @Test
    fun `nudge id survives as the server-side dedup key`() {
        // 幂等靠 nudge_id（服务端按它判重）；两个 id 必须不同——
        // 与 attachments 的 client_msg_id 同一个理由。
        val a = nudgeArgs("nudge-1")
        val b = nudgeArgs("nudge-2")
        assertEquals(false, a.getString("nudge_id") == b.getString("nudge_id"))
    }
}
