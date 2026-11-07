package com.openapi.chat.presentation.ui

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
import com.openapi.core.design.theme.AppColors
import com.openapi.core.design.theme.AppSpacing
import com.openapi.core.design.theme.AppTypography
import com.openapi.core.media.AudioPlayer
import com.openapi.ks.chat.R
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
    onMenuClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val playback by AudioPlayer.state.collectAsState()
    val identity = state.assistantIdentity()
    val items = remember(state.messages, playback, state.speakingMessageId, state.likedIds, state.dislikedIds) {
        ChatItemMapper.map(state.messages, playback, state.speakingMessageId, state.likedIds, state.dislikedIds)
    }
    val byId = remember(state.messages) { state.messages.associateBy { it.id } }
    val composerState = remember(state.input, state.drafts, state.sending, state.streaming, state.recording, state.transcribing, state.partialText, state.editingMessageId) {
        ComposerState(
            text = state.input,
            editing = state.editingMessageId != null,
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

            ChatMessageList(
                items = items,
                modifier = Modifier.weight(1f).fillMaxWidth(),
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
                onLongPress = { item -> byId[item.id]?.let { onMessageAction(it, MessageAction.Copy) } },
                emptyContent = {
                    if (!state.loading) EmptyChat(identity = identity, onSuggestion = onInputChange)
                },
            )

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
                onCancelEdit = onCancelEdit,
            )
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
            onDelete = onDeleteSession,
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

/** 点踩后的反馈框（与旧版 FeedbackDialog 一致：写一段原因后提交）。 */
@Composable
private fun FeedbackDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_feedback_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.chat_feedback_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.ui_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        },
    )
}

/**
 * 空对话：当前角色的头像、名字，和几句可以直接点的开场白。
 * 点开场白只是填进输入框，不会自己发出去，用户可以先改。
 */
@Composable
private fun EmptyChat(identity: AssistantIdentity, onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(AppSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RoleAvatar(emoji = identity.emoji, colorArgb = identity.colorArgb, size = 72, imageRes = identity.avatarRes)
        Spacer(Modifier.height(AppSpacing.md))
        Text(
            identity.name,
            fontSize = AppTypography.screenTitle,
            fontWeight = AppTypography.titleWeight,
            color = AppColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AppSpacing.xs))
        Text(
            stringResource(R.string.chat_empty_hint),
            fontSize = AppTypography.body,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AppSpacing.lg))
        listOf(
            stringResource(R.string.chat_suggest_intro),
            stringResource(R.string.chat_suggest_help),
            stringResource(R.string.chat_suggest_example),
        ).forEach { text ->
            SuggestionChip(
                onClick = { onSuggestion(text) },
                label = { Text(text) },
                modifier = Modifier.padding(vertical = AppSpacing.xs),
            )
        }
    }
}

/**
 * 创建/编辑自定义角色的弹窗（角色面板使用）。
 *
 * 只收 title + content（人设正文）两个字段——这是服务端 `CharacterForm` 的必填字段。
 */
@Composable
internal fun CharacterEditDialog(
    initial: ChatCharacterOption?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    // 用完整正文而不是 preview：preview 被截断过，回填它会静默截短人设
    var content by remember { mutableStateOf(initial?.content.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.ui_new_role) else stringResource(R.string.ui_edit_role)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.chat_role_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.chat_role_persona)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim(), content.trim()) },
                enabled = title.isNotBlank() && content.isNotBlank(),
            ) { Text(stringResource(R.string.ui_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        },
    )
}
