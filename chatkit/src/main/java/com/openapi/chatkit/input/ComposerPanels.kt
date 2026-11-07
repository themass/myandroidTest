package com.openapi.chatkit.input

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.openapi.chatkit.R
import com.openapi.chatkit.media.imageModel
import com.openapi.chatkit.message.parts.FileBadge
import com.openapi.chatkit.model.ComposerAction
import com.openapi.chatkit.model.DraftItem
import com.openapi.chatkit.model.DraftType
import com.openapi.chatkit.theme.ChatKit
import java.util.Locale

/** 附件草稿托盘：图片缩略图、文件名片，右上角 × 移除。 */
@Composable
fun DraftTray(drafts: List<DraftItem>, onRemove: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = ChatKit.colors
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(drafts, key = { it.id }) { draft ->
            Box(Modifier.padding(top = 6.dp, end = 6.dp)) {
                when (draft.type) {
                    DraftType.Image -> AsyncImage(
                        model = imageModel(draft.source),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceVariant),
                    )
                    DraftType.File -> Row(
                        modifier = Modifier
                            .height(64.dp)
                            .widthIn(max = 200.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceVariant)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FileBadge(extension = draft.name.substringAfterLast('.', "").uppercase(Locale.US).take(4), size = 36.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            draft.name,
                            fontSize = ChatKit.typography.caption,
                            color = colors.bubbleText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { onRemove(draft.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.chatkit_remove_attachment),
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

/** 「+」面板：4 列宫格，入口由调用方传入（微信式白色圆角方块 + 下方文字）。 */
@Composable
fun ActionPanel(actions: List<ComposerAction>, modifier: Modifier = Modifier) {
    val colors = ChatKit.colors
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .background(colors.surfaceVariant),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(actions, key = { it.id }) { action ->
            Column(
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = action.onClick),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(action.icon, contentDescription = action.label, tint = colors.bubbleText, modifier = Modifier.size(26.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(action.label, fontSize = ChatKit.typography.caption, color = colors.secondaryText, maxLines = 1)
            }
        }
    }
}

private val EMOJIS = listOf(
    "😀", "😄", "😂", "🤣", "😊", "😍", "🤔", "😅",
    "😭", "😡", "👍", "👎", "🙏", "👏", "🎉", "❤️",
    "😘", "😎", "🥳", "😴", "🤯", "🙄", "😮", "😰",
    "🔥", "✨", "💯", "👋", "🤝", "💪", "😏", "🤗",
)

/** 常用表情面板：点击把表情追加到输入框。 */
@Composable
fun EmojiPanel(onEmoji: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .background(ChatKit.colors.surfaceVariant),
        contentPadding = PaddingValues(8.dp),
    ) {
        items(EMOJIS) { emoji ->
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onEmoji(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 26.sp)
            }
        }
    }
}
