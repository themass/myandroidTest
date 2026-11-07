package com.openapi.chatkit.message

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openapi.chatkit.message.parts.DefaultMessageText
import com.openapi.chatkit.message.parts.FileCard
import com.openapi.chatkit.message.parts.ImageGrid
import com.openapi.chatkit.message.parts.TextBubble
import com.openapi.chatkit.message.parts.ThinkingDots
import com.openapi.chatkit.message.parts.VoiceBubble
import com.openapi.chatkit.model.ChatItem
import com.openapi.chatkit.model.MessageAction
import com.openapi.chatkit.model.MessagePart
import com.openapi.chatkit.model.SendStatus
import com.openapi.chatkit.model.Sender
import com.openapi.chatkit.theme.ChatKit

/**
 * 消息列表：新消息或流式增量到达时自动滚到底部。
 *
 * @param avatar 头像插槽，由调用方提供（组件库不知道角色和用户头像从哪来）
 * @param text 文字渲染插槽，默认纯文本；助手消息可以传 Markdown 渲染
 * @param onAction 所有消息操作的唯一入口
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
    onLongPress: (ChatItem) -> Unit = {},
    emptyContent: @Composable () -> Unit = {},
) {
    val latestAssistantId = items.lastOrNull { it.sender == Sender.Assistant && !it.thinking }?.id
    val last = items.lastOrNull()
    val scope = rememberCoroutineScope()

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
    // 流式增长：跟随时无动画贴底。不用 animateScrollToItem——每个增量都会取消上一次动画。
    LaunchedEffect(last?.plainText?.length, last?.thinking) {
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
                ChatMessageItem(
                    item = item,
                    isLatestAssistant = item.id == latestAssistantId,
                    avatar = avatar,
                    text = text,
                    onAction = { action -> onAction(item, action) },
                    onLongPress = { onLongPress(item) },
                )
            }
        }
        if (items.isEmpty()) emptyContent()
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
 * 一条消息：头像 + 内容 + footer。用户消息靠右（头像在右），助手消息靠左（头像在左）。
 * footer 与内容同侧对齐：用户 footer 贴着气泡右缘，助手 footer 从头像右侧开始。
 */
@Composable
fun ChatMessageItem(
    item: ChatItem,
    isLatestAssistant: Boolean,
    avatar: @Composable (ChatItem) -> Unit,
    text: @Composable (ChatItem, String) -> Unit,
    onAction: (MessageAction) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = ChatKit.dimens
    val isUser = item.sender == Sender.User
    val maxWidth = (LocalConfiguration.current.screenWidthDp * dimens.maxContentFraction).dp
    // 整行可用宽度：屏宽减去两侧留白和头像区
    val footerMaxWidth = LocalConfiguration.current.screenWidthDp.dp - dimens.listHorizontalPadding * 2 - dimens.avatarSize - dimens.avatarGap
    val showFooter = if (isUser) item.status != SendStatus.Sending else !item.thinking

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = dimens.listHorizontalPadding),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            Box(Modifier.size(dimens.avatarSize)) { avatar(item) }
            Spacer(Modifier.width(dimens.avatarGap))
        }
        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isUser) SendStatusIndicator(item.status) { onAction(MessageAction.Retry) }
                // 每条助手回复（生成完成后）都有 footer；生成中的那条不显示
                val assistantFooter: (@Composable () -> Unit)? =
                    if (!isUser && !item.streaming && !item.thinking) {
                        { AssistantFooter(item = item, isLatest = isLatestAssistant, onAction = onAction) }
                    } else {
                        null
                    }
                MessageContent(
                    item = item,
                    // 带 footer 的助手气泡要放下 6 个芯片（约 256dp），按整行宽度而不是 72% 限宽
                    maxWidth = if (assistantFooter != null) footerMaxWidth else maxWidth,
                    text = text,
                    onAction = onAction,
                    onLongPress = onLongPress,
                    footer = assistantFooter,
                )
            }
            if (isUser && showFooter) {
                MessageFooter(item = item, onAction = onAction)
            }
        }
        if (isUser) {
            Spacer(Modifier.width(dimens.avatarGap))
            Box(Modifier.size(dimens.avatarSize)) { avatar(item) }
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
    /** 助手最新回复的 footer：放进最后一个文字气泡里面。 */
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
            TextBubble(sender = sender, onLongPress = onLongPress) { ThinkingDots() }
            return@Column
        }
        images.forEach { part -> ImageGrid(sources = part.sources, maxWidth = maxWidth, onLongPress = onLongPress) }
        files.forEach { file ->
            FileCard(file = file, onClick = { onAction(MessageAction.OpenFile(file)) }, onLongPress = onLongPress)
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
