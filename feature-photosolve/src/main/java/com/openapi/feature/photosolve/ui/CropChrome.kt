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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CropTopBar(onCancel: () -> Unit) {
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
internal fun CropBottomActions(
    confirmEnabled: Boolean,
    onRotate: () -> Unit,
    onMirror: () -> Unit,
    onAutoDetect: () -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), color = Color.Black.copy(alpha = 0.7f)) {
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
internal fun CropAction(
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
