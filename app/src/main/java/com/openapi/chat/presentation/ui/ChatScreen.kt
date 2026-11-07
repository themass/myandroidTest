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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Close
import com.openapi.chat.presentation.DraftAttachment
import com.openapi.chat.domain.model.MessageKind
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
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
private val BubbleText = Color.Black

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
    onRemoveDraft: (String) -> Unit = {},
    /** 点赞（true）或取消点赞（false），由 ChatRoute 写入本地收藏。 */
    onLikeMessage: (ChatMessage, Boolean) -> Unit = { _, _ -> },
    /** 点踩后提交的反馈文字。 */
    onFeedbackMessage: (ChatMessage, String) -> Unit = { _, _ -> },
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
    var likedIds by remember { mutableStateOf(setOf<String>()) }
    var dislikedIds by remember { mutableStateOf(setOf<String>()) }
    var feedbackTarget by remember { mutableStateOf<ChatMessage?>(null) }
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
                        liked = message.id in likedIds,
                        disliked = message.id in dislikedIds,
                        onVoiceClick = { onVoiceClick(message) },
                        onSpeakClick = { onSpeakMessage(message) },
                        onLikeClick = {
                            val now = message.id !in likedIds
                            likedIds = if (now) likedIds + message.id else likedIds - message.id
                            onLikeMessage(message, now)
                        },
                        onDislikeClick = { feedbackTarget = message },
                        onCopyClick = { copyMessageText(context, message.text) },
                        onDeleteClick = { deleteTarget = message },
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
            drafts = state.drafts,
            partialText = state.partialText,
            onRemoveDraft = onRemoveDraft,
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

    feedbackTarget?.let { message ->
        FeedbackDialog(
            onDismiss = { feedbackTarget = null },
            onSubmit = { text ->
                onFeedbackMessage(message, text)
                dislikedIds = dislikedIds + message.id
                feedbackTarget = null
            },
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
private fun AssistantAvatar(identity: AssistantIdentity) {
    val res = identity.avatarRes
    if (res != null) {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(36.dp).clip(CircleShape),
        )
    } else {
        RoleAvatar(emoji = identity.emoji, colorArgb = identity.colorArgb, size = 36)
    }
}

@Composable
private fun UserAvatar() {
    Image(
        painter = painterResource(R.drawable.ic_default_nan),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(36.dp).clip(CircleShape),
    )
}

/**
 * 一条消息的气泡 + 底部操作栏（footer）。
 *
 * 气泡内容按顺序堆叠：图片、文件、语音、文字，一条消息可以是任意组合（如「图片 + 文字」「语音 + 文字」）。
 * footer 与旧版 MyIncomingAudioFooterView 对齐：语音播放、朗读、点赞、点踩（弹反馈框）、复制、删除。
 */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    identity: AssistantIdentity,
    speaking: Boolean,
    liked: Boolean,
    disliked: Boolean,
    onVoiceClick: () -> Unit,
    onSpeakClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onCopyClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val fromUser = message.role == ChatRole.USER
    // 气泡最宽占屏幕的 75%（除去头像），大屏和折叠屏上不会显得窄
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * AppShapes.BUBBLE_MAX_WIDTH_FRACTION).dp
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
        horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (!fromUser) {
                AssistantAvatar(identity)
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
                BubbleContent(message = message, onVoiceClick = onVoiceClick)
            }
            if (fromUser) {
                Spacer(Modifier.width(AppSpacing.sm))
                UserAvatar()
            }
        }
        if (!message.pending) {
            MessageFooter(
                message = message,
                fromUser = fromUser,
                speaking = speaking,
                liked = liked,
                disliked = disliked,
                onSpeakClick = onSpeakClick,
                onLikeClick = onLikeClick,
                onDislikeClick = onDislikeClick,
                onCopyClick = onCopyClick,
                onDeleteClick = onDeleteClick,
            )
        }
    }
}

@Composable
private fun BubbleContent(message: ChatMessage, onVoiceClick: () -> Unit) {
    val hasAttachment = message.image != null || message.file != null || message.voice != null
    Column(Modifier.padding(horizontal = AppShapes.bubblePaddingHorizontal, vertical = AppShapes.bubblePaddingVertical)) {
        // 还没收到任何内容时显示「正在思考」；一旦开始吐字就直接展示已收到的文字（打字机效果）
        if (message.pending && message.text.isEmpty() && !hasAttachment) {
            PendingRow()
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            message.image?.let { ImageRow(path = it.path) }
            message.file?.let { FileRow(file = it) }
            message.voice?.let { voice ->
                VoiceBubbleRow(
                    messageId = message.id,
                    durationSeconds = voice.durationSeconds,
                    // 语音消息的文字就是转写，放在语音条下面，不重复渲染
                    transcript = message.text.takeIf { it.isNotBlank() },
                    onClick = onVoiceClick,
                )
            }
            if (message.voice == null && message.text.isNotEmpty()) {
                MessageText(message)
            }
        }
    }
}

