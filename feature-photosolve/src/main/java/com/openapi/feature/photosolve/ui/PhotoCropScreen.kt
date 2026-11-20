package com.openapi.feature.photosolve.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapi.imaging.cropassist.CropAssistResult
import com.openapi.imaging.CropNorm
import com.openapi.imaging.cropassist.CropAssistRegistry
import com.openapi.feature.photosolve.R

/**
 * 拍照解题的裁剪页（自绘，不再依赖 easycrop 的界面）。
 *
 * - 裁剪框：四角粗角标 + 每条边中点一根粗横线（设计稿：图3）。
 * - 手势：按下时就判断抓的是哪个手柄（角 / 边中点 / 框内整体移动），不需要先移动一下。
 * - 旋转、镜像：生成新图并重置裁剪框；自动识别的结果直接套用到框上；确认时按框裁出图片。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCropScreen(
    bitmap: Bitmap,
    cropAssistRegistry: CropAssistRegistry,
    activeCropAssistId: String,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var working by remember(bitmap) { mutableStateOf(bitmap) }
    var norm by remember(bitmap) { mutableStateOf(CropNorm.Default) }
    var showComparison by remember { mutableStateOf(false) }
    var suggestionApplied by remember(bitmap) { mutableStateOf(false) }

    // 进入页面时，若选了自动识别方案，直接把它的建议框套上
    LaunchedEffect(working) {
        if (suggestionApplied) return@LaunchedEffect
        suggestionApplied = true
        val provider = cropAssistRegistry.byId(activeCropAssistId)
        if (provider.id == cropAssistRegistry.manual.id) return@LaunchedEffect
        val suggestion = provider.suggest(working)
        if (suggestion is CropAssistResult.SuggestedBox) norm = suggestion.norm
    }

    Column(modifier.fillMaxSize().background(Color.Black)) {
        CropTopBar(onCancel = onCancel)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CropOverlay(
                bitmap = working,
                norm = norm,
                onNormChange = { norm = it },
            )
        }
        CropBottomActions(
            confirmEnabled = true,
            onRotate = {
                working = working.rotatedBy(90f)
                norm = CropNorm.Default
            },
            onMirror = {
                working = working.mirrored()
                norm = CropNorm.Default
            },
            onAutoDetect = { showComparison = true },
            onReset = { norm = CropNorm.Default },
            onConfirm = { onConfirm(working.cropTo(norm)) },
        )
    }

    if (showComparison) {
        CropAssistComparisonScreen(
            bitmap = working,
            registry = cropAssistRegistry,
            onApply = { picked ->
                norm = picked
                showComparison = false
            },
            onDismiss = { showComparison = false },
        )
    }
}
