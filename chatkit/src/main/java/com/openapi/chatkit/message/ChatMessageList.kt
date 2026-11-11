package com.openapi.chatkit.message

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
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
import androidx.compose.ui.platform.LocalDensity
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
    // 键盘弹出或收起：列表高度变了，如果原来在底部就保持贴底，否则最后几条会被键盘和输入栏盖住
    // 用键盘高度本身作为 key：键盘动画的每一帧都会重新贴底，等布局稳定后也不会留下被盖住的尾部
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (following && last != null) {
            state.scrollToItem(items.lastIndex)
            state.scrollBy(BOTTOM_SCROLL)
        }
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
                        showAnswerAgain = item.id == latestUserId && last?.sender == Sender.User && !selection.active,
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
                MessageMenuSheet(items = menuItemsFor(target.item, target.item.id == latestAssistantId), onSelect = onSelect, onDismiss = { menu = null })
            } else {
                MessageMenu(
                    anchor = target.anchor,
                    items = menuItemsFor(target.item, target.item.id == latestAssistantId),
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

/** 一次滚动的距离取一个很大的值：列表会在到达底部时自己截断，所以等价于「滚到最底」。 */
internal const val BOTTOM_SCROLL = 100_000f

/** 不在底部时出现的「回到底部」小按钮。 */
@Composable
internal fun ScrollToBottomChip(modifier: Modifier, onClick: () -> Unit) {
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
