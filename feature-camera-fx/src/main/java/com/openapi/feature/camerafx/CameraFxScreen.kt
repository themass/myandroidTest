package com.openapi.feature.camerafx

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * 全屏 lab：CameraX 预览 + Compose Canvas 叠加 + 头顶 Lottie。
 */
@Composable
fun CameraFxScreen() {
    val context = LocalContext.current
    val labPrefs = remember { CameraFxLabPreferences(context) }
    var policy by remember { mutableStateOf(labPrefs.overlayPolicy) }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var consentPending by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    if (!granted) {
        if (consentPending) {
            AlertDialog(
                onDismissRequest = { consentPending = false },
                title = { Text("摄像头定位演示") },
                text = {
                    Text(
                        "画面仅在本机实时处理，不上传服务器、不保存、不记录任何人脸数据。" +
                            "关闭本页即停止全部处理。",
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            consentPending = false
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                    ) { Text("同意并开始") }
                },
                dismissButton = {
                    TextButton(onClick = { consentPending = false }) { Text("取消") }
                },
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("没有摄像头权限，无法演示", color = Color.White, fontSize = 16.sp)
                    Button(
                        onClick = { consentPending = true },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("重试") }
                }
            }
        }
        return
    }

    fun persist(next: CameraFxOverlayPolicy) {
        policy = next
        labPrefs.overlayPolicy = next
    }

    Box(Modifier.fillMaxSize()) {
        CameraFxGrantedContent(
            modifier = Modifier.fillMaxSize(),
            overlayPolicy = policy,
        )
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            FilterChip(
                selected = policy.drawWireframe,
                onClick = { persist(policy.copy(drawWireframe = !policy.drawWireframe)) },
                label = { Text("线框") },
            )
            FilterChip(
                selected = policy.drawStickers,
                onClick = { persist(policy.copy(drawStickers = !policy.drawStickers)) },
                label = { Text("贴纸") },
                modifier = Modifier.padding(start = 6.dp),
            )
            FilterChip(
                selected = policy.stickerAssetId == StickerAssetIds.WAVE,
                onClick = {
                    val id = if (policy.stickerAssetId == StickerAssetIds.PAT_HEAD) {
                        StickerAssetIds.WAVE
                    } else {
                        StickerAssetIds.PAT_HEAD
                    }
                    persist(policy.copy(stickerAssetId = id))
                },
                label = { Text(if (policy.stickerAssetId == StickerAssetIds.WAVE) "光弧" else "爱心") },
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
