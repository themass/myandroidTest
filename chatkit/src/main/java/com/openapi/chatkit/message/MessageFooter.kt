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
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
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

/** 助手 footer 的芯片：36dp 方块、圆角 11dp；底色和图标色取自主题（chipBackground / chipIcon）。 */
private val AssistantChipSize = 36.dp
private val AssistantChipRadius = 11.dp

/**
 * 芯片间距从 8dp 收到 6dp：平铺后一段回复最多同时有 5 个芯片（复制、朗读、赞、踩、删除），
 * 5 × 36 + 4 × 6 = 204dp，加上气泡左右内边距约 228dp，320dp 宽的小屏上也排得下，
 * 所以不再需要横向滚动或裁切。
 */
private val AssistantChipGap = 6.dp

/**
 * 助手 footer 的默认芯片：有文字时「复制、朗读」，然后「赞、踩、删除」。
 *
 * 调用方可以只取其中一部分（见 [AssistantFooter] 的 `actions`）——比如陪伴页没有
 * 评价接口，就不该摆一个点了也没用的「赞」。
 */
fun defaultAssistantFooterActions(item: ChatItem): List<MessageAction> = buildList {
    if (item.plainText.isNotBlank()) {
        add(MessageAction.Copy)
        add(MessageAction.Speak)
    }
    add(MessageAction.Like)
    add(MessageAction.Dislike)
    add(MessageAction.Delete)
}

/**
 * 助手每条已完成回复的 footer：放在气泡卡片内部，正文下面一条分割线，分割线下一排浅蓝芯片。
 * 默认显示 [defaultAssistantFooterActions]；调用方可以用 `actions` 换成自己的集合与顺序。
 * 分享与重新生成在长按菜单里。
 */
@Composable
fun AssistantFooter(
    item: ChatItem,
    onAction: (MessageAction) -> Unit,
    modifier: Modifier = Modifier,
    /** 显示哪些芯片、按什么顺序。空集合时整个 footer 不画。 */
    actions: List<MessageAction> = defaultAssistantFooterActions(item),
) {
    // 空集合时一个像素都不画：否则会留下一条只有分割线的空白条。
    if (actions.isEmpty()) return
    Column(modifier.padding(top = 11.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(ChatKit.colors.divider))
        Spacer(Modifier.height(11.dp))
        // 平铺常驻：这些操作是回复的「主要出口」，收进收纳盒后要多点一次才看得见，
        // 很多用户压根不会发现；一行排开、一眼可见更直接。顺序按使用频率从左到右。
        // 不横向滚动也不裁切：间距已按最坏情况（5 个芯片）算过，见 [AssistantChipGap]。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AssistantChipGap),
        ) {
            actions.forEach { action ->
                when (action) {
                    MessageAction.Copy ->
                        AssistantChip(Icons.Outlined.ContentCopy, stringResource(R.string.chatkit_copy)) { onAction(action) }
                    MessageAction.Speak -> AssistantChip(
                        icon = if (item.speaking) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Outlined.VolumeUp,
                        description = stringResource(if (item.speaking) R.string.chatkit_speaking else R.string.chatkit_speak),
                    ) { onAction(action) }
                    MessageAction.Like -> AssistantChip(
                        icon = if (item.reaction == Reaction.Like) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        description = stringResource(R.string.chatkit_like),
                    ) { onAction(action) }
                    MessageAction.Dislike -> AssistantChip(
                        icon = if (item.reaction == Reaction.Dislike) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        description = stringResource(R.string.chatkit_dislike),
                    ) { onAction(action) }
                    MessageAction.Delete ->
                        AssistantChip(Icons.Outlined.Delete, stringResource(R.string.chatkit_delete)) { onAction(action) }
                    // 其余动作（重试、分享……）不在 footer 里，由长按菜单提供
                    else -> Unit
                }
            }
        }
    }
}

/** 助手 footer 的一个芯片：浅色圆角方块 + 主色图标（实心图标表示已选中，如已点赞）。 */
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
            .background(ChatKit.colors.chipBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = ChatKit.colors.chipIcon,
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
            // 失败：气泡左侧只留一个重试图标（与助手的重新生成图标一样），颜色用错误红；点它重新发送同一条消息
            Box(
                modifier = Modifier
                    .size(ChatKit.dimens.footerHitSize)
                    .clickable(onClickLabel = stringResource(R.string.chatkit_send_failed), onClick = onRetry),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.chatkit_send_failed),
                    tint = ChatKit.colors.error,
                    modifier = Modifier.size(ChatKit.dimens.footerIconSize),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        SendStatus.Sent -> Unit
    }
}
