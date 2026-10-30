package com.aimath.paizhao.photosolve.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Google's whole-flow alternative to our own Capture/Crop screens. ML Kit Document Scanner has
 * no API to retrieve raw corner coordinates — it only exposes [GmsDocumentScanning.getStartScanIntent],
 * which launches Google's own full-screen scan->crop->cleanup UI and hands back a final image
 * URI. That's why this is a separate entry point rather than a [com.aimath.paizhao.photosolve.cropassist.CropAssistProvider]:
 * there is nothing to plug into our own crop screen.
 *
 * Needs Google Play Services on the device; on an emulator without the Play Store image this
 * will fail with an unavailable-API error, surfaced via [onUnavailable].
 */
@Composable
fun rememberMlKitScanLauncher(
    onScanned: (imageUriString: String) -> Unit,
    onCancelled: () -> Unit,
    onUnavailable: (reason: String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val activity = context as? Activity

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val imageUri = scanResult?.pages?.firstOrNull()?.imageUri
            if (imageUri != null) onScanned(imageUri.toString()) else onUnavailable("未获取到扫描结果")
        } else {
            onCancelled()
        }
    }

    val scanner = remember {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
    }

    return {
        if (activity == null) {
            onUnavailable("无法获取 Activity")
        } else {
            val startScanTask: Task<android.content.IntentSender> = scanner.getStartScanIntent(activity)
            startScanTask
                .addOnSuccessListener { intentSender ->
                    launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener { e ->
                    onUnavailable(e.message ?: "Google 智能扫描不可用")
                }
        }
    }
}
