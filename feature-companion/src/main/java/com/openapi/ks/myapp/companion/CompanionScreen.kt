package com.openapi.ks.myapp.companion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openapi.chatkit.message.ChatMessageList
import com.openapi.chatkit.model.ChatItem
import com.openapi.chatkit.model.MessagePart
import com.openapi.chatkit.model.Sender
import com.openapi.chatkit.theme.ChatKitTheme
import com.openapi.feature.companion.R
import com.openapi.ks.myapp.avatar.AvatarCommand
import com.openapi.ks.myapp.avatar.AvatarEventMapper
import com.openapi.ks.myapp.avatar.AvatarSpec
import com.openapi.ks.myapp.avatar.ProactiveKind
import com.openapi.ks.myapp.avatar.SimpleAvatar
import com.openapi.ks.myapp.avatar.SimpleAvatarRenderer

/**
 * 陪伴页：和「对话」页长得像一家人，但有自己的角色形象与语音能力。
 *
 * 与旧版的区别（都是用户逐条提过的）：
 * 1. 去掉了「文字 / 语音」分段按钮——它切的是传输路径，用户看不懂也没有意义；
 *    真正该有的是「她怎么回」：文字 or 语音（语音时语音条 + 文字同时出现）。
 * 2. 消息区改用 chatkit 的 [ChatMessageList]，与「对话」页同一套气泡、时间戳、操作。
 * 3. 那个占屏 42%、用户不知道是什么的大框改成**可收起的形象区**，默认收起。
 * 4. 等待首字时显示「对方正在输入…」，而不是一个空气泡。
 * 5. 输入区与「对话」页一致：一个输入框 + 发送按钮，外加语音输出开关与通话入口。
 */
@Composable
fun CompanionScreen(
    viewModel: CompanionViewModel,
    characterId: String,
    modifier: Modifier = Modifier,
    /** 进全屏通话页。由 CompanionRoute 接，页面本身不持有导航。 */
    onOpenCall: () -> Unit = {},
    /** 打开角色选择。 */
    onOpenCharacters: () -> Unit = {},
    /** 打开个性化页面（记忆、重要日子、她的主动性）。 */
    onOpenProfile: () -> Unit = {},
    /** 打开抽屉。与「对话」页一致。 */
    onMenuClick: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val renderer = remember { SimpleAvatarRenderer() }
    val listState = rememberLazyListState()

    LaunchedEffect(characterId) { viewModel.start(characterId) }

    // 绑定素材：先用本地已有的（离线也能看）或内置默认，再向服务端同步。
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val avatarSync = remember(context) { buildAvatarSync(context) }
    LaunchedEffect(renderer, characterId) {
        val local = avatarSync.localFor(characterId)
        renderer.bind(AvatarSpec(files = local))
        val synced = runCatching { avatarSync.syncFor(characterId) }.getOrNull()
        if (synced != null && synced != local) renderer.bind(AvatarSpec(files = synced))
    }

    LaunchedEffect(state.avatarState) {
        AvatarEventMapper.mapJson("""{"type":"state","state":"${state.avatarState}"}""")
            ?.let(renderer::dispatch)
    }
    LaunchedEffect(state.emotion) {
        state.emotion?.let { renderer.dispatch(AvatarCommand.Emotion(it)) }
    }

    // 新消息到达时滚到底。用消息条数与最后一条文本做 key：流式追加时也要跟。
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    ChatKitTheme {
        Surface(color = Color(0xFFFBF8F5), modifier = modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                CompanionTopBar(
                    title = stringResource(R.string.companion_title),
                    avatarState = state.avatarState,
                    expanded = state.avatarExpanded,
                    onMenuClick = onMenuClick,
                    onToggleAvatar = viewModel::toggleAvatarExpanded,
                    onCharacters = onOpenCharacters,
                    onProfile = onOpenProfile,
                )

                // 形象区：默认收起成一个头像；展开后是完整形象，占屏高但可随时收回去。
                // 旧版是固定 weight(0.42f) 常驻，用户的原话是"不知道这个大框是什么"。
                AnimatedVisibility(
                    visible = state.avatarExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        SimpleAvatar(renderer = renderer, modifier = Modifier.fillMaxSize())
                    }
                }

                val items = remember(state.messages) { companionItems(state.messages) }
                ChatMessageList(
                    items = items,
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onAction = { _, action ->
                        // 陪伴页的 footer 动作先只接「删除」之外的语义：复制由 chatkit 内部处理，
                        // 语音播报要等语音条做出来。这里保持空实现，避免误触发。
                        when (action) {
                            else -> Unit
                        }
                    },
                    avatar = { item -> CompanionAvatar(emoji = "澈", active = item.streaming) },
                    emptyContent = {
                        Text(
                            text = stringResource(R.string.companion_input_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    },
                )

                // 等待首字：她还没开始说话。用「正在输入」而不是一个空气泡——空气泡看起来像卡住了。
                if (state.awaitingReply) TypingHint()

                state.incomingCall?.let { kind ->
                    IncomingCallBanner(
                        kind = kind,
                        onAccept = {
                            viewModel.dismissIncomingCall()
                            viewModel.setCallActive(true)
                        },
                        onDecline = viewModel::dismissIncomingCall,
                    )
                }

                // 与「对话」页一致：错误必须看得见，否则用户不知道是没发出去还是没回复。
                state.error?.let { message ->
                    ErrorBar(message = message, onDismiss = viewModel::clearError)
                }

                CompanionComposer(
                    input = state.input,
                    canSend = state.canSend,
                    output = state.output,
                    callActive = state.callActive,
                    onInputChange = viewModel::onInputChange,
                    onSend = viewModel::send,
                    onToggleOutput = viewModel::toggleOutput,
                    onOpenCall = onOpenCall,
                )
            }
        }
    }
}

