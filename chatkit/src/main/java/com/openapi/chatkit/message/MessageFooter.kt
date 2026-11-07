package com.openapi.chatkit.message

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openapi.chatkit.R
import com.openapi.chatkit.model.ChatItem
import com.openapi.chatkit.model.MessageAction
import com.openapi.chatkit.model.Reaction
import com.openapi.chatkit.model.SendStatus
import com.openapi.chatkit.model.Sender
import com.openapi.chatkit.theme.ChatKit

/**
 * 消息底部操作栏。助手和用户两侧用同一个 [FooterIcon]，图标大小、点击区域、颜色一致。
 * - 助手（参照抖音 / 豆包）：复制、朗读、赞、踩、分享；最新一条末尾多一个「重新生成」。
 * - 用户：发送失败时「重新发送」，有文字时复制、编辑，最后删除。
 */
@Composable
fun MessageFooter(
    item: ChatItem,
    isLatestAssistant: Boolean,
    onAction: (MessageAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (item.sender == Sender.Assistant) {
        AssistantFooter(item, isLatestAssistant, onAction, modifier)
    } else {
        UserFooter(item, onAction, modifier)
    }
}

@Composable
private fun AssistantFooter(
    item: ChatItem,
    isLatest: Boolean,
    onAction: (MessageAction) -> Unit,
    modifier: Modifier,
) {
    val hasText = item.plainText.isNotBlank()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (hasText) {
            FooterIcon(Icons.Outlined.ContentCopy, stringResource(R.string.chatkit_copy)) { onAction(MessageAction.Copy) }
            FooterIcon(
                icon = if (item.speaking) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Outlined.VolumeUp,
                description = stringResource(if (item.speaking) R.string.chatkit_speaking else R.string.chatkit_speak),
                active = item.speaking,
            ) { onAction(MessageAction.Speak) }
        }
        FooterIcon(
            icon = if (item.reaction == Reaction.Like) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
            description = stringResource(R.string.chatkit_like),
            active = item.reaction == Reaction.Like,
        ) { onAction(MessageAction.Like) }
        FooterIcon(
            icon = if (item.reaction == Reaction.Dislike) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
            description = stringResource(R.string.chatkit_dislike),
            active = item.reaction == Reaction.Dislike,
        ) { onAction(MessageAction.Dislike) }
        if (hasText) {
            FooterIcon(Icons.Outlined.Share, stringResource(R.string.chatkit_share)) { onAction(MessageAction.Share) }
        }
        if (isLatest) {
            FooterDivider()
            FooterIcon(Icons.Outlined.Refresh, stringResource(R.string.chatkit_regenerate)) { onAction(MessageAction.Regenerate) }
        }
    }
}

@Composable
private fun UserFooter(item: ChatItem, onAction: (MessageAction) -> Unit, modifier: Modifier) {
    val hasText = item.plainText.isNotBlank()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (item.status == SendStatus.Failed) {
            FooterIcon(
                icon = Icons.Outlined.Refresh,
                description = stringResource(R.string.chatkit_retry),
                tint = ChatKit.colors.error,
            ) { onAction(MessageAction.Retry) }
        }
        if (hasText) {
            FooterIcon(Icons.Outlined.ContentCopy, stringResource(R.string.chatkit_copy)) { onAction(MessageAction.Copy) }
            FooterIcon(Icons.Outlined.Edit, stringResource(R.string.chatkit_edit)) { onAction(MessageAction.Edit) }
        }
        FooterIcon(Icons.Outlined.Delete, stringResource(R.string.chatkit_delete)) { onAction(MessageAction.Delete) }
    }
}

/** footer 里的一个图标按钮。所有 footer 图标都走这里，保证风格一致。 */
@Composable
fun FooterIcon(
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val colors = ChatKit.colors
    val dimens = ChatKit.dimens
    IconButton(onClick = onClick, modifier = Modifier.size(dimens.footerHitSize)) {
        Icon(
            icon,
            contentDescription = description,
            tint = tint ?: if (active) colors.accent else colors.icon,
            modifier = Modifier.size(dimens.footerIconSize),
        )
    }
}

@Composable
private fun FooterDivider() {
    Spacer(Modifier.width(4.dp))
    Box(
        Modifier
            .width(1.dp)
            .height(14.dp)
            .background(ChatKit.colors.divider),
    )
    Spacer(Modifier.width(4.dp))
}

/** 用户消息左侧的发送状态：发送中转圈，失败显示红色感叹号（点击重试）。 */
@Composable
internal fun SendStatusIndicator(status: SendStatus, onRetry: () -> Unit) {
    when (status) {
        SendStatus.Sending -> {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = ChatKit.colors.secondaryText,
            )
            Spacer(Modifier.width(8.dp))
        }
        SendStatus.Failed -> {
            IconButton(onClick = onRetry, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = stringResource(R.string.chatkit_send_failed),
                    tint = ChatKit.colors.error,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        SendStatus.Sent -> Unit
    }
}
