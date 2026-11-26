package com.openapi.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.openapi.chatkit.message.ChatMessageList
import com.openapi.chatkit.model.ChatItem
import com.openapi.chatkit.model.MessagePart
import com.openapi.chatkit.model.Sender
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A43-2/#11 第二层兜底的渲染回归（第一层在 ChatItemMapper，有单测）：
 * **零 part 且非 thinking 的消息整行不渲染**——用户投诉「取消后留一个空 AI 气泡」。
 * 组件库的底线：调用方递来一个没内容的行，也不能画出「头像占位 + 停止芯片」的幽灵行
 * （footer 早在 stream 期间就不挂，见 ChatMessageItem 的 assistantFooter）。
 *
 * 对照必须同时钉住，防止兜底过猛：
 * - thinking 占位（零 part 是它的正常形状）照常渲染「正在思考…」；
 * - 有内容的行不受影响。
 *
 * 放在 :app：chatkit 库模块没有 compose ui-test（A36 同一注释）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "zh-rCN-w411dp-h891dp",
)
class A43EmptyAssistantRenderTest {

    @get:Rule
    val compose = createComposeRule()

    /** 零内容、非 thinking：取消/空回复在 UI 模型里的形状。 */
    private val emptyAssistant = ChatItem(id = "a1", sender = Sender.Assistant, parts = emptyList())

    @Test
    fun emptyAssistantRowRendersNothing() {
        compose.setContent {
            ChatMessageList(
                items = listOf(emptyAssistant),
                onAction = { _, _ -> },
                // 停止芯片是幽灵行最显眼的残留：即使调用方给了入口，零内容行也不许出现它
                onStopGenerating = {},
                modifier = Modifier.size(400.dp, 700.dp),
            )
        }
        compose.onNodeWithTag("stop-generating").assertDoesNotExist()
        // 兜底前的形状：这一行会占位渲染（头像槽 + 空 Column）；现在整行连内容都不产出。
        // 用「正在思考…」「已发送」这类行内文案都为不存在来钉「什么都没画」。
        compose.onNodeWithText("正在思考…").assertDoesNotExist()
    }

    @Test
    fun emptyTextOnlyAssistantRowRendersNothing() {
        // parts 非空但只有一个空 Text：与 MessageContent 的 texts 过滤同口径，同样算零内容
        compose.setContent {
            ChatMessageList(
                items = listOf(
                    ChatItem(id = "a1", sender = Sender.Assistant, parts = listOf(MessagePart.Text(""))),
                ),
                onAction = { _, _ -> },
                onStopGenerating = {},
                modifier = Modifier.size(400.dp, 700.dp),
            )
        }
        compose.onNodeWithTag("stop-generating").assertDoesNotExist()
    }

    @Test
    fun thinkingPlaceholderStillRenders() {
        // 合法占位：零 part + thinking=true —— 兜底绝不能吞掉「正在思考…」
        compose.setContent {
            ChatMessageList(
                items = listOf(emptyAssistant.copy(thinking = true)),
                onAction = { _, _ -> },
                onStopGenerating = {},
                modifier = Modifier.size(400.dp, 700.dp),
            )
        }
        compose.onNodeWithText("正在思考…").assertIsDisplayed()
    }
}
