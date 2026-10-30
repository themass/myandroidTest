package com.aimath.paizhao.photosolve.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aimath.paizhao.photosolve.CropNorm
import com.aimath.paizhao.photosolve.cropassist.CropAssistRegistry
import com.aimath.paizhao.photosolve.cropassist.CropAssistResult
import com.mr0xf00.easycrop.CropError
import com.mr0xf00.easycrop.CropResult
import com.mr0xf00.easycrop.CropState
import com.mr0xf00.easycrop.crop
import com.mr0xf00.easycrop.rememberImageCropper
import com.mr0xf00.easycrop.ui.ImageCropperDialog

/**
 * Crop UI backed by the open-source `easycrop` library (replaces the hand-rolled drag-handle
 * math Phase 1 shipped with). When the active [com.aimath.paizhao.photosolve.cropassist.CropAssistProvider]
 * isn't the manual baseline, its suggested box is applied to the crop session once it starts —
 * the user can still freely adjust it inside easycrop's own UI. A toolbar action opens
 * [CropAssistComparisonScreen] to compare every available auto-detect backend on this photo.
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
    val imageCropper = rememberImageCropper()
    var showComparison by remember { mutableStateOf(false) }
    var suggestionApplied by remember(bitmap) { mutableStateOf(false) }

    LaunchedEffect(bitmap) {
        when (val result = imageCropper.crop(bmp = bitmap.asImageBitmap())) {
            is CropResult.Success -> onConfirm(result.bitmap.asAndroidBitmap())
            CropResult.Cancelled -> onCancel()
            is CropError -> onCancel()
        }
    }

    val cropState = imageCropper.cropState

    LaunchedEffect(cropState) {
        val state = cropState ?: return@LaunchedEffect
        if (suggestionApplied) return@LaunchedEffect
        suggestionApplied = true
        val provider = cropAssistRegistry.byId(activeCropAssistId)
        if (provider.id == cropAssistRegistry.manual.id) return@LaunchedEffect
        val suggestion = provider.suggest(bitmap)
        if (suggestion is CropAssistResult.SuggestedBox) {
            applySuggestion(state, bitmap, suggestion.norm)
        }
    }

    Box(modifier.fillMaxSize()) {
        if (cropState != null) {
            ImageCropperDialog(
                state = cropState,
                topBar = { state ->
                    CropTopBar(
                        state = state,
                        onCompareClick = { showComparison = true },
                    )
                },
            )
        } else {
            Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        if (showComparison) {
            CropAssistComparisonScreen(
                bitmap = bitmap,
                registry = cropAssistRegistry,
                onApply = { norm ->
                    cropState?.let { applySuggestion(it, bitmap, norm) }
                    showComparison = false
                },
                onDismiss = { showComparison = false },
            )
        }
    }
}

private fun applySuggestion(state: CropState, bitmap: Bitmap, norm: CropNorm) {
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    state.region = Rect(
        left = norm.left * w,
        top = norm.top * h,
        right = norm.right * w,
        bottom = norm.bottom * h,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CropTopBar(state: CropState, onCompareClick: () -> Unit) {
    Surface {
        TopAppBar(
            title = { Text("裁剪题目") },
            navigationIcon = {
                IconButton(onClick = { state.done(accept = false) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "取消")
                }
            },
            actions = {
                IconButton(onClick = onCompareClick) {
                    Icon(Icons.Filled.Compare, contentDescription = "对比检测方案")
                }
                IconButton(onClick = { state.reset() }) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = "重置")
                }
                IconButton(onClick = { state.done(accept = true) }, enabled = !state.accepted) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "确认",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )
    }
}
