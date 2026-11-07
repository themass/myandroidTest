package com.openapi.ks.myapp.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openapi.ks.chat.R
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarEventMapper
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.ProactiveKind
import com.openapi.ks.myapp.avatar.SimpleAvatar
import com.openapi.ks.myapp.avatar.SimpleAvatarRenderer

/**
 * 角色扮演对话页。上半屏形象，下半屏消息与输入。默认文字模式，可切语音。
 *
 * 形象由 [SimpleAvatarRenderer] 驱动；渲染器实例放在 remember 里，避免重组时
 * 重开眨眼定时器。
 */
@Composable
fun CompanionScreen(
    viewModel: CompanionViewModel,
    characterId: String,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val renderer = remember { SimpleAvatarRenderer() }
    val listState = rememberLazyListState()

    LaunchedEffect(characterId) { viewModel.start(characterId) }

    // 绑定素材。角色自带的表情图由角色资源决定，界面只给一张立绘占位；
    // 美术素材接入后，这里换成按 characterId 查角色资源包。
    LaunchedEffect(renderer) { renderer.bind(AvatarSpec()) }

    // 服务端事件 → 渲染器指令。映射在 AvatarEventMapper 里，界面不认协议细节。
    LaunchedEffect(state.avatarState) {
        AvatarEventMapper.mapJson("""{"type":"state","state":"${state.avatarState}"}""")
            ?.let(renderer::dispatch)
    }

    // 情绪是独立的一次性事件，不随状态变化重复下发。
    LaunchedEffect(state.emotion) {
        state.emotion?.let { renderer.dispatch(AvatarCommand.Emotion(it)) }
    }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // 形象区：固定高度，不随消息列表滚动，也不遮挡下方输入。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            SimpleAvatar(renderer = renderer, modifier = Modifier.fillMaxSize())
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages) { message ->
                MessageBubble(
                    message = message,
                    onConfirm = viewModel::confirmAction,
                    onCancel = viewModel::cancelAction,
                )
            }
        }

        // 主动来电不当作一条消息：它要接管整屏，否则用户会当成普通提示划过去。
        state.incomingCall?.let { kind ->
            IncomingCallBanner(
                kind = kind,
                onAccept = {
                    viewModel.dismissIncomingCall()
                    if (state.mode != CompanionMode.VOICE) viewModel.toggleMode()
                },
                onDecline = viewModel::dismissIncomingCall,
            )
        }

        if (state.error != null) {
            Text(
                text = state.error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            ModeToggle(state.mode, onToggle = viewModel::toggleMode)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.companion_input_hint)) },
                    maxLines = 4,
                )
                IconButton(
                    onClick = viewModel::send,
                    enabled = state.canSend,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.companion_send),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: CompanionMessage,
    onConfirm: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    val mine = message.role == MessageRole.USER
    val proactive = message.proactive != ProactiveKind.REPLY
    Column(modifier = Modifier.fillMaxWidth()) {
        // 主动消息加一个前缀标签：用户得一眼看出没人跟她说话，是她自己开口的。
        if (proactive) {
            Text(
                text = stringResource(
                    when (message.proactive) {
                        ProactiveKind.EVENT -> R.string.companion_proactive_event
                        ProactiveKind.GREETING -> R.string.companion_proactive_greeting
                        else -> R.string.companion_proactive_generic
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        ) {
            Text(
                // 流式消息末尾加光标，让「还在生成」可见。
                text = if (message.streaming) message.text + "▍" else message.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            mine -> MaterialTheme.colorScheme.primaryContainer
                            proactive -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // 待确认动作：必须给用户一个明确的出口，否则她会替用户做了不该做的事。
        message.confirmationId?.let { id ->
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { onConfirm(id) }) {
                    Text(stringResource(R.string.companion_action_confirm))
                }
                TextButton(onClick = { onCancel(id) }) {
                    Text(stringResource(R.string.companion_action_cancel))
                }
            }
        }
    }
}

@Composable
private fun IncomingCallBanner(
    kind: ProactiveKind,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.companion_incoming_call),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAccept) {
                    Text(stringResource(R.string.companion_call_accept))
                }
                TextButton(onClick = onDecline) {
                    Text(stringResource(R.string.companion_call_decline))
                }
            }
        }
    }
}

@Composable
private fun ModeToggle(mode: CompanionMode, onToggle: () -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == CompanionMode.TEXT,
            onClick = { if (mode != CompanionMode.TEXT) onToggle() },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text(stringResource(R.string.companion_mode_text)) }
        SegmentedButton(
            selected = mode == CompanionMode.VOICE,
            onClick = { if (mode != CompanionMode.VOICE) onToggle() },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text(stringResource(R.string.companion_mode_voice)) }
    }
}
