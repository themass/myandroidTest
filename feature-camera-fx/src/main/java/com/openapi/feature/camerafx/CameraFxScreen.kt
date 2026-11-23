package com.openapi.feature.camerafx

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat

/**
 * 全屏 lab：CameraX 预览 + Compose Canvas 叠加 + 头顶 Lottie。
 *
 * 右上角 FilterChip = **开关**（高亮=已开）。误点会把线框关掉，见 [resetDemoDefaults]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraFxScreen(onNavigateBack: () -> Unit = {}) {
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

    fun persist(next: CameraFxOverlayPolicy) {
        policy = next
        labPrefs.overlayPolicy = next
    }

    fun resetDemoDefaults() {
        val next = CameraFxOverlayPolicy()
        persist(next)
    }

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
                    TextButton(onClick = onNavigateBack) { Text("返回") }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            modifier = Modifier.statusBarsPadding(),
            title = { Text("摄像头定位 Demo") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                TextButton(onClick = { resetDemoDefaults() }) {
                    Text("全开", style = MaterialTheme.typography.labelLarge)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xCC000000),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White,
            ),
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            CameraFxGrantedContent(
                modifier = Modifier.fillMaxSize(),
                overlayPolicy = policy,
            )
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .zIndex(2f),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    "高亮=已开启（再点关闭）",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Row {
                    FilterChip(
                        selected = policy.drawWireframe,
                        onClick = { persist(policy.copy(drawWireframe = !policy.drawWireframe)) },
                        label = {
                            Text(if (policy.drawWireframe) "线框·开" else "线框·关")
                        },
                    )
                    FilterChip(
                        selected = policy.drawStickers,
                        onClick = { persist(policy.copy(drawStickers = !policy.drawStickers)) },
                        label = {
                            Text(if (policy.drawStickers) "贴纸·开" else "贴纸·关")
                        },
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    FilterChip(
                        selected = policy.drawStickers,
                        onClick = {
                            val id = if (policy.stickerAssetId == StickerAssetIds.PAT_HEAD) {
                                StickerAssetIds.WAVE
                            } else {
                                StickerAssetIds.PAT_HEAD
                            }
                            persist(
                                policy.copy(
                                    drawStickers = true,
                                    stickerAssetId = id,
                                ),
                            )
                        },
                        label = {
                            Text(
                                if (policy.stickerAssetId == StickerAssetIds.WAVE) "光弧" else "爱心",
                            )
                        },
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            if (!policy.drawWireframe && !policy.drawStickers) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .zIndex(2f)
                        .background(Color(0x99000000))
                        .padding(16.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("线框和贴纸都已关闭", color = Color.White, fontSize = 14.sp)
                        TextButton(onClick = { resetDemoDefaults() }) {
                            Text("点「全开」恢复演示")
                        }
                    }
                }
            }
        }
    }
}
