package com.openapi.feature.photosolve.ui

import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.common.ui.theme.AppColors
import com.openapi.feature.photosolve.R
import com.openapi.feature.photosolve.history.SolveDetailViewModel
import com.openapi.feature.photosolve.history.SolveRecord
import com.openapi.feature.photosolve.markdown.MathText
import com.openapi.feature.photosolve.presentation.PhotoSolveResult

/**
 * 解题历史详情：与结果页同样的展示（题目、可折叠思考过程、步骤、答案），
 * 底部多一排管理操作（收藏 / 重命名 / 删除 / 再拍一题）。
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
            )
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
                onFavorite = { viewModel.setFavorite(!record.favorite) },
                onRename = { renaming = true },
                onDelete = { deleting = true },
                onRetake = onOpenCamera,
                onBack = onBack,
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
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onRetake: () -> Unit,
    onBack: () -> Unit,
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

        // 复用结果页的展示结构：把记录转成 PhotoSolveResult，避免两套渲染逻辑各改各的
        val result = PhotoSolveResult(
            recognizedQuestion = record.question,
            steps = record.steps,
            finalAnswer = record.answer,
            thinking = record.thinking,
            subject = record.subject,
            requestId = record.requestId,
        )

        Text(
            stringResource(R.string.result_question_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        MathText(result.recognizedQuestion, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)

        if (result.thinking.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            ThinkingSection(result.thinking)
        }

        if (result.steps.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.result_steps_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            result.steps.forEachIndexed { index, step ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        "${index + 1}.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    MathText(step, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        if (result.finalAnswer.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = AppColors.SuccessContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.result_answer_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = AppColors.Success,
                    )
                    Spacer(Modifier.height(6.dp))
                    MathText(result.finalAnswer, fontSize = 20.sp, color = AppColors.Success)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.history_saved_at, formatTime(record.updatedAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        HistoryDetailActions(
            favorite = record.favorite,
            onFavorite = onFavorite,
            onRename = onRename,
            onDelete = onDelete,
            onRetake = onRetake,
        )
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ui_back))
        }
        Spacer(Modifier.height(20.dp))
    }
}
