package com.openapi.chat.presentation.ui

import androidx.compose.ui.res.stringResource
import com.openapi.ks.chat.R
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.openapi.chat.domain.model.FileAttachment
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.chat.domain.model.ChatCharacterOption
import com.openapi.chat.domain.model.ChatMessage
import com.openapi.chat.domain.model.ChatRole
import com.openapi.chat.presentation.ChatUiState
import androidx.compose.material3.SuggestionChip
import androidx.compose.ui.platform.LocalConfiguration
import com.openapi.core.design.theme.AppColors
import com.openapi.core.design.theme.AppShapes
import com.openapi.core.design.theme.AppSpacing
import com.openapi.core.design.theme.AppTypography
import com.openapi.roles.domain.SelectedRole
import com.openapi.roles.presentation.RoleAvatar

// 颜色统一取自 :core:core-design 的 token，不再在页面里硬编码——
// 之前每改一次配色都要全工程搜 0x 十六进制。
private val ChatBackground = AppColors.ChatBackground
private val IncomingBubble = AppColors.ChatIncoming
private val OutgoingBubble = AppColors.ChatOutgoing
private val TimeText = AppColors.ChatTimestamp
private val IconTint = AppColors.TextSecondary

@Composable
fun ChatScreen(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCharacterSelected: (Int?) -> Unit,
    onCreateCharacter: (title: String, content: String) -> Unit,
    onUpdateCharacter: (id: Int, title: String, content: String) -> Unit,
    onDeleteCharacter: (id: Int) -> Unit,
    onRoleSelected: (SelectedRole) -> Unit,
    onRolePickerVisibleChange: (Boolean) -> Unit,
    onVoiceClick: (ChatMessage) -> Unit,
    onSpeakMessage: (ChatMessage) -> Unit = {},
    /** 长按菜单里的删除：参数是消息 id，只删本机记录。 */
    onDeleteMessage: (String) -> Unit = {},
    onVoiceRecordStart: () -> Unit,
    onVoiceRecordStop: () -> Unit,
    onVoiceRecordCancel: () -> Unit,
    /** 电话按钮：以后接 LiveKit agent 语音通话，目前由调用方决定展示什么。 */
    onCallClick: () -> Unit = {},
    onPickImage: () -> Unit = {},
    onPickFile: () -> Unit = {},
    onSendPendingImage: (useCrop: Boolean) -> Unit = {},
    onDismissPendingImage: () -> Unit = {},
    onSessionsVisibleChange: (Boolean) -> Unit = {},
    onSessionSelected: (Long) -> Unit = {},
    onCreateSession: (String) -> Unit = {},
    onDeleteSession: (Long) -> Unit = {},
    /** 非空时头部显示菜单按钮，由主页接到侧边抽屉。 */
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // 长按气泡弹出的操作菜单，以及等待确认的删除目标
    var actionMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatMessage?>(null) }
    val context = LocalContext.current

    // 新消息到达时滚到底部；流式增量也会触发（state.messages.lastOrNull()?.text 随打字机效果变化）
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChatBackground)
            .imePadding(),
    ) {
        RoleHeader(
            state = state,
            onClick = { onRolePickerVisibleChange(true) },
            onCallClick = onCallClick,
            onSessionsClick = { onSessionsVisibleChange(true) },
            onMenuClick = onMenuClick,
        )

        val identity = state.assistantIdentity()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        identity = identity,
                        speaking = message.id == state.speakingMessageId,
                        onVoiceClick = { onVoiceClick(message) },
                        onSpeakClick = { onSpeakMessage(message) },
                        onLongPress = { if (!message.pending) actionMessage = message },
                    )
                }
            }
            if (state.messages.isEmpty() && !state.loading) {
                EmptyChat(identity = identity, onSuggestion = onInputChange)
            }
        }

        ChatInputBar(
            text = state.input,
            canSend = state.canSend,
            recording = state.recording,
            transcribing = state.transcribing,
            onTextChange = onInputChange,
            onSend = onSend,
            onVoicePressStart = onVoiceRecordStart,
            onVoicePressStop = onVoiceRecordStop,
            onVoicePressCancel = onVoiceRecordCancel,
            onPickImage = onPickImage,
            onPickFile = onPickFile,
        )
    }

    if (state.pendingImage != null) {
        ImageReviewSheet(
            review = state.pendingImage,
            onSend = onSendPendingImage,
            onRePick = {
                onDismissPendingImage()
                onPickImage()
            },
            onDismiss = onDismissPendingImage,
        )
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

    actionMessage?.let { message ->
        MessageActionSheet(
            canCopy = message.text.isNotBlank(),
            onCopy = {
                copyMessageText(context, message.text)
                actionMessage = null
            },
            onDelete = {
                deleteTarget = message
                actionMessage = null
            },
            onDismiss = { actionMessage = null },
        )
    }

    deleteTarget?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.chat_delete_message_title)) },
            text = { Text(stringResource(R.string.chat_delete_message_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMessage(message.id)
                    deleteTarget = null
                }) { Text(stringResource(R.string.ui_delete), color = AppColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.ui_cancel)) }
            },
        )
    }
}

