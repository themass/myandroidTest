package com.openapi.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.openapi.chatkit.message.ChatMessageList
import com.openapi.chatkit.message.MessageMenuStyle
import com.openapi.chatkit.model.ChatItem
import com.openapi.chatkit.model.ImageLayout
import com.openapi.chatkit.model.MessagePart
import com.openapi.chatkit.model.Sender
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 收纳盒角标的读屏说法（cleanup 项 12 + 复审 minor 4）。
 *
 * 角标给人看的是「2/3」，读屏要念「第 2 张，共 3 张」。语言资源带两个占位符
 * （`chatkit_stack_position`），填参数必须走 `stringResource(res, current, total)`
 * ——这是仓库约定（`MessageSelection` 里 `chatkit_selected_count` 同款），
 * 也是为什么 mediaview 那个参数是 `@Composable (Int, Int) -> String?`：
 * `stringResource` 只能在组合里调。以前这里先取回模板再 `String.format`，绕过了约定，
 * 而参数顺序填反（`total, current`）编译期没人拦。
 *
 * 所以这里钉的是**念出来的那句的顺序**：顶卡必须是「第 1 张，共 3 张」。
 * 放在 :app：只有 :app 的资源与主题能在这里链接（库模块开 includeAndroidResources 会失败，
 * 同目录先例注释）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "zh-rCN-w411dp-h891dp",
)
class StackBadgeA11yRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun renderImages(count: Int) {
        val part = MessagePart.Images(
            sources = (1..count).map { index -> "file:///no-such-image-$index.jpg" },
            layout = ImageLayout.Stack,
        )
        val item = ChatItem(id = "1", sender = Sender.User, parts = listOf(part))
        compose.setContent {
            ChatMessageList(
                items = listOf(item),
                onAction = { _, _ -> },
                menuStyle = MessageMenuStyle.Sheet,
                modifier = Modifier.size(400.dp, 700.dp),
            )
        }
    }

    @Test
    fun theStackBadgeReadsOutItsPositionInWords() {
        renderImages(3)

        // 顶卡是第 1 张、共 3 张：两个参数谁先谁后在这里立刻见分晓
        compose.onNodeWithContentDescription("第 1 张，共 3 张").assertExists()
    }

    @Test
    fun aSingleImageReportsNoPosition() {
        renderImages(1)

        val read = compose.onAllNodes(hasContentDescription("第 1 张，共 1 张")).fetchSemanticsNodes()
        assertTrue("单张图片没有位置可言，不该凭空念一个「共 1 张」", read.isEmpty())
        // 「2/3」那个人眼看到的字面这里**不钉**：clearAndSetSemantics 换掉的就是同一个
        // Text 的语义节点，语义查询看不见它，「断言它不在」会在整棵子树没渲染时也成立（假绿）。
        // 屏幕上那个数字还在不在属于像素，台账里登记为真机项（与 cleanup 项 11 同一批）。
    }
}
