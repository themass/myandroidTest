package com.openapi.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * A36 的渲染回归：「发送一个消息就开始转圈，发送键变成取消键……不能变成取消键，
 * 应该允许用户继续发送消息，否则就变成了一问一答了。」
 *
 * ① 发送键常驻：生成中有文字时发送键仍在键位上（走 followup 队列），
 *   「N 条排队中」徽标可展开、逐条撤回；
 * ② 停止入口移到正在生成的气泡下方（testTag `stop-generating`），不再占用发送键位。
 *
 * 判定层（sendVisible / withdrawQueued）各有单测；这里钉的是「键位真的画出来了、
 * 位置对、点得到」——A36 的旧实现判定其实也在，坏就坏在画的位置。
 * 放在 :app 而不是 :chatkit：库模块开 includeAndroidResources 会资源链接失败
 * （同目录先例的实测注释）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "zh-rCN-w411dp-h891dp",
)
class A36SendKeyAndStopRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private var stopped = 0
    private var withdrawn = mutableListOf<Int>()

    private fun render(state: ChatUiState) {
        compose.setContent {
            ChatScreen(
                state = state,
                voiceStyle = VoiceStyle.Bar,
                composerActions = emptyList(),
                onVoiceStyleChange = {},
                onInputChange = {},
                onSend = {},
                onStop = { stopped += 1 },
                onRemoveDraft = {},
                onRemoveQueued = { withdrawn += it },
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
                modifier = Modifier.size(411.dp, 891.dp),
            )
        }
    }

    /** ①：生成中 + 输入框有文字 → 发送键仍在（不被替换成取消键）。 */
    @Test
    fun sendKeyStaysWhenGeneratingWithText() {
        render(ChatUiState(input = "再问一句", streaming = true))
        compose.onNodeWithContentDescription("发送").assertIsDisplayed()
    }

    /** ②：正在流式输出的助手气泡下方有「停止生成」；点它走 onStop。 */
    @Test
    fun stopButtonLivesOnTheGeneratingBubble() {
        render(
            ChatUiState(
                streaming = true,
                messages = listOf(
                    ChatMessage(id = "u1", role = ChatRole.USER, text = "问题", createdAtMillis = 1L),
                    ChatMessage(id = "a1", role = ChatRole.ASSISTANT, text = "半截回复", createdAtMillis = 2L, pending = true),
                ),
            ),
        )
        compose.onNodeWithTag("stop-generating").assertIsDisplayed().performClick()
        check(stopped == 1) { "点气泡上的停止必须回调 onStop（实到 $stopped）" }
    }

    /** ②：没有生成中的消息时，气泡上不挂停止芯片。 */
    @Test
    fun noStopChipWhenNothingGenerating() {
        render(
            ChatUiState(
                messages = listOf(
                    ChatMessage(id = "a1", role = ChatRole.ASSISTANT, text = "完整回复", createdAtMillis = 2L),
                ),
            ),
        )
        compose.onNodeWithTag("stop-generating").assertDoesNotExist()
    }

    /** ①：排队徽标显示条数；点「展开」出现逐条「撤回」；撤回走 onRemoveQueued(index)。 */
    @Test
    fun queueBadgeExpandsAndWithdraws() {
        render(ChatUiState(streaming = true, input = "还要发", queued = listOf("排队甲", "排队乙")))
        compose.onNodeWithText("2 条排队中").assertIsDisplayed()
        compose.onNodeWithText("展开").performClick()
        compose.onNodeWithTag("queued-withdraw-0").performClick()
        check(withdrawn == listOf(0)) { "撤回必须按 index 回调（实到 $withdrawn）" }
    }
}