/**
 * 长按消息气泡弹出的操作面板：复制（有文字时才显示）和删除。
 * 删除不直接执行，由调用方再弹一次确认。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionSheet(
    canCopy: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = AppSpacing.lg)) {
            if (canCopy) {
                ActionRow(
                    icon = Icons.Filled.ContentCopy,
                    label = stringResource(R.string.ui_copy),
                    tint = AppColors.TextPrimary,
                    onClick = onCopy,
                )
            }
            ActionRow(
                icon = Icons.Filled.Delete,
                label = stringResource(R.string.ui_delete),
                tint = AppColors.Error,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(AppSpacing.md))
        Text(label, fontSize = AppTypography.body, color = tint)
    }
}

private fun copyMessageText(context: android.content.Context, text: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("chat", text))
    Toast.makeText(context, context.getString(R.string.ui_copied), Toast.LENGTH_SHORT).show()
}

/**
 * 创建/编辑自定义角色的弹窗。
 *
 * 只收 title + content（人设正文）两个字段——这是服务端 `CharacterForm` 的必填字段，
 * 头像 url 本轮不做上传入口，留给后续接图片选择器时再补。
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

    androidx.compose.material3.AlertDialog(
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
        RoleAvatar(emoji = identity.emoji, colorArgb = identity.colorArgb, size = 72)
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
        listOf(stringResource(R.string.chat_suggest_intro), stringResource(R.string.chat_suggest_help), stringResource(R.string.chat_suggest_example)).forEach { text ->
            SuggestionChip(
                onClick = { onSuggestion(text) },
                label = { Text(text) },
                modifier = Modifier.padding(vertical = AppSpacing.xs),
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    identity: AssistantIdentity,
    speaking: Boolean,
    onVoiceClick: () -> Unit,
    onSpeakClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val fromUser = message.role == ChatRole.USER
    // 气泡最宽占屏幕的 75%（除去助手头像），而不是写死 280dp：大屏和折叠屏上不会显得窄
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * AppShapes.BUBBLE_MAX_WIDTH_FRACTION).dp
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!fromUser) {
            RoleAvatar(emoji = identity.emoji, colorArgb = identity.colorArgb, size = 32)
            Spacer(Modifier.width(AppSpacing.sm))
        }
        Surface(
            color = if (fromUser) OutgoingBubble else IncomingBubble,
            shape = RoundedCornerShape(
                topStart = AppShapes.bubble,
                topEnd = AppShapes.bubble,
                bottomStart = if (fromUser) AppShapes.bubble else AppSpacing.xs,
                bottomEnd = if (fromUser) AppSpacing.xs else AppShapes.bubble,
            ),
            modifier = Modifier
                .widthIn(max = maxBubbleWidth)
                .pointerInput(message.id) { detectTapGestures(onLongPress = { onLongPress() }) },
        ) {
            Column(Modifier.padding(horizontal = AppShapes.bubblePaddingHorizontal, vertical = AppShapes.bubblePaddingVertical)) {
                // 一条消息可以同时带图片、文件、语音和文字（例如「图片 + 文字」「语音 + 文字」），
                // 所以按顺序堆叠各个部分，而不是只选一种。
                val hasAttachment = message.image != null || message.file != null || message.voice != null
                if (message.pending && message.text.isEmpty() && !hasAttachment) {
                    // 还没收到任何增量文本时显示"正在思考"；一旦开始吐字就直接展示已收到的文字（打字机效果）
                    PendingRow()
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        message.image?.let { ImageRow(path = it.path) }
                        message.file?.let { FileRow(file = it) }
                        message.voice?.let { voice ->
                            VoiceRow(
                                durationSeconds = voice.durationSeconds,
                                transcript = message.text.takeIf { it.isNotBlank() },
                                playing = false,
                                onClick = onVoiceClick,
                            )
                        }
                        // 语音消息的文字就是转写，已经在 VoiceRow 里显示，不再重复
                        if (message.voice == null && message.text.isNotEmpty()) {
                            MessageText(message)
                        }
                    }
                }
                if (!fromUser && !message.pending && message.text.isNotBlank() && message.voice == null) {
                    IconButton(onClick = onSpeakClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (speaking) stringResource(R.string.chat_speaking_desc) else stringResource(R.string.chat_read_aloud_desc),
                            tint = if (speaking) AppColors.Primary else IconTint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.chat_thinking), fontSize = AppTypography.body, color = IconTint)
    }
}

/** 语音条：播放按钮 + 时长，转写文字（若有）显示在下面。 */
@Composable
private fun VoiceRow(durationSeconds: Int, transcript: String?, playing: Boolean, onClick: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) stringResource(R.string.chat_stop_playing_desc) else stringResource(R.string.chat_play_voice_desc),
                    tint = AppColors.Primary,
                )
            }
            Text("${durationSeconds}″", fontSize = AppTypography.body, color = AppColors.TextPrimary)
        }
        if (transcript != null) {
            Text(transcript, fontSize = AppTypography.caption, color = AppColors.TextSecondary)
        }
    }
}


