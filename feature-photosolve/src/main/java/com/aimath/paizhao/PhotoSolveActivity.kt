package com.aimath.paizhao

import com.openapi.feature.photosolve.R
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anggrayudi.storage.SimpleStorageHelper
import com.aimath.paizhao.photosolve.ImageUtils.decodeUriApplyingExifOrientation
import com.aimath.paizhao.photosolve.PhotoSolveStep
import com.aimath.paizhao.photosolve.PhotoSolveViewModel
import com.aimath.paizhao.photosolve.ui.CropAssistSettingsSheet
import com.aimath.paizhao.photosolve.ui.PhotoCaptureScreen
import com.aimath.paizhao.photosolve.ui.PhotoCropScreen
import com.aimath.paizhao.photosolve.ui.PhotoQualityDialog
import com.aimath.paizhao.photosolve.ui.PhotoRecognizingScreen
import com.aimath.paizhao.photosolve.ui.PhotoResultScreen
import com.aimath.paizhao.photosolve.ui.PhotoSolveHomeScreen
import com.aimath.paizhao.photosolve.ui.rememberMlKitScanLauncher
import com.openapi.common.design.theme.AppMaterialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 拍照解题的宿主 Activity（移植自 ai-math/paizhao 的 MainActivity）。
 *
 * 原工程是单 Activity 的独立 App；在这里它只作为抽屉菜单里的一个入口，
 * 内部流程（拍照 → 裁剪 → 识别 → 结果）与原型完全一致。
 */
class PhotoSolveActivity : ComponentActivity() {

    /** 从文件管理器选择（SAF）：anggrayudi 的 SimpleStorageHelper 需要 Activity 生命周期钩子，
     *  所以放在这里而不是 composable 内，[pickedFileUri] 把回调桥接回 Compose。 */
    private val storageHelper = SimpleStorageHelper(this)
    private val pickedFileUri: MutableState<Uri?> = mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        storageHelper.onFileSelected = { _, files -> pickedFileUri.value = files.firstOrNull()?.uri }

        setContent {
            AppMaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PhotoSolveApp(
                        pickedFileUri = pickedFileUri,
                        onPickFromFileManagerClick = { storageHelper.openFilePicker() },
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        storageHelper.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        storageHelper.onRestoreInstanceState(savedInstanceState)
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, PhotoSolveActivity::class.java))
        }
    }
}

@Composable
private fun PhotoSolveApp(
    pickedFileUri: MutableState<Uri?>,
    onPickFromFileManagerClick: () -> Unit,
    onClose: () -> Unit,
    viewModel: PhotoSolveViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeCropAssistId by viewModel.activeCropAssistId.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var permissionPermanentlyDenied by remember { mutableStateOf(false) }
    var showCropAssistSettings by remember { mutableStateOf(false) }
    var mlKitErrorMessage by remember { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            permissionPermanentlyDenied = false
            viewModel.onRequestCapture()
        } else {
            permissionPermanentlyDenied = true
        }
    }

    fun decodeAndUse(uri: Uri) {
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeUriApplyingExifOrientation(context, uri) }
            if (bitmap != null) viewModel.onPhotoCaptured(bitmap)
        }
    }

    val homeGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(::decodeAndUse) }

    fun requestCameraPermissionAndCapture() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onRequestCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val startMlKitScan = rememberMlKitScanLauncher(
        onScanned = { uriString ->
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeUriApplyingExifOrientation(context, Uri.parse(uriString))
                }
                // Google 的扫描器已经完成了拍摄+裁剪，跳过我们自己的 Capture/Crop 直接识别
                if (bitmap != null) viewModel.onCropConfirmed(bitmap)
            }
        },
        onCancelled = { /* 留在首页 */ },
        onUnavailable = { reason -> mlKitErrorMessage = reason },
    )

    LaunchedEffect(mlKitErrorMessage) {
        mlKitErrorMessage?.let { message ->
            Toast.makeText(context, context.getString(R.string.photosolve_google_unavailable, message), Toast.LENGTH_LONG).show()
            mlKitErrorMessage = null
        }
    }

    LaunchedEffect(pickedFileUri.value) {
        pickedFileUri.value?.let { uri ->
            decodeAndUse(uri)
            pickedFileUri.value = null
        }
    }

    when (uiState.step) {
        PhotoSolveStep.Home -> PhotoSolveHomeScreen(
            onTakePhotoClick = { requestCameraPermissionAndCapture() },
            onPickGalleryClick = {
                homeGalleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onGoogleScanClick = startMlKitScan,
            onPickFromFileManagerClick = onPickFromFileManagerClick,
            onOpenCropAssistSettingsClick = { showCropAssistSettings = true },
            permissionPermanentlyDenied = permissionPermanentlyDenied,
            onOpenSettingsClick = { openAppSettings(context) },
            onClose = onClose,
        )

        PhotoSolveStep.Capture -> PhotoCaptureScreen(
            onPhotoCaptured = viewModel::onPhotoCaptured,
            onPickFromGallery = ::decodeAndUse,
            onBack = viewModel::onCaptureCancelled,
        )

        PhotoSolveStep.Crop -> {
            val bitmap = uiState.capturedBitmap
            if (bitmap != null) {
                PhotoCropScreen(
                    bitmap = bitmap,
                    cropAssistRegistry = viewModel.cropAssistRegistry,
                    activeCropAssistId = activeCropAssistId,
                    onConfirm = viewModel::onCropConfirmed,
                    onCancel = viewModel::onCropCancelled,
                )
            }
        }

        PhotoSolveStep.Recognizing -> PhotoRecognizingScreen(
            croppedBitmap = uiState.croppedBitmap,
            statusIndex = uiState.recognizingStatusIndex,
            onCancel = viewModel::onRetake,
        )

        PhotoSolveStep.Result -> {
            val result = uiState.result
            if (result != null) {
                PhotoResultScreen(
                    croppedBitmap = uiState.croppedBitmap,
                    result = result,
                    onRetake = viewModel::onRetake,
                )
            }
        }
    }

    // 识别失败（40002/40003/42901/50001）：统一用一个提示弹窗，点确定关闭，回到裁剪页
    uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissError,
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::onDismissError) { Text("知道了") }
            },
        )
    }

    uiState.qualityWarning?.let { warning ->
        PhotoQualityDialog(
            issues = warning.issues,
            onRetake = viewModel::onRetakeAfterQualityWarning,
            onSolveAnyway = viewModel::onSolveAnyway,
            onDismiss = viewModel::onDismissQualityWarning,
        )
    }

    if (showCropAssistSettings) {
        CropAssistSettingsSheet(
            registry = viewModel.cropAssistRegistry,
            activeId = activeCropAssistId,
            onSelect = { id -> viewModel.setActiveCropAssistProvider(id) },
            onDismiss = { showCropAssistSettings = false },
        )
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}
