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

/** 长按菜单的目标：哪条消息，以及它在窗口里的位置（菜单锚在它上方或下方）。 */
internal data class MenuTarget(val item: ChatItem, val anchor: Rect)

/**
 * 长按菜单的菜单项（按消息类型）：
 * - 语音：复制（有转写时）、朗读（播放原声）、删除
 * - 用户：重试（发送失败时）、复制、编辑、删除
 * - 助手：复制、朗读、分享（有文字时）、重试（重新生成，只在最新一条回复上）、删除
 */
@Composable
internal fun menuItemsFor(item: ChatItem, isLatestAssistant: Boolean): List<MenuItem> {
    val hasText = item.plainText.isNotBlank()
    val hasVoice = item.parts.any { it is MessagePart.Voice }
    val isUser = item.sender == Sender.User
    val copy = stringResource(R.string.chatkit_copy)
    val speak = stringResource(R.string.chatkit_speak)
    val edit = stringResource(R.string.chatkit_edit)
    val retry = stringResource(R.string.chatkit_retry)
    val share = stringResource(R.string.chatkit_share)
    val regenerate = stringResource(R.string.chatkit_retry_label)
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
                    add(MenuItem(share, MessageAction.Share, Icons.Outlined.Share))
                }
                if (isLatestAssistant) add(MenuItem(regenerate, MessageAction.Regenerate, Icons.Outlined.Refresh))
            }
        }
        add(MenuItem(multiSelect, MessageAction.Select, Icons.Outlined.CheckCircle))
        add(MenuItem(delete, MessageAction.Delete, Icons.Outlined.Delete))
    }
}
