package com.aimath.paizhao.photosolve.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openapi.ks.chat.R

@Composable
fun PhotoRecognizingScreen(
    croppedBitmap: Bitmap?,
    statusIndex: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusText = when (statusIndex) {
        0 -> stringResource(R.string.recognizing_step_1)
        1 -> stringResource(R.string.recognizing_step_2)
        else -> stringResource(R.string.recognizing_step_3)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (croppedBitmap != null) {
            Image(
                bitmap = croppedBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(32.dp))
        }
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(statusText, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.recognizing_cancel))
        }
    }
}
