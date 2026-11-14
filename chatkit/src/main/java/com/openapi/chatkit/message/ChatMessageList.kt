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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * @param itemOverlay 每条消息**上方**的附加内容插槽；默认空实现，布局与不传时完全一致。
 *   用于承载 chatkit 的 [ChatItem] 里没有的领域概念（如陪伴页「她记得」这类来源标签）。
 * @param footerActions 助手 footer 显示哪些芯片；默认 chatkit 的标准五件套。
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
    /** 每条消息上方的附加内容；不传时列表布局与以前逐像素一致。 */
    itemOverlay: @Composable (ChatItem) -> Unit = {},
    /** 助手 footer 显示哪些芯片、按什么顺序。 */
    footerActions: (ChatItem) -> List<MessageAction> = { defaultAssistantFooterActions(it) },
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

    // 三个 LaunchedEffect 都会去滚动这个列表（新消息、流式增长、键盘高度）。
    // 它们可能在列表完成第一帧布局之前**同时**调 scrollToItem/scrollBy：
    // LazyColumn 内部的 AwaitFirstLayoutModifier 只保存一份等待者列表，两个等待者
    // 同时挂上去、其中一个先被唤醒并清空列表，另一个醒来就 IndexOutOfBoundsException。
    // 真机上首帧就有多条消息时偶发；Robolectric 下必现。
    // 用一把互斥锁把滚动串行化：并发滚动请求本来也没有「同时生效」的语义。
    val scrollLock = remember { Mutex() }

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
            scrollLock.withLock {
                state.scrollToItem(items.lastIndex)
                state.scrollBy(BOTTOM_SCROLL)
            }
        }
    }
    // 流式增长、以及回复结束时 footer 出现：跟随时无动画贴底，保证最后一条完整显示
    LaunchedEffect(last?.plainText?.length, last?.thinking, last?.streaming) {
        if (following && last != null) scrollLock.withLock { state.scrollBy(BOTTOM_SCROLL) }
    }
    // 键盘弹出或收起：列表高度变了，如果原来在底部就保持贴底，否则最后几条会被键盘和输入栏盖住
    // 用键盘高度本身作为 key：键盘动画的每一帧都会重新贴底，等布局稳定后也不会留下被盖住的尾部
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (following && last != null) {
            scrollLock.withLock {
                state.scrollToItem(items.lastIndex)
                state.scrollBy(BOTTOM_SCROLL)
            }
        }
    }

    Box(modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(ChatKit.dimens.messageSpacing),
        ) {
            itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
                // 多选时：可选的消息左侧有勾选圆点，整行点击切换选中；生成中的消息不能选
                val selectable = !item.thinking && !item.streaming && item.id.isNotBlank()
                // 连续同一发送者只在第一条显示头像：每行都顶一个头像会把列表挤得很吵，
                // 而头像本身不携带"这是第几条"的信息。
                val showAvatar = items.getOrNull(index - 1)?.sender != item.sender
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
                        showAvatar = showAvatar,
                        text = text,
                        onAction = { action -> handleAction(item, action) },
                        onLongPress = { anchor -> if (!selection.active) menu = MenuTarget(item, anchor) },
                        onAttachmentLongPress = { target -> if (!selection.active) attachment = target },
                        selectionActive = selection.active,
                        footerActions = footerActions(item),
                        // 多选时把 overlay 收掉：它不在消息行内部，点它不会切换选中。
                        overlay = if (selection.active) null else { { itemOverlay(item) } },
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
