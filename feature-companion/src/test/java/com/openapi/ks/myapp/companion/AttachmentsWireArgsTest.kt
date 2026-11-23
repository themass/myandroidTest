package com.openapi.ks.myapp.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `control.input` 的 attachments 参数体的**三个 wire 键**（cleanup 项 1）。
 *
 * `kind` / `upload_ids` / `client_msg_id` 以及 `kind` 的值 `"attachments"` 都是与服务端
 * 逐字约定的字符串：服务端按 `args["kind"]` 决定这条 control.input 走不走图片分派，按
 * `args["upload_ids"]` 取刚上传好的 id，按 `args["client_msg_id"]` 做幂等。以前它们是
 * [LiveKitCompanionTransport.sendAttachments] 里的内联字面量，**零测试**——改一个字母
 * 编译照过、两端照常握手，只是图片永远静默落不进回合（发出去的人看到 warn，用户只看到
 * 「她没看见我的图」）。`media.image` 那个 capability 串是同一款风险，那次我们已经付过学费。
 *
 * 这里能直接跑是因为参数体被抽成了 [attachmentsArgs]：`sendAttachments` 本体要 Room 级
 * 环境（Room + 协商完成的 ProtocolLink），那一层不是单测能搭的，而**串本身**不需要。
 *
 * 用 Robolectric：`org.json` 必须是真实现（Android stub 下 `optJSONArray` 一律回 null，
 * 断言会假绿——这是本仓已经确认过的坑）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AttachmentsWireArgsTest {

    @Test
    fun `the wire kind is the literal the server dispatches on`() {
        val args = attachmentsArgs(listOf("u1"), "att-1-1")

        assertEquals("服务端按 args[\"kind\"] 分派，这个串改一个字母就静默失联",
            "attachments", args.optString("kind"))
    }

    @Test
    fun `the upload ids ride under upload_ids in the order they were picked`() {
        val args = attachmentsArgs(listOf("u1", "u2", "u3"), "att-1-1")

        val ids = args.optJSONArray("upload_ids")
        assertTrue("键名必须是 upload_ids（服务端只认这个），实际 keys=${args.keys().asSequence().toList()}",
            ids != null)
        assertEquals(3, ids!!.length())
        assertEquals("顺序就是选定顺序，服务端按它挂图",
            listOf("u1", "u2", "u3"), (0 until ids.length()).map { ids.getString(it) })
    }

    @Test
    fun `the idempotency key rides under client_msg_id verbatim`() {
        val args = attachmentsArgs(listOf("u1"), "att-1700000000000-4")

        assertEquals("服务端拿它认「同一组附件」，重复补发才不会挂两遍",
            "att-1700000000000-4", args.optString("client_msg_id"))
    }
}
