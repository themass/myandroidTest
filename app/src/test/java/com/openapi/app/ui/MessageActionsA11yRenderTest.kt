package com.openapi.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.openapi.chatkit.message.ChatMessageList
import com.openapi.chatkit.message.MessageMenuStyle
import com.openapi.chatkit.model.ChatItem
import com.openapi.chatkit.model.MessageAction
import com.openapi.chatkit.model.MessagePart
import com.openapi.chatkit.model.Sender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 消息操作的**读屏可达性**回归（整改清单 P2-16）。
 *
 * 以前复制/编辑/删除/重新生成/多选全挂在长按上，而 `combinedClickable`
 * 不把 `OnLongClick` 暴露成语义动作、`onClick = {}` 又是空操作——
 * 读屏用户双击只会听到「已激活」，**没有任何一条消息能被操作**。
 *
 * 修法的契约（这里逐条钉住）：
 * 1. 每条消息气泡的语义节点带 `customActions`，内容与长按菜单**同一批**操作；
 * 2. 执行某个 customAction = 点开菜单再点那一项（同一条 handleAction，含「多选」的本地拦截）。
 *
 * 放在 :app：chatkit 是库模块，开 includeAndroidResources 会资源链接失败（同目录先例注释）。
 */
@RunWith(RobolectricTestRunner::class)
// 真实窗口尺寸，否则 LazyColumn 首帧布局在 Robolectric 下量出 0 高度直接崩（同目录先例实测）。
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "zh-rCN-w411dp-h891dp",
)
class MessageActionsA11yRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<Pair<ChatItem, MessageAction>>()

    private fun assistant(text: String) = ChatItem(
        id = "1",
        sender = Sender.Assistant,
        parts = listOf(MessagePart.Text(text)),
    )

    private fun renderAssistant(text: String) {
        compose.setContent {
            ChatMessageList(
                items = listOf(assistant(text)),
                onAction = { item, action -> actions += item to action },
                menuStyle = MessageMenuStyle.Sheet,
                modifier = Modifier.size(400.dp, 700.dp),
            )
        }
    }

    /** 唯一的气泡动作集。一条消息只该有一个——多了说明动作挂重了。 */
    @Suppress("UNCHECKED_CAST")
    private fun bubbleActions(): List<CustomAccessibilityAction> {
        val nodes = compose
            .onAllNodes(SemanticsMatcher("带 customActions 的节点") { it.config.contains(SemanticsActions.CustomActions) })
            .fetchSemanticsNodes()
        assertEquals("每条消息必须恰好暴露一个 customActions 动作集（P2-16）", 1, nodes.size)
        return nodes[0].config.get(SemanticsActions.CustomActions) as List<CustomAccessibilityAction>
    }

    /** 最新一条助手消息的菜单项（zh）：菜单变了这里要跟着变，这正是「读屏动作=菜单」的钉法。 */
    private val assistantLabels = listOf("复制", "朗读", "分享", "重试", "多选", "删除")

    @Test
    fun bubbleExposesItsMenuActionsAsScreenReaderActions() {
        renderAssistant("你好呀")

        val labels = bubbleActions().map { it.label }
        assertTrue("读屏动作必须覆盖菜单里的全部操作，实际: $labels", labels.containsAll(assistantLabels))
    }

    /**
     * 读屏里选中动作 = 直接执行：和看得见的人「长按→点菜单项」殊途同归。
     * 不给读屏用户先弹一个他看不见的 Sheet 再让他找一行——终点动作必须走同一条 onAction。
     */
    @Test
    fun invokingTheScreenReaderActionRunsTheSameMessageAction() {
        renderAssistant("你好呀")

        val custom = bubbleActions()
        assertTrue(custom.first { it.label == "复制" }.action())
        assertTrue(custom.first { it.label == "删除" }.action())
        assertEquals(listOf(MessageAction.Copy, MessageAction.Delete), actions.map { it.second })
    }
}
