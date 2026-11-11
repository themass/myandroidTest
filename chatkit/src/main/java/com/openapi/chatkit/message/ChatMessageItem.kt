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
                        { AssistantFooter(item = item, onAction = onAction) }
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
