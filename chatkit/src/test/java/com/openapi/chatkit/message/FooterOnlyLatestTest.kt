package com.openapi.chatkit.message

import com.openapi.chatkit.model.ChatItem
import com.openapi.chatkit.model.MessageAction
import com.openapi.chatkit.model.Sender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「footer 挂在哪些助手消息上」的判定（A43-8，20261126 用户反馈：每条历史回复下面都顶着一排
 * 按钮，列表很吵——只有最新一条该挂）。
 *
 * 与 [shouldOpenAttachmentMenu] 同一提取理由：判定坐在组合里观察不到，提出来才能被测试钉住。
 * 非最新一条并没有失去操作：长按菜单仍然全部提供（见 menuItemsFor / ChatMenuTargetsTest）。
 */
class FooterOnlyLatestTest {

    private val five = listOf(MessageAction.Copy, MessageAction.Speak, MessageAction.Like, MessageAction.Dislike, MessageAction.Delete)
    private val provider: (ChatItem) -> List<MessageAction> = { five }

    private fun item(id: String, sender: Sender = Sender.Assistant) = ChatItem(
        id = id,
        sender = sender,
        parts = emptyList(),
    )

    @Test
    fun `only the latest assistant message gets footer actions`() {
        assertEquals(five, footerActionsFor(item("a2"), latestAssistantId = "a2", footerActions = provider))
        assertEquals(emptyList<MessageAction>(), footerActionsFor(item("a1"), latestAssistantId = "a2", footerActions = provider))
    }

    @Test
    fun `user messages never get footer actions`() {
        assertEquals(emptyList<MessageAction>(), footerActionsFor(item("u1", Sender.User), latestAssistantId = "a1", footerActions = provider))
    }

    @Test
    fun `no assistant in the list means nobody gets a footer`() {
        assertEquals(emptyList<MessageAction>(), footerActionsFor(item("u1", Sender.User), latestAssistantId = null, footerActions = provider))
        assertEquals(emptyList<MessageAction>(), footerActionsFor(item("a1"), latestAssistantId = null, footerActions = provider))
    }

    /** 自定义 provider（陪伴页只取一部分芯片）原样透传，不被判定吃掉或替换。 */
    @Test
    fun `custom provider set is passed through for the latest only`() {
        val custom: (ChatItem) -> List<MessageAction> = { listOf(MessageAction.Copy) }
        assertEquals(listOf(MessageAction.Copy), footerActionsFor(item("a1"), "a1", custom))
        assertTrue(footerActionsFor(item("a0"), "a1", custom).isEmpty())
    }
}
