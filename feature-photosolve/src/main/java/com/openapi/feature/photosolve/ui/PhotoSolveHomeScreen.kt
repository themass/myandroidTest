package com.openapi.feature.photosolve.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openapi.feature.photosolve.R

@Composable
fun PhotoSolveHomeScreen(
    onTakePhotoClick: () -> Unit,
    onPickGalleryClick: () -> Unit,
    onGoogleScanClick: () -> Unit,
    onPickFromFileManagerClick: () -> Unit,
    onOpenCropAssistSettingsClick: () -> Unit,
    onOpenHistoryClick: () -> Unit,
    permissionPermanentlyDenied: Boolean,
    onOpenSettingsClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        // 作为抽屉入口进入时，需要一个返回/关闭按钮（原工程是独立 App，没有这一项）
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_back))
        }
        IconButton(
            onClick = onOpenCropAssistSettingsClick,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.photosolve_crop_settings_desc))
        }
        IconButton(
            onClick = onOpenHistoryClick,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 56.dp, top = 8.dp),
        ) {
            Icon(Icons.Filled.History, contentDescription = stringResource(R.string.home_history))
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onTakePhotoClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_take_photo))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onPickGalleryClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_pick_gallery))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onGoogleScanClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.photosolve_google_scan))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onPickFromFileManagerClick) {
                Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.photosolve_pick_from_files))
            }
            if (permissionPermanentlyDenied) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.home_permission_denied),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onOpenSettingsClick) {
                    Text(stringResource(R.string.home_open_settings))
                }
            }
        }
    }
}
