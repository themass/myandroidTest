package com.openapi.feature.photosolve.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openapi.feature.photosolve.R
import com.openapi.feature.photosolve.history.SolveDetailViewModel
import com.openapi.feature.photosolve.history.SolveRecord
import com.openapi.feature.photosolve.presentation.PhotoSolveResult

/**
 * 解题历史详情：与结果页同样的展示（题目、可折叠思考过程、步骤、答案），
 * 顶栏「⋯」菜单提供收藏 / 重命名 / 删除，底部只留「再拍一题」主操作。
 *
 * 数据从存储读，不依赖刚解完的内存状态，所以关掉 App 再进来看也一样。
 */
@Composable
fun SolveHistoryDetailScreen(
    recordId: Long,
    viewModel: SolveDetailViewModel,
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(recordId) { viewModel.load(recordId) }

    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_back))
            }
            Text(
                state.record?.title ?: stringResource(R.string.history_detail_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.record != null) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.ui_more))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (state.record?.favorite == true) R.string.history_unfavorite else R.string.history_favorite)) },
                            leadingIcon = {
                                Icon(
                                    if (state.record?.favorite == true) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                state.record?.let { viewModel.setFavorite(!it.favorite) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_rename)) },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; renaming = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; deleting = true },
                        )
                    }
                }
            }
        }

        val record = state.record
        when {
            state.loading && record == null -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator(Modifier.padding(32.dp)) }
            state.notFound || record == null -> ResultPlaceholder(stringResource(R.string.history_empty))
            else -> DetailBody(
                record = record,
                image = state.image,
                onRetake = onOpenCamera,
            )
        }
    }

    if (renaming) {
        state.record?.let { record ->
            RenameDialog(
                initial = record.title,
                onDismiss = { renaming = false },
                onConfirm = { title ->
                    viewModel.rename(title)
                    renaming = false
                },
            )
        }
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            text = { Text(stringResource(R.string.history_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    viewModel.delete(onDeleted)
                }) { Text(stringResource(R.string.history_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.ui_cancel)) }
            },
        )
    }
}

@Composable
private fun DetailBody(
    record: SolveRecord,
    image: android.graphics.Bitmap?,
    onRetake: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(16.dp))
        }

        // 复用结果页的展示结构：把记录转成 PhotoSolveResult，走同一个 SolveResultSections，
        // 字号只在那一处定义。之前这里自己写了一份（题目/答案 20sp、步骤 16sp），
        // 结果页排版改小后详情页没跟上，从历史进来的用户看到的还是旧的大字号。
        val result = PhotoSolveResult(
            recognizedQuestion = record.question,
            steps = record.steps,
            finalAnswer = record.answer,
            thinking = record.thinking,
            subject = record.subject,
            requestId = record.requestId,
        )

        SolveResultSections(result)

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.history_saved_at, formatTime(record.updatedAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        HistoryDetailActions(onRetake = onRetake)
        Spacer(Modifier.height(20.dp))
    }
}
