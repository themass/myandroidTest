package com.openapi.feature.chat.presentation.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.openapi.chatkit.message.rememberMessageSelection
import com.openapi.chatkit.message.MessageSelectionBar
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openapi.feature.chat.domain.model.ChatMessage
import com.openapi.feature.chat.presentation.ChatUiState
import com.openapi.feature.chat.presentation.EmptyState
import com.openapi.feature.chat.presentation.emptyStateFor
import com.openapi.chatkit.input.ChatComposer
import com.openapi.chatkit.message.ChatMessageList
import com.openapi.chatkit.message.parts.DefaultMessageText
import com.openapi.chatkit.model.ComposerAction
import com.openapi.chatkit.model.ComposerState
import com.openapi.chatkit.model.DraftItem
import com.openapi.chatkit.model.DraftType
import com.openapi.chatkit.model.MessageAction
import com.openapi.chatkit.model.Sender
import com.openapi.chatkit.model.VoiceStyle
import com.openapi.chatkit.theme.ChatKitTheme
import com.openapi.feature.chat.domain.model.MessageKind
import com.openapi.common.ui.theme.AppColors
import com.openapi.speech.audio.AudioPlayer
import com.openapi.feature.chat.R
import com.openapi.feature.chat.roles.domain.SelectedRole
import com.openapi.feature.chat.roles.presentation.RoleAvatar

/**
 * 位置草稿 chip 在托盘里的固定 id。它不是 DraftAttachment（状态里位置是单值字段），
 * 移除时 ChatRoute 按这个 id 翻给 onRemoveLocationDraft。
 */
const val LOCATION_DRAFT_ID = "draft-location"

/**
 * 用户侧头像的语义锚点。`ChatUserAvatarRenderTest` 靠它断言这一位真的接上了
 * `userAvatar` 插槽——这个 bug 的形态是「头像 lambda 写在 `avatar` 的 User 分支里，
 * 而 chatkit 从不在用户行调用 `avatar`」，不钉住就会再犯一次。
 */
const val USER_AVATAR_TAG = "chat-user-avatar"