@Composable
private fun MessageFooter(
    message: ChatMessage,
    fromUser: Boolean,
    speaking: Boolean,
    liked: Boolean,
    disliked: Boolean,
    onSpeakClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onCopyClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val canSpeak = !fromUser && message.text.isNotBlank() && message.voice == null
    Row(
        // 助手消息的 footer 从头像右侧开始；用户消息的 footer 靠右，与用户气泡右缘对齐
        modifier = Modifier.padding(top = 2.dp, start = if (fromUser) 0.dp else 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canSpeak) {
            FooterIcon(
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                description = stringResource(if (speaking) R.string.chat_speaking_desc else R.string.chat_read_aloud_desc),
                tint = if (speaking) AppColors.Primary else FooterTint,
                onClick = onSpeakClick,
            )
        }
        if (!fromUser) {
            FooterIcon(
                icon = if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                description = stringResource(R.string.chat_like_desc),
                tint = if (liked) AppColors.Primary else FooterTint,
                onClick = onLikeClick,
            )
            FooterIcon(
                icon = if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                description = stringResource(R.string.chat_dislike_desc),
                tint = if (disliked) AppColors.Primary else FooterTint,
                onClick = onDislikeClick,
            )
        }
        if (message.text.isNotBlank()) {
            FooterIcon(
                icon = Icons.Outlined.ContentCopy,
                description = stringResource(R.string.ui_copy),
                tint = FooterTint,
                onClick = onCopyClick,
            )
        }
        FooterIcon(
            icon = Icons.Outlined.Delete,
            description = stringResource(R.string.ui_delete),
            tint = FooterTint,
            onClick = onDeleteClick,
        )
    }
}

/** footer 统一的图标颜色、尺寸和点击区域：所有图标都是同一套线性风格。 */
private val FooterTint = AppColors.TextSecondary

@Composable
private fun FooterIcon(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
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
        Text(stringResource(R.string.chat_thinking), fontSize = AppTypography.bodyLarge, color = IconTint)
    }
}

@Composable
private fun MessageText(message: ChatMessage) {
    // 仅 assistant 消息走 Markdown；用户输入按纯文本显示。流式增量时 Markdown 对未闭合语法是容错的。
    if (message.role == ChatRole.ASSISTANT) {
        MarkdownText(markdown = message.text, textColor = BubbleText)
    } else {
        Text(
            text = message.text,
            fontSize = AppTypography.bodyLarge,
            color = BubbleText,
            textAlign = TextAlign.Start,
        )
    }
}

