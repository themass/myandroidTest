package com.openapi.chatkit.message.parts

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「附件（含位置）+ 文字要不要共用一个气泡」的纯布局判定（202611 用户反馈：图文消息看着像两条消息；
 * A43-6：带配文的位置消息同样看着像两条——「位置消息应该是一个整体」）。
 *
 * 与 [imageArrangement] 同一提取理由：判断坐在 Compose 里观察不到，提出来才能被测试钉住。
 * 钉的是决策表本身，不测渲染。
 */
class AttachmentTextLayoutTest {

    @Test
    fun attachmentsAndTextShareOneBubble() {
        assertEquals(AttachmentTextLayout.Merged, attachmentTextLayout(hasAttachments = true, hasLocation = false, hasText = true))
    }

    @Test
    fun locationWithTextSharesOneBubble() {
        // A43-6：位置不是附件，但同样并入合并判定——位置卡 + 配文 = 一个气泡
        assertEquals(AttachmentTextLayout.Merged, attachmentTextLayout(hasAttachments = false, hasLocation = true, hasText = true))
        // 图片+位置+文字：仍是一个气泡
        assertEquals(AttachmentTextLayout.Merged, attachmentTextLayout(hasAttachments = true, hasLocation = true, hasText = true))
    }

    @Test
    fun eitherSideMissingKeepsTheOldSeparateLayout() {
        // 纯附件：裸排布照旧，不为合并而造一个空气泡
        assertEquals(AttachmentTextLayout.Separate, attachmentTextLayout(hasAttachments = true, hasLocation = false, hasText = false))
        // 纯位置：独立位置气泡照旧（A43-6 不回退这条）
        assertEquals(AttachmentTextLayout.Separate, attachmentTextLayout(hasAttachments = false, hasLocation = true, hasText = false))
        // 纯文字：单文字气泡照旧
        assertEquals(AttachmentTextLayout.Separate, attachmentTextLayout(hasAttachments = false, hasLocation = false, hasText = true))
        // 什么都没有（思考中等外围状态自己处理）
        assertEquals(AttachmentTextLayout.Separate, attachmentTextLayout(hasAttachments = false, hasLocation = false, hasText = false))
    }
}
