package com.openapi.chatkit.message

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import com.openapi.chatkit.R
import com.openapi.chatkit.model.AttachmentActionKind
import com.openapi.chatkit.theme.ChatKit

/**
 * 附件长按菜单（图片、文件共用）：和消息底部弹层同一种样式（暖白底、顶部圆角、拖动条），
 * 每个动作一行。动作由调用方给出（[actions]），组件只负责显示和回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentActionSheet(
    actions: List<AttachmentActionKind>,
    onSelect: (AttachmentActionKind) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ChatKit.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.menuBackground,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            actions.forEach { kind ->
                val (labelRes, icon) = attachmentLook(kind)
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (pressed) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(kind) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(labelRes), fontSize = 16.sp, color = colors.bubbleText)
                }
            }
        }
    }
}

/** 动作的文字与图标（组件统一的外观）。 */
private fun attachmentLook(kind: AttachmentActionKind): Pair<Int, ImageVector> = when (kind) {
    AttachmentActionKind.Download -> R.string.chatkit_att_download to Icons.Outlined.Download
    AttachmentActionKind.CopyImage -> R.string.chatkit_att_copy_image to Icons.Outlined.ContentCopy
    AttachmentActionKind.Share -> R.string.chatkit_att_share to Icons.Outlined.Share
    AttachmentActionKind.ShowPath -> R.string.chatkit_att_show_path to Icons.Outlined.Info
    AttachmentActionKind.CopyPath -> R.string.chatkit_att_copy_path to Icons.Outlined.ContentCopy
}
