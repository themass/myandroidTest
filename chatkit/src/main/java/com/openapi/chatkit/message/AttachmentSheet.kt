package com.openapi.chatkit.message

import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
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
import com.openapi.chatkit.model.AttachmentPathInfo
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

/** 「查看路径」卡片：文件名、完整路径（带复制按钮）、大小；另存过的公共目录路径也显示（同样带复制）。 */
@Composable
internal fun AttachmentPathCard(
    info: AttachmentPathInfo,
    onDismiss: () -> Unit,
) {
    val colors = ChatKit.colors
    val clipboard = LocalClipboardManager.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.menuBackground,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(info.name, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = colors.bubbleText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                info.sizeBytes?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(formatBytes(it), fontSize = 13.sp, color = colors.secondaryText)
                }
                Spacer(Modifier.height(14.dp))
                PathRow(label = stringResource(R.string.chatkit_path_app), path = info.path) { clipboard.setText(AnnotatedString(info.path)) }
                info.savedPath?.let { saved ->
                    Spacer(Modifier.height(12.dp))
                    PathRow(label = stringResource(R.string.chatkit_path_saved), path = saved) { clipboard.setText(AnnotatedString(saved)) }
                }
            }
        }
    }
}

@Composable
private fun PathRow(label: String, path: String, onCopy: () -> Unit) {
    val colors = ChatKit.colors
    Column {
        Text(label, fontSize = 12.sp, color = colors.secondaryText)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(path, modifier = Modifier.weight(1f), fontSize = 13.sp, color = colors.bubbleText, maxLines = 3, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.chatkit_att_copy_path), tint = colors.accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}
