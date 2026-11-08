package com.openapi.chatkit.message

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.openapi.chatkit.R
import com.openapi.chatkit.message.parts.DefaultMessageText
import com.openapi.chatkit.message.parts.FileCard
import com.openapi.chatkit.message.parts.ImageGrid
import com.openapi.chatkit.message.parts.TextBubble
import com.openapi.chatkit.message.parts.ThinkingIndicator
import com.openapi.chatkit.message.parts.VoiceBubble
import com.openapi.chatkit.model.AttachmentActionKind
import com.openapi.chatkit.model.AttachmentPathInfo
import com.openapi.chatkit.model.AttachmentTarget
import com.openapi.chatkit.model.ChatItem
import com.openapi.chatkit.model.MessageAction
import com.openapi.chatkit.model.MessagePart
import com.openapi.chatkit.model.SendStatus
import com.openapi.chatkit.model.Sender
import com.openapi.chatkit.theme.ChatKit

/**
 * 消息列表：新消息或流式增量到达时自动滚到底部。长按消息弹出操作菜单（见 [MessageMenu]）。
 *
 * @param avatar 头像插槽，可选；App 不再传（消息不显示头像）
 * @param text 文字渲染插槽，默认纯文本；助手消息可以传 Markdown 渲染
 * @param onAction 所有消息操作的唯一入口（长按菜单里的操作也走这里）
 * @param emptyContent 列表为空时显示的内容
 */
@Composable
fun ChatMessageList(
    items: List<ChatItem>,
    onAction: (ChatItem, MessageAction) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    avatar: @Composable (ChatItem) -> Unit = {},
    text: @Composable (ChatItem, String) -> Unit = { _, value -> DefaultMessageText(value) },
    emptyContent: @Composable () -> Unit = {},
    /** 多选状态：由调用方持有（底部操作条也要用），长按菜单的「多选」会进入它。 */
    selection: MessageSelectionState = rememberMessageSelection(),
    /** 长按菜单样式（原型对比）。 */
    menuStyle: MessageMenuStyle = MessageMenuStyle.Popup,
    /** 附件长按菜单里有哪些动作（由调用方按图片 / 文件决定）。 */
    attachmentActions: (AttachmentTarget) -> List<AttachmentActionKind> = { emptyList() },
    /** 点了附件菜单里的某个动作。 */
    onAttachmentAction: (AttachmentTarget, AttachmentActionKind) -> Unit = { _, _ -> },
    /** 「查看路径」卡片的内容（由调用方提供）；返回 null 时不显示卡片。 */
    attachmentInfo: (AttachmentTarget) -> AttachmentPathInfo? = { null },
) {
    val latestAssistantId = items.lastOrNull { it.sender == Sender.Assistant && !it.thinking }?.id
    val latestUserId = items.lastOrNull { it.sender == Sender.User }?.id
    val last = items.lastOrNull()
    val scope = rememberCoroutineScope()
    // 当前打开的长按菜单：哪条消息、锚在哪个位置
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    // 当前打开的附件长按菜单（图片或文件）
    var attachment by remember { mutableStateOf<AttachmentTarget?>(null) }
    var pathCard by remember { mutableStateOf<AttachmentPathInfo?>(null) }
    // 「多选」不交给调用方：这里直接进入多选状态，其它动作照常交给调用方
    val handleAction: (ChatItem, MessageAction) -> Unit = { item, action ->
        if (action == MessageAction.Select) selection.start(item.id) else onAction(item, action)
    }

    // 是否跟随最新内容：只由用户的滑动决定。用户停下来时如果在底部就跟随，往上翻了就不再抢滚动。
    // 内容增长（流式）本身不会改变这个状态，所以不会出现「刚长高一点就被判定为不在底部」。
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) following = !state.canScrollForward
        }
    }

    // 新增一条消息：自己刚发的一定滚到底；别人的（助手回复开始）只在跟随时滚到底
    LaunchedEffect(last?.key) {
        if (last != null && (last.sender == Sender.User || following)) {
            following = true
            state.scrollToItem(items.lastIndex)
            state.scrollBy(BOTTOM_SCROLL)
        }
    }
    // 流式增长、以及回复结束时 footer 出现：跟随时无动画贴底，保证最后一条完整显示
    LaunchedEffect(last?.plainText?.length, last?.thinking, last?.streaming) {
        if (following && last != null) state.scrollBy(BOTTOM_SCROLL)
    }

    Box(modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(ChatKit.dimens.messageSpacing),
        ) {
            items(items, key = { it.key }) { item ->
                // 多选时：可选的消息左侧有勾选圆点，整行点击切换选中；生成中的消息不能选
                val selectable = !item.thinking && !item.streaming && item.id.isNotBlank()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (selection.active && selectable) Modifier.clickable { selection.toggle(item.id) } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selection.active && selectable) {
                        SelectionDot(selected = selection.isSelected(item.id), modifier = Modifier.padding(start = 12.dp))
                    }
                    ChatMessageItem(
                        item = item,
                        isLatestAssistant = item.id == latestAssistantId,
                        showSentLabel = item.id == latestUserId,
                        avatar = avatar,
                        text = text,
                        onAction = { action -> handleAction(item, action) },
                        onLongPress = { anchor -> if (!selection.active) menu = MenuTarget(item, anchor) },
                        onAttachmentLongPress = { target -> if (!selection.active) attachment = target },
                        selectionActive = selection.active,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (items.isEmpty()) emptyContent()
        menu?.let { target ->
            val onSelect: (MessageAction) -> Unit = { action ->
                menu = null
                handleAction(target.item, action)
            }
            if (menuStyle == MessageMenuStyle.Sheet) {
                MessageMenuSheet(items = menuItemsFor(target.item), onSelect = onSelect, onDismiss = { menu = null })
            } else {
                MessageMenu(
                    anchor = target.anchor,
                    items = menuItemsFor(target.item),
                    onSelect = onSelect,
                    onDismiss = { menu = null },
                )
            }
        }
        attachment?.let { target ->
            AttachmentActionSheet(
                actions = attachmentActions(target),
                onSelect = { kind ->
                    attachment = null
                    if (kind == AttachmentActionKind.ShowPath) {
                        pathCard = attachmentInfo(target)
                    } else {
                        onAttachmentAction(target, kind)
                    }
                },
                onDismiss = { attachment = null },
            )
        }
        pathCard?.let { info -> AttachmentPathCard(info = info, onDismiss = { pathCard = null }) }
        if (!following && items.isNotEmpty()) {
            ScrollToBottomChip(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                onClick = {
                    following = true
                    scope.launch {
                        state.animateScrollToItem(items.lastIndex)
                        state.scrollBy(BOTTOM_SCROLL)
                    }
                },
            )
        }
    }
}

