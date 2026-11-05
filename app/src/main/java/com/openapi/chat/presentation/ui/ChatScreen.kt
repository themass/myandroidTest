package com.openapi.chat.presentation.ui

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
import com.openapi.core.design.theme.AppColors

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
    onVoiceClick: (ChatMessage) -> Unit,
    onVoiceRecordStart: () -> Unit,
    onVoiceRecordStop: () -> Unit,
    onVoiceRecordCancel: () -> Unit,
    /** 电话按钮：以后接 LiveKit agent 语音通话，目前由调用方决定展示什么。 */
    onCallClick: () -> Unit = {},
    onPickImage: () -> Unit = {},
    onPickFile: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

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
        CharacterSelectorBar(
            characters = state.characters,
            selectedCharacterId = state.selectedCharacterId,
            onCharacterSelected = onCharacterSelected,
            onCreateCharacter = onCreateCharacter,
            onUpdateCharacter = onUpdateCharacter,
            onDeleteCharacter = onDeleteCharacter,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    onVoiceClick = { onVoiceClick(message) },
                )
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
            onCallClick = onCallClick,
            onPickImage = onPickImage,
            onPickFile = onPickFile,
        )
    }
}

/**
 * 角色选择条：显示当前人设，点击展开列表切换。
 *
 * 对应后端 `/user/setting/character.json`；选中后持久化 characterId（见
 * ChatRepositoryImpl.setSelectedCharacterId），下次发消息就带着这个 id，
 * 不再是老版本那种"选了但后端从不读"的假选择（见 docs/agent-and-media-roadmap.md 第五节）。
 */
@Composable
private fun CharacterSelectorBar(
    characters: List<ChatCharacterOption>,
    selectedCharacterId: Int?,
    onCharacterSelected: (Int?) -> Unit,
    onCreateCharacter: (title: String, content: String) -> Unit,
    onUpdateCharacter: (id: Int, title: String, content: String) -> Unit,
    onDeleteCharacter: (id: Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // null 表示新建；非 null 表示在编辑这个角色
    var editingCharacter by remember { mutableStateOf<ChatCharacterOption?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    val selected = characters.firstOrNull { it.id == selectedCharacterId }

    Surface(color = Color.White, shadowElevation = 2.dp) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = IconTint)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = selected?.title ?: "默认助手",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { expanded = true }) {
                    Text("切换角色")
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("默认助手") },
                    onClick = {
                        onCharacterSelected(null)
                        expanded = false
                    },
                )
                characters.forEach { character ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(character.title, fontWeight = FontWeight.Medium)
                                    if (character.preview.isNotBlank()) {
                                        Text(
                                            character.preview,
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                // 编辑/删除只对自己上传的角色开放；官方角色没有这两个按钮
                                if (character.mine) {
                                    IconButton(onClick = {
                                        editingCharacter = character
                                        showEditDialog = true
                                        expanded = false
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "编辑角色", tint = IconTint)
                                    }
                                    IconButton(onClick = {
                                        onDeleteCharacter(character.id)
                                        expanded = false
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "删除角色", tint = AppColors.Error)
                                    }
                                }
                            }
                        },
                        onClick = {
                            onCharacterSelected(character.id)
                            expanded = false
                        },
                    )
                }
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = AppColors.Primary)
                            Spacer(Modifier.width(4.dp))
                            Text("新建角色", color = AppColors.Primary)
                        }
                    },
                    onClick = {
                        editingCharacter = null
                        showEditDialog = true
                        expanded = false
                    },
                )
            }
        }
    }

    if (showEditDialog) {
        CharacterEditDialog(
            initial = editingCharacter,
            onDismiss = { showEditDialog = false },
            onConfirm = { title, content ->
                val editing = editingCharacter
                if (editing == null) onCreateCharacter(title, content) else onUpdateCharacter(editing.id, title, content)
                showEditDialog = false
            },
        )
    }
}

/**
 * 创建/编辑自定义角色的弹窗。
 *
 * 只收 title + content（人设正文）两个字段——这是服务端 `CharacterForm` 的必填字段，
 * 头像 url 本轮不做上传入口，留给后续接图片选择器时再补。
 */