@Composable
private fun MessageText(message: ChatMessage) {
    // 仅 assistant 消息走 Markdown：用户自己输入的内容按纯文本显示更符合预期。
    // 流式增量过程中文本不是完整 Markdown（比如代码块只打了一半），Markwon 对不完整
    // 语法是容错的（按纯文本渲染未闭合部分），不会因为还没收完就崩或乱渲染。
    // 后端会把 LaTeX 转成 $...$，Markwon 不认，本轮不做公式渲染。
    if (message.role == ChatRole.ASSISTANT) {
        MarkdownText(markdown = message.text, textColor = AppColors.TextPrimary)
    } else {
        Text(
            text = message.text,
            fontSize = AppTypography.body,
            color = AppColors.TextPrimary,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    canSend: Boolean,
    recording: Boolean,
    transcribing: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoicePressStart: () -> Unit,
    onVoicePressStop: () -> Unit,
    onVoicePressCancel: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
) {
    var showEmoji by remember { mutableStateOf(false) }
    var showAttach by remember { mutableStateOf(false) }

    Column {
        if (recording) {
            Surface(color = AppColors.Warning.copy(alpha = 0.12f)) {
                Text(
                    stringResource(R.string.chat_recording_hint),
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = AppColors.Warning,
                )
            }
        } else if (transcribing) {
            // ASR 是远端服务，几秒内不一定回来；没有这行提示用户会以为按了没反应。
            Surface(color = AppColors.Primary.copy(alpha = 0.08f)) {
                Text(
                    stringResource(R.string.chat_transcribing),
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = AppColors.Primary,
                )
            }
        }

        Surface(color = AppColors.Surface, shadowElevation = 8.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 按住录音、松开发送、上滑取消——对应旧 RecordButton 的手势，只是现在落在
                // 一个固定的按钮上而不是切换整个输入框（老版本点一下在"文字/语音"两个模式间切换）。
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onVoicePressStart()
                                    val released = tryAwaitRelease()
                                    if (released) onVoicePressStop() else onVoicePressCancel()
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.chat_hold_to_talk_desc),
                        tint = if (recording) AppColors.Error else IconTint,
                    )
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    // 表情入口收进输入框尾部：底栏从六个控件减到四个（语音、输入、更多、发送）
                    trailingIcon = {
                        IconButton(onClick = { showEmoji = !showEmoji; showAttach = false }) {
                            Icon(
                                Icons.Filled.EmojiEmotions,
                                contentDescription = stringResource(R.string.chat_emoji_desc),
                                tint = if (showEmoji) MaterialTheme.colorScheme.primary else IconTint,
                            )
                        }
                    },
                )

                IconButton(onClick = { showAttach = !showAttach; showEmoji = false }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.chat_more_desc),
                        tint = if (showAttach) MaterialTheme.colorScheme.primary else IconTint,
                    )
                }
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.ui_send),
                        tint = if (canSend) MaterialTheme.colorScheme.primary else AppColors.TextSecondary,
                    )
                }
            }
        }

        if (showAttach) {
            AttachmentPanel(
                onImage = { showAttach = false; onPickImage() },
                onFile = { showAttach = false; onPickFile() },
            )
        }
        if (showEmoji) {
            EmojiPanel(onEmojiSelected = { onTextChange(text + it) })
        }
    }
}

