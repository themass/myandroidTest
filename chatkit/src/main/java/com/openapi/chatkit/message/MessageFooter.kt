package com.openapi.chatkit.message

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openapi.chatkit.R
import com.openapi.chatkit.model.ChatItem
import com.openapi.chatkit.model.MessageAction
import com.openapi.chatkit.model.Reaction
import com.openapi.chatkit.model.SendStatus
import com.openapi.chatkit.theme.ChatKit

/** 助手 footer 的芯片：实测（豆包）36dp 方块、圆角 11dp、底色 #ECF1F7、图标蓝色 #0066FF、间距 8dp。 */
private val AssistantChipSize = 36.dp
private val AssistantChipRadius = 11.dp
private val AssistantChipBackground = Color(0xFFECF1F7)
private val AssistantChipIcon = Color(0xFF0066FF)
private val AssistantChipGap = 8.dp
private val AssistantDividerColor = Color(0xFFDEDEDE)

/**
 * 助手每条已完成回复的 footer：放在气泡卡片内部，正文下面一条分割线，分割线下一排浅蓝芯片。
 * 复制、朗读、分享只在有文字时显示；赞、踩总是显示；「重新生成」只在最新一条回复上，靠右。
 */
@Composable
fun AssistantFooter(
    item: ChatItem,
    isLatest: Boolean,
    onAction: (MessageAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasText = item.plainText.isNotBlank()
    Column(modifier.padding(top = 11.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(AssistantDividerColor))
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AssistantChipGap)) {
            if (hasText) {
                AssistantChip(Icons.Outlined.ContentCopy, stringResource(R.string.chatkit_copy)) { onAction(MessageAction.Copy) }
                AssistantChip(
                    icon = if (item.speaking) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Outlined.VolumeUp,
                    description = stringResource(if (item.speaking) R.string.chatkit_speaking else R.string.chatkit_speak),
                ) { onAction(MessageAction.Speak) }
            }
            AssistantChip(
                icon = if (item.reaction == Reaction.Like) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                description = stringResource(R.string.chatkit_like),
            ) { onAction(MessageAction.Like) }
            AssistantChip(
                icon = if (item.reaction == Reaction.Dislike) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                description = stringResource(R.string.chatkit_dislike),
            ) { onAction(MessageAction.Dislike) }
            if (hasText) {
                AssistantChip(Icons.Outlined.Share, stringResource(R.string.chatkit_share)) { onAction(MessageAction.Share) }
            }
            if (isLatest) {
                Spacer(Modifier.weight(1f))
                AssistantChip(Icons.Outlined.Refresh, stringResource(R.string.chatkit_regenerate)) { onAction(MessageAction.Regenerate) }
            }
        }
    }
}

/** 助手 footer 的一个芯片：浅蓝圆角方块 + 蓝色图标（实心图标表示已选中，如已点赞）。 */
@Composable
private fun AssistantChip(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(AssistantChipSize)
            .clip(RoundedCornerShape(AssistantChipRadius))
            .background(AssistantChipBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = AssistantChipIcon,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 图标按钮（发送失败的重试等）。 */
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
