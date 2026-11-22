package com.openapi.chatkit.message

import com.openapi.chatkit.model.AttachmentActionKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「长按一张附件，到底该不该弹菜单」的判定（纯 Kotlin，与 [com.openapi.chatkit.message.parts.imageArrangement]
 * 同一个提取理由：这个判断坐在组合里就钉不住——:app 的 compose 渲染测试观察不到「没有弹层」，
 * ModalBottomSheet 的把手本身没有稳定的语义节点）。
 *
 * 为什么值得单独钉：AttachmentActionSheet 是**无条件**渲染的。调用方没给动作时
 * （[ChatMessageList.attachmentActions] 默认空实现，陪伴页就是这种不配附件菜单的调用方），
 * 长按一张图会画出一个只有遮罩 + 拖条的零行面板——比毫无响应更像界面坏了。
 * 纯图片消息里这又是唯一的长按入口（图片挂 onAttachmentLongPress，行级长按只给文字/位置气泡），
 * 所以这条边一旦回归，用户第一时间就会撞上。
 */
class AttachmentMenuGateTest {

    private val oneAction = listOf(AttachmentActionKind.Download)

    @Test
    fun `no actions means no menu`() {
        assertFalse(shouldOpenAttachmentMenu(selectionActive = false, actions = emptyList()))
    }

    @Test
    fun `actions means the menu opens`() {
        assertTrue(shouldOpenAttachmentMenu(selectionActive = false, actions = oneAction))
    }

    /** 多选进行中：任何长按都不该弹菜单（与改动前同一语义，守卫不能把它放开）。 */
    @Test
    fun `selection mode keeps the menu closed even with actions`() {
        assertFalse(shouldOpenAttachmentMenu(selectionActive = true, actions = oneAction))
        assertFalse(shouldOpenAttachmentMenu(selectionActive = true, actions = emptyList()))
    }
}