@Composable
private fun CompanionTopBar(
    title: String,
    avatarState: String,
    expanded: Boolean,
    onMenuClick: (() -> Unit)?,
    onToggleAvatar: () -> Unit,
    onCharacters: () -> Unit,
    onProfile: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 抽屉入口：与「对话」页同一个手势位置，切换页面时不用重新学。
        if (onMenuClick != null) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.companion_nav_back),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = if (onMenuClick == null) 12.dp else 0.dp),
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleAvatar) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起形象" else "展开形象",
            )
        }
        IconButton(onClick = onCharacters) {
            Icon(
                imageVector = Icons.Filled.Phone,
                contentDescription = stringResource(R.string.companion_open_characters),
                modifier = Modifier.alpha(0.85f),
            )
        }
        IconButton(onClick = onProfile) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.companion_open_settings),
            )
        }
    }
}

@Composable
private fun CompanionAvatar(emoji: String, active: Boolean) {
    // 头像先做一个圆形字标：真实形象是 SimpleAvatar 那套素材，消息列表里放不下也读不清，
    // 这里只承担"是谁在说话"的识别功能。
    val alpha = if (active) 1f else 0.9f
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f * alpha)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 「对方正在输入…」：三个点依次跳动。 */
@Composable
private fun TypingHint() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "对方正在输入",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        repeat(3) { index ->
            val dotAlpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Text(
                text = ".",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dotAlpha),
            )
        }
    }
}

@Composable
private fun ErrorBar(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("关闭", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * 输入区。
 *
 * 与「对话」页同样的输入框 + 发送；额外两个只属于陪伴页的入口：
 * - 语音输出开关（扬声器图标）：她回语音条 + 文字，而不是只回文字；
 * - 通话按钮：进全屏通话页。
 * 旧版那个「文字 / 语音」分段按钮已删除——它切的是传输路径，用户看不出区别。
 */
@Composable
private fun CompanionComposer(
    input: String,
    canSend: Boolean,
    output: CompanionOutput,
    callActive: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleOutput: () -> Unit,
    onOpenCall: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.companion_input_hint)) },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
            )
            IconButton(onClick = onSend, enabled = canSend) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.companion_send),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 语音输出：开=她会用语音回答（同时给文字），关=只回文字。
            IconButton(onClick = onToggleOutput) {
                Icon(
                    imageVector = if (output == CompanionOutput.VOICE) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = if (output == CompanionOutput.VOICE) "语音回答：开" else "语音回答：关",
                    tint = if (output == CompanionOutput.VOICE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = if (output == CompanionOutput.VOICE) "语音回答" else "文字回答",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenCall, enabled = !callActive) {
                Icon(
                    imageVector = Icons.Filled.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text("通话", style = MaterialTheme.typography.labelMedium)
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.companion_incoming_call),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAccept) { Text(stringResource(R.string.companion_call_accept)) }
                TextButton(onClick = onDecline) { Text(stringResource(R.string.companion_call_decline)) }
            }
        }
    }
}

/**
 * 陪伴页消息 → chatkit 的 UI 模型。
 *
 * 不需要映射附件/语音：陪伴页的消息只有文字（语音条要等服务端在事件里带上分句与时长）。
 * 主动消息与待确认动作在 chatkit 里没有对应形态，这里先按普通助手消息渲染，
 * 待确认的按钮由列表下方的独立区域处理（见 CompanionScreen 里的 confirmation 分支）。
 */
private fun companionItems(messages: List<CompanionMessage>): List<ChatItem> = messages.mapIndexed { index, m ->
    ChatItem(
        id = "c$index",
        key = "c$index",
        sender = if (m.role == MessageRole.USER) Sender.User else Sender.Assistant,
        parts = listOf(MessagePart.Text(m.text)).filter { m.text.isNotEmpty() },
        // 空文本 + 还在流式 = 她在思考：交给 chatkit 的 thinking 显示跳动点
        thinking = m.streaming && m.text.isEmpty(),
        streaming = m.streaming && m.text.isNotEmpty(),
    )
}

private fun buildAvatarSync(context: android.content.Context): com.openapi.ks.myapp.avatar.AvatarSync {
    @Suppress("DEPRECATION")
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionCode }
        .getOrDefault(0)
    return com.openapi.ks.myapp.avatar.AvatarSync(
        baseUrl = CompanionConfig.baseUrl,
        tokenProvider = {
            com.openapi.common.util.android.PreferenceUtils.getPrefString(
                context, com.openapi.common.net.http.NetConfig.tokenHeader(), null)
        },
        client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build(),
        store = com.openapi.ks.myapp.avatar.AvatarPackStore(context),
        cacheDir = context.cacheDir,
        appVersion = version,
    )
}
