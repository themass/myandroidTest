package com.openapi.chat.presentation.ui

import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.openapi.chatkit.message.rememberMessageSelection
import com.openapi.chatkit.message.MessageSelectionBar
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.presentation.ChatUiState
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
import com.openapi.chat.domain.model.MessageKind
import com.openapi.common.design.theme.AppColors
import com.openapi.common.design.theme.AppSpacing
import com.openapi.common.design.theme.AppTypography
import com.openapi.core.media.AudioPlayer
import com.openapi.feature.chat.R
import com.openapi.roles.domain.SelectedRole
import com.openapi.roles.presentation.RoleAvatar

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
    onStop: () -> Unit,
    onRemoveDraft: (String) -> Unit,
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
    modifier: Modifier = Modifier,
) {
    val playback by AudioPlayer.state.collectAsState()
    val identity = state.assistantIdentity()
    val items = remember(state.messages, playback, state.speakingMessageId, state.likedIds, state.dislikedIds) {
        ChatItemMapper.map(state.messages, playback, state.speakingMessageId, state.likedIds, state.dislikedIds)
    }
    val byId = remember(state.messages) { state.messages.associateBy { it.id } }
    val composerState = remember(state.input, state.drafts, state.sending, state.streaming, state.recording, state.transcribing, state.partialText, state.editingMessageId, state.voiceDraft) {
        ComposerState(
            text = state.input,
            editing = state.editingMessageId != null,
            voiceDraftText = state.voiceDraft?.text,
            drafts = state.drafts.map { draft ->
                DraftItem(
                    id = draft.id,
                    type = if (draft.kind == MessageKind.IMAGE) DraftType.Image else DraftType.File,
                    source = draft.staged.file.absolutePath,
                    name = draft.staged.displayName,
                )
            },
            sending = state.sending,
            generating = state.streaming,
            recording = state.recording,
            transcribing = state.transcribing,
            liveTranscript = state.partialText,
        )
    }
    val voiceLevel by com.openapi.core.media.VoiceRecorder.level.collectAsState()
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

            if (state.hasMoreHistory) {
                TextButton(
                    onClick = onLoadOlder,
                    enabled = !state.loadingOlder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(if (state.loadingOlder) R.string.chat_loading_older else R.string.chat_load_older),
                        color = AppColors.Primary,
                    )
                }
            }

            ChatMessageList(
                items = items,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                selection = selection,
                attachmentActions = AttachmentActions::actionsFor,
                attachmentInfo = { target -> AttachmentActions.infoFor(appContext, target) },
                onAttachmentAction = { target, kind ->
                    attachScope.launch { AttachmentActions.perform(appContext, target, kind) }
                },
                menuStyle = ChatPlatform.messageMenuStyle,
                avatar = { item ->
                    if (item.sender == Sender.User) {
                        Image(
                            painter = painterResource(R.drawable.ic_default_nan),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        RoleAvatar(emoji = identity.emoji, colorArgb = identity.colorArgb, size = 36, imageRes = identity.avatarRes)
                    }
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
                emptyContent = {
                    if (!state.loading) EmptyChat(identity = identity, onSuggestion = onInputChange)
                },
            )

            if (selection.active) {
                MessageSelectionBar(
                    count = selection.count,
                    onDelete = { confirmBatchDelete = true },
                    onCancel = selection::cancel,
                )
            }
            if (!selection.active) {
            ChatComposer(
                state = composerState.copy(voiceLevel = voiceLevel),
                actions = composerActions,
                voiceStyle = voiceStyle,
                onVoiceStyleChange = onVoiceStyleChange,
                onTextChange = onInputChange,
                onSend = onSend,
                onStop = onStop,
                onRemoveDraft = onRemoveDraft,
                onVoiceStart = onVoiceRecordStart,
                onVoiceStop = onVoiceRecordStop,
                onVoiceCancel = onVoiceRecordCancel,
                onVoiceToText = onVoiceRecordToText,
                // 切换去向时会震动：先让录音把接下来一小段写成静音，马达声不进语音
                onVoiceIntentChange = { com.openapi.core.media.VoiceRecorder.muteFor() },
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
