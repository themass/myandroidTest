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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.openapi.chatkit.R
import com.openapi.chatkit.message.parts.AttachmentTextLayout
import com.openapi.chatkit.message.parts.DefaultMessageText
import com.openapi.chatkit.message.parts.FileCard
import com.openapi.chatkit.message.parts.ImageGrid
import com.openapi.chatkit.message.parts.LocalMessageImageEditHandler
import com.openapi.chatkit.message.parts.LocationBubble
import com.openapi.chatkit.message.parts.TextBubble
import com.openapi.chatkit.message.parts.ThinkingIndicator
import com.openapi.chatkit.message.parts.VideoCard
import com.openapi.chatkit.message.parts.VoiceBubble
import com.openapi.chatkit.message.parts.attachmentTextLayout
import com.openapi.chatkit.model.AttachmentActionKind
import com.openapi.chatkit.model.AttachmentPathInfo
import com.openapi.chatkit.model.AttachmentTarget
import com.openapi.chatkit.model.ChatItem
import com.openapi.chatkit.model.MessageAction
import com.openapi.chatkit.model.MessagePart
import com.openapi.chatkit.model.SendStatus
import com.openapi.chatkit.model.Sender
import com.openapi.chatkit.theme.ChatKit

/** 助手头像：比气泡窄一圈，不抢内容的视觉重量。 */
private val AssistantAvatarSize = 28.dp
/** 头像与气泡之间的间距。 */
private val AssistantAvatarGap = 8.dp

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
    /** 最后一条用户消息且后面没有回复时为 true：在「已发送」旁边显示「重新回答」。 */
    showAnswerAgain: Boolean = false,
    /**
     * 是否在这一行显示头像。由 [ChatMessageList] 按「连续同一发送者的第一条」计算，
     * 否则每条消息头顶一个头像会把列表挤得很吵。用户消息永远不显示头像。
     */
    showAvatar: Boolean = false,
    /** 助手 footer 显示哪些芯片。默认是 chatkit 的标准五件套。 */
    footerActions: List<MessageAction> = defaultAssistantFooterActions(item),
    /** 这一行上方附加的内容（如「她记得」这类来源标签）。null 时布局与原来完全一致。 */
    overlay: (@Composable () -> Unit)? = null,
    /**
     * 用户侧头像插槽。为 null 时用户消息完全不变（对话页即如此：用户消息不带头像）。
     * 非 null 时在气泡**右侧**按 [showAvatar] 同样的「连续同一发送者只显一次」规则渲染，
     * 并留等宽空位，保证气泡右边缘对齐。
     */
    userAvatar: (@Composable (ChatItem) -> Unit)? = null,
    /**
     * A36②：停止生成的入口挂在**正在生成的气泡**下方（不再占用输入栏的发送键位）。
     * null = 不显示（陪伴页这类调用方行为不变）。调用方只在最新一条上非空。
     */
    onStopGenerating: (() -> Unit)? = null,
) {
    val dimens = ChatKit.dimens
    val isUser = item.sender == Sender.User
    val maxWidth = (LocalConfiguration.current.screenWidthDp * dimens.maxContentFraction).dp
    // 助手气泡：屏宽的 88%，且不超过「屏宽减两侧留白」，左右都留出空白
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val assistantMaxWidth = minOf(screenWidth * dimens.assistantMaxContentFraction, screenWidth - dimens.listHorizontalPadding * 2)
    var anchor by remember { mutableStateOf(Rect.Zero) }
    // 读屏用户做不了「长按」，也摸不到弹出来的菜单（整改清单 P2-16）。
    // 长按菜单里有什么，气泡的语义节点就挂什么——执行走同一条 onAction
    // （「多选」仍由列表本地拦截，读屏和点菜单殊途同归）。
    // 挂在气泡自己的节点上（见 MessageContent），而不是外面套一层：
    // 否则一条消息会被读屏拆成两个焦点，正文还要再读一遍。
    val screenReaderActions: List<CustomAccessibilityAction> =
        if (selectionActive || item.thinking || item.streaming) {
            emptyList()
        } else {
            menuItemsFor(item, isLatestAssistant).map { entry ->
                CustomAccessibilityAction(entry.label) {
                    onAction(entry.action)
                    true
                }
            }
        }

    // 根节点从 Row 换成 Column 只为容纳 [overlay]（如「她记得」来源标签）。
    // overlay 为 null 时不产生任何子项，Row 独占 Column 宽度，布局与原版逐像素一致。
    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        overlay?.invoke()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.listHorizontalPadding)
                // 锚点量的是「消息行」而不是含 overlay 的整块：长按菜单仍贴着气泡
                .onGloballyPositioned { anchor = it.boundsInWindow() },
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            // 头像只在助手侧、且是「连续同一发送者的第一条」时显示。
            // 不显示的那些行留一个等宽空位，这样气泡的左边缘对齐，列表不会忽宽忽窄。
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .padding(end = AssistantAvatarGap)
                        .size(AssistantAvatarSize),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (showAvatar) {
                        Box(Modifier.size(AssistantAvatarSize).clip(CircleShape)) { avatar(item) }
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f, fill = false),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isUser) SendStatusIndicator(item.status) { onAction(MessageAction.Retry) }
                    // 每条助手回复（生成完成后）都有 footer；生成中的那条不显示
                    val assistantFooter: (@Composable () -> Unit)? =
                        // 多选时不显示 footer：它的按钮在消息行下面，会抢走点击
                        if (!isUser && !item.streaming && !item.thinking && !selectionActive && footerActions.isNotEmpty()) {
                            { AssistantFooter(item = item, onAction = onAction, actions = footerActions) }
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
                        screenReaderActions = screenReaderActions,
                    )
                }
                // A36②：正在生成（思考中或流式）的助手气泡下方挂「停止生成」。
                // 入口跟着被停止的对象走，发送键位因此永远留给发送（A36①）。
                if (!isUser && (item.streaming || item.thinking) && onStopGenerating != null) {
                    StopGeneratingChip(onStop = onStopGenerating)
                }
                if (isUser && showSentLabel && item.status == SendStatus.Sent) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.chatkit_sent),
                            fontSize = ChatKit.typography.caption,
                            color = ChatKit.colors.secondaryText,
                        )
                        // 最后一条用户消息还没有回复（例如回复前点了停止）：给一个重新回答的入口，不重复发送
                        if (showAnswerAgain) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.chatkit_answer_again),
                                modifier = Modifier.clickable { onAction(MessageAction.Regenerate) }.padding(4.dp),
                                fontSize = ChatKit.typography.caption,
                                color = ChatKit.colors.accent,
                            )
                        }
                    }
                }
            }
            // 用户侧头像：只有调用方传了插槽才出现，且同样只在「连续同一发送者的第一条」渲染。
            // 不显示的那些行留等宽空位，气泡右边缘才不会忽宽忽窄。
            if (isUser && userAvatar != null) {
                Box(
                    modifier = Modifier
                        .padding(start = AssistantAvatarGap)
                        .size(AssistantAvatarSize),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (showAvatar) {
                        Box(Modifier.size(AssistantAvatarSize).clip(CircleShape)) { userAvatar(item) }
                    }
                }
            }
        }
    }
}