/**
 * 轻量表情面板：固定的常用表情网格，直接插入输入框光标位置之后（简化为追加到末尾）。
 *
 * 不是对老版本 `com.hrl.chaui.emoji`（数据库驱动的表情包）的等价移植——那套依赖的
 * EmojiDao/素材包随老对话栈一起退役。这里换成一份自包含的常用 emoji 列表，
 * 覆盖日常聊天场景，不依赖任何资源文件或数据库。
 */
@Composable
private fun AttachmentPanel(onImage: () -> Unit, onFile: () -> Unit) {
    // 与旧版 InputRecordView 的「+」面板一致：相册、文件、视频、位置、电话、帮助、拍照解题（旧版叫「提问」）
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.SurfaceVariant)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            AttachItem(Modifier.weight(1f), stringResource(R.string.chat_attach_album), Icons.Filled.Image, onClick = onImage)
            AttachItem(Modifier.weight(1f), stringResource(R.string.chat_attach_file), Icons.Filled.InsertDriveFile, onClick = onFile)
            AttachItem(Modifier.weight(1f), stringResource(R.string.chat_attach_video), Icons.Filled.Videocam) {
                Toast.makeText(context, context.getString(R.string.chat_video_soon), Toast.LENGTH_SHORT).show()
            }
            AttachItem(Modifier.weight(1f), stringResource(R.string.chat_attach_location), Icons.Filled.LocationOn) {
                Toast.makeText(context, context.getString(R.string.chat_location_soon), Toast.LENGTH_SHORT).show()
            }
        }
        Row(Modifier.fillMaxWidth()) {
            AttachItem(Modifier.weight(1f), stringResource(R.string.chat_attach_phone), Icons.Filled.Call) {
                Toast.makeText(context, context.getString(R.string.talktome_unavailable), Toast.LENGTH_SHORT).show()
            }
            AttachItem(Modifier.weight(1f), stringResource(R.string.chat_attach_help), Icons.AutoMirrored.Filled.Help) {
                // 旧版帮助页地址（Constants.helpUrl），用系统浏览器打开
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.openapi.ks.myapp.constant.Constants.helpUrl)))
            }
            AttachItem(Modifier.weight(1f), stringResource(R.string.chat_attach_photo_solve), Icons.Filled.CameraAlt) {
                // 旧版「提问」是选图后发给题目识别接口；现在由拍照解题模块统一承接
                com.aimath.paizhao.PhotoSolveActivity.start(context)
            }
            // 占位，保证第二行与第一行等宽
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun AttachItem(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
            Icon(icon, contentDescription = label, tint = IconTint, modifier = Modifier.size(26.dp))
        }
        Text(label, fontSize = AppTypography.caption, color = IconTint)
    }
}


@Composable
private fun ImageRow(path: String) {
    AndroidView(
        modifier = Modifier.size(200.dp),
        factory = { context ->
            ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        },
        update = { view -> Glide.with(view.context).load(File(path)).into(view) },
    )
}

@Composable
private fun FileRow(file: FileAttachment) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(fileIconRes(file.name)),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(file.name, fontSize = AppTypography.body, color = AppColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatFileSize(file.sizeBytes), fontSize = AppTypography.caption, color = IconTint)
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

@Composable
private fun EmojiPanel(onEmojiSelected: (String) -> Unit) {
    val emojis = remember {
        listOf(
            "😀", "😁", "😂", "🤣", "😊", "😍", "🤔", "😅",
            "😭", "😡", "👍", "👎", "🙏", "👏", "🎉", "❤️",
            "😘", "😎", "🥳", "😴", "🤯", "🙄", "😮", "😢",
            "🔥", "✨", "💯", "👋", "🤝", "💪", "😏", "🤗",
        )
    }
    Surface(color = AppColors.SurfaceVariant) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.fillMaxWidth().height(160.dp).padding(8.dp),
        ) {
            items(emojis) { emoji ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .pointerInput(emoji) {
                            detectTapGestures(onTap = { onEmojiSelected(emoji) })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
            }
        }
    }
}
