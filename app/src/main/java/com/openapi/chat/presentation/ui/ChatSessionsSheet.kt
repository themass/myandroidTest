package com.openapi.chat.presentation.ui

import androidx.compose.ui.res.stringResource
import com.openapi.ks.chat.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openapi.chat.domain.model.ChatSession
import com.openapi.core.design.theme.AppColors
import com.openapi.core.design.theme.AppSpacing
import com.openapi.core.design.theme.AppTypography

/**
 * 会话列表（底部弹层）：切换、新建、删除。
 *
 * 替代旧的 ChatSessionFragment：数据来自 [ChatSession] 列表，操作交给调用方（ViewModel）。
 * 至少保留一个会话，只剩一个时不显示删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSessionsSheet(
    sessions: List<ChatSession>,
    currentSessionId: Long,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = AppSpacing.lg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.chat_sessions_title),
                    modifier = Modifier.weight(1f),
                    fontSize = AppTypography.sectionTitle,
                    fontWeight = AppTypography.titleWeight,
                    color = AppColors.TextPrimary,
                )
                TextButton(onClick = { creating = true }) {
                    Text(stringResource(R.string.ui_new_session), color = AppColors.Primary)
                }
            }

            LazyColumn {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        current = session.id == currentSessionId,
                        canDelete = sessions.size > 1,
                        onSelect = { onSelect(session.id) },
                        onDelete = { onDelete(session.id) },
                    )
                }
            }
        }
    }

    if (creating) {
        NewSessionDialog(
            onConfirm = { name ->
                creating = false
                onCreate(name)
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun SessionRow(
    session: ChatSession,
    current: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = session.name.ifBlank { stringResource(R.string.chat_session_untitled) },
            modifier = Modifier.weight(1f),
            fontSize = AppTypography.bodyLarge,
            fontWeight = if (current) AppTypography.emphasisWeight else FontWeight.Normal,
            color = if (current) AppColors.Primary else AppColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (current) {
            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.ui_current_session), tint = AppColors.Primary)
        }
        if (canDelete && !session.isDefault) {
            Spacer(Modifier.width(AppSpacing.sm))
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.ui_delete), color = AppColors.Error)
            }
        }
    }
}

@Composable
private fun NewSessionDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_new_session)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.chat_session_name_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.ui_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        },
    )
}
