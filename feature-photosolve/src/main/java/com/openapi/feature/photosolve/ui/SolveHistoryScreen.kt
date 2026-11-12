package com.openapi.feature.photosolve.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openapi.feature.photosolve.R
import com.openapi.feature.photosolve.history.SolveHistoryViewModel
import com.openapi.feature.photosolve.history.SolveRecord
import com.openapi.feature.photosolve.history.SolveRecordCodec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 解题历史列表。
 *
 * 交互：点一条进详情；长按/右侧「⋯」出菜单（收藏、重命名、删除）；顶部可搜索、只看收藏。
 * 缩略图直接在 Compose 里用 [BitmapFactory] 解码（240px 的小图，几十 KB，不需要加图片库依赖）。
 */
@Composable
fun SolveHistoryScreen(
    viewModel: SolveHistoryViewModel,
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    var renaming by remember { mutableStateOf<SolveRecord?>(null) }
    var deleting by remember { mutableStateOf<SolveRecord?>(null) }
    var clearing by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_back))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleMedium)
                if (state.loaded && state.records.isNotEmpty()) {
                    Text(
                        stringResource(R.string.history_count, state.records.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRetake) {
                Icon(Icons.Filled.CameraAlt, contentDescription = stringResource(R.string.history_retake))
            }
            IconButton(onClick = { clearing = true }, enabled = state.records.isNotEmpty()) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_clear))
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text(stringResource(R.string.history_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.favoritesOnly,
                onClick = viewModel::toggleFavoritesOnly,
                label = { Text(stringResource(R.string.history_favorite)) },
                leadingIcon = {
                    Icon(
                        if (state.favoritesOnly) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }

        when {
            state.loading && !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.isEmpty -> EmptyHistory(onRetake)
            state.visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.visible, key = { it.id }) { record ->
                    HistoryRow(
                        record = record,
                        onClick = { onOpenDetail(record.id) },
                        onFavorite = { viewModel.setFavorite(record.id, !record.favorite) },
                        onRename = { renaming = record },
                        onDelete = { deleting = record },
                    )
                }
            }
        }
    }

    renaming?.let { record ->
        RenameDialog(
            initial = record.title,
            onDismiss = { renaming = null },
            onConfirm = { title ->
                viewModel.rename(record.id, title)
                renaming = null
            },
        )
    }

    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.history_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(record.id)
                    deleting = null
                }) { Text(stringResource(R.string.history_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.ui_cancel)) }
            },
        )
    }

    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            text = { Text(stringResource(R.string.history_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    clearing = false
                }) { Text(stringResource(R.string.history_clear), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { clearing = false }) { Text(stringResource(R.string.ui_cancel)) }
            },
        )
    }
}

@Composable
private fun EmptyHistory(onRetake: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.history_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onRetake) { Text(stringResource(R.string.history_retake)) }
    }
}

@Composable
private fun HistoryRow(
    record: SolveRecord,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val thumb = remember(record.thumbPath) {
        record.thumbPath?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 长按弹菜单，短按进详情
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    record.title.ifBlank { SolveRecordCodec.UNTITLED },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (record.answer.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        com.openapi.feature.photosolve.markdown.MathMarkdown.plain(record.answer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    formatTime(record.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (record.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(
                        if (record.favorite) R.string.history_unfavorite else R.string.history_favorite,
                    ),
                    tint = if (record.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.ui_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_favorite)) },
                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                        onClick = { menu = false; onFavorite() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ui_rename)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ui_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(60) },
                placeholder = { Text(stringResource(R.string.history_rename_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.ui_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        },
    )
}

private val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

internal fun formatTime(millis: Long): String =
    if (millis <= 0L) "" else TIME_FORMAT.format(Date(millis))
