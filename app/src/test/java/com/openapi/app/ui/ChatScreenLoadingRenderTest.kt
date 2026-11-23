package com.openapi.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.openapi.chatkit.model.VoiceStyle
import com.openapi.feature.chat.presentation.ChatUiState
import com.openapi.feature.chat.presentation.ui.ChatScreen
import com.openapi.feature.chat.roles.domain.SelectedRole
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 对话页**加载态**的渲染回归（整改清单 P0-6.2）：
 * 历史还在读的时候，消息区必须显示「会话骨架」，而不是一片空白。
 *
 * 整改文档原话：`EmptyState.Loading` 以前的唯一消费点只是「不显示空状态」——
 * 也就是什么都不画。冷启动切会话时用户看到的就是空白页，
 * 分不清「在读」还是「读出来就是空的」。
 *
 * 为什么放在 :app 而不是 :feature-chat：库模块的清单合并进 :core 后引用了
 * :app 独有的主题与颜色，打开 includeAndroidResources 会资源链接失败（实测，
 * 见 CompanionMessageAreaRenderTest 同款说明）；骨架文案要走 stringResource，
 * 只能在 :app 渲染。ChatScreen 与 ChatUiState 是公开 API，测的就是真实装配。
 */
@RunWith(RobolectricTestRunner::class)
// 必须声明真实窗口尺寸：默认小窗口 + LazyColumn 首帧布局会让 Robolectric 量出 0 高度，
// 直接崩在 AwaitFirstLayoutModifier（见同目录先例的实测注释）。
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "zh-rCN-w411dp-h891dp",
)
class ChatScreenLoadingRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(state: ChatUiState) {
        compose.setContent {
            ChatScreen(
                state = state,
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

    /**
     * 加载中的会话不能画成空白：必须有可被读屏报出的骨架占位，
     * 而且不能把「有什么想聊的」这类空会话话术顶上来——
     * 那会让用户以为历史是空的。
     */
    @Test
    fun loadingHistoryRendersASkeletonNotABlankScreen() {
        render(ChatUiState(loading = true, messages = emptyList()))

        compose.onNodeWithContentDescription("正在加载历史").assertIsDisplayed()
        // 加载中不许出现空会话的开场话术（那等于告诉用户「这里没有消息」）
        compose.onNodeWithText("有什么想聊的，直接说就行").assertDoesNotExist()
    }

    /** 读完了（不再 loading、也没有消息）才回到空会话：骨架必须让位。 */
    @Test
    fun loadedAndStillEmptyFallsBackToTheBlankChat() {
        render(ChatUiState(loading = false, messages = emptyList()))

        compose.onNodeWithContentDescription("正在加载历史").assertDoesNotExist()
        compose.onNodeWithText("有什么想聊的，直接说就行").assertIsDisplayed()
    }

    /** 读失败也不许显示骨架：那是在暗示「还要再等等」，而真相是「读不出来了」。
     *  原因原样渲染——「没能读到历史：」前缀是写入 loadError 时加上的（EmptyState.kt），
     *  这里给的就是写入后的原文。 */
    @Test
    fun loadFailureShowsTheReasonNotASkeleton() {
        render(ChatUiState(loading = false, messages = emptyList(), loadError = "没能读到历史：网络不可用"))

        compose.onNodeWithContentDescription("正在加载历史").assertDoesNotExist()
        compose.onNodeWithText("没能读到历史：网络不可用").assertIsDisplayed()
    }
}
