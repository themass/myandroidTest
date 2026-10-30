package com.aimath.paizhao.photosolve.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openapi.ks.chat.R
import com.aimath.paizhao.photosolve.PhotoSolveResult
import com.aimath.paizhao.ui.theme.GreenAnswer
import com.aimath.paizhao.ui.theme.GreenAnswerContainer

@Composable
fun PhotoResultScreen(
    croppedBitmap: Bitmap?,
    result: PhotoSolveResult,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        if (croppedBitmap != null) {
            Image(
                bitmap = croppedBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(20.dp))
        }

        SectionLabel(stringResource(R.string.result_question_label))
        Text(result.recognizedQuestion, style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.result_steps_label))
        result.steps.forEachIndexed { index, step ->
            Row(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(step, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(20.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = GreenAnswerContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.result_answer_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = GreenAnswer,
                )
                Spacer(Modifier.height(4.dp))
                Text(result.finalAnswer, style = MaterialTheme.typography.titleLarge, color = GreenAnswer)
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRetake,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.result_retake))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(6.dp))
}