/**
 * 对话页的组装层：角色头部 + 消息列表 + 输入栏 + 业务弹窗。
 *
 * 消息和输入的 UI 全部来自组件库 `:chatkit`；这里只做三件事：
 * 1. 把领域模型映射成组件库的 UI 模型（[ChatItemMapper]）；
 * 2. 提供业务插槽（头像、Markdown 渲染、「+」面板入口）；
 * 3. 处理需要弹窗确认的操作（删除确认、点踩反馈），其余操作原样交给 [onMessageAction]。
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    voiceStyle: VoiceStyle,
    composerActions: List<ComposerAction>,
    onVoiceStyleChange: (VoiceStyle) -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    /** A36②：停止生成——入口挂在生成中的气泡上（ChatMessageList），不再占用输入栏发送键位。 */
    onStop: () -> Unit,
    onRemoveDraft: (String) -> Unit,
    /** A36①：从「N 条排队中」徽标里撤回第 index 条（回到输入框）。 */
    onRemoveQueued: (Int) -> Unit = {},
    /** A40：「插话」——把第 index 条排队文字立刻提交服务端。 */
    onPromoteQueued: (Int) -> Unit = {},
    /** A40：「打断重发」——把第 index 条插到队首并取消当前回合。 */
    onInterruptQueued: (Int) -> Unit = {},
    /** 长按草稿托盘里的图片再编辑（S3）。不传 = 该页不提供编辑。 */
    onEditDraftImage: (String) -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onVoiceDraftSendText: (String) -> Unit = {},
    onVoiceDraftSendAudio: () -> Unit = {},
    onVoiceDraftCancel: () -> Unit = {},
    onVoiceRecordStart: () -> Unit,
    onVoiceRecordStop: () -> Unit,
    onVoiceRecordCancel: () -> Unit,
    onVoiceRecordToText: () -> Unit,
    onMessageAction: (ChatMessage, MessageAction) -> Unit,
    onFeedback: (ChatMessage, String) -> Unit,
    onCharacterSelected: (Int?) -> Unit,
    onCreateCharacter: (title: String, content: String) -> Unit,
    onUpdateCharacter: (id: Int, title: String, content: String) -> Unit,
    onDeleteCharacter: (id: Int) -> Unit,
    onRoleSelected: (SelectedRole) -> Unit,
    onRolePickerVisibleChange: (Boolean) -> Unit,
    onCallClick: () -> Unit,
    onSessionsVisibleChange: (Boolean) -> Unit,
    onSessionSelected: (Long) -> Unit,
    onCreateSession: (String) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onDeleteMessages: (List<String>) -> Unit,
    onClearConversation: () -> Unit,
    onRenameSession: (Long, String) -> Unit,
    onLoadOlder: () -> Unit,
    onMenuClick: (() -> Unit)?,
    /** 关掉错误条。错误信息本身来自 ViewModel，界面只负责显示与关闭。 */
    onDismissError: () -> Unit = {},
    /** 历史读取失败时的重试入口。 */
    onReload: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val playback by AudioPlayer.state.collectAsState()
    val identity = state.assistantIdentity()
    val items = remember(state.messages, playback, state.speakingMessageId, state.likedIds, state.dislikedIds) {
        ChatItemMapper.map(state.messages, playback, state.speakingMessageId, state.likedIds, state.dislikedIds)
    }
    val byId = remember(state.messages) { state.messages.associateBy { it.id } }
    // stringResource 不能在 remember 的计算块里调用（@DisallowComposableCalls），先取出来
    val locationChipName = stringResource(R.string.chat_attach_location)
    val composerState = remember(state.input, state.drafts, state.locationDraft, state.sending, state.streaming, state.recording, state.transcribing, state.partialText, state.editingMessageId, state.voiceDraft, state.queued) {
        ComposerState(
            text = state.input,
            editing = state.editingMessageId != null,
            voiceDraftText = state.voiceDraft?.text,
            drafts = buildList {
                state.drafts.mapTo(this) { draft ->
                    DraftItem(
                        id = draft.id,
                        type = when (draft.kind) {
                            MessageKind.IMAGE -> DraftType.Image
                            MessageKind.VIDEO -> DraftType.Video
                            else -> DraftType.File
                        },
                        // 视频草稿的缩略图（没有为空串，托盘退化成 MP4 徽标）
                        source = draft.staged.thumbnailPath ?: draft.staged.file.absolutePath,
                        name = draft.staged.displayName,
                        durationSeconds = draft.staged.durationSeconds,
                    )
                }
                // 位置 chip 排在附件后面；移除走 onRemoveDraft 同一个口——
                // 特殊 id 由 ChatRoute 翻给 onRemoveLocationDraft
                state.locationDraft?.let { loc ->
                    add(
                        DraftItem(
                            id = LOCATION_DRAFT_ID,
                            type = DraftType.Location,
                            source = "",
                            name = locationChipName,
                            locationLabel = String.format(java.util.Locale.US, "%.5f, %.5f", loc.latitude, loc.longitude),
                        ),
                    )
                }
            },
            sending = state.sending,
            generating = state.streaming,
            recording = state.recording,
            transcribing = state.transcribing,
            liveTranscript = state.partialText,
            // followup 队列的可见反馈：输入栏上方「N 条排队中」徽标（可展开撤回）。
            // chatkit 只画文字（按 index 回调），QueuedText 的本机 id 留在 feature-chat。
            queued = state.queued.map { it.text },
        )
    }
    val voiceLevel by com.openapi.speech.audio.VoiceRecorder.level.collectAsState()
    var deleteTarget by remember { mutableStateOf<ChatMessage?>(null) }
    val selection = rememberMessageSelection()
    val appContext = LocalContext.current
    val attachScope = rememberCoroutineScope()
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var feedbackTarget by remember { mutableStateOf<ChatMessage?>(null) }

    ChatKitTheme(colors = ChatPlatform.chatKitColors()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(AppColors.ChatBackground)
                .imePadding(),
        ) {
            RoleHeader(
                state = state,
                onClick = { onRolePickerVisibleChange(true) },
                onCallClick = onCallClick,
                onSessionsClick = { onSessionsVisibleChange(true) },
                onMenuClick = onMenuClick,
            )

            // 「加载更早」不再有常驻按钮（任务 4）：滚到顶自动加载，
            // 加载进行中由列表顶部的细进度条报告（见 ChatMessageList 的 loadingOlder）。

            ChatMessageList(
                items = items,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                hasMoreHistory = state.hasMoreHistory,
                loadingOlder = state.loadingOlder,
                onLoadOlder = onLoadOlder,
                selection = selection,
                attachmentActions = AttachmentActions::actionsFor,
                attachmentInfo = { target -> AttachmentActions.infoFor(appContext, target) },
                onAttachmentAction = { target, kind ->
                    attachScope.launch { AttachmentActions.perform(appContext, target, kind) }
                },
                menuStyle = ChatPlatform.messageMenuStyle,
                // 助手侧头像（连续同一发送者的第一条才画，见 ChatMessageList）。
                avatar = {
                    RoleAvatar(emoji = identity.emoji, colorArgb = identity.colorArgb, size = 36, imageRes = identity.avatarRes)
                },
                // 用户侧头像：陪伴页先有（2f4d4824），对话页此前一直没接这个插槽，
                // 右侧气泡贴到屏幕边（listHorizontalPadding 只有 2dp），看起来像边距坏了。
                // 接上后用户行右侧得到与助手行对称的 28dp 头像位 + 8dp 间距。
                // 素材沿用旧版默认头像（feature-chat 自带 ic_default_nan）；账号头像 URL 到位后只换这里。
                userAvatar = {
                    Image(
                        painter = painterResource(R.drawable.ic_default_nan),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // testTag 是给 ChatUserAvatarRenderTest 的锚点：这个 bug 就是「头像写进了
                        // avatar 的 User 分支」——那段代码在用户行永远不会被调用，肉眼在 JVM 上看不出，
                        // 而没有稳定锚点的渲染测试只能去比几何（Robolectric 的字体量不准）。
                        modifier = Modifier.fillMaxSize().clip(CircleShape).testTag(USER_AVATAR_TAG),
                    )
                },
                text = { item, value ->
                    if (item.sender == Sender.Assistant) {
                        MarkdownText(markdown = value, textColor = Color.Black)
                    } else {
                        DefaultMessageText(value)
                    }
                },
                onAction = { item, action ->
                    val message = byId[item.id] ?: return@ChatMessageList
                    when (action) {
                        MessageAction.Delete -> deleteTarget = message
                        MessageAction.Dislike -> feedbackTarget = message
                        else -> onMessageAction(message, action)
                    }
                },
                // A36②：停止生成挂在正在生成的气泡下方，发送键位让给发送（不再一进生成中就变取消键）
                onStopGenerating = onStop,
                emptyContent = {
                    // 读失败不能画成空会话：那和「历史被清空」没有区别。
                    when (val empty = emptyStateFor(state)) {
                        // 读历史的那几百毫秒画骨架，不留纯空白（整改清单 P0-6.2）：
                        // 空白页让人分不清「正在读」和「读出来就是空的」。
                        EmptyState.Loading -> LoadingChat()
                        is EmptyState.Failed -> LoadFailedChat(message = empty.message, onRetry = onReload)
                        EmptyState.Blank -> EmptyChat(identity = identity, onSuggestion = onInputChange)
                    }
                },
            )

            if (selection.active) {
                MessageSelectionBar(
                    count = selection.count,
                    onDelete = { confirmBatchDelete = true },
                    onCancel = selection::cancel,
                )
            }
            // 错误条必须真的渲染出来。`ChatUiState.error` 一直有字段、也一直被各个 Action 写入，
            // 但界面上从来没有渲染过它，于是「发送失败」在用户看来就是消息凭空消失——
            // 既看不到 HTTP 码，也不知道是登录态过期还是网络问题。放在输入框上方，
            // 这样它不会盖住消息，也不会被键盘顶走。
            state.error?.let { message ->
                ChatErrorBar(message = message, onDismiss = onDismissError)
            }
            if (!selection.active) {
            ChatComposer(
                state = composerState.copy(voiceLevel = voiceLevel),
                actions = composerActions,
                voiceStyle = voiceStyle,
                onVoiceStyleChange = onVoiceStyleChange,
                onTextChange = onInputChange,
                onSend = onSend,
                onRemoveDraft = onRemoveDraft,
                onRemoveQueued = onRemoveQueued,
                onPromoteQueued = onPromoteQueued,
                onInterruptQueued = onInterruptQueued,
                onEditDraftImage = onEditDraftImage,
                onVoiceStart = onVoiceRecordStart,
                onVoiceStop = onVoiceRecordStop,
                onVoiceCancel = onVoiceRecordCancel,
                onVoiceToText = onVoiceRecordToText,
                // 切换去向时会震动：先让录音把接下来一小段写成静音，马达声不进语音
                onVoiceIntentChange = { com.openapi.speech.audio.VoiceRecorder.muteFor() },
                onCancelEdit = onCancelEdit,
                onVoiceDraftSendText = onVoiceDraftSendText,
                onVoiceDraftSendAudio = onVoiceDraftSendAudio,
                onVoiceDraftCancel = onVoiceDraftCancel,
            )
            }
        }
    }

    if (state.rolePickerVisible) {
        RolePickerSheet(
            state = state,
            onDismiss = { onRolePickerVisibleChange(false) },
            onRoleSelected = onRoleSelected,
            onCharacterSelected = onCharacterSelected,
            onCreateCharacter = onCreateCharacter,
            onUpdateCharacter = onUpdateCharacter,
            onDeleteCharacter = onDeleteCharacter,
        )
    }

    if (state.sessionsVisible) {
        ChatSessionsSheet(
            sessions = state.sessions,
            currentSessionId = state.sessionId,
            onSelect = onSessionSelected,
            onCreate = onCreateSession,
            onRename = onRenameSession,
            onDelete = onDeleteSession,
            onClearCurrent = onClearConversation,
            onDismiss = { onSessionsVisibleChange(false) },
        )
    }

    deleteTarget?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.chat_delete_message_title)) },
            text = { Text(stringResource(R.string.chat_delete_message_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onMessageAction(message, MessageAction.Delete)
                    deleteTarget = null
                }) { Text(stringResource(R.string.ui_delete), color = AppColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.ui_cancel)) }
            },
        )
    }

    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text(stringResource(R.string.chat_batch_delete_title)) },
            text = { Text(stringResource(R.string.chat_batch_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmBatchDelete = false
                    val ids = selection.selectedIds
                    selection.cancel()
                    onDeleteMessages(ids)
                }) { Text(stringResource(R.string.ui_delete), color = AppColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) { Text(stringResource(R.string.ui_cancel)) }
            },
        )
    }

    feedbackTarget?.let { message ->
        FeedbackDialog(
            onDismiss = { feedbackTarget = null },
            onSubmit = { text ->
                onFeedback(message, text)
                feedbackTarget = null
            },
        )
    }
}

/**
 * 失败提示条：把 ViewModel 里那条错误的**原文**显示出来，而不是一句「请重试」。
 *
 * 为什么必须有它：`state.error` 以前只被写入、从未渲染。发送失败时用户看到的只是
 * 「消息没发出去、回复也没来」，无法判断是登录态过期、网络断了、还是服务端 5xx——
 * 这也是「chat 发消息失败概率很高、但不知道哪失败」查不动的原因。
 * 现在服务端返回的 errno/HTTP 码会原样出现在这里。
 */
@Composable
private fun ChatErrorBar(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(AppColors.Error.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = AppColors.Error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Text(stringResource(R.string.ui_close), color = AppColors.Error, style = MaterialTheme.typography.labelMedium)
        }
    }
}
