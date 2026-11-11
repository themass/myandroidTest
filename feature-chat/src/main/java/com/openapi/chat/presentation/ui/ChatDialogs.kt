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
import com.openapi.uikit.theme.AppColors
import com.openapi.uikit.theme.AppSpacing
import com.openapi.uikit.theme.AppTypography
import com.openapi.speech.audio.AudioPlayer
import com.openapi.feature.chat.R
import com.openapi.roles.domain.SelectedRole
import com.openapi.roles.presentation.RoleAvatar

/** 点踩后的反馈框（与旧版 FeedbackDialog 一致：写一段原因后提交）。 */
@Composable
internal fun FeedbackDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
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
