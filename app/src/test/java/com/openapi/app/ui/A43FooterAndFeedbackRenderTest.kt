package com.openapi.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.dp
import com.openapi.chatkit.message.ChatMessageList
import com.openapi.chatkit.model.ChatItem
import com.openapi.chatkit.model.MessageAction
import com.openapi.chatkit.model.MessagePart
import com.openapi.chatkit.model.Sender
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A43-8 的两条渲染回归：
 * 1. **footer 只挂最新一条助手消息**（用户反馈：每条历史回复下面都顶一排按钮，列表很吵）——
 *    用 footer 芯片的「复制」读屏标签计数钉住：两条历史回复整屏只画一排。
 * 2. **助手长按菜单加「赞/踩」**（footer 收窄后，历史回复的赞踩入口由长按菜单提供；
 *    读屏 customActions 与长按菜单同一批操作——P2-16 的契约，所以钉 customActions 就等于钉菜单）。
 *
 * 放在 :app：chatkit 库模块开 includeAndroidResources 会资源链接失败（A36 同一注释）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "zh-rCN-w411dp-h891dp",
)
class A43FooterAndFeedbackRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<Pair<ChatItem, MessageAction>>()

    private fun assistant(id: String, text: String) = ChatItem(
        id = id,
        sender = Sender.Assistant,
        parts = listOf(MessagePart.Text(text)),
    )

    @Suppress("UNCHECKED_CAST")
    private fun bubbleActions(): List<CustomAccessibilityAction> {
        val nodes = compose
            .onAllNodes(SemanticsMatcher("带 customActions 的节点") { it.config.contains(SemanticsActions.CustomActions) })
            .fetchSemanticsNodes()
        check(nodes.size == 1) { "单条消息应恰好一个 customActions 动作集，实际 ${nodes.size}" }
        return nodes[0].config.get(SemanticsActions.CustomActions) as List<CustomAccessibilityAction>
    }

    /** 只有最新一条助手消息挂 footer：整屏「复制」芯片恰好一排（两条历史回复不再各挂一排）。 */
    @Test
    fun footerRendersOnlyOnTheLatestAssistantMessage() {
        compose.setContent {
            ChatMessageList(
                items = listOf(assistant("a1", "旧回复"), assistant("a2", "最新回复")),
                onAction = { item, action -> actions += item to action },
                modifier = Modifier.size(400.dp, 700.dp),
            )
        }
        compose.onAllNodesWithContentDescription("复制").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("赞").assertCountEquals(1)
    }

    /** 长按菜单（=读屏 customActions 同一批）在助手分支加赞/踩，执行走同一条 onAction。 */
    @Test
    fun assistantMenuExposesLikeAndDislike() {
        compose.setContent {
            ChatMessageList(
                items = listOf(assistant("a1", "有文字的回复")),
                onAction = { item, action -> actions += item to action },
                modifier = Modifier.size(400.dp, 700.dp),
            )
        }
        val labels = bubbleActions().map { it.label }
        assertTrue("助手菜单必须含赞/踩，实际: $labels", labels.containsAll(listOf("赞", "踩")))
        // 执行语义：菜单项与 footer 芯片走同一条 handleAction
        assertTrue(bubbleActions().first { it.label == "赞" }.action())
        assertTrue(bubbleActions().first { it.label == "踩" }.action())
        assertTrue("赞/踩必须落到 onAction", actions.map { it.second } == listOf(MessageAction.Like, MessageAction.Dislike))
    }
}
