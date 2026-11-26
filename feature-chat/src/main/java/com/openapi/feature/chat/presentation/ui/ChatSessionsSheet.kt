package com.openapi.feature.chat.presentation.ui

import androidx.compose.ui.res.stringResource
import com.openapi.feature.chat.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.openapi.core.model.ChatMessageHit
import com.openapi.feature.chat.domain.model.ChatSession
import com.openapi.feature.chat.presentation.filterSessions
import com.openapi.feature.chat.presentation.searchMessages
import com.openapi.common.ui.theme.AppColors
import com.openapi.common.ui.theme.AppSpacing
import com.openapi.common.ui.theme.AppTypography

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
    /**
     * D1「生成中发消息」两态（设计 20261126 §2 D1）。null = 这一页不提供该设置，
     * 开关整行不画。值与回调都由调用方（ChatScreen←ChatRoute）持有，弹层只画不改 prefs。
     */
    sendMode: com.openapi.feature.chat.presentation.SendMode? = null,
    onSendModeChange: ((com.openapi.feature.chat.presentation.SendMode) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ChatSession?>(null) }
    // P2-15：搜索词。会话名过滤是内存操作即时生效；消息搜索查库，输入停顿 250ms 再发，
    // 避免每敲一个字都打一次 LIKE 全表扫。
    var query by remember { mutableStateOf("") }
    var messageHits by remember { mutableStateOf<List<ChatMessageHit>>(emptyList()) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            messageHits = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        messageHits = searchMessages(query)
    }
    val trimmedQuery = query.trim()
    val matchingSessions = filterSessions(sessions, query)
    val searching = trimmedQuery.isNotEmpty()

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

            // 搜索框（整改清单 P2-15）：同时过滤会话名与消息正文
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.screenHorizontal),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AppColors.TextSecondary) },
                placeholder = { Text(stringResource(R.string.chat_search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                if (searching) {
                    // 搜索态：会话命中 + 消息命中两个分组，都空时给明确的空态而不是留白
                    if (matchingSessions.isEmpty() && messageHits.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.chat_search_empty),
                                modifier = Modifier.padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.lg),
                                color = AppColors.TextSecondary,
                            )
                        }
                    }
                    if (matchingSessions.isNotEmpty()) {
                        item { SearchSectionTitle(stringResource(R.string.chat_search_sessions)) }
                        items(matchingSessions, key = { "s-" + it.id }) { session ->
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
                    if (messageHits.isNotEmpty()) {
                        item { SearchSectionTitle(stringResource(R.string.chat_search_messages)) }
                        items(messageHits, key = { "m-${it.sessionId}-${it.preview}" }) { hit ->
                            MessageHitRow(hit = hit, onSelect = { onSelect(hit.sessionId) })
                        }
                    }
                } else {
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

            // D1「生成中发消息」两态（设计 20261126 §2 D1）：放在会话列表下方，
            // 搜索态也照常显示——它是偏好，不是会话条目。不传值/回调 = 整行不画。
            if (sendMode != null && onSendModeChange != null) {
                SendModeRow(sendMode = sendMode, onSendModeChange = onSendModeChange)
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
private fun SearchSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = AppSpacing.screenHorizontal, end = AppSpacing.sm, top = AppSpacing.sm, bottom = AppSpacing.xs),
        fontSize = AppTypography.caption,
        color = AppColors.TextSecondary,
    )
}

/**
 * 一条消息命中：预览 + 所属会话名。点它 = 跳到那个会话（整改清单 P2-15）。
 * 预览是正文开头 120 字符（SQL substr），不做关键词高亮——那是下一步的事，先把「找得到、点得进」跑通。
 */
@Composable
private fun MessageHitRow(hit: ChatMessageHit, onSelect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.xs),
    ) {
        Text(
            text = hit.preview,
            fontSize = AppTypography.bodyLarge,
            color = AppColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = hit.sessionName,
            fontSize = AppTypography.caption,
            color = AppColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SendModeRow(
    sendMode: com.openapi.feature.chat.presentation.SendMode,
    onSendModeChange: (com.openapi.feature.chat.presentation.SendMode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SearchSectionTitle(stringResource(R.string.chat_send_mode_title))
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            SendModeChoice(
                label = stringResource(R.string.chat_send_mode_immediate),
                selected = sendMode == com.openapi.feature.chat.presentation.SendMode.Immediate,
                onClick = { onSendModeChange(com.openapi.feature.chat.presentation.SendMode.Immediate) },
            )
            SendModeChoice(
                label = stringResource(R.string.chat_send_mode_queue),
                selected = sendMode == com.openapi.feature.chat.presentation.SendMode.Queue,
                onClick = { onSendModeChange(com.openapi.feature.chat.presentation.SendMode.Queue) },
            )
        }
    }
}

/** 一个选项 = 单选语义（勾 + 主色），不画成两个独立开关——两态互斥。 */
@Composable
private fun SendModeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            contentColor = if (selected) AppColors.Primary else AppColors.TextSecondary,
        ),
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null)
        Text(label, fontWeight = if (selected) AppTypography.emphasisWeight else FontWeight.Normal)
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
