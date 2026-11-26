package com.openapi.chatkit.message

import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import com.openapi.chatkit.R
import com.openapi.chatkit.model.ChatItem
import com.openapi.chatkit.model.MessageAction
import com.openapi.chatkit.model.MessagePart
import com.openapi.chatkit.model.SendStatus
import com.openapi.chatkit.model.Sender

/** 长按菜单的目标：哪条消息，以及它在窗口里的位置（菜单锚在它上方或下方）。 */
internal data class MenuTarget(val item: ChatItem, val anchor: Rect)

/**
 * 长按菜单的菜单项（按消息类型）：
 * - 语音：复制（有转写时）、朗读（播放原声）、删除
 * - 用户：重试（发送失败时）、复制、编辑、删除
 * - 助手：复制、朗读、分享（有文字时）、赞/踩（A43-8：footer 只挂最新一条后，
 *   历史回复的赞踩入口由长按菜单提供；走与 footer 芯片同一条 onAction）、
 *   重试（重新生成，只在最新一条回复上）、删除
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
    val like = stringResource(R.string.chatkit_like)
    val dislike = stringResource(R.string.chatkit_dislike)
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
                    // A43-8：赞/踩。已赞/已踩的填充态由 ChatItemMapper 的 Reaction 提供，
                    // 菜单里只给同一文案（执行走与 footer 芯片同一条 onAction，宿主已接线）。
                    add(MenuItem(like, MessageAction.Like, Icons.Outlined.ThumbUp))
                    add(MenuItem(dislike, MessageAction.Dislike, Icons.Outlined.ThumbDown))
                }
                if (isLatestAssistant) add(MenuItem(regenerate, MessageAction.Regenerate, Icons.Outlined.Refresh))
            }
        }
        add(MenuItem(multiSelect, MessageAction.Select, Icons.Outlined.CheckCircle))
        add(MenuItem(delete, MessageAction.Delete, Icons.Outlined.Delete))
    }
}
