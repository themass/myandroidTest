package com.aimath.paizhao.photosolve.ui

import androidx.compose.ui.res.stringResource
import com.openapi.feature.photosolve.R
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.SwapHoriz
import com.mr0xf00.easycrop.CropperStyle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
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
import com.aimath.paizhao.photosolve.image.ImagePipeline.rotated
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
    // 旋转后用新图重新裁剪：easycrop 没有旋转能力，只能换图重开
    var working by remember(bitmap) { mutableStateOf(bitmap) }
    var suggestionApplied by remember(working) { mutableStateOf(false) }

    LaunchedEffect(working) {
        when (val result = imageCropper.crop(bmp = working.asImageBitmap())) {
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
        val suggestion = provider.suggest(working)
        if (suggestion is CropAssistResult.SuggestedBox) {
            applySuggestion(state, working, suggestion.norm)
        }
    }

    Box(modifier.fillMaxSize()) {
        if (cropState != null) {
            ImageCropperDialog(
                state = cropState,
                style = CropHandleStyle,
                // 图片尽量占满可用区域：只留很小的边距
                dialogPadding = PaddingValues(8.dp),
                topBar = { state ->
                    CropTopBar(onCancel = { state.done(accept = false) })
                },
                cropControls = { state ->
                    CropBottomActions(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        confirmEnabled = !state.accepted,
                        onRotate = { working = working.rotated(90f) },
                        onMirror = { working = working.mirrored() },
                        onAutoDetect = { showComparison = true },
                        onReset = { state.reset() },
                        onConfirm = { state.done(accept = true) },
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
                bitmap = working,
                registry = cropAssistRegistry,
                onApply = { norm ->
                    cropState?.let { applySuggestion(it, working, norm) }
                    showComparison = false
                },
                onDismiss = { showComparison = false },
            )
        }
    }
}

/** 裁剪框的角点：比库默认的大，触摸范围也更大，手指更容易按中。 */
private val CropHandleStyle: CropperStyle = CropperStyle(touchRad = 32.dp)

/** 水平镜像：左右翻转。和 [rotated] 一样返回新图，由外层用新图重开裁剪。 */
private fun Bitmap.mirrored(): Bitmap {
    val matrix = android.graphics.Matrix().apply { preScale(-1f, 1f) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
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
private fun CropTopBar(onCancel: () -> Unit) {
    Surface {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.photosolve_crop_title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_cancel))
                }
            },
        )
    }
}

/** 底部操作条：每个动作都有文字标签（旋转、镜像、自动识别、重置、确认）。 */
@Composable
private fun CropBottomActions(
    modifier: Modifier,
    confirmEnabled: Boolean,
    onRotate: () -> Unit,
    onMirror: () -> Unit,
    onAutoDetect: () -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth(), color = Color.Black.copy(alpha = 0.7f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CropAction(Icons.AutoMirrored.Filled.RotateRight, stringResource(R.string.photosolve_rotate), onRotate)
            CropAction(Icons.Filled.SwapHoriz, stringResource(R.string.photosolve_mirror), onMirror)
            CropAction(Icons.Filled.Compare, stringResource(R.string.photosolve_auto_detect), onAutoDetect)
            CropAction(Icons.Filled.RestartAlt, stringResource(R.string.photosolve_reset), onReset)
            CropAction(Icons.Filled.Check, stringResource(R.string.ui_confirm), onConfirm, enabled = confirmEnabled, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CropAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Color.White,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.4f))
        }
        Text(label, color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f), fontSize = 12.sp, maxLines = 1)
    }
}
