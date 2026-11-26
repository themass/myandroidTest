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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
 * 「长按一张附件，要不要弹菜单」的判定：多选进行中不弹；**调用方没给这个附件任何动作时也不弹**。
 *
 * 后面这条是必须的：[AttachmentActionSheet] 是无条件渲染的，actions 为空时会画出一个只有遮罩
 * 和拖条的零行面板——长按看起来「弹了个空抽屉」，比不响应更让人以为界面坏了。
 * `attachmentActions` 默认就是空实现（陪伴页这类不配附件菜单的调用方），而纯图片消息里附件长按
 * 是唯一的长按入口（图片挂在 `onAttachmentLongPress` 上，行级长按只给文字/位置气泡），
 * 所以这条边不守就直接暴露给用户。
 *
 * 提出来放在外面是为了能被测试钉住（[AttachmentMenuGateTest]）：这个判断坐在组合里就观察不到
 * 「没有弹层」——ModalBottomSheet 的把手没有稳定的语义节点。与
 * [com.openapi.chatkit.message.parts.imageArrangement] 同一个提取理由。
 */
internal fun shouldOpenAttachmentMenu(selectionActive: Boolean, actions: List<AttachmentActionKind>): Boolean =
    !selectionActive && actions.isNotEmpty()

/**
 * 「列表滚到顶 → 自动加载更早的一页」的判定（任务 4：删掉常驻的「加载更早」按钮）。
 *
 * 判据：还有更早的历史、当前没在加载、视口已经停在列表最顶
 * （`firstVisibleItemIndex == 0` 且不能再往上滚）。
 * 为什么两个条件都要：`firstVisibleItemIndex == 0` 只说明第一条**在组合范围内**，
 * 第一条被顶出视口上方时它仍然是 0（LazyList 的 index 相对 items，不是相对视口），
 * `canScrollBackward` 才是「真的到顶」。
 *
 * 防重触发由三层拼出来，这里只钉可纯函数验证的那层（loadingOlder 在加载中直接否掉）：
 * 调用点（[ChatMessageList]）还要求「用户真的拖动过列表」且「不是程序化滚动经过顶部」——
 * 那两个状态在组合里，见 `userDragged` / `programmaticScroll`。
 * 与 [shouldOpenAttachmentMenu] 同一个提取理由：判断坐在组合里就观察不到。
 */
/**
 * 「footer（复制/朗读/赞/踩/删除那一排）挂在哪些助手消息上」的判定（A43-8，20261126 用户反馈：
 * 每条历史回复下面都顶着一排按钮，列表很吵）。
 *
 * 规则：只有**最新一条**助手消息挂 footer，其余为空（[ChatMessageItem] 对空集合已经一个像素不画）。
 * 非最新的消息并没有失去这些操作——长按菜单仍然全部提供（[menuItemsFor]）。
 * 提出来放外面（与 [shouldOpenAttachmentMenu] 同一理由）：判定坐在组合里观察不到，提出来才能被测试钉住。
 */
internal fun footerActionsFor(
    item: ChatItem,
    latestAssistantId: String?,
    footerActions: (ChatItem) -> List<MessageAction>,
): List<MessageAction> =
    if (item.sender == Sender.Assistant && item.id == latestAssistantId) footerActions(item) else emptyList()

