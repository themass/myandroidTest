package com.openapi.ks.myapp.agent.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.openapi.ks.myapp.agent.AgentBubble
import com.openapi.ks.myapp.agent.AgentReminder
import com.openapi.ks.myapp.agent.BubbleState
import com.openapi.ks.myapp.agent.ConnectionState

/**
 * 新对话页（客户端设计 3、5）。
 *
 * - 发送永远可用（只受排队上限约束），「正在生成」时出现停止按钮，不禁止输入；
 * - 排队中的输入在列表底部显示，可单条撤回或清空；
 * - 失败的发送显示重试，复用同一个 client_message_id；
 * - RTL：会话语言为阿拉伯语时整屏布局反向，公式与代码由调用方自行包 LTR。
 * - 长按消息：删除（任何已同步的消息）、编辑（自己的消息）。删除与编辑会同时改模型看到的上下文；
 *   回合进行中服务端返回忙，界面提示稍后再试。
 * - 定时提醒：角色答应「明天下午 3 点叫你」后，提醒出现在顶部，可以取消。
 */
@Composable
fun AgentChatScreen(
    viewModel: AgentChatViewModel,
    isRtl: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val direction = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    var editing by remember { mutableStateOf<AgentBubble?>(null) }
    var showReminders by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    val consent by viewModel.consent.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadReminders()
        viewModel.loadPendingActions()
    }
    // 重新生成要的是「被回答的那条用户消息」：助手气泡往前找最近的用户消息。
    val answeredBy = remember(state.bubbles) { precedingUserMessage(state.bubbles) }

    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            if (state.connection != ConnectionState.CONNECTED) {
                Text(
                    text = if (state.connection == ConnectionState.OFFLINE) "连接已断开，请检查网络" else "正在重新连接…",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.loadConsent(); showPrivacy = true }) { Text("隐私与数据") }
                if (reminders.isNotEmpty()) {
                    TextButton(onClick = { showReminders = true }) { Text("${reminders.size} 条提醒待发送") }
                }
            }
            // 需要你点头才会执行的动作（例如「明天 15:00 提醒你开会」）：不确认就不会执行。
            state.pendingConfirmations.forEach { pending ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(pending.title, style = MaterialTheme.typography.labelLarge)
                    if (pending.summary.isNotBlank()) Text(pending.summary, style = MaterialTheme.typography.bodySmall)
                    Row {
                        Button(onClick = { viewModel.resolveAction(pending.confirmationId, true) }) { Text("确认") }
                        OutlinedButton(onClick = { viewModel.resolveAction(pending.confirmationId, false) }) { Text("取消") }
                    }
                }
            }
            // 连续使用提醒：服务端在连聊到 2 小时时下发一次，用户关掉即可，不拦输入。
            state.usageReminder?.let { text ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::dismissUsageReminder) { Text("知道了") }
                }
            }
            state.lastError?.let { error ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(error, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::dismissError) { Text("知道了") }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 反向布局：最新的在最下面。活跃回合与排队区放在最前（即视觉最下方）。
                state.active?.let { active ->
                    item(key = "active-${active.turnId}") {
                        AssistantBubble(
                            text = active.text.ifEmpty { "…" },
                            label = active.steps.lastOrNull()?.title?.let { "正在$it" },
                        )
                    }
                }
                items(state.bubbles.asReversed(), key = { it.key }) { bubble ->
                    BubbleRow(
                        bubble,
                        onRetry = viewModel::retry,
                        onRegenerate = answeredBy[bubble.key]?.let { userId -> { viewModel.regenerate(userId) } },
                        onSelectAlt = viewModel::selectAlternative,
                        onContinue = viewModel::continueReply,
                        onDelete = viewModel::deleteMessage,
                        onEdit = { editing = it },
                    )
                }
                if (state.queued.isNotEmpty()) {
                    item(key = "queued-header") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("排队中 ${state.queued.size} 条", style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::clearQueue) { Text("清空排队") }
                        }
                    }
                    items(state.queued.asReversed(), key = { "q-${it.messageId}" }) { queued ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(queued.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.withdraw(queued.messageId) }) { Text("撤回") }
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = viewModel::onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("说点什么…") },
                )
                if (state.active != null) {
                    OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.padding(start = 8.dp)) {
                        Text("停止")
                    }
                }
                Button(
                    onClick = viewModel::send,
                    enabled = draft.isNotBlank() && state.queued.size < AgentChatViewModel.QUEUE_MAX,
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("发送") }
            }
        }

        editing?.let { bubble ->
            EditDialog(bubble, onDismiss = { editing = null }) { text ->
                bubble.messageId?.let { viewModel.editMessage(it, text) }
                editing = null
            }
        }
        if (showPrivacy) {
            PrivacyDialog(
                consent = consent,
                onToggle = viewModel::setConsent,
                onDismiss = { showPrivacy = false },
                onExport = { viewModel.exportData { json -> shareExport(context, json) } },
            )
        }
        if (showReminders) {
            RemindersDialog(reminders, onCancel = viewModel::cancelReminder, onDismiss = { showReminders = false })
        }
    }
}

