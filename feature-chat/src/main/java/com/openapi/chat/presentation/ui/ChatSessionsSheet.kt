package com.openapi.chat.presentation.ui

import androidx.compose.ui.res.stringResource
import com.openapi.feature.chat.R
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openapi.chat.domain.model.ChatSession
import com.openapi.common.design.theme.AppColors
import com.openapi.common.design.theme.AppSpacing
import com.openapi.common.design.theme.AppTypography

/**
 * 会话列表（底部弹层）：切换、新建、重命名、删除。
 *
 * 替代旧的 ChatSessionFragment：数据来自 [ChatSession] 列表，操作交给调用方（ViewModel）。
 * 允许删到没有会话：服务端 /sessions.json 会自动新建一个。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSessionsSheet(
    sessions: List<ChatSession>,
    currentSessionId: Long,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    /** 清空当前对话：删掉当前会话（服务端会自动新建一个空的）。 */
    onClearCurrent: () -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ChatSession?>(null) }

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
                IconButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ui_new_session), tint = AppColors.Primary)
                }
            }

            LazyColumn {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        current = session.id == currentSessionId,
                        canDelete = true,
                        onSelect = { onSelect(session.id) },
                        onRename = { renaming = session },
                        onClear = { confirmClear = true },
                        onDelete = { onDelete(session.id) },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.session_clear_title)) },
            text = { Text(stringResource(R.string.session_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearCurrent()
                }) {
                    Text(stringResource(R.string.session_clear_confirm), color = AppColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.ui_cancel)) }
            },
        )
    }

    if (creating) {
        SessionNameDialog(
            title = stringResource(R.string.ui_new_session),
            initialName = "",
            onConfirm = { name ->
                creating = false
                onCreate(name)
            },
            onDismiss = { creating = false },
        )
    }

    renaming?.let { session ->
        SessionNameDialog(
            title = stringResource(R.string.ui_rename),
            initialName = session.name,
            onConfirm = { name ->
                renaming = null
                onRename(session.id, name)
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun SessionRow(
    session: ChatSession,
    current: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(start = AppSpacing.screenHorizontal, end = AppSpacing.sm, top = AppSpacing.xs, bottom = AppSpacing.xs),
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
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.ui_current_session),
                tint = AppColors.Primary,
                modifier = Modifier.padding(end = AppSpacing.xs),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.session_menu_more), tint = AppColors.TextSecondary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ui_rename), color = AppColors.TextPrimary) },
                    onClick = { menuOpen = false; onRename() },
                )
                if (current) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.session_menu_clear), color = AppColors.TextPrimary) },
                        onClick = { menuOpen = false; onClear() },
                    )
                }
                if (canDelete && !session.isDefault) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.session_menu_delete), color = AppColors.Error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
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