internal fun shouldLoadOlderAtTop(
    firstVisibleItemIndex: Int,
    canScrollBackward: Boolean,
    loadingOlder: Boolean,
    hasMoreHistory: Boolean,
): Boolean =
    hasMoreHistory && !loadingOlder && firstVisibleItemIndex == 0 && !canScrollBackward

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
    /**
     * 用户侧头像插槽；默认 null，用户消息不带头像（对话页行为不变）。
     * 陪伴页传入「我」字标，让右侧气泡也有头像锚点。
     */
    userAvatar: (@Composable (ChatItem) -> Unit)? = null,
    /**
     * 还有更早的历史可加载（列表滚到顶时自动调 [onLoadOlder]）。默认 false：
     * 不传时列表行为与以前逐像素一致（陪伴页这类没有分页的调用方不受影响）。
     */
    hasMoreHistory: Boolean = false,
    /** 正在加载更早的一页：顶部的细进度条 + 防重复触发。 */
    loadingOlder: Boolean = false,
    /** 滚到顶时自动加载更早的一页。 */
    onLoadOlder: () -> Unit = {},
    /**
     * A36②：停止生成的入口。挂在**正在生成的那条气泡**下方（发送键位让出来，
     * 见 ChatComposer）。默认 null：陪伴页这类调用方完全不变。
     * 只给最后一条（正在生成的气泡就是最后一条）；中途新消息插队也不会给旧消息挂按钮。
     */
    onStopGenerating: (() -> Unit)? = null,
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
    //
    // 关键：首帧 LazyColumn 停在 index=0，此时 canScrollForward=true、isScrollInProgress=false。
    // 若把「从未滚动过」也当成「用户翻走了」去关 following，历史一进来就钉在第一条，
    // 贴底逻辑全停。必须等用户真的拖过一次再评估。
    var following by remember { mutableStateOf(true) }
    var userDragged by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    // 上面三个 LaunchedEffect 也会让 isScrollInProgress 变 true（程序化滚动：新消息、
    // 流式增长、键盘高度）。必须把它们和「用户真的拖动」区分开：
    // 1) 键盘收起：只允许用户拖动收键盘——键盘高度那个效果一触发就去 hide，会把用户
    //    刚弹出的键盘当场收掉；
    // 2) following：程序滚动不能把 userDragged 置真，否则每来一条消息都会重新评估
    //    following，把「刚被程序滚到底」误判成用户翻动过。
    // 三处程序化滚动进入前置 true、结束后清 false（finally：失败也要清，不然粘住 true
    // 会把之后的用户拖动全部吞掉）。
    var programmaticScroll by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                if (!programmaticScroll) {
                    // 用户开始拖动列表：收起软键盘（微信式），不再让键盘挡住阅读
                    userDragged = true
                    keyboard?.hide()
                }
            } else if (userDragged) {
                following = !state.canScrollForward
            }
        }
    }

    // 新增一条消息：自己刚发的一定滚到底；别人的（助手回复开始）只在跟随时滚到底
    LaunchedEffect(last?.key) {
        if (last != null && (last.sender == Sender.User || following)) {
            following = true
            programmaticScroll = true
            try {
                scrollLock.withLock {
                    state.scrollToItem(items.lastIndex)
                    state.scrollBy(BOTTOM_SCROLL)
                }
            } finally {
                programmaticScroll = false
            }
        }
    }
    // 流式增长、以及回复结束时 footer 出现：跟随时无动画贴底，保证最后一条完整显示
    LaunchedEffect(last?.plainText?.length, last?.thinking, last?.streaming) {
        if (following && last != null) {
            programmaticScroll = true
            try {
                scrollLock.withLock { state.scrollBy(BOTTOM_SCROLL) }
            } finally {
                programmaticScroll = false
            }
        }
    }
    // 键盘弹出或收起：列表高度变了，如果原来在底部就保持贴底，否则最后几条会被键盘和输入栏盖住
    // 用键盘高度本身作为 key：键盘动画的每一帧都会重新贴底，等布局稳定后也不会留下被盖住的尾部
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (following && last != null) {
            programmaticScroll = true
            try {
                scrollLock.withLock {
                    state.scrollToItem(items.lastIndex)
                    state.scrollBy(BOTTOM_SCROLL)
                }
            } finally {
                programmaticScroll = false
            }
        }
    }

    // 滚到顶 → 自动加载更早的一页（取代常驻的「加载更早」按钮，任务 4）。
    // rememberUpdatedState：效果本身不随参数重启，避免「加载完成 → 效果重启 →
    // snapshotFlow 重放当前值 → 又触发一次加载」的循环；触发只认快照值的**变化沿**
    // （snapshotFlow 只在值变时发），加载完再想加载，要么再往上滚一下（到顶状态重新成立），
    // 要么 hasMoreHistory 真的变了。
    // 三道协调闸门，缺一都会误触发：
    // 1. `userDragged`：首帧 LazyColumn 就停在 index 0（canScrollBackward=false），
    //    不要求「用户真的拖过一次」的话，打开会话的瞬间就会自动拉一页——那不是用户意图；
    // 2. `programmaticScroll`：程序滚动（新消息贴底、键盘高度）经过顶部不算数
    //    （与上面收键盘/following 同一个协调点）；
    // 3. `loadingOlder`：加载中不再起第二页（判据本体见 [shouldLoadOlderAtTop]）。
    val latestHasMore by rememberUpdatedState(hasMoreHistory)
    val latestLoadingOlder by rememberUpdatedState(loadingOlder)
    val latestOnLoadOlder by rememberUpdatedState(onLoadOlder)
    LaunchedEffect(state) {
        var wasAtTop = false
        snapshotFlow {
            Triple(state.firstVisibleItemIndex to state.canScrollBackward, programmaticScroll, userDragged)
        }.collect { (topPair, scrolling, dragged) ->
            val atTop = dragged && !scrolling && shouldLoadOlderAtTop(
                firstVisibleItemIndex = topPair.first,
                canScrollBackward = topPair.second,
                loadingOlder = latestLoadingOlder,
                hasMoreHistory = latestHasMore,
            )
            if (atTop && !wasAtTop) latestOnLoadOlder()
            wasAtTop = atTop
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
                        // 只有调用方真的给了动作才记下这次长按，见 [shouldOpenAttachmentMenu]
                        // （没有它，不配附件菜单的调用方长按一张图会弹出一个零行的空面板）。
                        onAttachmentLongPress = { target ->
                            // 注意（台账 minor）：`attachmentActions(target)` 在**一次长按**里会被调
                            // 两次——这里判断一次，下面 `AttachmentActionSheet(actions = …)` 再调一次。
                            // 今天的调用方给的是纯 when 常量表（`AttachmentActions.actionsFor`），成本为零；
                            // 谁以后传非平凡的 lambda（要查表、算东西），就把这次长按变成两次计算，
                            // 那时应该在这里算一次、把结果存进 attachment 那个 MenuTarget。
                            if (shouldOpenAttachmentMenu(selection.active, attachmentActions(target))) {
                                attachment = target
                            }
                        },
                        selectionActive = selection.active,
                        // A43-8：footer 只挂最新一条助手消息（判定见 footerActionsFor，有单测）。
                        // 历史回复的复制/朗读/删除仍全部由长按菜单提供。
                        footerActions = footerActionsFor(item, latestAssistantId, footerActions),
                        // 多选时把 overlay 收掉：它不在消息行内部，点它不会切换选中。
                        overlay = if (selection.active) null else { { itemOverlay(item) } },
                        userAvatar = userAvatar,
                        // 生成中的入口只挂在正在生成的那一条（就是列表最后一条）
                        onStopGenerating = if (index == items.lastIndex) onStopGenerating else null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (items.isEmpty()) emptyContent()
        // 「正在加载更早的一页」：列表顶部一条细进度指示（任务 4 用它取代常驻按钮）。
        // 不是按钮：加载由「滚到顶」自动触发，这里只报告正在进行；按内容高度居中在顶部。
        if (loadingOlder) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(2.dp),
                color = ChatKit.colors.accent,
            )
        }
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
            // 这个按钮没有可见文字，读屏必须能报出它是干什么的（整改清单 P2-16）
            contentDescription = stringResource(R.string.chatkit_scroll_to_bottom),
            tint = colors.bubbleText,
            modifier = Modifier.padding(8.dp).size(20.dp),
        )
    }
}
