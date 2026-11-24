package com.openapi.app.fxlab

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.openapi.feature.camerafx.CameraFxGrantedContent
import com.openapi.feature.camerafx.CameraFxOverlayPolicy
import com.openapi.feature.camerafx.CameraFxStickerPip
import com.openapi.feature.camerafx.CameraFxStickerRenderer

/**
 * Lab 相机场景的叠加策略（纯函数，JVM 可测）。
 *
 * 只**用** [CameraFxOverlayPolicy]，不修改它：全屏才有 HUD 与线框，小窗保持通话页
 * 的干净口径（只贴纸）。线框只在 Lottie 引擎下画——比引擎时别让线框抢走对比的视线。
 */
fun labCameraPolicy(engine: FxEngineId, scene: FxScene): CameraFxOverlayPolicy = CameraFxOverlayPolicy(
    drawWireframe = engine == FxEngineId.LOTTIE && scene == FxScene.CAMERA_FULL,
    drawStickers = true,
    showDebugHud = scene == FxScene.CAMERA_FULL,
)

fun labCameraPolicy(engine: FxEngineId): CameraFxOverlayPolicy = labCameraPolicy(engine, FxScene.CAMERA_FULL)

/**
 * 同意弹窗只在「用户还没同意过 **且** 系统权限还没给」时出现。
 * 权限已授（例如相机 Demo 页里给过）就不再重复询问——但隐私口径文案不变。
 */
fun cameraNeedsConsent(accepted: Boolean, granted: Boolean): Boolean = !accepted && !granted

/**
 * Lab 里的相机场景：全屏铺满 / 小窗 132×176。复用 [CameraFxGrantedContent] 与
 * [CameraFxStickerPip]——CameraX 绑定、帧分析释放、头顶锚点 EMA 全归相机侧，
 * 本页只注入 renderer（Task 6–9；null = 今天的 Lottie）。
 *
 * 隐私边界与生产相机 Demo 完全一致：帧只在本机内存，不上传、不落盘、不 log 人脸数据。
 */
@Composable
fun CameraScene(
    state: LabUiState,
    renderer: CameraFxStickerRenderer?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var accepted by remember { mutableStateOf(false) }
    var consentPending by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    if (cameraNeedsConsent(accepted = accepted, granted = granted) && consentPending) {
        AlertDialog(
            onDismissRequest = { consentPending = false },
            title = { Text("特效引擎对比 · 相机") },
            text = {
                Text(
                    "画面仅在本机实时处理，不上传服务器、不保存、不记录任何人脸数据。" +
                        "关闭本页即停止全部处理。",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        accepted = true
                        consentPending = false
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                ) { Text("同意并开始") }
            },
            dismissButton = {
                TextButton(onClick = { consentPending = false }) { Text("取消") }
            },
        )
        return
    }

    if (!granted) {
        Box(
            modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("没有摄像头权限，无法对比", color = Color.White, fontSize = 16.sp)
                Button(
                    onClick = { consentPending = true },
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text("重试") }
            }
        }
        return
    }

    val policy = labCameraPolicy(state.engine, state.scene)
    if (state.scene == FxScene.CAMERA_PIP) {
        // 小窗尺寸与通话页 PIP 同一口径：132×176，深色衬底让「小尺寸下的可读性」可比。
        Column(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("摄像头小窗 132×176", color = Color.DarkGray)
            CameraFxStickerPip(
                modifier = Modifier.size(132.dp, 176.dp).padding(top = 8.dp).background(Color.Black),
                stickerRenderer = renderer,
            )
        }
    } else {
        CameraFxGrantedContent(
            modifier = modifier.fillMaxSize(),
            overlayPolicy = policy,
            stickerRenderer = renderer,
        )
    }
}