/** 助手气泡 key → 它回答的那条用户消息 id（用于「重新生成」）。 */
internal fun precedingUserMessage(bubbles: List<AgentBubble>): Map<String, String> {
    val out = mutableMapOf<String, String>()
    var lastUser: String? = null
    for (b in bubbles) {
        if (b.role == "user") lastUser = b.messageId ?: lastUser
        else if (b.role == "assistant") lastUser?.let { out[b.key] = it }
    }
    return out
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BubbleRow(
    bubble: AgentBubble,
    onRetry: (String) -> Unit,
    onRegenerate: (() -> Unit)?,
    onSelectAlt: (String) -> Unit,
    onContinue: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (AgentBubble) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val isUser = bubble.role == "user"
    val label = when (bubble.state) {
        BubbleState.SENDING -> "发送中"
        BubbleState.FAILED -> "未发送"
        BubbleState.QUEUED -> "排队中"
        BubbleState.MERGED -> "已并入本轮"
        BubbleState.STEER_PENDING -> "正在补充给她"
        BubbleState.STEERED -> "已补充给她"
        else -> null
    }
    val synced = bubble.messageId != null && bubble.state != BubbleState.SENDING && bubble.state != BubbleState.FAILED
    // 同一次提问的多个候选回复：左右滑动切换；滑过最后一个就再生成一个（和常见角色扮演应用的手势一致）。
    val alts = bubble.altIds
    val canSwipe = !isUser && synced && alts.size > 1
    val goPrev = { if (bubble.altIndex > 0) onSelectAlt(alts[bubble.altIndex - 1]) }
    val goNext = {
        if (bubble.altIndex < alts.size - 1) onSelectAlt(alts[bubble.altIndex + 1]) else onRegenerate?.invoke()
        Unit
    }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Box {
            Text(
                bubble.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .combinedClickable(onClick = {}, onLongClick = { if (synced) menu = true })
                    .pointerInput(canSwipe, bubble.altIndex, alts.size) {
                        if (!canSwipe) return@pointerInput
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total <= -SWIPE_THRESHOLD_PX) goNext() else if (total >= SWIPE_THRESHOLD_PX) goPrev()
                            },
                        ) { _, delta -> total += delta }
                    },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (isUser) {
                    DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; onEdit(bubble) })
                }
                DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; bubble.messageId?.let(onDelete) })
            }
        }
        label?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        if (bubble.state == BubbleState.FAILED) {
            TextButton(onClick = { onRetry(bubble.key) }) { Text("重试") }
        }
        if (!isUser && bubble.messageId != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canSwipe) {
                    TextButton(onClick = { goPrev() }, enabled = bubble.altIndex > 0) { Text("‹") }
                    Text("${bubble.altIndex + 1}/${alts.size}", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { goNext() }) { Text("›") }
                }
                onRegenerate?.let { regenerate -> TextButton(onClick = regenerate) { Text("重新生成") } }
                TextButton(onClick = { onContinue(bubble.messageId) }) { Text("继续") }
            }
        }
    }
}

@Composable
private fun EditDialog(bubble: AgentBubble, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(bubble.key) { mutableStateOf(bubble.content) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑消息") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }) },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RemindersDialog(reminders: List<AgentReminder>, onCancel: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("待发送的提醒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                reminders.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${r.dueLocal}  ${r.content}", modifier = Modifier.weight(1f))
                        TextButton(onClick = { onCancel(r.id) }) { Text("取消") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun AssistantBubble(text: String, label: String?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        label?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}

/** 横向拖动超过这个距离才算一次滑动（像素；避免长按、滚动时误触）。 */
private const val SWIPE_THRESHOLD_PX = 120f

/**
 * 隐私与数据：两件事必须由用户自己决定 —— 训练用途（默认关）和主动联系总开关；
 * 再加一个导出（办法要求用户能复制自己的数据）。这不是律师给的文案，只是把开关放在用户面前。
 */
@Composable
private fun PrivacyDialog(
    consent: com.openapi.ks.myapp.agent.AgentConsent?,
    onToggle: (Boolean?, Boolean?) -> Unit,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("隐私与数据") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "她一直在和你聊天，所以会记下一些关于你的事。下面两件事由你决定。",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (consent == null) {
                    Text("正在读取…", style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("允许把对话用于改进模型", style = MaterialTheme.typography.bodyMedium)
                            Text("默认关闭。打开后，你的对话可能被用于训练和改进。",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = consent.trainingConsent, onCheckedChange = { onToggle(it, null) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("允许她主动联系你", style = MaterialTheme.typography.bodyMedium)
                            Text("关闭后她不会主动找你；你明确要求过的提醒仍会到点提醒。",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = consent.proactiveEnabled, onCheckedChange = { onToggle(null, it) })
                    }
                }
                TextButton(onClick = onExport) { Text("导出我的数据") }
                Text(
                    "她记得的内容可以在「她记得的」里查看和删除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** 把导出内容写进缓存目录并交给系统分享 / 保存。分享失败时不假装成功。 */
private fun shareExport(context: android.content.Context, json: String) {
    runCatching {
        val dir = java.io.File(context.cacheDir, "export").apply { mkdirs() }
        val file = java.io.File(dir, "companion-export.json").apply { writeText(json) }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "com.openapi.ks.chat.fileprovider", file)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(send, "导出我的数据"))
    }.onFailure { error ->
        android.widget.Toast.makeText(context, "导出失败：${error.message ?: "未知错误"}", android.widget.Toast.LENGTH_LONG).show()
    }
}
