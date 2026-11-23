package com.openapi.feature.photosolve.ui

import androidx.compose.ui.res.stringResource
import com.openapi.feature.photosolve.R
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.openapi.imaging.CropNorm
import com.openapi.imaging.cropassist.CropAssistRegistry
import com.openapi.imaging.cropassist.CropAssistResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Runs every box-returning [com.openapi.imaging.cropassist.CropAssistProvider] against
 * the same photo and shows each one's suggested box side by side, so the user can judge which
 * detector works best. Google's ML Kit scan is intentionally absent here — it can't run headlessly
 * (see [rememberMlKitScanLauncher]'s doc), so it isn't part of this comparison.
 */
@Composable
fun CropAssistComparisonScreen(
    bitmap: Bitmap,
    registry: CropAssistRegistry,
    onApply: (CropNorm) -> Unit,
    onDismiss: () -> Unit,
) {
    var results by remember { mutableStateOf<Map<String, CropAssistResult>?>(null) }

    androidx.compose.runtime.LaunchedEffect(bitmap) {
        results = coroutineScope {
            registry.all.map { provider -> async { provider.id to provider.suggest(bitmap) } }.awaitAll()
        }.toMap()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.photosolve_compare_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.photosolve_compare_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val snapshot = results
                if (snapshot == null) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    registry.all.forEach { provider ->
                        ComparisonCard(
                            bitmap = bitmap,
                            displayName = provider.displayName,
                            result = snapshot[provider.id],
                            onApply = onApply,
                        )
                    }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.ui_close))
                }
            }
        }
    }
}

@Composable
private fun ComparisonCard(
    bitmap: Bitmap,
    displayName: String,
    result: CropAssistResult?,
    onApply: (CropNorm) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(displayName, style = MaterialTheme.typography.titleMedium)
            Box(Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp, bottom = 8.dp)) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val containerW = constraints.maxWidth.toFloat()
                    val containerH = constraints.maxHeight.toFloat()
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    val box = (result as? CropAssistResult.SuggestedBox)?.norm
                    if (box != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            val scale = minOf(containerW / bitmap.width, containerH / bitmap.height)
                            val offsetX = (containerW - bitmap.width * scale) / 2f
                            val offsetY = (containerH - bitmap.height * scale) / 2f
                            val l = offsetX + box.left * bitmap.width * scale
                            val t = offsetY + box.top * bitmap.height * scale
                            val r = offsetX + box.right * bitmap.width * scale
                            val b = offsetY + box.bottom * bitmap.height * scale
                            drawRect(
                                color = Color(0xFF2E7D32),
                                topLeft = Offset(l, t),
                                size = Size(r - l, b - t),
                                style = Stroke(width = 3.dp.toPx()),
                            )
                        }
                    }
                }
            }

            when (result) {
                is CropAssistResult.SuggestedBox -> {
                    Text(
                        listOfNotNull(result.confidenceLabel, "${result.elapsedMs}ms").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = { onApply(result.norm) }, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.photosolve_use_this))
                    }
                }

                is CropAssistResult.Unavailable -> Text(
                    stringResource(R.string.photosolve_unavailable_reason, result.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                null -> CircularProgressIndicator(modifier = Modifier.height(24.dp))
            }
        }
    }
}
