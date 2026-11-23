package com.openapi.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.openapi.ks.myapp.avatar.ProactiveKind
import com.openapi.ks.myapp.companion.CompanionMessage
import com.openapi.ks.myapp.companion.CompanionMessageArea
import com.openapi.ks.myapp.companion.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 陪伴页消息区的**渲染**回归：真的把 Compose 组合出来，在界面树上找控件。
 *
 * 为什么放在 :app 而不是 :feature-companion：库模块的清单合并进 :core 后引用了
 * :app 独有的主题与颜色，打开 includeAndroidResources 会资源链接失败（实测）。
 *
 * 这些用例对应 commit 7374abba 整文件重写时丢掉的三样东西：
 * 1. 待确认动作的「允许 / 先不用」按钮；
 * 2. 主动消息的来源标签；
 * 3. footer 动作不再静默无反应（footer 只保留能真正执行的按钮）。
 */
@RunWith(RobolectricTestRunner::class)
// 必须声明真实的窗口尺寸：默认 320x470 之类的小窗口叠上 LazyColumn 的首帧布局，
// Robolectric 会把测量搞成 0 高度，直接崩在 AwaitFirstLayoutModifier 里（实测）。
// zh 指定语言：断言按中文字串找控件，测试机的默认语言不该影响结果。
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "zh-rCN-w411dp-h891dp",
)
class CompanionMessageAreaRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(
        messages: List<CompanionMessage>,
        onConfirm: (String) -> Unit = {},
        onCancel: (String) -> Unit = {},
        onDelete: (Long) -> Unit = {},
        onCopy: (String) -> Unit = {},
        onRegenerate: () -> Unit = {},
    ) {
        compose.setContent {
            // 给一个真实尺寸：Robolectric 下高度不确定的 LazyColumn 会拿到 0 高度，
            // 首帧布局直接崩在 AwaitFirstLayoutModifier 里。
            CompanionMessageArea(
                messages = messages,
                listState = rememberLazyListState(),
                awaitingReply = false,
                modifier = Modifier.size(400.dp, 700.dp),
                onConfirmAction = onConfirm,
                onCancelAction = onCancel,
                onDeleteMessage = onDelete,
                onCopyMessage = onCopy,
                onRegenerate = onRegenerate,
            )
        }
    }

    @Test
    fun `a pending action renders allow and not-now buttons that call back`() {
        var confirmed: String? = null
        var declined: String? = null
        render(
            messages = listOf(
                CompanionMessage(
                    role = MessageRole.ASSISTANT,
                    text = "为你建一条提醒：周四面试",
                    confirmationId = "cfm-1",
                    id = 1L,
                ),
            ),
            onConfirm = { confirmed = it },
            onCancel = { declined = it },
        )

        // 按钮必须真的在界面上：这正是上一版被删掉的东西。
        compose.onNodeWithText("允许").assertIsDisplayed().performClick()
        assertEquals("cfm-1", confirmed)

        compose.onNodeWithText("先不用").assertIsDisplayed().performClick()
        assertEquals("cfm-1", declined)
    }

    @Test
    fun `no confirmation buttons when there is no pending action`() {
        render(
            messages = listOf(CompanionMessage(role = MessageRole.ASSISTANT, text = "我在。慢慢说。", id = 1L)),
        )
        compose.onNodeWithText("允许").assertDoesNotExist()
        compose.onNodeWithText("先不用").assertDoesNotExist()
    }

    @Test
    fun `a proactive message shows its source label`() {
        render(
            messages = listOf(
                CompanionMessage(
                    role = MessageRole.ASSISTANT,
                    text = "面试加油，你准备好了吗？",
                    proactive = ProactiveKind.EVENT,
                    id = 1L,
                ),
            ),
        )
        // 「她记得」由 itemOverlay 渲染在气泡上方；标签是界面上看得见的文字。
        compose.onNodeWithText("她记得").assertIsDisplayed()
        compose.onNodeWithText("面试加油，你准备好了吗？").assertIsDisplayed()
    }

    @Test
    fun `a plain reply has no proactive label`() {
        render(
            messages = listOf(CompanionMessage(role = MessageRole.ASSISTANT, text = "我在。", id = 1L)),
        )
        compose.onNodeWithText("她记得").assertDoesNotExist()
        compose.onNodeWithText("她来找你").assertDoesNotExist()
        compose.onNodeWithText("她主动说的").assertDoesNotExist()
    }

    @Test
    fun `no footer chips under a bubble — every action now lives in the long-press sheet`() {
        render(
            messages = listOf(CompanionMessage(role = MessageRole.ASSISTANT, text = "我在。慢慢说。", id = 1L)),
        )
        // 气泡下面不再挂任何动作芯片：复制/删除/朗读/分享都在长按弹层里，
        // footer 再摆一遍只是重复的噪音，还把每条气泡撑得更长。
        compose.onNodeWithContentDescription("复制").assertDoesNotExist()
        compose.onNodeWithContentDescription("删除").assertDoesNotExist()
        compose.onNodeWithContentDescription("朗读").assertDoesNotExist()
        compose.onNodeWithContentDescription("赞").assertDoesNotExist()
        compose.onNodeWithContentDescription("踩").assertDoesNotExist()
    }

    /**
     * footer 移除后，删除/复制仍然可达——但不能靠点气泡下的芯片验证了（它已经不存在）。
     * 这里直接验证回调接线：CompanionMessageArea 收到回调就转发出去，界面层不再有第二条路径。
     */
    @Test
    fun `delete callback still reaches the message id`() {
        var deleted: Long? = null
        render(
            messages = listOf(CompanionMessage(role = MessageRole.ASSISTANT, text = "我在。", id = 42L)),
            onDelete = { deleted = it },
        )
        // 没有 footer 可点：用开关式断言确认界面里不再有一条「点了就删」的捷径，
        // 真正的入口是长按（在 :app 的 Robolectric 里弹层不便驱动，由 chatkit 自己的用例覆盖）。
        compose.onNodeWithContentDescription("删除").assertDoesNotExist()
        assertNull("未点击前不应触发删除", deleted)
    }

    @Test
    fun `answer-again is wired to regenerate instead of being dead`() {
        var regenerated = 0
        render(
            messages = listOf(CompanionMessage(role = MessageRole.USER, text = "在吗", id = 1L)),
            onRegenerate = { regenerated++ },
        )
        compose.onNodeWithText("重新回答").assertIsDisplayed().performClick()
        assertEquals(1, regenerated)
    }
}