/** 长按菜单的目标：哪条消息，以及它在窗口里的位置（菜单锚在它上方或下方）。 */
private data class MenuTarget(val item: ChatItem, val anchor: Rect)

/**
 * 长按菜单的菜单项（按消息类型）：
 * - 语音：复制（有转写时）、朗读（播放原声）、删除
 * - 用户：重试（发送失败时）、复制、编辑、删除
 * - 助手：复制、朗读、删除
 */
@Composable
private fun menuItemsFor(item: ChatItem): List<MenuItem> {
    val hasText = item.plainText.isNotBlank()
    val hasVoice = item.parts.any { it is MessagePart.Voice }
    val isUser = item.sender == Sender.User
    val copy = stringResource(R.string.chatkit_copy)
    val speak = stringResource(R.string.chatkit_speak)
    val edit = stringResource(R.string.chatkit_edit)
    val retry = stringResource(R.string.chatkit_retry)
    val delete = stringResource(R.string.chatkit_delete)
    val multiSelect = stringResource(R.string.chatkit_multi_select)
    return buildList {
        when {
            hasVoice -> {
                if (hasText) add(MenuItem(copy, MessageAction.Copy, Icons.Outlined.ContentCopy))
                add(MenuItem(speak, MessageAction.PlayVoice, Icons.Outlined.PlayArrow))
            }
            isUser -> {
                if (item.status == SendStatus.Failed) add(MenuItem(retry, MessageAction.Retry, Icons.Outlined.Refresh))
                if (hasText) {
                    add(MenuItem(copy, MessageAction.Copy, Icons.Outlined.ContentCopy))
                    add(MenuItem(edit, MessageAction.Edit, Icons.Outlined.Edit))
                }
            }
            else -> {
                if (hasText) {
                    add(MenuItem(copy, MessageAction.Copy, Icons.Outlined.ContentCopy))
                    add(MenuItem(speak, MessageAction.Speak, Icons.AutoMirrored.Outlined.VolumeUp))
                }
            }
        }
        add(MenuItem(multiSelect, MessageAction.Select, Icons.Outlined.CheckCircle))
        add(MenuItem(delete, MessageAction.Delete, Icons.Outlined.Delete))
    }
}

/** 一次滚动的距离取一个很大的值：列表会在到达底部时自己截断，所以等价于「滚到最底」。 */
private const val BOTTOM_SCROLL = 100_000f

/** 不在底部时出现的「回到底部」小按钮。 */
@Composable
private fun ScrollToBottomChip(modifier: Modifier, onClick: () -> Unit) {
    val colors = ChatKit.colors
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = colors.surface,
        shadowElevation = 4.dp,
    ) {
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.bubbleText,
            modifier = Modifier.padding(8.dp).size(20.dp),
        )
    }
}

/**
 * 一条消息：内容 + footer（助手）。用户消息靠右，助手消息靠左；不显示头像。
 * 长按时把这一行在窗口里的位置交给 [onLongPress]，由列表弹出菜单。
 */
