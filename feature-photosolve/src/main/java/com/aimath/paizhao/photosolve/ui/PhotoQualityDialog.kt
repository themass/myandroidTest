package com.aimath.paizhao.photosolve.ui

import androidx.compose.ui.res.stringResource
import com.openapi.feature.photosolve.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import com.openapi.imaging.quality.QualityIssue
import com.openapi.imaging.quality.suggestion
import com.openapi.uikit.theme.AppTypography

/**
 * 裁剪后清晰度或曝光不合格时的提示。
 * stringResource(R.string.photosolve_solve_anyway) 会继续，但识别结果可能不准；stringResource(R.string.photosolve_retake) 回到拍照页。
 */
@Composable
fun PhotoQualityDialog(
    issues: List<QualityIssue>,
    onRetake: () -> Unit,
    onSolveAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.photosolve_quality_title)) },
        text = {
            Column {
                issues.forEach { issue ->
                    Text(
                        "· " + issue.suggestion(),
                        fontSize = AppTypography.body,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRetake) { Text(stringResource(R.string.photosolve_retake)) }
        },
        dismissButton = {
            TextButton(onClick = onSolveAnyway) { Text(stringResource(R.string.photosolve_solve_anyway)) }
        },
    )
}