/**
 * 输入栏，参照微信的布局。语音按钮的三种样式见 [VoiceStyle]，长按语音按钮可以切换。
 * - 左侧：语音 / 键盘切换。语音模式下中间是「按住说话」条，文字模式下是输入框。
 * - 右侧：表情；有文字或有附件草稿时「+」变成发送按钮，否则是「+」（展开后旋转成 ×）。
 * - 上方：草稿托盘、样式 B 的实时识别文字、样式 A 的录音浮层。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatInputBar(
    text: String,
    drafts: List<DraftAttachment>,
    canSend: Boolean,
    recording: Boolean,
    transcribing: Boolean,
    partialText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onRemoveDraft: (String) -> Unit,
    onVoicePressStart: () -> Unit,
    onVoicePressStop: () -> Unit,
    onVoicePressCancel: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
) {
    val context = LocalContext.current
    var showEmoji by remember { mutableStateOf(false) }
    var showAttach by remember { mutableStateOf(false) }
    var voiceMode by remember { mutableStateOf(false) }
    var inputFocused by remember { mutableStateOf(false) }
    var canceling by remember { mutableStateOf(false) }
    var style by remember { mutableIntStateOf(VoiceStyle.load(context)) }
    val history = rememberVoiceLevelHistory(recording)
    val hasText = text.isNotBlank()
    val canSubmit = canSend || drafts.isNotEmpty()
    val plusRotation by animateFloatAsState(targetValue = if (showAttach) 45f else 0f, label = "plus-rotation")
    val pillBorder by animateColorAsState(
        targetValue = if (inputFocused) AppColors.Primary.copy(alpha = 0.45f) else AppColors.Divider,
        label = "pill-border",
    )

    Column {
        if (partialText.isNotBlank() && recording && style == VoiceStyle.RIPPLE) {
            // 样式 B：录音时实时显示识别出的文字
            Surface(color = AppColors.Surface) {
                Text(
                    partialText,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = AppTypography.bodyLarge,
                    color = Color.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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

        Surface(color = AppColors.Surface) {
            Column {
                if (drafts.isNotEmpty()) {
                    DraftTray(drafts = drafts, onRemove = onRemoveDraft)
                }
                if (voiceMode && recording && style == VoiceStyle.FLOATING) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VoiceFloatingPanel(canceling = canceling, history = history)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 语音 / 键盘切换：单击切换模式，长按切换语音样式（A / B / C）
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = {
                                    voiceMode = !voiceMode
                                    showAttach = false
                                    showEmoji = false
                                },
                                onLongClick = {
                                    style = VoiceStyle.cycle(context)
                                    Toast.makeText(
                                        context,
                                        context.getString(VoiceStyle.labelRes(style)),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (voiceMode) Icons.Filled.Keyboard else Icons.Filled.Mic,
                            contentDescription = stringResource(R.string.chat_toggle_voice_desc),
                            tint = AppColors.TextPrimary,
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    if (voiceMode) {
                        VoicePressBar(
                            modifier = Modifier.weight(1f),
                            style = style,
                            recording = recording,
                            canceling = canceling,
                            onCancelingChange = { canceling = it },
                            onStart = onVoicePressStart,
                            onStop = onVoicePressStop,
                            onCancel = {
                                canceling = false
                                onVoicePressCancel()
                            },
                            history = history,
                        )
                    } else {
                        Surface(
                            modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = AppColors.Surface,
                            border = BorderStroke(1.dp, pillBorder),
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                BasicTextField(
                                    value = text,
                                    onValueChange = onTextChange,
                                    modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
                                        inputFocused = state.isFocused
                                        if (state.isFocused) {
                                            showEmoji = false
                                            showAttach = false
                                        }
                                    },
                                    textStyle = TextStyle(fontSize = AppTypography.bodyLarge, color = BubbleText),
                                    cursorBrush = SolidColor(AppColors.Primary),
                                    maxLines = 5,
                                    decorationBox = { inner ->
                                        if (text.isEmpty()) {
                                            Text(
                                                stringResource(R.string.chat_input_hint),
                                                fontSize = AppTypography.bodyLarge,
                                                color = AppColors.TextSecondary,
                                            )
                                        }
                                        inner()
                                    },
                                )
                            }
                        }
                    }

                    if (!voiceMode) {
                        IconButton(
                            onClick = { showEmoji = !showEmoji; showAttach = false },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Outlined.EmojiEmotions,
                                contentDescription = stringResource(R.string.chat_emoji_desc),
                                tint = if (showEmoji) AppColors.Primary else AppColors.TextPrimary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }

                    if (canSubmit && !voiceMode) {
                        Surface(
                            onClick = { if (canSend) onSend() },
                            enabled = canSend,
                            shape = RoundedCornerShape(18.dp),
                            color = if (canSend) AppColors.Primary else AppColors.Divider,
                            modifier = Modifier.height(36.dp),
                        ) {
                            Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(R.string.ui_send),
                                    color = Color.White,
                                    fontSize = AppTypography.body,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    } else if (!voiceMode) {
                        IconButton(
                            onClick = { showAttach = !showAttach; showEmoji = false },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Outlined.AddCircleOutline,
                                contentDescription = stringResource(R.string.chat_more_desc),
                                tint = if (showAttach) AppColors.Primary else AppColors.TextPrimary,
                                modifier = Modifier.size(26.dp).rotate(plusRotation),
                            )
                        }
                    }
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

/** 附件草稿托盘：图片缩略图、文件名片，右上角 × 移除。 */
@Composable
private fun DraftTray(drafts: List<DraftAttachment>, onRemove: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(drafts, key = { it.id }) { draft ->
            Box {
                when (draft.kind) {
                    MessageKind.IMAGE -> Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.SurfaceVariant),
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                            },
                            update = { view -> Glide.with(view.context).load(File(draft.staged.file.absolutePath)).into(view) },
                        )
                    }
                    else -> Row(
                        modifier = Modifier
                            .height(64.dp)
                            .widthIn(max = 200.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.SurfaceVariant)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(fileIconRes(draft.staged.displayName)),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            draft.staged.displayName,
                            fontSize = AppTypography.caption,
                            color = BubbleText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // 右上角的移除按钮
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .offset(x = 6.dp, y = (-6).dp)
                        .clip(CircleShape)
                        .background(AppColors.TextSecondary)
                        .clickable { onRemove(draft.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_remove_attachment_desc), tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

/** 点踩后的反馈框，与旧版 FeedbackDialog 一致：写一段原因后提交。 */
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
    var fullScreen by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { fullScreen = true },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            },
            update = { view -> Glide.with(view.context).load(File(path)).into(view) },
        )
    }
    if (fullScreen) {
        FullScreenImageDialog(path = path, onDismiss = { fullScreen = false })
    }
}

@Composable
private fun FileRow(file: FileAttachment) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { openAttachment(context, file.path, file.name) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(fileIconRes(file.name)),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(file.name, fontSize = AppTypography.body, color = BubbleText, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
