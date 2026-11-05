package com.aimath.paizhao.photosolve.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import com.aimath.paizhao.photosolve.quality.QualityIssue
import com.aimath.paizhao.photosolve.quality.suggestion
import com.openapi.core.design.theme.AppTypography

/**
 * 裁剪后清晰度或曝光不合格时的提示。
 * "仍然识别" 会继续，但识别结果可能不准；"重新拍照" 回到拍照页。
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
        title = { Text("图片可能影响识别") },
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
            TextButton(onClick = onRetake) { Text("重新拍照") }
        },
        dismissButton = {
            TextButton(onClick = onSolveAnyway) { Text("仍然识别") }
        },
    )
}
