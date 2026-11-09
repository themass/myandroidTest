package com.aimath.paizhao.photosolve.ui

import android.graphics.Bitmap
import android.net.Uri
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.openapi.feature.photosolve.R
import com.aimath.paizhao.photosolve.ImageUtils.decodeJpegApplyingExifOrientation
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PhotoCaptureScreen(
    onPhotoCaptured: (Bitmap) -> Unit,
    onPickFromGallery: (Uri) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onPickFromGallery(uri) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener(
                    {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        runCatching {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture,
                            )
                            cameraControl = camera.cameraControl
                        }
                        imageCapture = capture
                    },
                    ContextCompat.getMainExecutor(ctx),
                )

                previewView.setOnTouchListener { view, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        val point = (view as PreviewView).meteringPointFactory.createPoint(event.x, event.y)
                        cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                        view.performClick()
                    }
                    true
                }
                previewView
            },
        )

        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.capture_back_cd),
                        tint = Color.White,
                    )
                }
                IconButton(onClick = {
                    torchOn = !torchOn
                    cameraControl?.enableTorch(torchOn)
                }) {
                    Icon(
                        if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = stringResource(R.string.capture_flash_cd),
                        tint = Color.White,
                    )
                }
            }

            Text(
                text = stringResource(R.string.capture_hint),
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp),
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(
                        Icons.Filled.PhotoLibrary,
                        contentDescription = stringResource(R.string.capture_gallery_cd),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }

                IconButton(
                    onClick = {
                        if (isCapturing) return@IconButton
                        val capture = imageCapture ?: return@IconButton
                        isCapturing = true
                        val photoFile = File(context.cacheDir, "paizhao_capture_${System.currentTimeMillis()}.jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                        capture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    scope.launch {
                                        val bitmap = withContext(Dispatchers.IO) {
                                            decodeJpegApplyingExifOrientation(photoFile.absolutePath)
                                        }
                                        isCapturing = false
                                        if (bitmap != null) onPhotoCaptured(bitmap)
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                }
                            },
                        )
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .border(4.dp, Color.White, CircleShape),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(if (isCapturing) Color.Gray else Color.White, CircleShape),
                    )
                }

                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}