/**
 * 「停止生成」小芯片：挂在正在生成（思考中或流式）的助手气泡下方（A36②）。
 * 样式沿用「已发送 / 重新回答」那一行：caption 级文字、accent 色、可点。
 * 带一个方块停止图标——只有一行小字容易被当成状态说明而不是按钮（44dp 高由点击区撑）。
 */
@Composable
internal fun StopGeneratingChip(onStop: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onStop)
            .heightIn(min = 32.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("stop-generating"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Stop,
            contentDescription = null,
            tint = ChatKit.colors.accent,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.chatkit_stop_generating),
            fontSize = ChatKit.typography.caption,
            color = ChatKit.colors.accent,
        )
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
    /** 读屏动作（同长按菜单项），挂在有内容的气泡节点上；见 ChatMessageItem 的注释。 */
    screenReaderActions: List<CustomAccessibilityAction> = emptyList(),
) {
    val sender = item.sender
    val images = item.parts.filterIsInstance<MessagePart.Images>()
    val files = item.parts.filterIsInstance<MessagePart.File>()
    val videos = item.parts.filterIsInstance<MessagePart.Video>()
    val locations = item.parts.filterIsInstance<MessagePart.Location>()
    val voices = item.parts.filterIsInstance<MessagePart.Voice>()
    val texts = item.parts.filterIsInstance<MessagePart.Text>().filter { it.text.isNotEmpty() }
    val hasContent = images.isNotEmpty() || files.isNotEmpty() || videos.isNotEmpty() ||
        locations.isNotEmpty() || voices.isNotEmpty() || texts.isNotEmpty()
    // 「附件 + 文字 = 一条消息」的判定（决策在 attachmentTextLayout，有单测）。
    // Merged = 附件和文字共用一个气泡（202611 用户反馈：之前看图+文字像两条消息）。
    val merged = attachmentTextLayout(
        hasAttachments = images.isNotEmpty() || files.isNotEmpty() || videos.isNotEmpty(),
        hasText = texts.isNotEmpty(),
    ) == AttachmentTextLayout.Merged
    // 消息大图的「编辑」由宿主经 CompositionLocal 提供；null 时查看器与从前完全一致。
    val onEditImage = LocalMessageImageEditHandler.current

    Column(
        horizontalAlignment = if (sender == Sender.User) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (item.thinking && !hasContent) {
            TextBubble(sender = sender, onLongPress = onLongPress) { ThinkingIndicator() }
            return@Column
        }
        if (merged) {
            // 单气泡：附件在上、一条分隔线、文字在下——同底色同圆角，读起来是一件事。
            // 附件与文字之间的内部间距（8dp）刻意小于消息间距（14dp）：气泡之间的空 > 气泡内的空。
            // footer 仍挂在文字之后（与 Separate 分支同一语义）；读屏动作挂在这个唯一的气泡上。
            TextBubble(
                sender = sender,
                onLongPress = onLongPress,
                modifier = Modifier.widthIn(max = maxWidth),
                footer = footer,
                screenReaderActions = screenReaderActions,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    images.forEach { part ->
                        ImageGrid(
                            sources = part.sources,
                            maxWidth = maxWidth,
                            onLongPress = { source -> onAttachmentLongPress(AttachmentTarget.Image(source)) },
                            layout = part.layout,
                            onRetryImage = { index -> onAction(MessageAction.RetryImage(index)) },
                            onEdit = onEditImage,
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
                    videos.forEach { video ->
                        VideoCard(
                            video = video,
                            onClick = { onAction(MessageAction.OpenVideo(video)) },
                            onLongPress = { onAttachmentLongPress(AttachmentTarget.File(MessagePart.File(video.path, video.name, 0))) },
                            modifier = Modifier.widthIn(max = maxWidth),
                        )
                    }
                    HorizontalDivider(color = ChatKit.colors.divider)
                    texts.forEach { part -> text(item, part.text) }
                }
            }
        } else {
            images.forEach { part ->
                ImageGrid(
                    sources = part.sources,
                    maxWidth = maxWidth,
                    onLongPress = { source -> onAttachmentLongPress(AttachmentTarget.Image(source)) },
                    layout = part.layout,
                    // 失败图片上的点击：组件已做本地重载，这里把「换源」的活儿交给调用方（P1-10）
                    onRetryImage = { index -> onAction(MessageAction.RetryImage(index)) },
                    onEdit = onEditImage,
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
            videos.forEach { video ->
                VideoCard(
                    video = video,
                    onClick = { onAction(MessageAction.OpenVideo(video)) },
                    onLongPress = { onAttachmentLongPress(AttachmentTarget.File(MessagePart.File(video.path, video.name, 0))) },
                    modifier = Modifier.widthIn(max = maxWidth),
                )
            }
        }
        locations.forEach { location ->
            LocationBubble(
                sender = sender,
                location = location,
                onOpenMap = { onAction(MessageAction.OpenLocation(location)) },
                onLongPress = onLongPress,
                modifier = Modifier.widthIn(max = maxWidth),
                screenReaderActions = screenReaderActions,
            )
        }
        voices.forEach { voice ->
            VoiceBubble(
                sender = sender,
                voice = voice,
                onClick = { onAction(MessageAction.PlayVoice) },
                onLongPress = onLongPress,
                screenReaderActions = screenReaderActions,
                modifier = Modifier.widthIn(max = maxWidth),
            )
        }
        if (!merged) {
            texts.forEachIndexed { index, part ->
                TextBubble(
                    sender = sender,
                    onLongPress = onLongPress,
                    modifier = Modifier.widthIn(max = maxWidth),
                    footer = if (index == texts.lastIndex) footer else null,
                    screenReaderActions = screenReaderActions,
                ) {
                    text(item, part.text)
                }
            }
        }
    }
}
