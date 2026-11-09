package com.openapi.ks.myapp.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.openapi.ks.myapp.agent.AgentBubble
import com.openapi.ks.myapp.agent.BubbleState
import com.openapi.ks.myapp.agent.ConnectionState

/**
 * 新对话页（客户端设计 3、5）。
 *
 * - 发送永远可用（只受排队上限约束），「正在生成」时出现停止按钮，不禁止输入；
 * - 排队中的输入在列表底部显示，可单条撤回或清空；
 * - 失败的发送显示重试，复用同一个 client_message_id；
 * - RTL：会话语言为阿拉伯语时整屏布局反向，公式与代码由调用方自行包 LTR。
 */
@Composable
fun AgentChatScreen(
    viewModel: AgentChatViewModel,
    isRtl: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val direction = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            if (state.connection != ConnectionState.CONNECTED) {
                Text(
                    text = if (state.connection == ConnectionState.OFFLINE) "连接已断开，请检查网络" else "正在重新连接…",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
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
                    BubbleRow(bubble, onRetry = viewModel::retry, onRegenerate = viewModel::regenerate,
                        onContinue = viewModel::continueReply)
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
    }
}

@Composable
private fun BubbleRow(
    bubble: AgentBubble,
    onRetry: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onContinue: (String) -> Unit,
) {
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
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Text(bubble.content, style = MaterialTheme.typography.bodyMedium)
        label?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        if (bubble.state == BubbleState.FAILED) {
            TextButton(onClick = { onRetry(bubble.key) }) { Text("重试") }
        }
        if (!isUser && bubble.messageId != null) {
            Row {
                TextButton(onClick = { onRegenerate(bubble.messageId) }) { Text("重新生成") }
                TextButton(onClick = { onContinue(bubble.messageId) }) { Text("继续") }
            }
        }
    }
}

@Composable
private fun AssistantBubble(text: String, label: String?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        label?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}
