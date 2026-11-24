package com.openapi.chatkit.message.parts

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「附件 + 文字要不要共用一个气泡」的纯布局判定（202611 用户反馈：图文消息看着像两条消息）。
 *
 * 与 [imageArrangement] 同一提取理由：判断坐在 Compose 里观察不到，提出来才能被测试钉住。
 * 钉的是决策表本身，不测渲染。
 */
class AttachmentTextLayoutTest {

    @Test
    fun attachmentsAndTextShareOneBubble() {
        assertEquals(AttachmentTextLayout.Merged, attachmentTextLayout(hasAttachments = true, hasText = true))
    }

    @Test
    fun eitherSideMissingKeepsTheOldSeparateLayout() {
        // 纯附件：裸排布照旧，不为合并而造一个空气泡
        assertEquals(AttachmentTextLayout.Separate, attachmentTextLayout(hasAttachments = true, hasText = false))
        // 纯文字：单文字气泡照旧
        assertEquals(AttachmentTextLayout.Separate, attachmentTextLayout(hasAttachments = false, hasText = true))
        // 什么都没有（思考中等外围状态自己处理）
        assertEquals(AttachmentTextLayout.Separate, attachmentTextLayout(hasAttachments = false, hasText = false))
    }
}