@Composable
fun ChatMessageItem(
    item: ChatItem,
    isLatestAssistant: Boolean,
    /** 最新一条用户消息发送成功后，气泡下方显示「已发送」。 */
    showSentLabel: Boolean,
    avatar: @Composable (ChatItem) -> Unit,
    text: @Composable (ChatItem, String) -> Unit,
    onAction: (MessageAction) -> Unit,
    onLongPress: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    /** 长按图片或文件：交给列表打开附件菜单。 */
    onAttachmentLongPress: (AttachmentTarget) -> Unit = {},
    /** 多选中：不显示 footer，避免它的按钮抢走点击。 */
    selectionActive: Boolean = false,
) {
    val dimens = ChatKit.dimens
    val isUser = item.sender == Sender.User
    val maxWidth = (LocalConfiguration.current.screenWidthDp * dimens.maxContentFraction).dp
    // 助手气泡：屏宽的 88%，且不超过「屏宽减两侧留白」，左右都留出空白
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val assistantMaxWidth = minOf(screenWidth * dimens.assistantMaxContentFraction, screenWidth - dimens.listHorizontalPadding * 2)
    var anchor by remember { mutableStateOf(Rect.Zero) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.listHorizontalPadding)
            .onGloballyPositioned { anchor = it.boundsInWindow() },
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isUser) SendStatusIndicator(item.status) { onAction(MessageAction.Retry) }
                // 每条助手回复（生成完成后）都有 footer；生成中的那条不显示
                val assistantFooter: (@Composable () -> Unit)? =
                    // 多选时不显示 footer：它的按钮在消息行下面，会抢走点击
                    if (!isUser && !item.streaming && !item.thinking && !selectionActive) {
                        { AssistantFooter(item = item, isLatest = isLatestAssistant, onAction = onAction) }
                    } else {
                        null
                    }
                MessageContent(
                    item = item,
                    // 用户：85% 屏宽；助手：88% 屏宽（带 footer 的 6 个芯片约 256dp 也放得下）；图片、语音、文件都用同一个限宽
                    maxWidth = if (isUser) maxWidth else assistantMaxWidth,
                    text = text,
                    onAction = onAction,
                    onLongPress = { onLongPress(anchor) },
                    onAttachmentLongPress = onAttachmentLongPress,
                    footer = assistantFooter,
                )
            }
            if (isUser && showSentLabel && item.status == SendStatus.Sent) {
                Text(
                    stringResource(R.string.chatkit_sent),
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = ChatKit.typography.caption,
                    color = ChatKit.colors.secondaryText,
                )
            }
        }
    }
}

/**
 * 消息内容：按「图片 → 文件 → 语音 → 文字」的固定顺序堆叠各部分，
 * 所以一条消息可以是任意组合（多图 + 文字、文件 + 文字、语音 + 转写……）。
 */
@Composable
internal fun MessageContent(
    item: ChatItem,
    maxWidth: Dp,
    text: @Composable (ChatItem, String) -> Unit,
    onAction: (MessageAction) -> Unit,
    onLongPress: () -> Unit,
    onAttachmentLongPress: (AttachmentTarget) -> Unit = {},
    /** 助手回复的 footer：放进最后一个文字气泡里面。 */
    footer: (@Composable () -> Unit)? = null,
) {
    val sender = item.sender
    val images = item.parts.filterIsInstance<MessagePart.Images>()
    val files = item.parts.filterIsInstance<MessagePart.File>()
    val voices = item.parts.filterIsInstance<MessagePart.Voice>()
    val texts = item.parts.filterIsInstance<MessagePart.Text>().filter { it.text.isNotEmpty() }
    val hasContent = images.isNotEmpty() || files.isNotEmpty() || voices.isNotEmpty() || texts.isNotEmpty()

    Column(
        horizontalAlignment = if (sender == Sender.User) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (item.thinking && !hasContent) {
            TextBubble(sender = sender, onLongPress = onLongPress) { ThinkingIndicator() }
            return@Column
        }
        images.forEach { part ->
            ImageGrid(
                sources = part.sources,
                maxWidth = maxWidth,
                onLongPress = { source -> onAttachmentLongPress(AttachmentTarget.Image(source)) },
            )
        }
        files.forEach { file ->
            FileCard(
                file = file,
                onClick = { onAction(MessageAction.OpenFile(file)) },
                onLongPress = { onAttachmentLongPress(AttachmentTarget.File(file)) },
                modifier = Modifier.widthIn(max = maxWidth),
            )
        }
        voices.forEach { voice ->
            VoiceBubble(
                sender = sender,
                voice = voice,
                onClick = { onAction(MessageAction.PlayVoice) },
                onLongPress = onLongPress,
                modifier = Modifier.widthIn(max = maxWidth),
            )
        }
        texts.forEachIndexed { index, part ->
            TextBubble(
                sender = sender,
                onLongPress = onLongPress,
                modifier = Modifier.widthIn(max = maxWidth),
                footer = if (index == texts.lastIndex) footer else null,
            ) {
                text(item, part.text)
            }
        }
    }
}
