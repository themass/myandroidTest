package com.openapi.feature.photosolve.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.common.ui.theme.AppColors
import com.openapi.feature.photosolve.R
import com.openapi.feature.photosolve.markdown.MathText
import com.openapi.feature.photosolve.presentation.PhotoSolveResult

/**
 * 解题结果页。
 *
 * 布局顺序：题目图 → 识别到的题目 → **思考过程（默认折叠）** → 解题步骤 → 最终答案 → 操作按钮。
 *
 * 思考过程默认折叠的理由：学生第一眼要看的是「答案对不对」和「步骤怎么写」，思考过程是辅助理解用的；
 * 全部展开会让结果页很长，答案被挤到屏幕外。折叠标题旁显示一行摘要，不展开也知道内容方向。
 *
 * 所有公式文本都走 [MathText]（Markwon + jlatexmath），`$...$` 会渲染成真正的公式，
 * 而不是显示成 `$\frac{1}{2}$` 这样的源码。
 */
@Composable
fun PhotoResultScreen(
    croppedBitmap: Bitmap?,
    result: PhotoSolveResult,
    onRetake: () -> Unit,
    onExit: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        // 顶栏：退出（回首页/关闭解题页面）与历史入口。
        // 之前结果页只有一个「再拍一题」，用户没法离开，只能按系统返回键。
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.result_exit))
            }
            Text(
                stringResource(R.string.home_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Filled.History, contentDescription = stringResource(R.string.home_history))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            if (croppedBitmap != null) {
                Image(
                    bitmap = croppedBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(20.dp))
            }

            SectionLabel(stringResource(R.string.result_question_label))
            MathText(
                markdown = result.recognizedQuestion,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
            )

            if (result.thinking.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                ThinkingSection(result.thinking)
            }

            if (result.steps.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionLabel(stringResource(R.string.result_steps_label))
                result.steps.forEachIndexed { index, step ->
                    Row(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        MathText(
                            markdown = step,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                        )
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
                        MathText(
                            markdown = result.finalAnswer,
                            color = AppColors.Success,
                            fontSize = 20.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(onClick = onRetake, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.result_retake))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.result_exit))
            }
        }
    }
}

/**
 * 可折叠的思考过程。
 *
 * 折叠时标题右侧显示一行摘要（正文第一个换行/句号之前的内容，最多两行），
 * 这样不展开也能判断"要不要看"。展开状态只保存在本页，不持久化。
 */
@Composable
fun ThinkingSection(
    thinking: String,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(thinking) { mutableStateOf(initiallyExpanded) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "thinkingArrow")
    val summary = remember(thinking) { summaryOf(thinking) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            // 整块可点：标题、摘要、箭头都能切换（点击热区越大越好用）
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.result_thinking_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(if (expanded) R.string.ui_collapse else R.string.ui_expand),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.result_thinking_collapse else R.string.result_thinking_expand,
                ),
                tint = LocalContentColor.current,
                modifier = Modifier.size(20.dp).rotate(arrow),
            )
        }
        if (!expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 8.dp)) {
                MathText(
                    markdown = thinking,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

/** 折叠时的摘要：第一句，长度受限。 */
private fun summaryOf(text: String): String {
    val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (firstLine.isEmpty()) return text.trim().take(60)
    val cut = firstLine.indexOfFirst { it == '。' || it == '；' || it == '.' }
    val sentence = if (cut > 8) firstLine.substring(0, cut + 1) else firstLine
    return if (sentence.length > 80) sentence.take(80) + "…" else sentence
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(6.dp))
}

/** 结果页没有数据时的占位（历史详情里图片丢失时也能用）。 */
@Composable
fun ResultPlaceholder(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 历史详情底部的一排操作。 */
@Composable
fun HistoryDetailActions(
    favorite: Boolean,
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onFavorite, modifier = Modifier.weight(1f)) {
            Text(stringResource(if (favorite) R.string.history_unfavorite else R.string.history_favorite))
        }
        OutlinedButton(onClick = onRename, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.ui_rename))
        }
        OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.ui_delete), color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = onRetake, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.history_retake))
        }
    }
}
