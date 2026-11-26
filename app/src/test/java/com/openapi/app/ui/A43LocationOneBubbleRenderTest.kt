package com.openapi.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
 * A43-6「位置消息是一个整体」的渲染回归。
 *
 * 真因（协调会话取证，基线 0e9662e7）：`attachmentTextLayout` 只数图片/文件/视频，
 * location 不在合并判定里 → 带配文的位置消息走 Separate：独立位置气泡一条 + 文字气泡一条。
 * 修法把 location 并入合并判定（判定表本体由 chatkit 的 `AttachmentTextLayoutTest` 钉），
 * 这里钉渲染落点：合并气泡存在且独立位置气泡不存在；纯位置消息**不回退**（仍独立一条）。
 *
 * 放在 :app：chatkit 库模块开 `includeAndroidResources` 会资源链接失败（A36 同一注释）。
 * 断言用 testTag 而不是几何像素：Robolectric 的字体度量与真机差得多（ChatUserAvatarRenderTest 同注）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "zh-rCN-w411dp-h891dp",
)
class A43LocationOneBubbleRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(items: List<ChatItem>) {
        compose.setContent {
            ChatMessageList(
                items = items,
                onAction = { _, _ -> },
                modifier = Modifier.size(400.dp, 700.dp),
            )
        }
    }

    private fun locationItem(vararg parts: MessagePart) = ChatItem(
        id = "loc-1",
        sender = Sender.User,
        parts = parts.toList(),
    )

    /** 位置 + 配文 = 一个合并气泡：位置卡在气泡里，不再有第二条独立位置气泡。 */
    @Test
    fun locationWithCaptionRendersExactlyOneBubble() {
        render(
            listOf(
                locationItem(
                    MessagePart.Location(31.2304, 121.4737, 120),
                    MessagePart.Text(" meeting 在这"),
                ),
            ),
        )
        compose.onNodeWithTag("merged-bubble").assertIsDisplayed()
        compose.onNodeWithTag("location-standalone").assertDoesNotExist()
        // 「一条消息一个气泡」：整屏就一个合并气泡节点
        check(compose.onAllNodesWithTag("merged-bubble").fetchSemanticsNodes().size == 1) {
            "带配文的位置消息必须只画一个合并气泡"
        }
    }

    /** 纯位置（无配文）保持独立位置气泡——A43-6 不能把它合并成空气泡。 */
    @Test
    fun pureLocationKeepsTheStandaloneBubble() {
        render(listOf(locationItem(MessagePart.Location(31.2304, 121.4737, null))))
        compose.onNodeWithTag("location-standalone").assertIsDisplayed()
        compose.onNodeWithTag("merged-bubble").assertDoesNotExist()
    }
}
