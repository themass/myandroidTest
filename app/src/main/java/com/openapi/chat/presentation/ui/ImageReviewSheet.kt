package com.openapi.chat.presentation.ui

import androidx.compose.ui.res.stringResource
import com.openapi.ks.chat.R
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.aimath.paizhao.photosolve.quality.QualityIssue
import com.aimath.paizhao.photosolve.quality.suggestion
import com.openapi.chat.presentation.ImageReview
import com.openapi.core.design.theme.AppColors
import com.openapi.core.design.theme.AppSpacing
import com.openapi.core.design.theme.AppTypography
import java.io.File

/**
 * 图片发送前的审阅面板：预览（裁剪图或原图）、质量提示、以及"发送 / 重新选择"。
 * 有裁剪图时默认发送裁剪图，用户可以切回原图。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageReviewSheet(
    review: ImageReview,
    onSend: (useCrop: Boolean) -> Unit,
    onRePick: () -> Unit,
    onDismiss: () -> Unit,
) {
    var useCrop by remember { mutableStateOf(review.cropped != null) }
    val shown: File = if (useCrop && review.cropped != null) review.cropped else review.original

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.screenHorizontal)
                .padding(bottom = AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            Text(
                stringResource(R.string.chat_review_title),
                fontSize = AppTypography.sectionTitle,
                fontWeight = AppTypography.titleWeight,
                color = AppColors.TextPrimary,
            )

            ReviewPreview(file = shown)

            if (review.cropped != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    FilterChip(
                        selected = useCrop,
                        onClick = { useCrop = true },
                        label = { Text(stringResource(R.string.chat_review_crop)) },
                    )
                    FilterChip(
                        selected = !useCrop,
                        onClick = { useCrop = false },
                        label = { Text(stringResource(R.string.chat_review_full)) },
                    )
                }
                Text(
                    stringResource(R.string.chat_review_auto_region, review.detector?.let { "（$it）" }.orEmpty()),
                    fontSize = AppTypography.caption,
                    color = AppColors.TextSecondary,
                )
            }

            QualityNotes(issues = review.issues)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRePick) { Text(stringResource(R.string.ui_reselect)) }
                Spacer(Modifier.width(AppSpacing.sm))
                Button(onClick = { onSend(useCrop && review.cropped != null) }) { Text(stringResource(R.string.ui_send)) }
            }
        }
    }
}

@Composable
private fun ReviewPreview(file: File) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { view -> Glide.with(view.context).load(file).into(view) },
    )
}

@Composable
private fun QualityNotes(issues: List<QualityIssue>) {
    if (issues.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AppColors.Success)
            Spacer(Modifier.width(AppSpacing.sm))
            Text(stringResource(R.string.chat_review_ok), fontSize = AppTypography.body, color = AppColors.TextPrimary)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        issues.forEach { issue ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = AppColors.Warning)
                Spacer(Modifier.width(AppSpacing.sm))
                Text(
                    issue.suggestion(),
                    fontSize = AppTypography.body,
                    color = AppColors.TextPrimary,
                )
            }
        }
    }
}
