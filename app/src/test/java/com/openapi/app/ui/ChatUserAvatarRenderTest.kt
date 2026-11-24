package com.openapi.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import com.openapi.feature.chat.presentation.ui.USER_AVATAR_TAG
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.openapi.chatkit.model.VoiceStyle
import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.domain.model.ChatRole
import com.openapi.feature.chat.presentation.ChatUiState
import com.openapi.feature.chat.presentation.ui.ChatScreen
import com.openapi.feature.chat.roles.domain.SelectedRole
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 对话页**用户侧头像**的渲染回归（真机报告：右侧气泡贴屏幕边、用户头像不见了）。
 *
 * 根因不是边距被改坏，而是插槽接错：`:chatkit` 只在**助手行**调用 `avatar`
 * （`ChatMessageItem` 里的 `if (!isUser)`），用户行用的是另一个插槽 `userAvatar`
 * （陪伴页 `2f4d4824` 接的）。对话页把用户头像写在 `avatar` 的
 * `if (item.sender == Sender.User)` 分支里——那段 lambda 永远不会被调用。
 * 于是用户行右侧没有任何东西占位，气泡直接贴到 2dp 的列表留白上，
 * 看上去就像「左边距不对 + 头像丢了」。
 *
 * 断言用 testTag 而不是几何：Robolectric 的字体度量与真机差得多，
 * 量气泡边缘会钉出一堆假数字；「头像这一位到底有没有被接上」才是这片要守的东西。
 *
 * 放在 :app 而不是 :feature-chat：库模块打开 includeAndroidResources 会资源链接失败
 * （同目录 `ChatScreenLoadingRenderTest` 的实测注释）。
 */
@RunWith(RobolectricTestRunner::class)
// 真实窗口尺寸：默认小窗口 + LazyColumn 首帧会让 Robolectric 量出 0 高度，
// 直接崩在 AwaitFirstLayoutModifier（同目录先例的实测注释）。
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "zh-rCN-w411dp-h891dp",
)
class ChatUserAvatarRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(vararg messages: ChatMessage) {
        compose.setContent {
            ChatScreen(
                state = ChatUiState(messages = messages.toList()),
                voiceStyle = VoiceStyle.Bar,
                composerActions = emptyList(),
                onVoiceStyleChange = {},
                onInputChange = {},
                onSend = {},
                onStop = {},
                onRemoveDraft = {},
                onVoiceRecordStart = {},
                onVoiceRecordStop = {},
                onVoiceRecordCancel = {},
                onVoiceRecordToText = {},
                onMessageAction = { _, _ -> },
                onFeedback = { _, _ -> },
                onCharacterSelected = {},
                onCreateCharacter = { _, _ -> },
                onUpdateCharacter = { _, _, _ -> },
                onDeleteCharacter = {},
                onRoleSelected = { _: SelectedRole -> },
                onRolePickerVisibleChange = {},
                onCallClick = {},
                onSessionsVisibleChange = {},
                onSessionSelected = {},
                onCreateSession = {},
                onDeleteSession = {},
                onDeleteMessages = {},
                onClearConversation = {},
                onRenameSession = { _, _ -> },
                onLoadOlder = {},
                onMenuClick = null,
                modifier = Modifier.size(400.dp, 700.dp),
            )
        }
    }

    private fun message(id: String, role: ChatRole, text: String) = ChatMessage(
        id = id, role = role, text = text, createdAtMillis = 1_700_000_000_000L,
    )

    /** 用户行必须渲染用户侧头像（走 `userAvatar` 插槽）。整改前这一位是空的。 */
    @Test
    fun userRowRendersAnAvatar() {
        render(message("u1", ChatRole.USER, "用户正文"))

        compose.onNodeWithTag(USER_AVATAR_TAG).assertIsDisplayed()
    }

    /**
     * 连续同一发送者只画一个头像（chatkit 的规则：每行顶一个反而看不出哪条是新的）。
     * 第二行留的是等宽空位——所以气泡右边缘仍对齐，只是不重复画头像。
     */
    @Test
    fun consecutiveUserRowsDrawOneAvatar() {
        render(
            message("u1", ChatRole.USER, "第一条"),
            message("u2", ChatRole.USER, "第二条"),
        )

        compose.onAllNodesWithTag(USER_AVATAR_TAG).assertCountEquals(1)
    }

    /** 只有助手消息时不该出现用户侧头像。 */
    @Test
    fun assistantOnlyRowHasNoUserAvatar() {
        render(message("a1", ChatRole.ASSISTANT, "助手正文"))

        compose.onNodeWithTag(USER_AVATAR_TAG).assertDoesNotExist()
    }
}