@Composable
private fun CharacterEditDialog(
    initial: ChatCharacterOption?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    // 用完整正文而不是 preview：preview 被截断过，回填它会静默截短人设
    var content by remember { mutableStateOf(initial?.content.orEmpty()) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建角色" else "编辑角色") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("角色名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("人设描述（会作为对话的提示词）") },
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
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onVoiceClick: () -> Unit,
) {
    val fromUser = message.role == ChatRole.USER
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (fromUser) OutgoingBubble else IncomingBubble,
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (fromUser) 12.dp else 4.dp,
                bottomEnd = if (fromUser) 4.dp else 12.dp,
            ),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                when {
                    // pending 且还没收到任何增量文本时显示"正在思考"；一旦开始吐字，
                    // 哪怕还没 done，也直接展示已收到的文字（打字机效果的关键）
                    message.pending && message.text.isEmpty() -> PendingRow()
                    message.image != null -> ImageRow(path = message.image.path)
                    message.file != null -> FileRow(file = message.file)
                    message.voice != null -> VoiceRow(
                        text = message.text,
                        playing = false,
                        onClick = onVoiceClick,
                    )
                    else -> MessageText(message)
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
        Text("正在思考…", fontSize = 15.sp, color = IconTint)
    }
}

@Composable
private fun VoiceRow(text: String, playing: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = if (playing) "停止播放" else "播放语音",
                tint = AppColors.Primary,
            )
        }
        Text(text, fontSize = 15.sp, color = Color.Black)
    }
}

@Composable
private fun MessageText(message: ChatMessage) {
    // 仅 assistant 消息走 Markdown：用户自己输入的内容按纯文本显示更符合预期。
    // 流式增量过程中文本不是完整 Markdown（比如代码块只打了一半），Markwon 对不完整
    // 语法是容错的（按纯文本渲染未闭合部分），不会因为还没收完就崩或乱渲染。
    // 后端会把 LaTeX 转成 $...$，Markwon 不认，本轮不做公式渲染。
    if (message.role == ChatRole.ASSISTANT) {
        MarkdownText(markdown = message.text, textColor = Color.Black)
    } else {
        Text(
            text = message.text,
            fontSize = 15.sp,
            color = Color.Black,
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
    onCallClick: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
) {
    var showEmoji by remember { mutableStateOf(false) }
    var showAttach by remember { mutableStateOf(false) }

    Column {
        if (recording) {
            Surface(color = AppColors.Warning.copy(alpha = 0.12f)) {
                Text(
                    "正在录音…松开发送，上滑取消",
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
                    "正在识别语音…",
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = AppColors.Primary,
                )
            }
        }

        Surface(color = Color.White, shadowElevation = 8.dp) {
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
                        contentDescription = "按住说话",
                        tint = if (recording) AppColors.Error else IconTint,
                    )
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    placeholder = { Text("说点什么…") },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                )

                IconButton(onClick = onCallClick) {
                    Icon(Icons.Filled.Call, contentDescription = "语音通话", tint = IconTint)
                }
                IconButton(onClick = { showAttach = !showAttach; showEmoji = false }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "更多",
                        tint = if (showAttach) MaterialTheme.colorScheme.primary else IconTint,
                    )
                }
                IconButton(onClick = { showEmoji = !showEmoji; showAttach = false }) {
                    Icon(
                        Icons.Filled.EmojiEmotions,
                        contentDescription = "表情",
                        tint = if (showEmoji) MaterialTheme.colorScheme.primary else IconTint,
                    )
                }
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = if (canSend) MaterialTheme.colorScheme.primary else Color.Gray,
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
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF3F3F3))
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        AttachItem(label = "相册", icon = Icons.Filled.Image, onClick = onImage)
        AttachItem(label = "文件", icon = Icons.Filled.InsertDriveFile, onClick = onFile)
        AttachItem(label = "视频", icon = Icons.Filled.Videocam) {
            Toast.makeText(context, "视频消息即将上线", Toast.LENGTH_SHORT).show()
        }
        AttachItem(label = "位置", icon = Icons.Filled.LocationOn) {
            Toast.makeText(context, "位置消息即将上线", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun AttachItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
            Icon(icon, contentDescription = label, tint = IconTint, modifier = Modifier.size(28.dp))
        }
        Text(label, fontSize = 12.sp, color = IconTint)
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
        Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = AppColors.Primary)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(file.name, fontSize = 15.sp, color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatFileSize(file.sizeBytes), fontSize = 12.sp, color = IconTint)
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
